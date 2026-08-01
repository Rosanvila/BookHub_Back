package com.eni.bookhub.service;

import com.eni.bookhub.dto.response.LoanResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Loan;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.LoanMapper;
import com.eni.bookhub.notification.MailService;
import com.eni.bookhub.repository.BookRepository;
import com.eni.bookhub.repository.LoanRepository;
import com.eni.bookhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests des règles de gestion des emprunts.
 * <p>
 * Les dépendances (accès aux données, envoi d'e-mails) sont remplacées par des mocks :
 * on isole la logique métier pour la vérifier sans base ni serveur de messagerie.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoanMapper loanMapper;
    @Mock
    private MailService mailService;

    @InjectMocks
    private LoanService loanService;

    @Captor
    private ArgumentCaptor<Loan> loanCaptor;

    private User user;
    private Book book;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .nom("Dupont")
                .prenom("Jean")
                .email("jean.dupont@email.com")
                .role(User.Role.UTILISATEUR)
                .build();

        book = Book.builder()
                .id(1)
                .titre("Dune")
                .auteur("Frank Herbert")
                .totalExemplaires(5)
                .exemplairesDisponibles(3)
                .build();
    }

    /** Place le contexte du cas nominal : livre disponible, adhérent sans emprunt ni retard. */
    private void givenBorrowIsAllowed() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(loanRepository.countByUtilisateurIdAndStatut(1, "EN COURS")).thenReturn(0);
        when(loanRepository.existsByUtilisateurIdAndStatut(1, "EN RETARD")).thenReturn(false);
    }

    // ── Emprunter un livre ─────────────────────────────────────────────────────

    @Test
    void borrowBook_allConditionsMet_savesLoanInProgress() {
        givenBorrowIsAllowed();
        when(loanMapper.toResponse(any())).thenReturn(LoanResponse.builder().statut("EN COURS").build());

        LoanResponse response = loanService.borrowBook(1, 1);

        verify(loanRepository).save(loanCaptor.capture());
        Loan saved = loanCaptor.getValue();
        assertThat(saved.getUtilisateur()).isEqualTo(user);
        assertThat(saved.getLivre()).isEqualTo(book);
        assertThat(saved.getStatut()).isEqualTo("EN COURS");
        assertThat(response.getStatut()).isEqualTo("EN COURS");
    }

    @Test
    void borrowBook_setsReturnDateFourteenDaysLater() {
        givenBorrowIsAllowed();

        loanService.borrowBook(1, 1);

        verify(loanRepository).save(loanCaptor.capture());
        Loan saved = loanCaptor.getValue();
        assertThat(saved.getDateRetourPrevue()).isEqualTo(saved.getDateEmprunt().plusDays(14));
    }

    @Test
    void borrowBook_doesNotDecrementStockItself() {
        // Le nombre d'exemplaires disponibles est mis à jour par un trigger SQL,
        // pas par le service : celui-ci ne doit donc jamais enregistrer le livre.
        givenBorrowIsAllowed();

        loanService.borrowBook(1, 1);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void borrowBook_unknownBook_isRejected() {
        when(bookRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.borrowBook(1, 99));

        assertThat(exception.getMessage()).isEqualTo("Livre introuvable");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowBook_unknownUser_isRejected() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.borrowBook(99, 1));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
    }

    @Test
    void borrowBook_noCopyLeft_isRejected() {
        book.setExemplairesDisponibles(0);
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.borrowBook(1, 1));

        assertThat(exception.getMessage()).isEqualTo("Livre non disponible");
    }

    @Test
    void borrowBook_threeLoansAlreadyInProgress_isRejected() {
        // Règle de gestion : trois emprunts simultanés au maximum par adhérent
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(loanRepository.countByUtilisateurIdAndStatut(1, "EN COURS")).thenReturn(3);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.borrowBook(1, 1));

        assertThat(exception.getMessage()).isEqualTo("Max 3 emprunts atteints");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void borrowBook_userHasOverdueLoan_isRejected() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(loanRepository.countByUtilisateurIdAndStatut(1, "EN COURS")).thenReturn(1);
        when(loanRepository.existsByUtilisateurIdAndStatut(1, "EN RETARD")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.borrowBook(1, 1));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur bloqué (retard)");
    }

    @Test
    void borrowBook_sendsConfirmationEmail() {
        givenBorrowIsAllowed();

        loanService.borrowBook(1, 1);

        verify(mailService).sendLoanConfirmation(any(Loan.class));
    }

    @Test
    void borrowBook_emailFailure_doesNotCancelLoan() {
        // L'envoi du courriel est accessoire : une panne du serveur de messagerie
        // ne doit pas empêcher l'adhérent d'emprunter.
        givenBorrowIsAllowed();
        doThrow(new RuntimeException("Serveur SMTP injoignable"))
                .when(mailService).sendLoanConfirmation(any());

        loanService.borrowBook(1, 1);

        verify(loanRepository).save(any());
    }

    // ── Rendre un livre ────────────────────────────────────────────────────────

    @Test
    void returnBook_loanInProgress_isMarkedAsReturned() {
        Loan loan = Loan.builder().id(10).utilisateur(user).livre(book).statut("EN COURS").build();
        when(loanRepository.findById(10)).thenReturn(Optional.of(loan));

        loanService.returnBook(10);

        assertThat(loan.getStatut()).isEqualTo("RENDU");
        assertThat(loan.getDateRetourEffective()).isNotNull();
        verify(loanRepository).save(loan);
    }

    @Test
    void returnBook_overdueLoan_isAlsoAccepted() {
        // Un retard n'empêche pas la restitution, il faut au contraire l'encourager
        Loan loan = Loan.builder().id(11).utilisateur(user).livre(book).statut("EN RETARD").build();
        when(loanRepository.findById(11)).thenReturn(Optional.of(loan));

        loanService.returnBook(11);

        assertThat(loan.getStatut()).isEqualTo("RENDU");
    }

    @Test
    void returnBook_alreadyReturned_isRejected() {
        Loan loan = Loan.builder().id(12).utilisateur(user).livre(book).statut("RENDU").build();
        when(loanRepository.findById(12)).thenReturn(Optional.of(loan));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.returnBook(12));

        assertThat(exception.getMessage()).isEqualTo("Emprunt déjà retourné");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void returnBook_unknownLoan_isRejected() {
        when(loanRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.returnBook(99));

        assertThat(exception.getMessage()).isEqualTo("Emprunt introuvable");
    }

    // ── Traitement planifié des retards ────────────────────────────────────────

    @Test
    void markOverdueLoans_switchesExpiredLoansToOverdue() {
        Loan expired = Loan.builder()
                .id(20)
                .statut("EN COURS")
                .dateRetourPrevue(LocalDateTime.now().minusDays(2))
                .build();
        when(loanRepository.findByStatutAndDateRetourPrevueBefore(eq("EN COURS"), any()))
                .thenReturn(List.of(expired));

        loanService.markOverdueLoans();

        assertThat(expired.getStatut()).isEqualTo("EN RETARD");
        verify(loanRepository).saveAll(List.of(expired));
    }

    @Test
    void markOverdueLoans_noExpiredLoan_savesNothingMeaningful() {
        when(loanRepository.findByStatutAndDateRetourPrevueBefore(eq("EN COURS"), any()))
                .thenReturn(List.of());

        loanService.markOverdueLoans();

        verify(loanRepository).saveAll(List.of());
    }

    // ── Consultation ───────────────────────────────────────────────────────────

    @Test
    void getUserLoans_returnsLoansOfTheGivenAccount() {
        Loan loan = Loan.builder().id(30).utilisateur(user).livre(book).statut("EN COURS").build();
        when(userRepository.findByEmail("jean.dupont@email.com")).thenReturn(Optional.of(user));
        when(loanRepository.findByUtilisateurIdAndStatutIn(eq(1), anyList())).thenReturn(List.of(loan));
        when(loanMapper.toResponse(loan)).thenReturn(LoanResponse.builder().id(30).build());

        List<LoanResponse> loans = loanService.getUserLoans("jean.dupont@email.com");

        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).getId()).isEqualTo(30);
    }

    @Test
    void getUserLoans_unknownEmail_isRejected() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> loanService.getUserLoans("inconnu@email.com"));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
    }

    @Test
    void getAllLoans_returnsEveryLoan() {
        Loan loan = Loan.builder().id(40).utilisateur(user).livre(book).statut("EN COURS").build();
        when(loanRepository.findAll()).thenReturn(List.of(loan));
        when(loanMapper.toResponse(loan)).thenReturn(LoanResponse.builder().id(40).build());

        assertThat(loanService.getAllLoans()).hasSize(1);
    }
}

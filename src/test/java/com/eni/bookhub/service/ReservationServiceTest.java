package com.eni.bookhub.service;

import com.eni.bookhub.dto.request.ReservationRequest;
import com.eni.bookhub.dto.response.ReservationResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Reservation;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.ReservationMapper;
import com.eni.bookhub.notification.MailService;
import com.eni.bookhub.repository.BookRepository;
import com.eni.bookhub.repository.ReservationRepository;
import com.eni.bookhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests des règles de gestion des réservations.
 * <p>
 * Ce service lève des {@link ResponseStatusException} : le code HTTP fait partie du
 * contrat métier, les tests le vérifient au même titre que le message.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final String EMAIL = "jean.dupont@email.com";

    /** Statuts considérés comme actifs par le service. */
    private static final List<Reservation.Status> ACTIFS =
            List.of(Reservation.Status.EN_ATTENTE, Reservation.Status.DISPONIBLE);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoanService loanService;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private MailService mailService;

    @InjectMocks
    private ReservationService reservationService;

    @Captor
    private ArgumentCaptor<Reservation> reservationCaptor;

    private User user;
    private Book book;
    private ReservationRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .nom("Dupont")
                .prenom("Jean")
                .email(EMAIL)
                .role(User.Role.UTILISATEUR)
                .build();

        book = Book.builder()
                .id(5)
                .titre("Dune")
                .totalExemplaires(5)
                .exemplairesDisponibles(2)
                .build();

        request = new ReservationRequest();
        request.setBookId(5);
    }

    private Reservation reservation(Reservation.Status status, int rang) {
        return Reservation.builder()
                .id(99)
                .user(user)
                .book(book)
                .reservationDate(LocalDateTime.now())
                .rankWaitingList(rang)
                .status(status)
                .build();
    }

    /** Place le contexte du cas nominal : adhérent connu, livre disponible, aucun blocage. */
    private void givenReservationIsAllowed(int reservationsEnAttente) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookRepository.findById(5)).thenReturn(Optional.of(book));
        when(reservationRepository.existsByUserIdAndBookIdAndStatusIn(1, 5, ACTIFS)).thenReturn(false);
        when(reservationRepository.countByUserIdAndStatusIn(1, ACTIFS)).thenReturn(0);
        when(reservationRepository.countByBookIdAndStatus(5, Reservation.Status.EN_ATTENTE))
                .thenReturn(reservationsEnAttente);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Créer une réservation ──────────────────────────────────────────────────

    @Test
    void createReservation_allConditionsMet_savesPendingReservation() {
        givenReservationIsAllowed(0);

        reservationService.createReservation(EMAIL, request);

        verify(reservationRepository).save(reservationCaptor.capture());
        Reservation saved = reservationCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getBook()).isEqualTo(book);
        assertThat(saved.getStatus()).isEqualTo(Reservation.Status.EN_ATTENTE);
    }

    @Test
    void createReservation_placesUserAtEndOfWaitingList() {
        // Trois adhérents attendent déjà : le nouveau prend le rang 4
        givenReservationIsAllowed(3);

        reservationService.createReservation(EMAIL, request);

        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getRankWaitingList()).isEqualTo(4);
    }

    @Test
    void createReservation_unknownUser_returnsNotFound() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.createReservation("inconnu@email.com", request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Utilisateur introuvable");
    }

    @Test
    void createReservation_unknownBook_returnsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookRepository.findById(5)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.createReservation(EMAIL, request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Livre introuvable");
    }

    @Test
    void createReservation_bookOutOfStock_returnsBadRequest() {
        book.setExemplairesDisponibles(0);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookRepository.findById(5)).thenReturn(Optional.of(book));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.createReservation(EMAIL, request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReservation_sameBookAlreadyReserved_returnsConflict() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookRepository.findById(5)).thenReturn(Optional.of(book));
        when(reservationRepository.existsByUserIdAndBookIdAndStatusIn(1, 5, ACTIFS)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.createReservation(EMAIL, request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("déjà une réservation active");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_fiveActiveReservations_returnsConflict() {
        // Règle de gestion : cinq réservations actives au maximum par adhérent
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookRepository.findById(5)).thenReturn(Optional.of(book));
        when(reservationRepository.existsByUserIdAndBookIdAndStatusIn(1, 5, ACTIFS)).thenReturn(false);
        when(reservationRepository.countByUserIdAndStatusIn(1, ACTIFS)).thenReturn(5);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.createReservation(EMAIL, request));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("maximum de 5 réservations");
        verify(reservationRepository, never()).save(any());
    }

    // ── Annuler une réservation ────────────────────────────────────────────────

    @Test
    void cancelReservation_ownerCancelsOwnReservation_setsStatusCancelled() {
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 2);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                5, Reservation.Status.EN_ATTENTE, 2)).thenReturn(List.of());

        reservationService.cancelReservation(EMAIL, 99, false);

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.ANNULEE);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void cancelReservation_shiftsFollowingUsersUpTheWaitingList() {
        // La réservation annulée était au rang 2 : les rangs 3 et 4 remontent en 2 et 3
        Reservation cancelled = reservation(Reservation.Status.EN_ATTENTE, 2);
        Reservation troisieme = Reservation.builder().id(100).book(book).rankWaitingList(3).build();
        Reservation quatrieme = Reservation.builder().id(101).book(book).rankWaitingList(4).build();

        when(reservationRepository.findById(99)).thenReturn(Optional.of(cancelled));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                5, Reservation.Status.EN_ATTENTE, 2)).thenReturn(List.of(troisieme, quatrieme));

        reservationService.cancelReservation(EMAIL, 99, true);

        assertThat(troisieme.getRankWaitingList()).isEqualTo(2);
        assertThat(quatrieme.getRankWaitingList()).isEqualTo(3);
        verify(reservationRepository).saveAll(List.of(troisieme, quatrieme));
    }

    @Test
    void cancelReservation_staffCancelsWithoutOwnershipCheck() {
        // Un bibliothécaire peut annuler la réservation d'un adhérent :
        // son propre compte n'est même pas consulté.
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                5, Reservation.Status.EN_ATTENTE, 1)).thenReturn(List.of());

        reservationService.cancelReservation("bibliothecaire@email.com", 99, true);

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.ANNULEE);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void cancelReservation_otherUsersReservation_returnsForbidden() {
        User autreAdherent = User.builder().id(2).email("autre@email.com").build();
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("autre@email.com")).thenReturn(Optional.of(autreAdherent));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.cancelReservation("autre@email.com", 99, false));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelReservation_alreadyCancelled_returnsBadRequest() {
        Reservation reservation = reservation(Reservation.Status.ANNULEE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.cancelReservation(EMAIL, 99, true));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("ne peut pas être annulée");
    }

    @Test
    void cancelReservation_unknownReservation_returnsNotFound() {
        when(reservationRepository.findById(404)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.cancelReservation(EMAIL, 404, true));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Réservation introuvable");
    }

    // ── Valider une réservation ────────────────────────────────────────────────

    @Test
    void validateReservation_pendingReservation_createsLoanAndMarksAvailable() {
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                5, Reservation.Status.EN_ATTENTE, 1)).thenReturn(List.of());

        reservationService.validateReservation(99);

        verify(loanService).borrowBook(1, 5);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.DISPONIBLE);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void validateReservation_notifiesUserByEmail() {
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                any(), any(), any())).thenReturn(List.of());

        reservationService.validateReservation(99);

        verify(mailService).sendReservationAvailable(reservation);
    }

    @Test
    void validateReservation_emailFailure_doesNotCancelValidation() {
        // Comme pour l'emprunt, une panne du serveur de messagerie ne doit pas
        // remettre en cause l'opération métier déjà enregistrée.
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findByBookIdAndStatusAndRankWaitingListGreaterThan(
                any(), any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("Serveur SMTP injoignable"))
                .when(mailService).sendReservationAvailable(any());

        reservationService.validateReservation(99);

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.DISPONIBLE);
    }

    @Test
    void validateReservation_statusIsNotPending_returnsBadRequest() {
        Reservation reservation = reservation(Reservation.Status.DISPONIBLE, 1);
        when(reservationRepository.findById(99)).thenReturn(Optional.of(reservation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.validateReservation(99));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).contains("EN_ATTENTE");
        verify(loanService, never()).borrowBook(any(), any());
    }

    // ── Consultation ───────────────────────────────────────────────────────────

    @Test
    void getMyReservations_returnsReservationsOfTheGivenAccount() {
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(reservationRepository.findByUserId(1)).thenReturn(List.of(reservation));
        when(reservationMapper.toResponse(reservation))
                .thenReturn(ReservationResponse.builder().id(99).build());

        List<ReservationResponse> reservations = reservationService.getMyReservations(EMAIL);

        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getId()).isEqualTo(99);
    }

    @Test
    void getMyReservations_unknownEmail_returnsNotFound() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> reservationService.getMyReservations("inconnu@email.com"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("Utilisateur introuvable");
    }

    @Test
    void getAllReservations_returnsEveryReservation() {
        Reservation reservation = reservation(Reservation.Status.EN_ATTENTE, 1);
        when(reservationRepository.findAll()).thenReturn(List.of(reservation));
        when(reservationMapper.toResponse(reservation))
                .thenReturn(ReservationResponse.builder().id(99).build());

        assertThat(reservationService.getAllReservations()).hasSize(1);
    }
}

package com.eni.bookhub.repository;

import com.eni.bookhub.AbstractIntegrationTest;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Category;
import com.eni.bookhub.entity.Loan;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du dépôt des emprunts.
 * <p>
 * Ces requêtes portent les contrôles appliqués avant chaque emprunt : nombre de prêts
 * en cours, présence d'un retard, et détection des emprunts arrivés à échéance.
 */
class LoanRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private User adherent;
    private Book livre;

    @BeforeEach
    void setUp() {
        Category categorie = categoryRepository.save(Category.builder().nom("Science-Fiction").build());

        adherent = userRepository.save(User.builder()
                .nom("Dupont")
                .prenom("Jean")
                .email("jean.dupont@email.com")
                .telephone("0612345678")
                .motDePasse("$2a$12$empreinte-de-test")
                .role(User.Role.UTILISATEUR)
                .dateCreation(LocalDateTime.now())
                .build());

        livre = bookRepository.save(Book.builder()
                .titre("Dune")
                .auteur("Frank Herbert")
                .isbn("978-2266320481")
                .dateParution(LocalDate.of(1965, 8, 1))
                .nombrePages(880)
                .description("Un classique de la science-fiction")
                .totalExemplaires(5)
                .exemplairesDisponibles(3)
                .categorie(categorie)
                .build());
    }

    private Loan saveLoan(String statut, LocalDateTime dateRetourPrevue) {
        return loanRepository.save(Loan.builder()
                .utilisateur(adherent)
                .livre(livre)
                .dateEmprunt(LocalDateTime.now())
                .dateRetourPrevue(dateRetourPrevue)
                .statut(statut)
                .build());
    }

    private LocalDateTime dansDeuxSemaines() {
        return LocalDateTime.now().plusDays(14);
    }

    // ── Contrôle avant suppression d'un livre ──────────────────────────────────

    @Test
    void existsByLivreIdAndStatutIn_bookStillBorrowed_returnsTrue() {
        // Ce contrôle empêche la suppression d'un livre encore détenu par un adhérent
        saveLoan("EN COURS", dansDeuxSemaines());

        assertThat(loanRepository.existsByLivreIdAndStatutIn(
                livre.getId(), List.of("EN COURS", "EN RETARD"))).isTrue();
    }

    @Test
    void existsByLivreIdAndStatutIn_allCopiesReturned_returnsFalse() {
        saveLoan("RENDU", dansDeuxSemaines());

        assertThat(loanRepository.existsByLivreIdAndStatutIn(
                livre.getId(), List.of("EN COURS", "EN RETARD"))).isFalse();
    }

    // ── Contrôles avant un nouvel emprunt ──────────────────────────────────────

    @Test
    void countByUtilisateurIdAndStatut_countsOnlyLoansInProgress() {
        // Le service compare ce résultat à la limite de trois emprunts simultanés
        saveLoan("EN COURS", dansDeuxSemaines());
        saveLoan("EN COURS", dansDeuxSemaines());
        saveLoan("RENDU", dansDeuxSemaines());

        assertThat(loanRepository.countByUtilisateurIdAndStatut(adherent.getId(), "EN COURS"))
                .isEqualTo(2);
    }

    @Test
    void existsByUtilisateurIdAndStatut_memberHasOverdueLoan_returnsTrue() {
        saveLoan("EN RETARD", LocalDateTime.now().minusDays(1));

        assertThat(loanRepository.existsByUtilisateurIdAndStatut(adherent.getId(), "EN RETARD")).isTrue();
    }

    @Test
    void existsByUtilisateurIdAndStatut_noOverdueLoan_returnsFalse() {
        saveLoan("EN COURS", dansDeuxSemaines());

        assertThat(loanRepository.existsByUtilisateurIdAndStatut(adherent.getId(), "EN RETARD")).isFalse();
    }

    // ── Historique de l'adhérent ───────────────────────────────────────────────

    @Test
    void findByUtilisateurIdAndStatutIn_filtersOnRequestedStatuses() {
        saveLoan("EN COURS", dansDeuxSemaines());
        saveLoan("EN RETARD", LocalDateTime.now().minusDays(1));
        saveLoan("RENDU", LocalDateTime.now().plusDays(7));

        List<Loan> emprunts = loanRepository.findByUtilisateurIdAndStatutIn(
                adherent.getId(), List.of("EN COURS", "EN RETARD"));

        assertThat(emprunts).extracting(Loan::getStatut)
                .containsExactlyInAnyOrder("EN COURS", "EN RETARD");
    }

    // ── Traitement planifié des retards ────────────────────────────────────────

    @Test
    void findByStatutAndDateRetourPrevueBefore_returnsExpiredLoansOnly() {
        // Requête utilisée toutes les heures pour basculer les emprunts échus en retard
        saveLoan("EN COURS", LocalDateTime.now().minusDays(2));
        saveLoan("EN COURS", dansDeuxSemaines());

        List<Loan> echus = loanRepository.findByStatutAndDateRetourPrevueBefore(
                "EN COURS", LocalDateTime.now());

        assertThat(echus).hasSize(1);
        assertThat(echus.get(0).getDateRetourPrevue()).isBefore(LocalDateTime.now());
    }

    @Test
    void findByStatutAndDateRetourPrevueBefore_ignoresLoansAlreadyReturned() {
        saveLoan("RENDU", LocalDateTime.now().minusDays(2));

        assertThat(loanRepository.findByStatutAndDateRetourPrevueBefore("EN COURS", LocalDateTime.now()))
                .isEmpty();
    }
}

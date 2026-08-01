package com.eni.bookhub.repository;

import com.eni.bookhub.AbstractIntegrationTest;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Category;
import com.eni.bookhub.entity.Reservation;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.eni.bookhub.entity.Reservation.Status.ANNULEE;
import static com.eni.bookhub.entity.Reservation.Status.DISPONIBLE;
import static com.eni.bookhub.entity.Reservation.Status.EN_ATTENTE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du dépôt des réservations.
 * <p>
 * Ces requêtes gèrent la file d'attente : position d'un adhérent, décompte des
 * réservations actives, et sélection des rangs à décaler après une annulation.
 */
class ReservationRepositoryIT extends AbstractIntegrationTest {

    /** Statuts considérés comme actifs par la couche métier. */
    private static final List<Reservation.Status> ACTIFS = List.of(EN_ATTENTE, DISPONIBLE);

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private Category categorie;
    private User jean;
    private Book dune;

    @BeforeEach
    void setUp() {
        categorie = categoryRepository.save(Category.builder().nom("Science-Fiction").build());
        jean = saveUser("jean.dupont@email.com", "0612345678");
        dune = saveBook("Dune", "978-2266320481");
    }

    private User saveUser(String email, String telephone) {
        return userRepository.save(User.builder()
                .nom("Dupont")
                .prenom("Jean")
                .email(email)
                .telephone(telephone)
                .motDePasse("$2a$12$empreinte-de-test")
                .role(User.Role.UTILISATEUR)
                .dateCreation(LocalDateTime.now())
                .build());
    }

    private Book saveBook(String titre, String isbn) {
        return bookRepository.save(Book.builder()
                .titre(titre)
                .auteur("Auteur de test")
                .isbn(isbn)
                .dateParution(LocalDate.of(1965, 8, 1))
                .nombrePages(880)
                .description("Description de test")
                .totalExemplaires(5)
                // Un livre se réserve lorsqu'il n'a plus d'exemplaire disponible
                .exemplairesDisponibles(0)
                .categorie(categorie)
                .build());
    }

    private Reservation saveReservation(User user, Book book, Reservation.Status statut, int rang) {
        return reservationRepository.save(Reservation.builder()
                .user(user)
                .book(book)
                .reservationDate(LocalDateTime.now())
                .rankWaitingList(rang)
                .status(statut)
                .build());
    }

    // ── Réservations d'un adhérent ─────────────────────────────────────────────

    @Test
    void findByUserId_returnsOnlyReservationsOfThatMember() {
        saveReservation(jean, dune, EN_ATTENTE, 1);
        saveReservation(saveUser("marie.curie@email.com", "0798765432"), dune, EN_ATTENTE, 2);

        List<Reservation> reservations = reservationRepository.findByUserId(jean.getId());

        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getUser().getId()).isEqualTo(jean.getId());
    }

    // ── Contrôle du doublon ────────────────────────────────────────────────────

    @Test
    void existsByUserIdAndBookIdAndStatusIn_activeReservation_returnsTrue() {
        // Empêche un adhérent de réserver deux fois le même ouvrage
        saveReservation(jean, dune, EN_ATTENTE, 1);

        assertThat(reservationRepository.existsByUserIdAndBookIdAndStatusIn(
                jean.getId(), dune.getId(), ACTIFS)).isTrue();
    }

    @Test
    void existsByUserIdAndBookIdAndStatusIn_cancelledReservation_returnsFalse() {
        // Après une annulation, l'adhérent doit pouvoir réserver de nouveau
        saveReservation(jean, dune, ANNULEE, 1);

        assertThat(reservationRepository.existsByUserIdAndBookIdAndStatusIn(
                jean.getId(), dune.getId(), ACTIFS)).isFalse();
    }

    // ── Décomptes ──────────────────────────────────────────────────────────────

    @Test
    void countByUserIdAndStatusIn_countsActiveReservationsAcrossBooks() {
        // Ce total est comparé à la limite de cinq réservations actives
        Book autreLivre = saveBook("1984", "978-0451524935");
        saveReservation(jean, dune, EN_ATTENTE, 1);
        saveReservation(jean, autreLivre, DISPONIBLE, 1);
        saveReservation(jean, autreLivre, ANNULEE, 0);

        assertThat(reservationRepository.countByUserIdAndStatusIn(jean.getId(), ACTIFS)).isEqualTo(2);
    }

    @Test
    void countByBookIdAndStatus_givesLengthOfWaitingList() {
        // Le service s'en sert pour attribuer son rang au prochain adhérent
        saveReservation(jean, dune, EN_ATTENTE, 1);
        saveReservation(saveUser("marie.curie@email.com", "0798765432"), dune, EN_ATTENTE, 2);
        saveReservation(saveUser("paul.martin@email.com", "0755555555"), dune, ANNULEE, 0);

        assertThat(reservationRepository.countByBookIdAndStatus(dune.getId(), EN_ATTENTE)).isEqualTo(2);
    }

    // ── Décalage de la file après une annulation ───────────────────────────────

    @Test
    void findByBookIdAndStatusAndRankWaitingListGreaterThan_returnsFollowingMembersOnly() {
        saveReservation(jean, dune, EN_ATTENTE, 1);
        saveReservation(saveUser("marie.curie@email.com", "0798765432"), dune, EN_ATTENTE, 2);
        saveReservation(saveUser("paul.martin@email.com", "0755555555"), dune, EN_ATTENTE, 3);

        List<Reservation> aDecaler = reservationRepository
                .findByBookIdAndStatusAndRankWaitingListGreaterThan(dune.getId(), EN_ATTENTE, 1);

        assertThat(aDecaler).extracting(Reservation::getRankWaitingList)
                .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void findByBookIdAndStatusAndRankWaitingListGreaterThan_lastRank_returnsEmpty() {
        saveReservation(jean, dune, EN_ATTENTE, 1);

        assertThat(reservationRepository
                .findByBookIdAndStatusAndRankWaitingListGreaterThan(dune.getId(), EN_ATTENTE, 1))
                .isEmpty();
    }

    // ── Ordre chronologique de la file ─────────────────────────────────────────

    @Test
    void findByBookIdAndStatusOrderByReservationDateAsc_returnsOldestFirst() {
        Reservation premiere = saveReservation(jean, dune, EN_ATTENTE, 1);
        premiere.setReservationDate(LocalDateTime.now().minusDays(3));
        reservationRepository.save(premiere);

        saveReservation(saveUser("marie.curie@email.com", "0798765432"), dune, EN_ATTENTE, 2);

        List<Reservation> file = reservationRepository
                .findByBookIdAndStatusOrderByReservationDateAsc(dune.getId(), EN_ATTENTE);

        assertThat(file).hasSize(2);
        assertThat(file.get(0).getReservationDate()).isBefore(file.get(1).getReservationDate());
    }
}

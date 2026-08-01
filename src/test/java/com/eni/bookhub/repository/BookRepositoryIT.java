package com.eni.bookhub.repository;

import com.eni.bookhub.AbstractIntegrationTest;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Category;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du dépôt du catalogue.
 * <p>
 * Plusieurs requêtes reposent sur des fonctions propres à SQL Server, comme
 * {@code YEAR(...)} utilisée pour calculer les bornes du filtre par année. C'est
 * précisément pour ces cas qu'une base réelle est nécessaire : une base en mémoire
 * ne les interpréterait pas de la même façon.
 */
class BookRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private Category categorie;

    @BeforeEach
    void setUp() {
        categorie = categoryRepository.save(Category.builder().nom("Science-Fiction").build());
    }

    private Book saveBook(String titre, String auteur, String isbn, LocalDate dateParution,
                          String urlCouverture, int total, int disponibles) {
        return bookRepository.save(Book.builder()
                .titre(titre)
                .auteur(auteur)
                .isbn(isbn)
                .dateParution(dateParution)
                .nombrePages(300)
                .description("Description de test")
                .urlCouverture(urlCouverture)
                .totalExemplaires(total)
                .exemplairesDisponibles(disponibles)
                .categorie(categorie)
                .build());
    }

    private Book saveBook(String titre, LocalDate dateParution) {
        return saveBook(titre, "Auteur de test", "ISBN-" + titre, dateParution, null, 5, 5);
    }

    // ── Bornes du filtre par année ─────────────────────────────────────────────

    @Test
    void findMinYear_returnsEarliestPublicationYear() {
        saveBook("Le plus ancien", LocalDate.of(1950, 1, 1));
        saveBook("Le plus récent", LocalDate.of(2020, 6, 15));

        assertThat(bookRepository.findMinYear()).isEqualTo(1950);
    }

    @Test
    void findMaxYear_returnsLatestPublicationYear() {
        saveBook("Le plus ancien", LocalDate.of(1950, 1, 1));
        saveBook("Le plus récent", LocalDate.of(2020, 6, 15));

        assertThat(bookRepository.findMaxYear()).isEqualTo(2020);
    }

    @Test
    void findMinYear_emptyCatalog_returnsNull() {
        // Le service remplace ce null par une valeur par défaut
        assertThat(bookRepository.findMinYear()).isNull();
    }

    // ── Totaux du tableau de bord ──────────────────────────────────────────────

    @Test
    void sumTotalExemplaires_addsUpEveryCopy() {
        saveBook("Dune", "Frank Herbert", "ISBN-001", LocalDate.of(1965, 8, 1), null, 5, 3);
        saveBook("1984", "George Orwell", "ISBN-002", LocalDate.of(1949, 6, 8), null, 3, 1);

        assertThat(bookRepository.sumTotalExemplaires()).isEqualTo(8);
    }

    @Test
    void sumExemplairesDisponibles_addsUpAvailableCopiesOnly() {
        saveBook("Dune", "Frank Herbert", "ISBN-001", LocalDate.of(1965, 8, 1), null, 5, 3);
        saveBook("1984", "George Orwell", "ISBN-002", LocalDate.of(1949, 6, 8), null, 3, 1);

        assertThat(bookRepository.sumExemplairesDisponibles()).isEqualTo(4);
    }

    @Test
    void sumTotalExemplaires_emptyCatalog_returnsZeroNotNull() {
        // Le COALESCE de la requête évite un null qui ferait échouer le calcul des statistiques
        assertThat(bookRepository.sumTotalExemplaires()).isZero();
    }

    // ── Recherche plein texte ──────────────────────────────────────────────────

    @Test
    void searchByTitreAuteurOrIsbn_matchesRegardlessOfCase() {
        saveBook("Dune", "Frank Herbert", "978-2266320481", LocalDate.of(1965, 8, 1), null, 5, 5);
        saveBook("1984", "George Orwell", "978-0451524935", LocalDate.of(1949, 6, 8), null, 3, 3);

        Page<Book> resultat = bookRepository
                .findByTitreContainingIgnoreCaseOrAuteurContainingIgnoreCaseOrIsbnContainingIgnoreCase(
                        "dune", "dune", "dune", PageRequest.of(0, 20));

        assertThat(resultat.getContent()).extracting(Book::getTitre).containsExactly("Dune");
    }

    @Test
    void searchByTitreAuteurOrIsbn_matchesOnAuthorToo() {
        saveBook("Dune", "Frank Herbert", "978-2266320481", LocalDate.of(1965, 8, 1), null, 5, 5);
        saveBook("1984", "George Orwell", "978-0451524935", LocalDate.of(1949, 6, 8), null, 3, 3);

        Page<Book> resultat = bookRepository
                .findByTitreContainingIgnoreCaseOrAuteurContainingIgnoreCaseOrIsbnContainingIgnoreCase(
                        "orwell", "orwell", "orwell", PageRequest.of(0, 20));

        assertThat(resultat.getContent()).extracting(Book::getTitre).containsExactly("1984");
    }

    // ── Récupération automatique des couvertures ───────────────────────────────

    @Test
    void findByUrlCouvertureIsNull_returnsOnlyBooksWithoutCover() {
        // Cette liste alimente le traitement planifié qui interroge OpenLibrary
        saveBook("Sans couverture", "Auteur", "ISBN-001", LocalDate.of(2000, 1, 1), null, 5, 5);
        saveBook("Avec couverture", "Auteur", "ISBN-002", LocalDate.of(2000, 1, 1),
                "http://couvertures/livre.jpg", 5, 5);

        List<Book> sansCouverture = bookRepository.findByUrlCouvertureIsNull();

        assertThat(sansCouverture).extracting(Book::getTitre).containsExactly("Sans couverture");
    }

    @Test
    void updateCoverUrl_persistsNewUrl() {
        Book livre = saveBook("Dune", LocalDate.of(1965, 8, 1));

        bookRepository.updateCoverUrl(livre.getId(), "http://couvertures/dune.jpg");

        /*
         * La requête JPQL annotée @Modifying s'exécute directement en base et ne met pas
         * à jour l'instance déjà chargée en mémoire. On vide le cache de premier niveau
         * pour forcer une relecture et vérifier ce qui a réellement été écrit.
         */
        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(livre.getId()))
                .isPresent()
                .get()
                .extracting(Book::getUrlCouverture)
                .isEqualTo("http://couvertures/dune.jpg");
    }
}

package com.eni.bookhub.service;

import com.eni.bookhub.dto.request.BookRequest;
import com.eni.bookhub.dto.response.BookResponse;
import com.eni.bookhub.dto.response.BookStatsResponse;
import com.eni.bookhub.dto.response.BookSummaryResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Category;
import com.eni.bookhub.mapper.BookMapper;
import com.eni.bookhub.repository.BookRepository;
import com.eni.bookhub.repository.CategoryRepository;
import com.eni.bookhub.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du service de gestion du catalogue.
 * <p>
 * La recherche multicritère construit une {@link Specification} JPA. Le contenu exact
 * de la spécification ne peut pas être vérifié ici sans base de données : ces tests
 * s'assurent que la recherche est bien déléguée au dépôt et que la pagination est
 * respectée, le comportement réel des filtres étant couvert par les tests d'intégration.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private BookService bookService;

    @Captor
    private ArgumentCaptor<Book> bookCaptor;

    private Category categorie;
    private Book book;

    @BeforeEach
    void setUp() {
        categorie = Category.builder().id(2).nom("Science-Fiction").build();

        book = Book.builder()
                .id(1)
                .titre("Dune")
                .auteur("Frank Herbert")
                .isbn("978-2266320481")
                .dateParution(LocalDate.of(1965, 8, 1))
                .nombrePages(880)
                .description("Un classique de la science-fiction")
                .totalExemplaires(5)
                .exemplairesDisponibles(3)
                .categorie(categorie)
                .build();
    }

    private BookRequest bookRequest() {
        BookRequest request = new BookRequest();
        request.setTitre("Le Hobbit");
        request.setAuteur("J.R.R. Tolkien");
        request.setIsbn("978-2266282727");
        request.setDateParution(LocalDate.of(1937, 9, 21));
        request.setNombrePages(320);
        request.setDescription("Le voyage de Bilbon");
        request.setUrlCouverture("http://couvertures/hobbit.jpg");
        request.setTotalExemplaires(4);
        request.setCategorieId(2);
        return request;
    }

    // ── Consultation du catalogue ──────────────────────────────────────────────

    @Test
    void getAllBooks_returnsPageOfSummaries() {
        when(bookRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(book)));
        when(bookMapper.toSummaryResponse(book))
                .thenReturn(BookSummaryResponse.builder().id(1).titre("Dune").build());

        Page<BookSummaryResponse> page = bookService.getAllBooks(PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitre()).isEqualTo("Dune");
    }

    @Test
    void getById_existingBook_returnsDetailedResponse() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(bookMapper.toResponse(book)).thenReturn(BookResponse.builder().id(1).titre("Dune").build());

        assertThat(bookService.getById(1).getTitre()).isEqualTo("Dune");
    }

    @Test
    void getById_unknownBook_isRejected() {
        when(bookRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookService.getById(99));

        assertThat(exception.getMessage()).isEqualTo("Livre introuvable");
    }

    // ── Recherche multicritère ─────────────────────────────────────────────────

    @Test
    void search_withoutCriteria_stillQueriesRepository() {
        when(bookRepository.findAll(any(Specification.class), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(book)));
        when(bookMapper.toSummaryResponse(book))
                .thenReturn(BookSummaryResponse.builder().id(1).build());

        Page<BookSummaryResponse> page = bookService.search(null, null, null, null, null, PAGE);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void search_blankQuery_isTreatedAsNoCriteria() {
        // Une chaîne vide envoyée par le formulaire ne doit pas filtrer le catalogue
        when(bookRepository.findAll(any(Specification.class), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(book)));
        when(bookMapper.toSummaryResponse(book))
                .thenReturn(BookSummaryResponse.builder().id(1).build());

        assertThat(bookService.search("   ", "  ", null, null, null, PAGE).getContent()).hasSize(1);
    }

    @Test
    void search_allCriteriaProvided_returnsMatchingPage() {
        when(bookRepository.findAll(any(Specification.class), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(book)));
        when(bookMapper.toSummaryResponse(book))
                .thenReturn(BookSummaryResponse.builder().id(1).titre("Dune").build());

        Page<BookSummaryResponse> page =
                bookService.search("dune", "Science-Fiction", true, 1900, 2000, PAGE);

        assertThat(page.getContent()).hasSize(1);
        verify(bookRepository).findAll(any(Specification.class), eq(PAGE));
    }

    @Test
    void search_noResult_returnsEmptyPage() {
        when(bookRepository.findAll(any(Specification.class), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(bookService.search("titre inexistant", null, null, null, null, PAGE)).isEmpty();
    }

    // ── Bornes du filtre par année ─────────────────────────────────────────────

    @Test
    void getYearRange_returnsBoundsFromCatalog() {
        when(bookRepository.findMinYear()).thenReturn(1950);
        when(bookRepository.findMaxYear()).thenReturn(2020);

        assertThat(bookService.getYearRange()).containsExactly(1950, 2020);
    }

    @Test
    void getYearRange_emptyCatalog_returnsDefaultBounds() {
        // Sans aucun livre, le filtre du front doit tout de même disposer d'un intervalle
        when(bookRepository.findMinYear()).thenReturn(null);
        when(bookRepository.findMaxYear()).thenReturn(null);

        assertThat(bookService.getYearRange()).containsExactly(1800, Year.now().getValue());
    }

    // ── Statistiques ───────────────────────────────────────────────────────────

    @Test
    void getStats_computesBorrowedCopiesFromTotalAndAvailable() {
        when(bookRepository.count()).thenReturn(6L);
        when(bookRepository.sumTotalExemplaires()).thenReturn(30L);
        when(bookRepository.sumExemplairesDisponibles()).thenReturn(22L);

        BookStatsResponse stats = bookService.getStats();

        assertThat(stats.getTotalTitres()).isEqualTo(6);
        assertThat(stats.getTotalExemplaires()).isEqualTo(30);
        assertThat(stats.getDisponibles()).isEqualTo(22);
        assertThat(stats.getEnPret()).isEqualTo(8);
    }

    // ── Création ───────────────────────────────────────────────────────────────

    @Test
    void createBook_newBook_isFullyAvailableOnCreation() {
        // À l'entrée en stock, tous les exemplaires sont disponibles
        when(categoryRepository.findById(2)).thenReturn(Optional.of(categorie));
        when(bookRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        bookService.createBook(bookRequest());

        verify(bookRepository).save(bookCaptor.capture());
        Book saved = bookCaptor.getValue();
        assertThat(saved.getTitre()).isEqualTo("Le Hobbit");
        assertThat(saved.getCategorie()).isEqualTo(categorie);
        assertThat(saved.getTotalExemplaires()).isEqualTo(4);
        assertThat(saved.getExemplairesDisponibles()).isEqualTo(4);
    }

    @Test
    void createBook_unknownCategory_isRejected() {
        when(categoryRepository.findById(2)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.createBook(bookRequest()));

        assertThat(exception.getReason()).isEqualTo("Catégorie introuvable");
        verify(bookRepository, never()).save(any());
    }

    // ── Modification ───────────────────────────────────────────────────────────

    @Test
    void updateBook_existingBook_appliesNewValues() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(categorie));

        bookService.updateBook(1, bookRequest());

        assertThat(book.getTitre()).isEqualTo("Le Hobbit");
        assertThat(book.getAuteur()).isEqualTo("J.R.R. Tolkien");
        assertThat(book.getTotalExemplaires()).isEqualTo(4);
    }

    @Test
    void updateBook_doesNotResetAvailableCopies() {
        // Trois exemplaires sur cinq étaient disponibles : modifier la fiche
        // ne doit pas faire réapparaître les deux exemplaires empruntés.
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(categorie));

        bookService.updateBook(1, bookRequest());

        assertThat(book.getExemplairesDisponibles()).isEqualTo(3);
    }

    @Test
    void updateBook_unknownBook_isRejected() {
        when(bookRepository.findById(99)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.updateBook(99, bookRequest()));

        assertThat(exception.getReason()).isEqualTo("Livre introuvable");
    }

    @Test
    void updateBook_unknownCategory_isRejected() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(categoryRepository.findById(2)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.updateBook(1, bookRequest()));

        assertThat(exception.getReason()).isEqualTo("Catégorie introuvable");
    }

    // ── Suppression ────────────────────────────────────────────────────────────

    @Test
    void deleteBook_noLoanInProgress_deletesBook() {
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(loanRepository.existsByLivreIdAndStatutIn(1, List.of("EN COURS", "EN RETARD")))
                .thenReturn(false);

        bookService.deleteBook(1);

        verify(bookRepository).delete(book);
    }

    @Test
    void deleteBook_loanStillInProgress_isRejected() {
        // Règle de gestion : on ne supprime pas un livre encore détenu par un adhérent
        when(bookRepository.findById(1)).thenReturn(Optional.of(book));
        when(loanRepository.existsByLivreIdAndStatutIn(1, List.of("EN COURS", "EN RETARD")))
                .thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> bookService.deleteBook(1));

        assertThat(exception.getMessage()).contains("des emprunts sont en cours");
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void deleteBook_unknownBook_isRejected() {
        when(bookRepository.findById(99)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.deleteBook(99));

        assertThat(exception.getReason()).isEqualTo("Livre introuvable");
    }
}

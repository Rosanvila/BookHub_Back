package com.eni.bookhub.mapper;

import com.eni.bookhub.dto.response.BookResponse;
import com.eni.bookhub.dto.response.BookSummaryResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le mapper ne dépend d'aucun autre composant : on l'instancie directement,
 * sans mock ni contexte Spring.
 */
class BookMapperTest {

    private final BookMapper mapper = new BookMapper();

    private Book book;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .id(1)
                .titre("Dune")
                .auteur("Frank Herbert")
                .isbn("978-2266320481")
                .dateParution(LocalDate.of(1965, 8, 1))
                .nombrePages(880)
                .description("Un classique de la science-fiction")
                .urlCouverture("http://couvertures/dune.jpg")
                .totalExemplaires(5)
                .exemplairesDisponibles(3)
                .categorie(Category.builder().id(2).nom("Science-Fiction").build())
                .build();
    }

    @Test
    void toResponse_mapsAllBookFields() {
        BookResponse response = mapper.toResponse(book);

        assertThat(response.getId()).isEqualTo(1);
        assertThat(response.getTitre()).isEqualTo("Dune");
        assertThat(response.getAuteur()).isEqualTo("Frank Herbert");
        assertThat(response.getIsbn()).isEqualTo("978-2266320481");
        assertThat(response.getDateParution()).isEqualTo(LocalDate.of(1965, 8, 1));
        assertThat(response.getNombrePages()).isEqualTo(880);
        assertThat(response.getDescription()).isEqualTo("Un classique de la science-fiction");
        assertThat(response.getUrlCouverture()).isEqualTo("http://couvertures/dune.jpg");
        assertThat(response.getTotalExemplaires()).isEqualTo(5);
        assertThat(response.getExemplairesDisponibles()).isEqualTo(3);
    }

    @Test
    void toResponse_flattensCategoryIntoNameAndId() {
        // Le client reçoit le nom et l'identifiant de la catégorie, pas l'objet imbriqué
        BookResponse response = mapper.toResponse(book);

        assertThat(response.getCategorie()).isEqualTo("Science-Fiction");
        assertThat(response.getCategorieId()).isEqualTo(2);
    }

    @Test
    void toResponse_bookWithoutCover_mapsNullUrl() {
        // Un livre créé sans couverture attend que le planificateur la récupère
        book.setUrlCouverture(null);

        BookResponse response = mapper.toResponse(book);

        assertThat(response.getUrlCouverture()).isNull();
    }

    @Test
    void toSummaryResponse_keepsOnlyFieldsNeededForCatalog() {
        BookSummaryResponse summary = mapper.toSummaryResponse(book);

        assertThat(summary.getId()).isEqualTo(1);
        assertThat(summary.getTitre()).isEqualTo("Dune");
        assertThat(summary.getAuteur()).isEqualTo("Frank Herbert");
        assertThat(summary.getUrlCouverture()).isEqualTo("http://couvertures/dune.jpg");
        assertThat(summary.getTotalExemplaires()).isEqualTo(5);
        assertThat(summary.getExemplairesDisponibles()).isEqualTo(3);
        assertThat(summary.getCategorie()).isEqualTo("Science-Fiction");
        assertThat(summary.getDateParution()).isEqualTo(LocalDate.of(1965, 8, 1));
    }
}

package com.eni.bookhub.repository;

import com.eni.bookhub.AbstractIntegrationTest;
import com.eni.bookhub.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du dépôt des catégories.
 * <p>
 * Seule {@code findByNomIgnoreCase} est une requête écrite pour BookHub : c'est donc
 * la seule qui mérite d'être testée. Les méthodes héritées de Spring Data
 * ({@code save}, {@code findById}...) sont déjà couvertes par le framework lui-même.
 */
class CategoryRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository.save(Category.builder().nom("Science-Fiction").build());
    }

    @Test
    void findByNomIgnoreCase_exactName_returnsCategory() {
        assertThat(categoryRepository.findByNomIgnoreCase("Science-Fiction"))
                .isPresent()
                .get()
                .extracting(Category::getNom)
                .isEqualTo("Science-Fiction");
    }

    @Test
    void findByNomIgnoreCase_differentCase_stillReturnsCategory() {
        // Le filtre par catégorie du catalogue ne doit pas dépendre de la casse saisie
        assertThat(categoryRepository.findByNomIgnoreCase("science-fiction")).isPresent();
        assertThat(categoryRepository.findByNomIgnoreCase("SCIENCE-FICTION")).isPresent();
    }

    @Test
    void findByNomIgnoreCase_unknownName_returnsEmpty() {
        assertThat(categoryRepository.findByNomIgnoreCase("Poésie")).isEmpty();
    }
}

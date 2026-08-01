package com.eni.bookhub.service;

import com.eni.bookhub.dto.response.CategoryResponse;
import com.eni.bookhub.entity.Category;
import com.eni.bookhub.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Le service de catégories se contente de lire et de trier : le tri alphabétique
 * est son seul comportement propre, c'est donc ce que l'on vérifie.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    /** Renvoie les catégories dans un ordre volontairement non alphabétique. */
    private void givenCategoriesInDatabase() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(3).nom("Thriller").build(),
                Category.builder().id(1).nom("Informatique").build(),
                Category.builder().id(2).nom("Science-Fiction").build()));
    }

    @Test
    void getAllCategoryNames_returnsNamesSortedAlphabetically() {
        givenCategoriesInDatabase();

        List<String> noms = categoryService.getAllCategoryNames();

        assertThat(noms).containsExactly("Informatique", "Science-Fiction", "Thriller");
    }

    @Test
    void getAllCategoryNames_noCategory_returnsEmptyList() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        assertThat(categoryService.getAllCategoryNames()).isEmpty();
    }

    @Test
    void getAllWithDetails_returnsIdAndNameSortedAlphabetically() {
        givenCategoriesInDatabase();

        List<CategoryResponse> categories = categoryService.getAllWithDetails();

        assertThat(categories)
                .extracting(CategoryResponse::getNom)
                .containsExactly("Informatique", "Science-Fiction", "Thriller");
        assertThat(categories.get(0).getId()).isEqualTo(1);
    }
}

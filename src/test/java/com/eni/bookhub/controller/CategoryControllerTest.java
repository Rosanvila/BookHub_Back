package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.CategoryResponse;
import com.eni.bookhub.service.CategoryService;
import com.eni.bookhub.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les deux endpoints des catégories alimentent les filtres du catalogue.
 * Ils sont accessibles sans être connecté, au même titre que le catalogue lui-même.
 */
@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void getAll_withoutLogin_returnsCategoryNames() throws Exception {
        when(categoryService.getAllCategoryNames())
                .thenReturn(List.of("Informatique", "Science-Fiction"));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Informatique"))
                .andExpect(jsonPath("$[1]").value("Science-Fiction"));
    }

    @Test
    void getAllWithDetails_returnsIdAndName() throws Exception {
        when(categoryService.getAllWithDetails())
                .thenReturn(List.of(new CategoryResponse(2, "Science-Fiction")));

        mockMvc.perform(get("/categories/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nom").value("Science-Fiction"));
    }
}

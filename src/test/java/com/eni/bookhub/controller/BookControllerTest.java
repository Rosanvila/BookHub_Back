package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.BookResponse;
import com.eni.bookhub.dto.response.BookSummaryResponse;
import com.eni.bookhub.service.BookService;
import com.eni.bookhub.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la couche API du catalogue.
 * <p>
 * {@code @WebMvcTest} ne démarre que la partie web de l'application : le controller,
 * la configuration de sécurité et la conversion JSON. Le service est un mock, ce qui
 * permet de vérifier le contrat HTTP sans dépendre de la logique métier ni de la base.
 * <p>
 * {@code @WithMockUser} simule un utilisateur connecté avec le rôle indiqué. Une
 * méthode sans cette annotation représente un appel non authentifié.
 */
@WebMvcTest(BookController.class)
@Import(SecurityConfig.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    /** Requis par le filtre JWT, présent dans la chaîne de sécurité. */
    @MockitoBean
    private JwtService jwtService;

    // ── Consultation : ouverte à tous ──────────────────────────────────────────

    @Test
    void getAllBooks_withoutLogin_returnsCatalog() throws Exception {
        when(bookService.getAllBooks(any())).thenReturn(new PageImpl<>(List.of(
                BookSummaryResponse.builder().id(1).titre("Dune").auteur("Frank Herbert").build())));

        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titre").value("Dune"));
    }

    @Test
    void getById_returnsBookDetails() throws Exception {
        when(bookService.getById(1)).thenReturn(
                BookResponse.builder().id(1).titre("Dune").auteur("Frank Herbert").build());

        mockMvc.perform(get("/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Dune"))
                .andExpect(jsonPath("$.auteur").value("Frank Herbert"));
    }

    // ── Suppression : réservée au personnel ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteBook_asAdmin_isAllowed() throws Exception {
        mockMvc.perform(delete("/books/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "LIBRAIRE")
    void deleteBook_asLibrarian_isAllowed() throws Exception {
        mockMvc.perform(delete("/books/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "UTILISATEUR")
    void deleteBook_asMember_isForbidden() throws Exception {
        // Un adhérent connecté ne peut pas retirer un livre du catalogue
        mockMvc.perform(delete("/books/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBook_withoutLogin_isRejected() throws Exception {
        mockMvc.perform(delete("/books/1"))
                .andExpect(status().isForbidden());
    }
}

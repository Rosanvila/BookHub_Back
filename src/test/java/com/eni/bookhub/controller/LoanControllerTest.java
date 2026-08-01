package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.LoanResponse;
import com.eni.bookhub.service.JwtService;
import com.eni.bookhub.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les emprunts sont enregistrés au comptoir : seul le personnel peut en créer un
 * ou enregistrer un retour. L'adhérent, lui, ne peut que consulter les siens.
 */
@WebMvcTest(LoanController.class)
@Import(SecurityConfig.class)
class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanService loanService;

    @MockitoBean
    private JwtService jwtService;

    // ── Enregistrer un emprunt ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "LIBRAIRE")
    void borrowBook_asLibrarian_isAllowed() throws Exception {
        when(loanService.borrowBook(1, 5))
                .thenReturn(LoanResponse.builder().id(10).titre("Dune").statut("EN COURS").build());

        mockMvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"bookId\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN COURS"));
    }

    @Test
    @WithMockUser(roles = "UTILISATEUR")
    void borrowBook_asMember_isForbidden() throws Exception {
        // Un adhérent ne s'enregistre pas un emprunt tout seul
        mockMvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"bookId\": 5}"))
                .andExpect(status().isForbidden());
    }

    // ── Enregistrer un retour ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "LIBRAIRE")
    void returnBook_asLibrarian_isAllowed() throws Exception {
        when(loanService.returnBook(10))
                .thenReturn(LoanResponse.builder().id(10).statut("RENDU").build());

        mockMvc.perform(put("/loans/10/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RENDU"));
    }

    // ── Consulter les emprunts ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "UTILISATEUR")
    void getAllLoans_asMember_isForbidden() throws Exception {
        // La liste complète des emprunts est réservée au personnel
        mockMvc.perform(get("/loans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void getMyLoans_asMember_returnsOnlyTheirOwnLoans() throws Exception {
        // Le service reçoit l'adresse issue du jeton, pas un identifiant fourni par le client :
        // un adhérent ne peut donc pas consulter les emprunts d'un autre.
        when(loanService.getUserLoans("jean.dupont@email.com"))
                .thenReturn(List.of(LoanResponse.builder().id(10).titre("Dune").build()));

        mockMvc.perform(get("/loans/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titre").value("Dune"));

        verify(loanService).getUserLoans("jean.dupont@email.com");
    }
}

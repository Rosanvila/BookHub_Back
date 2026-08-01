package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.AuthResponse;
import com.eni.bookhub.service.AuthService;
import com.eni.bookhub.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les deux endpoints d'authentification sont ouverts : ils doivent être accessibles
 * sans jeton, puisque c'est précisément eux qui le délivrent.
 * <p>
 * Ces tests vérifient aussi la validation des données reçues : les annotations
 * {@code @Valid} placées sur les DTO doivent refuser une demande incomplète.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void register_validRequest_returnsCreatedWithToken() throws Exception {
        when(authService.register(any()))
                .thenReturn(new AuthResponse("jeton-jwt", "jean.dupont@email.com", "UTILISATEUR"));

        String corps = """
                {
                  "nom": "Dupont",
                  "prenom": "Jean",
                  "email": "jean.dupont@email.com",
                  "telephone": "0612345678",
                  "motDePasse": "MotDePasse@2026"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jeton-jwt"))
                .andExpect(jsonPath("$.role").value("UTILISATEUR"));
    }

    @Test
    void register_missingFields_returnsBadRequest() throws Exception {
        // La validation intervient avant le service : celui-ci ne doit pas être appelé
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthResponse("jeton-jwt", "jean.dupont@email.com", "UTILISATEUR"));

        String corps = """
                {
                  "email": "jean.dupont@email.com",
                  "motDePasse": "MotDePasse@2026"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jeton-jwt"))
                .andExpect(jsonPath("$.email").value("jean.dupont@email.com"));
    }
}

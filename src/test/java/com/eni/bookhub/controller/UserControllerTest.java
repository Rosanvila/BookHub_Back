package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.UserResponse;
import com.eni.bookhub.service.JwtService;
import com.eni.bookhub.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tous les endpoints du profil agissent sur le compte de l'utilisateur connecté,
 * identifié par son jeton. Aucun identifiant n'est accepté depuis la requête, ce qui
 * rend impossible la consultation ou la suppression du compte d'un autre adhérent.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void getProfile_returnsProfileOfConnectedUser() throws Exception {
        when(userService.getProfile("jean.dupont@email.com"))
                .thenReturn(UserResponse.builder().id(1).nom("Dupont").prenom("Jean").build());

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Dupont"));
    }

    @Test
    void getProfile_withoutLogin_isRejected() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void deleteAccount_targetsConnectedUserOnly() throws Exception {
        // L'adresse transmise au service provient du contexte de sécurité, jamais de l'URL
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount("jean.dupont@email.com");
    }
}

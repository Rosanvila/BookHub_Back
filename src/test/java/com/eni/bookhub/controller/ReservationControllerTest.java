package com.eni.bookhub.controller;

import com.eni.bookhub.config.SecurityConfig;
import com.eni.bookhub.dto.response.ReservationResponse;
import com.eni.bookhub.service.JwtService;
import com.eni.bookhub.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un adhérent réserve et annule ses propres réservations ; le personnel voit la
 * totalité des réservations et peut les valider.
 */
@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtService jwtService;

    // ── Réserver ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void createReservation_asMember_returnsCreated() throws Exception {
        when(reservationService.createReservation(any(), any()))
                .thenReturn(ReservationResponse.builder().id(99).bookTitle("Dune").build());

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("Dune"));
    }

    @Test
    void createReservation_withoutLogin_isRejected() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isForbidden());
    }

    // ── Consulter ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void getMyReservations_returnsOnlyTheirOwn() throws Exception {
        when(reservationService.getMyReservations("jean.dupont@email.com"))
                .thenReturn(List.of(ReservationResponse.builder().id(99).build()));

        mockMvc.perform(get("/reservations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99));
    }

    @Test
    @WithMockUser(roles = "UTILISATEUR")
    void getAllReservations_asMember_isForbidden() throws Exception {
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isForbidden());
    }

    // ── Annuler ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "jean.dupont@email.com", roles = "UTILISATEUR")
    void cancelReservation_asMember_isNotTreatedAsStaff() throws Exception {
        // Le dernier paramètre indique au service si l'appelant est membre du personnel.
        // Pour un adhérent il vaut false : le service vérifiera qu'il est bien propriétaire.
        mockMvc.perform(delete("/reservations/99"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelReservation("jean.dupont@email.com", 99, false);
    }

    @Test
    @WithMockUser(username = "libraire@email.com", roles = "LIBRAIRE")
    void cancelReservation_asLibrarian_isTreatedAsStaff() throws Exception {
        mockMvc.perform(delete("/reservations/99"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelReservation("libraire@email.com", 99, true);
    }

    // ── Valider ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "LIBRAIRE")
    void validateReservation_asLibrarian_isAllowed() throws Exception {
        when(reservationService.validateReservation(99))
                .thenReturn(ReservationResponse.builder().id(99).status("DISPONIBLE").build());

        mockMvc.perform(put("/reservations/99/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"));
    }

    @Test
    @WithMockUser(roles = "UTILISATEUR")
    void validateReservation_asMember_isForbidden() throws Exception {
        mockMvc.perform(put("/reservations/99/validate"))
                .andExpect(status().isForbidden());
    }
}

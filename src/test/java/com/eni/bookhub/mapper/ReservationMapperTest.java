package com.eni.bookhub.mapper;

import com.eni.bookhub.dto.response.ReservationResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Reservation;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce mapper effectue deux transformations qui méritent d'être verrouillées :
 * la concaténation prénom + nom, et le formatage de la date au format français.
 */
class ReservationMapperTest {

    private final ReservationMapper mapper = new ReservationMapper();

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder()
                .id(99)
                .user(User.builder().id(2).prenom("Marie").nom("Curie").build())
                .book(Book.builder()
                        .id(5)
                        .titre("Cosmos")
                        .urlCouverture("http://couvertures/cosmos.jpg")
                        .build())
                .reservationDate(LocalDateTime.of(2026, 6, 15, 14, 30))
                .rankWaitingList(3)
                .status(Reservation.Status.EN_ATTENTE)
                .build();
    }

    @Test
    void toResponse_mapsReservationFields() {
        ReservationResponse response = mapper.toResponse(reservation);

        assertThat(response.getId()).isEqualTo(99);
        assertThat(response.getUserId()).isEqualTo(2);
        assertThat(response.getBookId()).isEqualTo(5);
        assertThat(response.getBookTitle()).isEqualTo("Cosmos");
        assertThat(response.getUrlCouverture()).isEqualTo("http://couvertures/cosmos.jpg");
        assertThat(response.getRankWaitingList()).isEqualTo(3);
    }

    @Test
    void toResponse_buildsUserNameAsFirstNameThenLastName() {
        assertThat(mapper.toResponse(reservation).getUserName()).isEqualTo("Marie Curie");
    }

    @Test
    void toResponse_formatsDateInFrenchFormat() {
        // Format attendu : jj/MM/aaaa hh:mm, avec zéros non significatifs conservés
        reservation.setReservationDate(LocalDateTime.of(2026, 1, 5, 8, 5));

        assertThat(mapper.toResponse(reservation).getReservationDate()).isEqualTo("05/01/2026 08:05");
    }

    @Test
    void toResponse_convertsStatusEnumToString() {
        reservation.setStatus(Reservation.Status.DISPONIBLE);

        assertThat(mapper.toResponse(reservation).getStatus()).isEqualTo("DISPONIBLE");
    }
}

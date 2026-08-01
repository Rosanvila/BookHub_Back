package com.eni.bookhub.mapper;

import com.eni.bookhub.dto.response.LoanResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Loan;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'emprunt est l'entité la plus liée du modèle : elle référence à la fois le livre
 * et l'adhérent. Le mapper aplatit ces deux relations dans une réponse unique, ce qui
 * évite au front d'avoir à recomposer l'information.
 */
class LoanMapperTest {

    private static final LocalDateTime DATE_EMPRUNT = LocalDateTime.of(2026, 3, 1, 9, 0);
    private static final LocalDateTime DATE_RETOUR_PREVUE = LocalDateTime.of(2026, 3, 15, 9, 0);

    private final LoanMapper mapper = new LoanMapper();

    private Loan loan;

    @BeforeEach
    void setUp() {
        Book book = Book.builder()
                .id(1)
                .titre("Dune")
                .auteur("Frank Herbert")
                .urlCouverture("http://couvertures/dune.jpg")
                .dateParution(LocalDate.of(1965, 8, 1))
                .build();

        User user = User.builder()
                .id(7)
                .nom("Dupont")
                .prenom("Jean")
                .build();

        loan = Loan.builder()
                .id(10)
                .livre(book)
                .utilisateur(user)
                .dateEmprunt(DATE_EMPRUNT)
                .dateRetourPrevue(DATE_RETOUR_PREVUE)
                .statut("EN COURS")
                .build();
    }

    @Test
    void toResponse_includesBookInformation() {
        LoanResponse response = mapper.toResponse(loan);

        assertThat(response.getId()).isEqualTo(10);
        assertThat(response.getTitre()).isEqualTo("Dune");
        assertThat(response.getAuteur()).isEqualTo("Frank Herbert");
        assertThat(response.getUrlCouverture()).isEqualTo("http://couvertures/dune.jpg");
    }

    @Test
    void toResponse_includesBorrowerIdentity() {
        // Le nom de l'adhérent est affiché sur le tableau de bord du bibliothécaire
        LoanResponse response = mapper.toResponse(loan);

        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.getNom()).isEqualTo("Dupont");
        assertThat(response.getPrenom()).isEqualTo("Jean");
    }

    @Test
    void toResponse_convertsDatesToStrings() {
        // Les dates sont sérialisées en chaînes pour être consommées telles quelles par le front
        LoanResponse response = mapper.toResponse(loan);

        assertThat(response.getDateEmprunt()).isEqualTo(DATE_EMPRUNT.toString());
        assertThat(response.getDateRetourPrevue()).isEqualTo(DATE_RETOUR_PREVUE.toString());
        assertThat(response.getDateParution()).isEqualTo("1965-08-01");
    }

    @Test
    void toResponse_keepsLoanStatusUnchanged() {
        loan.setStatut("EN RETARD");

        assertThat(mapper.toResponse(loan).getStatut()).isEqualTo("EN RETARD");
    }
}

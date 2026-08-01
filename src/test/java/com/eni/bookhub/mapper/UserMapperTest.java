package com.eni.bookhub.mapper;

import com.eni.bookhub.dto.request.RegisterRequest;
import com.eni.bookhub.dto.response.UserResponse;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux conversions à vérifier ici, dans les deux sens :
 * l'entité vers la réponse envoyée au client, et la demande d'inscription vers l'entité.
 */
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setNom("Dupont");
        request.setPrenom("Jean");
        request.setEmail("jean.dupont@email.com");
        request.setTelephone("0612345678");
        request.setMotDePasse("MotDePasse@2026");
        return request;
    }

    @Test
    void toResponse_mapsUserFields() {
        LocalDateTime creation = LocalDateTime.of(2026, 1, 15, 10, 0);
        User user = User.builder()
                .id(1)
                .nom("Dupont")
                .prenom("Jean")
                .email("jean.dupont@email.com")
                .telephone("0612345678")
                .motDePasse("$2a$12$hash")
                .role(User.Role.UTILISATEUR)
                .dateCreation(creation)
                .build();

        UserResponse response = mapper.toResponse(user);

        assertThat(response.getId()).isEqualTo(1);
        assertThat(response.getNom()).isEqualTo("Dupont");
        assertThat(response.getPrenom()).isEqualTo("Jean");
        assertThat(response.getEmail()).isEqualTo("jean.dupont@email.com");
        assertThat(response.getTelephone()).isEqualTo("0612345678");
        assertThat(response.getDateCreation()).isEqualTo(creation);
    }

    @Test
    void toResponse_convertsRoleEnumToString() {
        User librarian = User.builder()
                .id(2)
                .role(User.Role.LIBRAIRE)
                .dateCreation(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();

        assertThat(mapper.toResponse(librarian).getRole()).isEqualTo("LIBRAIRE");
    }

    @Test
    void toEntity_mapsRegistrationFields() {
        User user = mapper.toEntity(registerRequest(), "$2a$12$hash");

        assertThat(user.getNom()).isEqualTo("Dupont");
        assertThat(user.getPrenom()).isEqualTo("Jean");
        assertThat(user.getEmail()).isEqualTo("jean.dupont@email.com");
        assertThat(user.getTelephone()).isEqualTo("0612345678");
    }

    @Test
    void toEntity_storesEncodedPasswordNeverTheRawOne() {
        // Point de sécurité : le mot de passe en clair ne doit jamais atteindre l'entité
        User user = mapper.toEntity(registerRequest(), "$2a$12$hash");

        assertThat(user.getMotDePasse()).isEqualTo("$2a$12$hash");
        assertThat(user.getMotDePasse()).isNotEqualTo("MotDePasse@2026");
    }

    @Test
    void toEntity_alwaysAssignsUtilisateurRole() {
        // Le rôle n'est pas lu depuis la requête : impossible de s'auto-déclarer administrateur
        User user = mapper.toEntity(registerRequest(), "$2a$12$hash");

        assertThat(user.getRole()).isEqualTo(User.Role.UTILISATEUR);
    }

    @Test
    void toEntity_setsCreationDate() {
        LocalDateTime avant = LocalDateTime.now();

        User user = mapper.toEntity(registerRequest(), "$2a$12$hash");

        assertThat(user.getDateCreation()).isBetween(avant, LocalDateTime.now());
    }
}

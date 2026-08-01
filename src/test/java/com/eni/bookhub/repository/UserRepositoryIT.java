package com.eni.bookhub.repository;

import com.eni.bookhub.AbstractIntegrationTest;
import com.eni.bookhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration du dépôt des adhérents.
 * <p>
 * Ces requêtes servent à garantir l'unicité de l'adresse e-mail et du numéro de
 * téléphone lors de l'inscription et de la modification du profil.
 */
class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User jean;

    @BeforeEach
    void setUp() {
        jean = saveUser("jean.dupont@email.com", "0612345678");
    }

    private User saveUser(String email, String telephone) {
        return userRepository.save(User.builder()
                .nom("Dupont")
                .prenom("Jean")
                .email(email)
                .telephone(telephone)
                .motDePasse("$2a$12$empreinte-de-test")
                .role(User.Role.UTILISATEUR)
                .dateCreation(LocalDateTime.now())
                .build());
    }

    // ── Recherche par adresse e-mail ───────────────────────────────────────────

    @Test
    void findByEmail_registeredEmail_returnsUser() {
        // C'est cette requête qui identifie l'adhérent à la connexion
        assertThat(userRepository.findByEmail("jean.dupont@email.com"))
                .isPresent()
                .get()
                .extracting(User::getNom)
                .isEqualTo("Dupont");
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        assertThat(userRepository.findByEmail("inconnu@email.com")).isEmpty();
    }

    // ── Unicité à l'inscription ────────────────────────────────────────────────

    @Test
    void existsByEmail_registeredEmail_returnsTrue() {
        assertThat(userRepository.existsByEmail("jean.dupont@email.com")).isTrue();
    }

    @Test
    void existsByEmail_unknownEmail_returnsFalse() {
        assertThat(userRepository.existsByEmail("inconnu@email.com")).isFalse();
    }

    @Test
    void existsByTelephone_registeredPhone_returnsTrue() {
        assertThat(userRepository.existsByTelephone("0612345678")).isTrue();
    }

    @Test
    void existsByTelephone_unknownPhone_returnsFalse() {
        assertThat(userRepository.existsByTelephone("0799999999")).isFalse();
    }

    // ── Unicité à la modification du profil ────────────────────────────────────

    @Test
    void existsByTelephoneAndIdNot_ownPhoneNumber_returnsFalse() {
        // Un adhérent qui enregistre son profil sans changer de numéro
        // ne doit pas entrer en conflit avec lui-même.
        assertThat(userRepository.existsByTelephoneAndIdNot("0612345678", jean.getId())).isFalse();
    }

    @Test
    void existsByTelephoneAndIdNot_phoneNumberOfAnotherMember_returnsTrue() {
        saveUser("marie.curie@email.com", "0798765432");

        assertThat(userRepository.existsByTelephoneAndIdNot("0798765432", jean.getId())).isTrue();
    }
}

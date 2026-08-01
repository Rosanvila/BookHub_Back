package com.eni.bookhub.service;

import com.eni.bookhub.dto.request.UpdatePasswordRequest;
import com.eni.bookhub.dto.request.UpdateProfileRequest;
import com.eni.bookhub.dto.response.UserResponse;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.UserMapper;
import com.eni.bookhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de la gestion du compte adhérent.
 * <p>
 * Le service instancie lui-même son {@link BCryptPasswordEncoder} : il ne peut donc pas
 * être remplacé par un mock. Les tests utilisent de véritables empreintes BCrypt, ce qui
 * a l'avantage de vérifier réellement la comparaison des mots de passe.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EMAIL = "jean.dupont@email.com";
    private static final String MOT_DE_PASSE = "MotDePasse@2026";

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .nom("Dupont")
                .prenom("Jean")
                .email(EMAIL)
                .telephone("0612345678")
                .motDePasse(ENCODER.encode(MOT_DE_PASSE))
                .role(User.Role.UTILISATEUR)
                .dateCreation(LocalDateTime.of(2026, 1, 15, 10, 0))
                .build();
    }

    /*
     * Ces deux DTO n'exposent que des accesseurs en lecture : ils sont normalement
     * remplis par Jackson à la désérialisation. On les alimente donc par réflexion.
     */

    private UpdateProfileRequest profileRequest(String telephone) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "nom", "Durand");
        ReflectionTestUtils.setField(request, "prenom", "Jeanne");
        ReflectionTestUtils.setField(request, "telephone", telephone);
        return request;
    }

    private UpdatePasswordRequest passwordRequest(String ancien, String nouveau) {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        ReflectionTestUtils.setField(request, "ancienMotDePasse", ancien);
        ReflectionTestUtils.setField(request, "nouveauMotDePasse", nouveau);
        return request;
    }

    // ── Consultation du profil ─────────────────────────────────────────────────

    @Test
    void getProfile_knownEmail_returnsProfile() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().id(1).nom("Dupont").build());

        UserResponse response = userService.getProfile(EMAIL);

        assertThat(response.getNom()).isEqualTo("Dupont");
    }

    @Test
    void getProfile_unknownEmail_isRejected() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.getProfile("inconnu@email.com"));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
    }

    // ── Modification du profil ─────────────────────────────────────────────────

    @Test
    void updateProfile_newPhoneNumberIsFree_appliesChanges() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.existsByTelephoneAndIdNot("0798765432", 1)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(EMAIL, profileRequest("0798765432"));

        assertThat(user.getNom()).isEqualTo("Durand");
        assertThat(user.getPrenom()).isEqualTo("Jeanne");
        assertThat(user.getTelephone()).isEqualTo("0798765432");
    }

    @Test
    void updateProfile_unchangedPhoneNumber_skipsUniquenessCheck() {
        // Conserver son propre numéro ne doit pas déclencher de conflit avec soi-même
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(EMAIL, profileRequest("0612345678"));

        verify(userRepository, never()).existsByTelephoneAndIdNot(any(), any());
    }

    @Test
    void updateProfile_phoneNumberUsedByAnotherAccount_isRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.existsByTelephoneAndIdNot("0798765432", 1)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updateProfile(EMAIL, profileRequest("0798765432")));

        assertThat(exception.getMessage()).isEqualTo("Téléphone déjà utilisé");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_unknownEmail_isRejected() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updateProfile("inconnu@email.com", profileRequest("0798765432")));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
    }

    // ── Changement de mot de passe ─────────────────────────────────────────────

    @Test
    void updatePassword_correctCurrentPassword_storesNewHash() {
        String ancienneEmpreinte = user.getMotDePasse();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        userService.updatePassword(EMAIL, passwordRequest(MOT_DE_PASSE, "NouveauMotDePasse@2026"));

        assertThat(user.getMotDePasse()).isNotEqualTo(ancienneEmpreinte);
        assertThat(ENCODER.matches("NouveauMotDePasse@2026", user.getMotDePasse())).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void updatePassword_neverStoresPasswordInPlainText() {
        // Vérification explicite : l'empreinte enregistrée ne doit jamais être le mot de passe saisi
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        userService.updatePassword(EMAIL, passwordRequest(MOT_DE_PASSE, "NouveauMotDePasse@2026"));

        assertThat(user.getMotDePasse())
                .isNotEqualTo("NouveauMotDePasse@2026")
                .startsWith("$2a$12$");
    }

    @Test
    void updatePassword_wrongCurrentPassword_isRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword(EMAIL, passwordRequest("MauvaisMotDePasse", "NouveauMotDePasse@2026")));

        assertThat(exception.getMessage()).isEqualTo("Ancien mot de passe incorrect");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePassword_unknownEmail_isRejected() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword("inconnu@email.com", passwordRequest(MOT_DE_PASSE, "Nouveau@2026")));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
    }

    // ── Suppression du compte ──────────────────────────────────────────────────

    @Test
    void deleteAccount_knownEmail_deletesUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        userService.deleteAccount(EMAIL);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccount_unknownEmail_isRejected() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.deleteAccount("inconnu@email.com"));

        assertThat(exception.getMessage()).isEqualTo("Utilisateur introuvable");
        verify(userRepository, never()).delete(any(User.class));
    }
}

package com.eni.bookhub.service;

import com.eni.bookhub.dto.request.LoginRequest;
import com.eni.bookhub.dto.request.RegisterRequest;
import com.eni.bookhub.dto.response.AuthResponse;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.UserMapper;
import com.eni.bookhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de l'inscription et de la connexion.
 * <p>
 * Comme pour {@link UserServiceTest}, l'encodeur BCrypt est instancié par le service
 * lui-même : les tests manipulent donc de vraies empreintes.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "jean.dupont@email.com";
    private static final String MOT_DE_PASSE = "MotDePasse@2026";
    private static final String TELEPHONE = "0612345678";

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<String> passwordCaptor;

    private User user;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .nom("Dupont")
                .prenom("Jean")
                .email(EMAIL)
                .telephone(TELEPHONE)
                .motDePasse(ENCODER.encode(MOT_DE_PASSE))
                .role(User.Role.UTILISATEUR)
                .dateCreation(LocalDateTime.of(2026, 1, 15, 10, 0))
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setNom("Dupont");
        registerRequest.setPrenom("Jean");
        registerRequest.setEmail(EMAIL);
        registerRequest.setTelephone(TELEPHONE);
        registerRequest.setMotDePasse(MOT_DE_PASSE);
    }

    private LoginRequest loginRequest(String email, String motDePasse) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setMotDePasse(motDePasse);
        return request;
    }

    // ── Inscription ────────────────────────────────────────────────────────────

    @Test
    void register_newUser_savesAccountAndReturnsToken() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.existsByTelephone(TELEPHONE)).thenReturn(false);
        when(userMapper.toEntity(eq(registerRequest), any())).thenReturn(user);
        when(jwtService.generateToken(EMAIL, "UTILISATEUR")).thenReturn("jeton-jwt");

        AuthResponse response = authService.register(registerRequest);

        verify(userRepository).save(user);
        assertThat(response.getToken()).isEqualTo("jeton-jwt");
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getRole()).isEqualTo("UTILISATEUR");
    }

    @Test
    void register_hashesPasswordBeforePersisting() {
        // Le mot de passe transmis au mapper doit déjà être une empreinte BCrypt
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.existsByTelephone(TELEPHONE)).thenReturn(false);
        when(userMapper.toEntity(any(), passwordCaptor.capture())).thenReturn(user);
        when(jwtService.generateToken(any(), any())).thenReturn("jeton-jwt");

        authService.register(registerRequest);

        String empreinte = passwordCaptor.getValue();
        assertThat(empreinte).isNotEqualTo(MOT_DE_PASSE).startsWith("$2a$12$");
        assertThat(ENCODER.matches(MOT_DE_PASSE, empreinte)).isTrue();
    }

    @Test
    void register_emailAlreadyUsed_returnsConflict() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(registerRequest));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("Email déjà utilisé");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_phoneNumberAlreadyUsed_returnsConflict() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.existsByTelephone(TELEPHONE)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.register(registerRequest));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("Téléphone déjà utilisé");
        verify(userRepository, never()).save(any());
    }

    // ── Connexion ──────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokenWithRole() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(EMAIL, "UTILISATEUR")).thenReturn("jeton-jwt");

        AuthResponse response = authService.login(loginRequest(EMAIL, MOT_DE_PASSE));

        assertThat(response.getToken()).isEqualTo("jeton-jwt");
        assertThat(response.getRole()).isEqualTo("UTILISATEUR");
    }

    @Test
    void login_librarianAccount_tokenCarriesLibrarianRole() {
        // Le rôle est embarqué dans le jeton : c'est lui qui pilotera les autorisations
        user.setRole(User.Role.LIBRAIRE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(EMAIL, "LIBRAIRE")).thenReturn("jeton-libraire");

        assertThat(authService.login(loginRequest(EMAIL, MOT_DE_PASSE)).getRole()).isEqualTo("LIBRAIRE");
    }

    @Test
    void login_unknownEmail_returnsUnauthorized() {
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest("inconnu@email.com", MOT_DE_PASSE)));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest(EMAIL, "MauvaisMotDePasse")));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void login_failureMessageDoesNotRevealWhichFieldIsWrong() {
        // Même message pour un compte inexistant et un mot de passe erroné :
        // impossible d'énumérer les adresses inscrites.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("inconnu@email.com")).thenReturn(Optional.empty());

        ResponseStatusException motDePasseErrone = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest(EMAIL, "MauvaisMotDePasse")));

        ResponseStatusException compteInconnu = assertThrows(ResponseStatusException.class,
                () -> authService.login(loginRequest("inconnu@email.com", MOT_DE_PASSE)));

        assertThat(motDePasseErrone.getReason()).isEqualTo(compteInconnu.getReason());
    }
}

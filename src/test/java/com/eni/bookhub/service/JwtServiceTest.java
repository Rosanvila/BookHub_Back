package com.eni.bookhub.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests de la fabrication des jetons d'authentification.
 * <p>
 * Aucun mock ici : le service est instancié directement et sa clé de signature est
 * injectée par réflexion, puisqu'elle provient normalement du fichier de configuration.
 */
class JwtServiceTest {

    /** HMAC-SHA256 impose une clé d'au moins 32 octets. */
    private static final String CLE_EN_CLAIR = "cle-de-signature-de-test-32-car.";
    private static final String CLE_BASE64 = Base64.getEncoder()
            .encodeToString(CLE_EN_CLAIR.getBytes(StandardCharsets.UTF_8));

    private static final long DUREE_24_HEURES_EN_MS = 24L * 60 * 60 * 1000;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", CLE_BASE64);
    }

    private Claims lireContenu(String token) {
        return Jwts.parser()
                .verifyWith(jwtService.getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void generateToken_storesEmailAsSubject() {
        String token = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");

        assertThat(lireContenu(token).getSubject()).isEqualTo("jean.dupont@email.com");
    }

    @Test
    void generateToken_storesRoleAsClaim() {
        // C'est ce rôle que Spring Security lira pour autoriser ou refuser un appel
        String token = jwtService.generateToken("admin@email.com", "ADMIN");

        assertThat(lireContenu(token).get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_expiresAfterTwentyFourHours() {
        long avant = System.currentTimeMillis();

        String token = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");

        long expiration = lireContenu(token).getExpiration().getTime();
        assertThat(expiration).isBetween(
                avant + DUREE_24_HEURES_EN_MS - 2000,
                System.currentTimeMillis() + DUREE_24_HEURES_EN_MS + 2000);
    }

    @Test
    void generateToken_isSignedWithConfiguredKey() {
        String token = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");

        assertThat(lireContenu(token)).isNotNull();
    }

    @Test
    void generateToken_cannotBeVerifiedWithAnotherKey() {
        // Un jeton forgé avec une autre clé doit être rejeté : c'est le fondement
        // de la sécurité du mécanisme.
        String token = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");
        var autreCle = Keys.hmacShaKeyFor("une-toute-autre-cle-de-32-carac.".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> Jwts.parser()
                .verifyWith(autreCle)
                .build()
                .parseSignedClaims(token));
    }

    @Test
    void generateToken_differentRoles_produceDifferentTokens() {
        String jetonAdherent = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");
        String jetonAdmin = jwtService.generateToken("jean.dupont@email.com", "ADMIN");

        assertThat(jetonAdherent).isNotEqualTo(jetonAdmin);
    }

    @Test
    void getSigningKey_acceptsPlainTextSecret() {
        // La configuration peut fournir la clé en clair plutôt qu'encodée en Base64
        ReflectionTestUtils.setField(jwtService, "secretKey", CLE_EN_CLAIR);

        String token = jwtService.generateToken("jean.dupont@email.com", "UTILISATEUR");

        assertThat(lireContenu(token).getSubject()).isEqualTo("jean.dupont@email.com");
    }
}

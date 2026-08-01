package com.eni.bookhub;

import com.eni.bookhub.service.BookCoverSchedulerService;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Classe de base des tests d'intégration.
 * <p>
 * Contrairement aux tests unitaires qui travaillent sur des mocks, les classes qui
 * héritent d'ici s'exécutent contre une véritable base SQL Server. Ce choix est
 * volontaire : le schéma BookHub s'appuie sur des triggers et des requêtes natives
 * que seul le moteur SQL Server sait interpréter fidèlement.
 * <p>
 * Chaque test est encapsulé dans une transaction annulée à la fin de la méthode
 * ({@code @Transactional}), ce qui garantit que les tests ne se polluent pas entre eux.
 * <p>
 * Le tag {@code integration} permet à Gradle de ne déclencher ces tests que via la
 * tâche dédiée {@code integrationTest}, qui suppose la présence de Docker.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        // Hibernate génère le schéma au démarrage et le supprime à la fin
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=integration-test-jwt-secret-32-characters"
})
@Transactional
public abstract class AbstractIntegrationTest {

    /**
     * Le planificateur interroge l'API OpenLibrary pour compléter les couvertures.
     * On le neutralise pour que les tests restent hors ligne et déterministes.
     */
    @MockitoBean
    BookCoverSchedulerService bookCoverSchedulerService;
}
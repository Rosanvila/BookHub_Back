package com.eni.bookhub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Déclare le conteneur SQL Server utilisé par les tests d'intégration.
 * <p>
 * L'annotation {@code @ServiceConnection} laisse Spring Boot renseigner lui-même
 * l'URL, l'utilisateur et le mot de passe de la datasource à partir du conteneur
 * démarré. Cela évite d'avoir à les recopier manuellement dans les propriétés du
 * contexte de test.
 * <p>
 * Le conteneur est un bean Spring : son cycle de vie (démarrage, arrêt) est géré
 * par le conteneur d'injection. Comme Spring met en cache le contexte de test
 * entre les classes, l'image SQL Server n'est démarrée qu'une seule fois pour
 * l'ensemble de la campagne de tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName SQL_SERVER_IMAGE =
            DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest");

    @Bean
    @ServiceConnection
    MSSQLServerContainer<?> sqlServerContainer() {
        return new MSSQLServerContainer<>(SQL_SERVER_IMAGE).acceptLicense();
    }
}

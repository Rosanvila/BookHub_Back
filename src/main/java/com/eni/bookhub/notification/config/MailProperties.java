package com.eni.bookhub.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bookhub.mail")
public record MailProperties(
        @NotBlank String from,
        @NotBlank String fromName
) {
}

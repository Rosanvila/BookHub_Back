package com.eni.bookhub.notification.dto;

import java.util.Objects;

public record EmailMessage(
    String to,
    String subject,
    String htmlContent
) {
    public EmailMessage {
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(htmlContent, "htmlContent must not be null");
        if (to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (htmlContent.isBlank()) {
            throw new IllegalArgumentException("htmlContent must not be blank");
        }
    }

    public static EmailMessage of(String to, String subject, String htmlContent) {
        return new EmailMessage(to, subject, htmlContent);
    }
}
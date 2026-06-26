package com.eni.bookhub.notification.impl;

import com.eni.bookhub.notification.NotificationService;
import com.eni.bookhub.notification.config.MailProperties;
import com.eni.bookhub.notification.dto.EmailMessage;
import com.eni.bookhub.notification.exception.NotificationSendingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class EmailNotificationServiceImpl implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationServiceImpl.class);
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailNotificationServiceImpl(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void sendEmail(EmailMessage emailMessage) {
        Objects.requireNonNull(emailMessage, "emailMessage must not be null");

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailProperties.from(), mailProperties.fromName());
            helper.setTo(emailMessage.to());
            helper.setSubject(emailMessage.subject());
            helper.setText(emailMessage.htmlContent(), true);

            mailSender.send(mimeMessage);

            log.info("Email sent successfully to {} (subject: {})",
                    emailMessage.to(), emailMessage.subject());

        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Échec de l'envoi de l'email à {} (sujet : '{}')",
                    emailMessage.to(), emailMessage.subject(), e);
            throw new NotificationSendingException(
                    "Impossible d'envoyer l'email à " + emailMessage.to(), e);
        }
    }
}
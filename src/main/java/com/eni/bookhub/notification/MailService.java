package com.eni.bookhub.notification;

import com.eni.bookhub.entity.Loan;
import com.eni.bookhub.entity.Reservation;
import com.eni.bookhub.notification.dto.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService notificationService;
    private final TemplateEngine templateEngine;

    public MailService(NotificationService notificationService, TemplateEngine templateEngine) {
        this.notificationService = notificationService;
        this.templateEngine = templateEngine;
    }

    public void sendLoanConfirmation(Loan loan) {
        String email = loan.getUtilisateur().getEmail();
        String prenom = loan.getUtilisateur().getPrenom();
        String titre = loan.getLivre().getTitre();
        String auteur = loan.getLivre().getAuteur();

        Context context = new Context();
        context.setVariable("prenom", prenom);
        context.setVariable("titre", titre);
        context.setVariable("auteur", auteur);
        context.setVariable("dateEmprunt", loan.getDateEmprunt().format(DATE_FORMAT));
        context.setVariable("dateRetour", loan.getDateRetourPrevue().format(DATE_FORMAT));

        String html = templateEngine.process("email/loan-confirmation", context);

        notificationService.sendEmail(EmailMessage.of(
                email,
                "Confirmation de votre emprunt — " + titre,
                html
        ));

        log.info("Email de confirmation d'emprunt envoyé à {}", email);
    }

    public void sendReservationAvailable(Reservation reservation) {
        String email = reservation.getUser().getEmail();
        String prenom = reservation.getUser().getPrenom();
        String titre = reservation.getBook().getTitre();
        String auteur = reservation.getBook().getAuteur();

        Context context = new Context();
        context.setVariable("prenom", prenom);
        context.setVariable("titre", titre);
        context.setVariable("auteur", auteur);

        // La dateRetour sera renseignée depuis l'emprunt créé par validateReservation
        // On passe juste la date prévue sous 14 jours
        context.setVariable("dateRetour",
                java.time.LocalDateTime.now().plusDays(14).format(DATE_FORMAT));

        String html = templateEngine.process("email/reservation-available", context);

        notificationService.sendEmail(EmailMessage.of(
                email,
                "Votre réservation est disponible — " + titre,
                html
        ));

        log.info("Email de réservation disponible envoyé à {}", email);
    }
}

package com.eni.bookhub.service;

import com.eni.bookhub.dto.response.LoanResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Loan;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.LoanMapper;
import com.eni.bookhub.notification.MailService;
import com.eni.bookhub.repository.BookRepository;
import com.eni.bookhub.repository.LoanRepository;
import com.eni.bookhub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanMapper loanMapper;
    private final MailService mailService;

    public LoanService(LoanRepository loanRepository,
            BookRepository bookRepository,
            UserRepository userRepository,
            LoanMapper loanMapper,
            MailService mailService) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.loanMapper = loanMapper;
        this.mailService = mailService;
    }

    @Transactional
    public LoanResponse borrowBook(Integer userId, Integer bookId) {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Livre introuvable"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (book.getExemplairesDisponibles() <= 0) {
            throw new RuntimeException("Livre non disponible");
        }

        int activeLoans = loanRepository
                .countByUtilisateurIdAndStatut(userId, "EN COURS");

        if (activeLoans >= 3) {
            throw new RuntimeException("Max 3 emprunts atteints");
        }

        boolean hasLate = loanRepository
                .existsByUtilisateurIdAndStatut(userId, "EN RETARD");

        if (hasLate) {
            throw new RuntimeException("Utilisateur bloqué (retard)");
        }

        Loan loan = new Loan();
        loan.setUtilisateur(user);
        loan.setLivre(book);
        LocalDateTime now = LocalDateTime.now();
        loan.setDateEmprunt(now);
        loan.setDateRetourPrevue(now.plusDays(14));
        loan.setStatut("EN COURS");

        loanRepository.save(loan);

        try {
            mailService.sendLoanConfirmation(loan);
        } catch (Exception e) {
            log.warn("Échec de l'envoi de l'email de confirmation pour l'emprunt {}", loan.getId(), e);
        }

        return loanMapper.toResponse(loan);
    }

    @Transactional
    public LoanResponse returnBook(Integer loanId) {

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Emprunt introuvable"));

        if (!loan.getStatut().equals("EN COURS") && !loan.getStatut().equals("EN RETARD")) {
            throw new RuntimeException("Emprunt déjà retourné");
        }

        loan.setDateRetourEffective(LocalDateTime.now());
        loan.setStatut("RENDU");

        loanRepository.save(loan);

        return loanMapper.toResponse(loan);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void markOverdueLoans() {
        List<Loan> overdue = loanRepository.findByStatutAndDateRetourPrevueBefore("EN COURS", LocalDateTime.now());
        overdue.forEach(loan -> loan.setStatut("EN RETARD"));
        loanRepository.saveAll(overdue);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getAllLoans() {
        return loanRepository.findAll().stream()
                .map(loanMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getUserLoans(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        return loanRepository
                .findByUtilisateurIdAndStatutIn(
                        user.getId(),
                        List.of("EN COURS", "EN RETARD", "RENDU"))
                .stream()
                .map(loanMapper::toResponse)
                .toList();
    }
}

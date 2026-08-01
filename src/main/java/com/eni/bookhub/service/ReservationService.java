package com.eni.bookhub.service;

import com.eni.bookhub.dto.request.ReservationRequest;
import com.eni.bookhub.dto.response.ReservationResponse;
import com.eni.bookhub.entity.Book;
import com.eni.bookhub.entity.Reservation;
import com.eni.bookhub.entity.User;
import com.eni.bookhub.mapper.ReservationMapper;
import com.eni.bookhub.notification.MailService;
import com.eni.bookhub.repository.BookRepository;
import com.eni.bookhub.repository.ReservationRepository;
import com.eni.bookhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private static final List<Reservation.Status> ACTIVE_STATUSES =
            List.of(Reservation.Status.EN_ATTENTE, Reservation.Status.DISPONIBLE);

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanService loanService;
    private final ReservationMapper reservationMapper;
    private final MailService mailService;

    @Transactional
    public ReservationResponse createReservation(String email, ReservationRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livre introuvable"));

        if (book.getExemplairesDisponibles() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce livre n'a aucun exemplaire disponible");
        }

        if (reservationRepository.existsByUserIdAndBookIdAndStatusIn(user.getId(), book.getId(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous avez déjà une réservation active pour ce livre");
        }

        int activeReservations = reservationRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES);
        if (activeReservations >= 5) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous avez atteint le maximum de 5 réservations actives");
        }

        int rank = reservationRepository.countByBookIdAndStatus(book.getId(), Reservation.Status.EN_ATTENTE) + 1;

        Reservation reservation = Reservation.builder()
                .user(user)
                .book(book)
                .reservationDate(LocalDateTime.now())
                .rankWaitingList(rank)
                .status(Reservation.Status.EN_ATTENTE)
                .build();

        return reservationMapper.toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return reservationRepository.findByUserId(user.getId()).stream()
                .map(reservationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(reservationMapper::toResponse)
                .toList();
    }

    @Transactional
    public void cancelReservation(String email, Integer reservationId, boolean isStaff) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable"));

        if (!isStaff) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
            if (!reservation.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous ne pouvez annuler que vos propres réservations");
            }
        }

        if (!ACTIVE_STATUSES.contains(reservation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cette réservation ne peut pas être annulée (statut : " + reservation.getStatus() + ")");
        }

        int cancelledRank = reservation.getRankWaitingList();
        reservation.setStatus(Reservation.Status.ANNULEE);
        reservationRepository.save(reservation);
        shiftRanksDown(reservation.getBook().getId(), cancelledRank);
    }

    /**
     * Validates a reservation: creates the loan for the user and marks the reservation as DISPONIBLE.
     * Only LIBRAIRE or ADMIN can call this.
     */
    @Transactional
    public ReservationResponse validateReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable"));

        if (reservation.getStatus() != Reservation.Status.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seules les réservations EN_ATTENTE peuvent être validées (statut actuel : " + reservation.getStatus() + ")");
        }

        User user = reservation.getUser();
        Book book = reservation.getBook();

        loanService.borrowBook(user.getId(), book.getId());

        int validatedRank = reservation.getRankWaitingList();
        reservation.setStatus(Reservation.Status.DISPONIBLE);
        reservationRepository.save(reservation);
        shiftRanksDown(book.getId(), validatedRank);

        try {
            mailService.sendReservationAvailable(reservation);
        } catch (Exception e) {
            log.warn("Échec de l'envoi de l'email de réservation disponible pour la réservation {}", reservationId, e);
        }

        return reservationMapper.toResponse(reservation);
    }

    private void shiftRanksDown(Integer bookId, Integer fromRank) {
        List<Reservation> toShift = reservationRepository
                .findByBookIdAndStatusAndRankWaitingListGreaterThan(bookId, Reservation.Status.EN_ATTENTE, fromRank);
        toShift.forEach(r -> r.setRankWaitingList(r.getRankWaitingList() - 1));
        reservationRepository.saveAll(toShift);
    }


}

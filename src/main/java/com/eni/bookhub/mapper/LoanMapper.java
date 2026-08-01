package com.eni.bookhub.mapper;

import com.eni.bookhub.dto.response.LoanResponse;
import com.eni.bookhub.entity.Loan;
import org.springframework.stereotype.Component;

@Component
public class LoanMapper {

    public LoanResponse toResponse(Loan loan) {
        return LoanResponse.builder()
                .id(loan.getId())
                .titre(loan.getLivre().getTitre())
                .auteur(loan.getLivre().getAuteur())
                .urlCouverture(loan.getLivre().getUrlCouverture())
                .dateParution(loan.getLivre().getDateParution().toString())
                .dateEmprunt(loan.getDateEmprunt().toString())
                .dateRetourPrevue(loan.getDateRetourPrevue().toString())
                .statut(loan.getStatut())
                .userId(loan.getUtilisateur().getId())
                .nom(loan.getUtilisateur().getNom())
                .prenom(loan.getUtilisateur().getPrenom())
                .build();
    }
}

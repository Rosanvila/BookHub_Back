package com.eni.bookhub.notification;

import com.eni.bookhub.notification.dto.EmailMessage;

public interface NotificationService {
    void sendEmail(EmailMessage emailMessage);
}

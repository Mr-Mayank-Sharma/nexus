package com.nexus.oms.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends password-reset links. Uses SMTP when {@code spring.mail.host} is configured;
 * otherwise logs that the mail was not sent (and, only when
 * {@code app.mail.log-reset-links=true} for local development, the link itself).
 */
@Service
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.mail.from:no-reply@nexus.local}")
    private String from;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.log-reset-links:false}")
    private boolean logResetLinks;

    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResetLink(String email, String username, String rawToken) {
        String link = frontendUrl + "/#/reset-password?token=" + rawToken;
        JavaMailSender sender = mailHost == null || mailHost.isBlank() ? null : mailSender.getIfAvailable();
        if (sender == null || email == null || email.isBlank()) {
            log.warn("Password reset requested for user {} but mail is not configured or the user has no e-mail; nothing sent.", username);
            if (logResetLinks) {
                log.info("[dev] Password reset link for {}: {}", username, link);
            }
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your Nexus password");
        message.setText("Hi " + username + ",\n\n"
                + "Use the link below to choose a new password. It expires in 1 hour and works once.\n\n"
                + link + "\n\n"
                + "If you did not ask for this, you can ignore this e-mail.\n");
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("Failed to send password reset e-mail to user {}: {}", username, e.getMessage());
        }
    }
}

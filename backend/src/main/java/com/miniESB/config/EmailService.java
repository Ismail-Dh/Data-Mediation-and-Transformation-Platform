package com.miniESB.config;

import com.miniESB.exception.EmailSendingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@miniesb.app}")
    private String fromAddress;

    @Value("${spring.mail.from-name:miniESB}")
    private String fromName;

    public void sendResetPasswordEmail(String to, String code) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setFrom(fromAddress, fromName);
            helper.setSubject("Code de réinitialisation de mot de passe");
            helper.setText(buildPlainText(code), buildHtml(code));

            mailSender.send(mimeMessage);
            log.info("Email de réinitialisation envoyé à {}", maskEmail(to));
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Échec de l'envoi de l'email de réinitialisation à {}", maskEmail(to), e);
            throw new EmailSendingException("Échec de l'envoi de l'email de réinitialisation", e);
        } catch (org.springframework.mail.MailException e) {
            log.error("Erreur SMTP lors de l'envoi à {}", maskEmail(to), e);
            throw new EmailSendingException("Échec de l'envoi de l'email de réinitialisation", e);
        }
    }

    private String buildPlainText(String code) {
        return "Bonjour,\n\n" +
                "Votre code de réinitialisation est : " + code + "\n\n" +
                "Ce code est valable pendant 10 minutes.\n\n" +
                "Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.";
    }

    private String buildHtml(String code) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;border:1px solid #eee;border-radius:8px\">"
                + "<h2 style=\"color:#1a1a1a;margin-top:0\">Réinitialisation de mot de passe</h2>"
                + "<p>Bonjour,</p>"
                + "<p>Voici votre code de réinitialisation :</p>"
                + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:4px;background:#f4f4f4;padding:12px 16px;border-radius:6px;text-align:center\">"
                + code + "</p>"
                + "<p>Ce code est valable pendant <strong>10 minutes</strong>.</p>"
                + "<p style=\"color:#777;font-size:13px\">Si vous n'avez pas demandé cette réinitialisation, ignorez simplement cet email.</p>"
                + "</div>";
    }

    /** Évite de logger l'adresse email complète (RGPD / bonnes pratiques). */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
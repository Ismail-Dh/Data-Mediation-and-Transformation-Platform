package com.miniESB.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendResetPasswordEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Code de réinitialisation de mot de passe");
        message.setText(
            "Bonjour,\n\n" +
            "Votre code de réinitialisation est : " + code + "\n\n" +
            "Ce code est valable pendant 10 minutes.\n\n" +
            "Si vous n'avez pas demandé cette réinitialisation, ignorez cet email."
        );
        mailSender.send(message);
    }
}


package com.swu.atch.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// 이메일 인증코드 발송
@Service
public class EmailSender {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public EmailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    // 회원가입용 인증코드 이메일 발송
    public void sendVerificationCode(String recipient, String code) {
        sendCode(recipient, code, "이메일 인증코드");
    }

    public void sendPasswordResetCode(String recipient, String code) {
        sendCode(recipient, code, "비밀번호 재설정 인증코드");
    }

    private void sendCode(String recipient, String code, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("[Atch!] " + purpose);
        message.setText(
                "안녕하세요, Atch!입니다.\n\n"
                        + "이메일 인증코드: " + code + "\n\n"
                        + "인증코드는 발급 후 5분 동안 유효합니다.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );

        mailSender.send(message);
    }
}

package com.bidr.email.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Title: EmailService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/08/14 11:21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    static {
        System.setProperty("mail.mime.splitlongparameters", "false");
    }

    @Resource
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String sender;

    public void sendEmail(String to, String subject) {
        sendEmail(to, null, subject, null, null, null);
    }

    public void sendEmail(String to, List<String> cc, String subject, String text, String attachmentName,
                          FileSystemResource resource) {

        log.debug("sendEmail {}-{}-{}-{}", to, subject, text, attachmentName);
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "utf-8");
            helper.setFrom(sender);
            helper.setSubject(subject);
            helper.setTo(to);
            if (text == null) {
                helper.setText("");
            } else {
                helper.setText(text);
            }
            if (cc != null) {
                for (String c : cc) {
                    helper.addCc(c);
                }
            }
            if (resource != null) {
                helper.addAttachment(MimeUtility.encodeWord(attachmentName, "utf-8", "B"), resource);
            }
            sendEmail(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("sendEmail", e);
        }
    }

    public void sendEmail(MimeMessage message) {
        javaMailSender.send(message);
    }

    public void sendEmail(String to, List<String> cc, String subject) {
        sendEmail(to, cc, subject, null, null, null);
    }

    public void sendEmail(String to, String subject, String text) {
        sendEmail(to, null, subject, text, null, null);
    }

    public void sendEmail(String to, List<String> cc, String subject, String text) {
        sendEmail(to, cc, subject, text, null, null);
    }

    public void sendEmail(String to, String subject, String text, String attachmentName, FileSystemResource resource) {
        sendEmail(to, null, subject, text, attachmentName, resource);
    }

    public MimeMessage createMimeMessage() {
        return javaMailSender.createMimeMessage();
    }
}

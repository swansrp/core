package com.bidr.email.service;

import com.bidr.email.vo.SendEmailReq;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
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

    @Value("${email.proxy.host:}")
    private String emailProxyHost;

    @Value("${email.proxy.port:}")
    private Integer emailProxyPort;

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
            if (FuncUtil.isEmpty(text)) {
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

    public void sendEmail(SendEmailReq req) {
        try {
            if (FuncUtil.isNotEmpty(req.getFilePath()) && req.getFilePath().startsWith("http")) {
                File tempFile = File.createTempFile("tmp", null);
                InputStream stream;
                if (FuncUtil.isNotEmpty(emailProxyHost) && FuncUtil.isNotEmpty(emailProxyPort)) {
                    // 创建代理服务器
                    InetSocketAddress addr = new InetSocketAddress(emailProxyHost, emailProxyPort);
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, addr);
                    stream = HttpUtil.getStream(req.getFilePath(), proxy);
                } else {
                    stream = HttpUtil.getStream(req.getFilePath());
                }
                FileUtils.copyInputStreamToFile(stream, tempFile);
                FileSystemResource fileSystemResource = new FileSystemResource(tempFile.getPath());
                sendEmail(req.getTo(), req.getCc(), req.getSubject(), req.getContent(), req.getAttachmentName(),
                        fileSystemResource);
                tempFile.delete();
            } else {
                sendEmail(req.getTo(), req.getCc(), req.getSubject(), req.getContent(), req.getAttachmentName(), null);
            }
        } catch (IOException e) {
            Validator.assertException(e);
        }
    }

    public MimeMessage createMimeMessage() {
        return javaMailSender.createMimeMessage();
    }
}

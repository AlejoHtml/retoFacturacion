package com.example.emailprocessor.service;

import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final DocumentService documentService;

    @Value("${mail.imap.host}")
    private String host;
    @Value("${mail.imap.port}")
    private String port;
    @Value("${mail.imap.user}")
    private String user;
    @Value("${mail.imap.password}")
    private String password;
    @Value("${mail.imap.sender.filter}")
    private String senderFilter;

    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkEmails() {
        log.info("Checking for new emails from: {}", senderFilter);
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", host);
        properties.put("mail.imaps.port", port);
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.starttls.enable", "true");
        properties.put("mail.imaps.auth", "true");

        try {
            Session emailSession = Session.getInstance(properties);
            Store store = emailSession.getStore("imaps");
            store.connect(host, user, password);

            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_WRITE);

            // Search for unread messages
            Flags seen = new Flags(Flags.Flag.SEEN);
            FlagTerm unseenFlagTerm = new FlagTerm(seen, false);
            Message[] messages = emailFolder.search(unseenFlagTerm);

            log.info("Found {} unread messages", messages.length);

            for (Message message : messages) {
                String from = message.getFrom()[0].toString();
                if (from.contains(senderFilter)) {
                    log.info("Processing message from: {}", from);
                    processMessage(message, from);
                    // Mark as read
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            }

            emailFolder.close(true);
            store.close();
        } catch (Exception e) {
            log.error("Error checking emails", e);
        }
    }

    private void processMessage(Message message, String from) throws Exception {
        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) &&
                    bodyPart.getFileName().toLowerCase().endsWith(".pdf")) {
                    
                    File tempFile = File.createTempFile("attachment", ".pdf");
                    try (InputStream is = bodyPart.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    documentService.processEmailAttachment(tempFile, from);
                }
            }
        }
    }
}
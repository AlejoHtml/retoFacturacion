package com.example.emailprocessor.service;

import jakarta.mail.*;
import jakarta.mail.search.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
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
        
        // Protocolo y Host
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", host);
        properties.put("mail.imaps.port", port);
        properties.put("mail.imaps.user", user);
        
        // Seguridad y Autenticación
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.auth", "true");
        properties.put("mail.imaps.ssl.trust", "*");
        
        // Timeouts para evitar bloqueos
        properties.put("mail.imaps.timeout", "10000");
        properties.put("mail.imaps.connectiontimeout", "10000");
        
        // Debug para ver el handshake y errores detallados
        properties.put("mail.debug", "true");

        try {
            Session emailSession = Session.getInstance(properties);
            // emailSession.setDebug(true); // Ya habilitado vía properties
            
            Store store = emailSession.getStore("imaps");
            log.info("Attempting to connect to IMAP: {} as user: {}", host, user);
            
            // Usamos la firma completa para asegurar que se usen los parámetros correctos
            store.connect(host, Integer.parseInt(port), user, password);
            log.info("Successfully connected to IMAP server");

            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_WRITE);

            // Search for unread messages from the last month
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            Date lastMonth = cal.getTime();

            // Usamos SentDateTerm que suele ser más consistente en Gmail
            SearchTerm dateTerm = new SentDateTerm(ComparisonTerm.GT, lastMonth);
            // Filtramos por remitente directamente en la búsqueda IMAP
            SearchTerm fromTerm = new FromStringTerm(senderFilter);
            SearchTerm combinedTerm = new AndTerm(dateTerm, fromTerm);
            
            Message[] messages = emailFolder.search(combinedTerm);

            log.info("Found {} total messages from {} in the last month (checking for unread...)", messages.length, senderFilter);

            for (Message message : messages) {
                try {
                    String from = message.getFrom()[0].toString();
                    if (!message.isSet(Flags.Flag.SEEN)) {
                        log.info("Processing unread message from: {} - Subject: {}", from, message.getSubject());
                        processMessage(message, from);
                        // Mark as read
                        message.setFlag(Flags.Flag.SEEN, true);
                    } else {
                        log.debug("Skipping already seen message: {}", message.getSubject());
                    }
                } catch (Exception e) {
                    log.error("Error processing message: " + message.getSubject(), e);
                }
            }

            emailFolder.close(true);
            store.close();
        } catch (AuthenticationFailedException e) {
            log.error("AUTHENTICATION FAILED: Please verify that the App Password is correct and IMAP is enabled in Gmail settings for account: {}", user);
            log.error("Error details: {}", e.getMessage());
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
                    bodyPart.getFileName() != null && 
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
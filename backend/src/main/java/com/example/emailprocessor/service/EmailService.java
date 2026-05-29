package com.example.emailprocessor.service;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.repository.ProcessedDocumentRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final DocumentService documentService;
    private final JavaMailSender mailSender;
    private final ProcessedDocumentRepository repository;

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

    public EmailService(DocumentService documentService, JavaMailSender mailSender, ProcessedDocumentRepository repository) {
        this.documentService = documentService;
        this.mailSender = mailSender;
        this.repository = repository;
    }

    @Scheduled(fixedRate = 60000)
    public void checkEmails() {
        log.info("Checking for new emails from: {}", senderFilter);
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", host);
        properties.put("mail.imaps.port", port);
        properties.put("mail.imaps.user", user);
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.auth", "true");
        properties.put("mail.imaps.ssl.trust", "*");
        properties.put("mail.imaps.timeout", "10000");
        properties.put("mail.imaps.connectiontimeout", "10000");

        try {
            Session emailSession = Session.getInstance(properties);
            Store store = emailSession.getStore("imaps");
            store.connect(host, Integer.parseInt(port), user, password);

            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_WRITE);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            Date lastMonth = cal.getTime();
            SearchTerm dateTerm = new SentDateTerm(ComparisonTerm.GT, lastMonth);
            
            Message[] messages = emailFolder.search(dateTerm);
            String[] allowedSenders = senderFilter.toLowerCase().split(",");

            for (Message message : messages) {
                try {
                    String from = message.getFrom()[0].toString().toLowerCase();
                    boolean isAllowed = false;
                    for (String allowed : allowedSenders) {
                        if (from.contains(allowed.trim())) {
                            isAllowed = true;
                            break;
                        }
                    }

                    if (isAllowed && !message.isSet(Flags.Flag.SEEN)) {
                        log.info("Processing unread message from: {} - Subject: {}", from, message.getSubject());
                        processMessage(message, from);
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                } catch (Exception e) {
                    log.error("Error processing message: " + message.getSubject(), e);
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
                String fileName = bodyPart.getFileName();
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) &&
                    fileName != null && 
                    (fileName.toLowerCase().endsWith(".pdf") || 
                     fileName.toLowerCase().endsWith(".xls") || 
                     fileName.toLowerCase().endsWith(".xlsx"))) {
                    
                    String extension = fileName.substring(fileName.lastIndexOf("."));
                    File tempFile = File.createTempFile("attachment", extension);
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

    @Async
    public void sendRecoveryEmail(String to, String newPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Recuperación de Contraseña - Reto Facturación");
        message.setText("Su nueva contraseña temporal es: " + newPassword +
                "\nPor seguridad, deberá cambiarla al iniciar sesión.");
        mailSender.send(message);
    }

    public void resendDocument(String id, String targetEmail) {
        ProcessedDocument doc = repository.findById(id).orElseThrow();
        log.info("Resending document ID: {} to: {}. Invoice Number: {}", id, targetEmail, doc.getInvoiceNumber());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(targetEmail);
            helper.setSubject("Reenvío de Factura: " + doc.getInvoiceNumber());
            helper.setText("Se adjunta la información de la factura " + doc.getInvoiceNumber() + 
                            "\nDatos extraídos: " + (doc.getExtractedData() != null ? doc.getExtractedData().toString() : "N/A"));
            
            File file = new File(doc.getFilePath());
            if (file.exists()) {
                String originalName = file.getName();
                String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";
                String invoiceNum = doc.getInvoiceNumber() != null ? doc.getInvoiceNumber() : "Factura_" + id;
                String cleanInvoiceNum = invoiceNum.replaceAll("[^a-zA-Z0-9-_]", "_");
                helper.addAttachment(cleanInvoiceNum + extension, file);
            }
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error resending document", e);
            throw new RuntimeException("Error al reenviar el documento: " + e.getMessage(), e);
        }
    }
}

package org.rac.services;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.File;
import java.util.Properties;

public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // TODO: These SMTP settings should be configurable by the user, ideally through a settings UI or a configuration file.
    // For now, they are hardcoded placeholders.
    private static final String SMTP_HOST = "smtp.gmail.com"; // e.g., smtp.gmail.com, smtp.outlook.com
    private static final String SMTP_PORT = "587"; // e.g., 587 for TLS, 465 for SSL
    private static final String SMTP_USERNAME = "29.abhishek.mittal@gmail.com"; // Your email address
    private static final String SMTP_PASSWORD = "jmmg zuwn atqd nedn"; // Your email password or app-specific password

    public void sendEmailWithAttachment(String toEmail, String fromEmail, String subject, String body, File attachment) {
        logger.info("Attempting to send email to {} from {} with subject '{}'", toEmail, fromEmail, subject);

        // SMTP server configuration
        Properties properties = new Properties();
        properties.put("mail.smtp.host", SMTP_HOST);
        properties.put("mail.smtp.port", SMTP_PORT);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true"); // Use STARTTLS for secure connection
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2"); // Specify TLS protocol

        // Get the Session object and pass username and password
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
            }
        });

        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(fromEmail));

            // Set To: header field of the header.
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));

            // Set Subject: header field
            message.setSubject(subject);

            // Create the message part
            BodyPart messageBodyPart = new MimeBodyPart();

            // Fill the message
            messageBodyPart.setText(body);

            // Create a multipart message for attachment
            Multipart multipart = new MimeMultipart();

            // Add text part
            multipart.addBodyPart(messageBodyPart);

            // Add attachment
            if (attachment != null && attachment.exists()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource source = new FileDataSource(attachment);
                attachmentPart.setDataHandler(new DataHandler(source));
                attachmentPart.setFileName(attachment.getName());
                multipart.addBodyPart(attachmentPart);
                logger.debug("Attached file: {}", attachment.getAbsolutePath());
            } else {
                logger.warn("No attachment provided or attachment file does not exist.");
            }

            // Send the complete message parts
            message.setContent(multipart);

            // Send message
            Transport.send(message);
            logger.info("Email sent successfully to {}", toEmail);

        } catch (javax.mail.MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}

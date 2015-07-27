/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Date;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

import play.Logger;
import play.Play;
import play.data.validation.Validation;

public class MailSettingsValidator {

    public static class Settings {
        public String server;
        public String port;
        public String username;
        public String password;
        public String channel;
        public String authType;
        public String fromAddress;
    }

    public static void validate(Settings settings, String toEmail) {
        SimpleEmail email = new SimpleEmail();
        try {
            email.setFrom(settings.fromAddress);
        }
        catch (Exception e) {
            Logger.error(e, "Failed to parse From email address [%s]", settings.fromAddress);
            Validation.addError(null, "MailSettings.failedToParseAddress", settings.fromAddress);
        }
        try {
            email.addTo(toEmail);
        }
        catch (EmailException e) {
            Logger.error(e, "Failed to parse To email address [%s]", toEmail);
            Validation.addError(null, "MailSettings.failedToParseAddress", toEmail);
        }
        email.setSubject(MessagesUtils.get("MailSettings.testSubject"));
        try {
            email.setMsg(MessagesUtils.get("MailSettings.testMessage"));
        }
        catch (EmailException e) {
            Logger.error(e, "Failed to set email message");
            Validation.addError(null, "MailSettings.failedToSetMessage");
        }

        if (!Validation.hasErrors()) {
            try {
                send(email, settings);
            }
            catch (EmailException e) {
                Logger.error(e, "Failed to send email");
                addExceptionError(e);
            }
            catch (RuntimeException e) {
                Logger.error(e, "Failed to send email");
                addExceptionError(e);
            }
        }
    }

    /**
     * Send an email
     */
    private static boolean send(Email email, Settings settings) throws EmailException {
        email = buildMessage(email, settings);
        email.setMailSession(getSession(settings));
        return sendMessage(email);
    }

    private static Email buildMessage(Email email, Settings form) throws EmailException {
        String from = form.fromAddress;
        if (email.getFromAddress() == null && !StringUtils.isEmpty(from)) {
            email.setFrom(from);
        }
        else if (email.getFromAddress() == null) {
            throw new EmailException("Please define a 'from' email address");
        }
        if ((email.getToAddresses() == null || email.getToAddresses().size() == 0)
                && (email.getCcAddresses() == null || email.getCcAddresses().size() == 0)
                && (email.getBccAddresses() == null || email.getBccAddresses().size() == 0)) {
            throw new EmailException("Please define a recipient email address");
        }
        if (email.getSubject() == null) {
            throw new EmailException("Please define a subject");
        }
        if (email.getReplyToAddresses() == null || email.getReplyToAddresses().size() == 0) {
            email.addReplyTo(email.getFromAddress().getAddress());
        }

        return email;
    }

    public static Session getSession(Settings form) {
        Properties props = new Properties();

        String host = form.server;
        String port = form.port;

        // Put a bogus value even if we are on dev mode, otherwise JavaMail
        // will complain
        props.put("mail.smtp.host", host);

        String channelEncryption = form.channel;

        if (channelEncryption.equals("clear")) {
            props.put("mail.smtp.port", ConfigPropertyUtils.defaultPort(port, "25"));
        }
        else if (channelEncryption.equals("ssl")) {
            // port 465 + setup yes ssl socket factory (won't verify that
            // the server certificate is signed with a root ca.)

            props.put("mail.smtp.port", ConfigPropertyUtils.defaultPort(port, "465"));
            props.put("mail.smtp.ssl.socketFactory.port", ConfigPropertyUtils.defaultPort(port, "465"));
            props.put("mail.smtp.ssl.socketFactory.class", "play.utils.YesSSLSocketFactory");
            props.put("mail.smtp.ssl.socketFactory.fallback", "false");
        }
        else if (channelEncryption.equals("starttls")) {
            // port 25 + enable starttls + ssl socket factory
            props.put("mail.smtp.port", ConfigPropertyUtils.defaultPort(port, "25"));
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.socketFactory.port", ConfigPropertyUtils.defaultPort(port, "465"));
            props.put("mail.smtp.ssl.socketFactory.class", "play.utils.YesSSLSocketFactory");
            props.put("mail.smtp.ssl.socketFactory.fallback", "false");
        }

        String user = form.username;
        String password = form.password;

        String authenticator = Play.configuration.getProperty("mail.smtp.authenticator");
        Session session = null;

        if (authenticator != null) {
            props.put("mail.smtp.auth", "true");
            try {
                session = Session.getInstance(props, (Authenticator) Play.classloader.loadClass(authenticator)
                        .newInstance());
            }
            catch (Exception e) {
                Logger.error(e, "Cannot instantiate custom SMTP authenticator (%s)", authenticator);
                addExceptionError(e);
            }
        }

        if (session == null) {
            if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password)) {
                props.put("mail.smtp.auth", "true");
                session = Session.getInstance(props, new SMTPAuthenticator(user, password));
            }
            else {
                props.remove("mail.smtp.auth");
                session = Session.getInstance(props);
            }
        }

        return session;
    }

    /**
     * Send a JavaMail message
     * 
     * @param message
     *        the email message
     */
    public static boolean sendMessage(Email message) throws EmailException {
        message.setSentDate(new Date());
        message.send();
        return true;
    }

    public static class SMTPAuthenticator extends Authenticator {
        private String user;
        private String password;

        public SMTPAuthenticator(String user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password);
        }
    }

    private static void addExceptionError(Throwable t) {
        if (t.getCause() != null && (t instanceof javax.mail.MessagingException == false)) {
            addExceptionError(t.getCause());
        }
        else {
            Validation.addError(null, t.getMessage());
        }
    }

}

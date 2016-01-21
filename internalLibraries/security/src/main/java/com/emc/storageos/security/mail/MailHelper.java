/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.mail;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class MailHelper {

    private static final Logger log = Logger.getLogger(MailHelper.class);

    private CoordinatorClient coordinatorClient;
    private JavaMailSenderImpl mailSender;

    public static final String SMTP_PROPERTY_PREFIX = "system_connectemc_smtp_";

    public static final String ZERO_PORT = "0";
    public static final String DEFAULT_SMTP_TLS_PORT = "465";
    public static final String DEFAULT_SMTP_PORT = "25";
    
    public static final String SMTP_CONNECTION_TIMEOUT = "10000"; // 10 seconds
    public static final String SMTP_WRITE_TIMEOUT = "10000"; // 10 seconds
    public static final String SMTP_READ_TIMEOUT = "10000"; //10 seconds

    public static final String SMTP_SERVER = "system_connectemc_smtp_server";
    public static final String SMTP_PORT = "system_connectemc_smtp_port";
    public static final String SMTP_ENABLE_TLS = "system_connectemc_smtp_enabletls";
    public static final String SMTP_AUTH_TYPE = "system_connectemc_smtp_authtype";
    public static final String SMTP_USERNAME = "system_connectemc_smtp_username";
    public static final String SMTP_PASSWORD = "system_connectemc_smtp_password"; // NOSONAR
                                                                                  // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String SMTP_FROM_ADDRESS = "system_connectemc_smtp_from";

    private String smtpServer;
    private String smtpPort;
    private String smtpEnableTls;
    private String fromAddress;
    private String smtpAuthType;
    private String username;
    private String password;

    public MailHelper(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void sendMailMessage(String to, String subject, String html) {
        try {
            JavaMailSender mailSender = getMailSender();
            if (mailSender != null) {
                MimeMessage mimeMessage = mailSender.createMimeMessage();

                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "utf-8");
                helper.setText(html, true);
                String[] addresses = StringUtils.split(to, ",");
                for (String address : addresses) {
                    address = StringUtils.trimToNull(address);
                    if (address != null) {
                        helper.addTo(address);
                    }
                }
                helper.setSubject(subject);

                mailSender.send(mimeMessage);
            }
            else {
                log.warn("Unable to send notification email.  Email settings not configured.");
            }
        } catch (MailException | MessagingException ex) {
            String message = String.format("Failed to notify user by email");
            log.error(message, ex);
            throw APIException.internalServerErrors.genericApisvcError(message, ex);
        }
    }

    private JavaMailSender getMailSender() {
        if (smtpSettingsUpdated()) {
            this.mailSender = createJavaMailSender();
        }
        return this.mailSender;
    }

    private boolean smtpSettingsUpdated() {
        Map<String, String> properties = getPropertiesFromCoordinator();
        if (this.mailSender == null && containsSmtpSettings(properties)) {
            return StringUtils.isNotBlank(properties.get(SMTP_SERVER));
        }
        else if (this.mailSender != null) {

            String newSmtpServer = properties.get(SMTP_SERVER);
            String newSmtpPort = properties.get(SMTP_PORT);
            String newSmtpEnableTls = properties.get(SMTP_ENABLE_TLS);
            String newFromAddress = properties.get(SMTP_FROM_ADDRESS);
            String newSmtpAuthType = properties.get(SMTP_AUTH_TYPE);
            String newUsername = properties.get(SMTP_USERNAME);
            String newPassword = properties.get(SMTP_PASSWORD);

            if (StringUtils.equalsIgnoreCase(this.smtpServer, newSmtpServer) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.smtpPort, newSmtpPort) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.smtpEnableTls, newSmtpEnableTls) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.fromAddress, newFromAddress) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.smtpAuthType, newSmtpAuthType) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.username, newUsername) == false) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(this.password, newPassword) == false) {
                return true;
            }

        }
        return false;
    }

    private JavaMailSenderImpl createJavaMailSender() {
        Map<String, String> properties = getPropertiesFromCoordinator();

        smtpServer = properties.get(SMTP_SERVER);
        smtpPort = properties.get(SMTP_PORT);
        smtpEnableTls = properties.get(SMTP_ENABLE_TLS);
        fromAddress = properties.get(SMTP_FROM_ADDRESS);
        smtpAuthType = properties.get(SMTP_AUTH_TYPE);
        username = properties.get(SMTP_USERNAME);
        password = properties.get(SMTP_PASSWORD);

        boolean enableTls = StringUtils.equalsIgnoreCase("yes", smtpEnableTls);
        boolean authEnabled = StringUtils.isNotBlank(smtpAuthType) && !StringUtils.equalsIgnoreCase(smtpAuthType, "none");

        Properties javaMailProperties = new Properties();
        javaMailProperties.setProperty("mail.smtp.host", smtpServer);
        javaMailProperties.setProperty("mail.smtp.connectiontimeout", SMTP_CONNECTION_TIMEOUT);
        javaMailProperties.setProperty("mail.smtp.timeout", SMTP_READ_TIMEOUT);
        javaMailProperties.setProperty("mail.smtp.writetimeout", SMTP_WRITE_TIMEOUT);

        if (enableTls) {
            javaMailProperties.setProperty("mail.smtp.channel", "starttls");
            smtpPort = defaultPort(smtpPort, DEFAULT_SMTP_TLS_PORT);
        }
        else {
            smtpPort = defaultPort(smtpPort, DEFAULT_SMTP_PORT);
        }
        javaMailProperties.setProperty("mail.smtp.port", smtpPort);

        javaMailProperties.setProperty("mail.smtp.from", fromAddress);

        if (authEnabled) {
            javaMailProperties.setProperty("mail.smtp.auth", "true");
            javaMailProperties.setProperty("mail.smtp.user", username);
            javaMailProperties.setProperty("mail.smtp.pass", password);
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpServer);
        try {
            mailSender.setPort(Integer.parseInt(smtpPort));
        } catch (NumberFormatException e) {
            log.error(String.format("Failed to parse smtp port [%s]", smtpPort), e);
        }
        // this.mailSender.setProtocol(protocol);
        if (authEnabled) {
            mailSender.setUsername(username);
            mailSender.setPassword(password);
        }
        mailSender.setJavaMailProperties(javaMailProperties);

        log.debug(String.format("  mail.smtp.host    = %s", mailSender.getJavaMailProperties().get("mail.smtp.host")));
        log.debug(String.format("  mail.smtp.port    = %s", mailSender.getJavaMailProperties().get("mail.smtp.port")));
        log.debug(String.format("  mail.smtp.channel = %s", mailSender.getJavaMailProperties().get("mail.smtp.channel")));
        log.debug(String.format("  mail.smtp.auth    = %s", mailSender.getJavaMailProperties().get("mail.smtp.auth")));
        log.debug(String.format("  mail.smtp.user    = %s", mailSender.getJavaMailProperties().get("mail.smtp.user")));
        log.debug(String.format("  mail.smtp.from    = %s", mailSender.getJavaMailProperties().get("mail.smtp.from")));

        return mailSender;
    }

    private Map<String, String> getPropertiesFromCoordinator() {
        com.emc.storageos.model.property.PropertyInfo propertyInfo = coordinatorClient.getPropertyInfo();
        if (propertyInfo != null) {
            return propertyInfo.getAllProperties();
        }
        return Maps.newHashMap();
    }

    private boolean containsSmtpSettings(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            if (key.startsWith(SMTP_PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private String defaultPort(String port, String defaultPort) {
        if (StringUtils.isBlank(port) || port.equalsIgnoreCase(ZERO_PORT)) {
            return defaultPort;
        }
        return port;
    }

    /**
     * read template html
     * 
     * @param resource
     * @return
     */
    public static String readTemplate(String resource) {
        InputStream in = MailHelper.class.getResourceAsStream(resource);
        try {
            return IOUtils.toString(in, "UTF-8");
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /**
     * substitute parameters into template
     * 
     * @param parameters
     * @param input
     * @return
     */
    public static String parseTemplate(Map parameters, String input) {
        if (parameters == null) {
            return input;
        }

        StrSubstitutor substitutor = new StrSubstitutor(StrLookup.mapLookup(parameters));
        return substitutor.replace(input);
    }
}

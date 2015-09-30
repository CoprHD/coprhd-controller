/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.eventhandler;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "connectemc_email")
public class ConnectEmcEmail extends ConnectEmcEmailFtpsBase {

    private String emailServer;
    private String port;
    private String primaryEmailAddress;
    private String notifyEmailAddress;
    private String emailSender;
    private String smtpAuthType;
    private String userName;
    private String password;
    private String startTls;
    private String enableTlsCert;

    // SMTP Authorization Types.
    private final static String LOGIN = "login";
    private final static String CRAM_MD5 = "cram-md5";
    private final static String PLAIN = "plain";

    /**
     * Optional, SMTP server or relay for sending email
     */
    @XmlElement(name = "email_server")
    public String getEmailServer() {
        return emailServer;
    }

    public void setEmailServer(String emailServer) {
        this.emailServer = emailServer;
    }

    /**
     * Optional, SMTP server port. If set to 0, the default SMTP port is used (25, or 465 if TLS/SSL is enabled)
     */
    @XmlElement(name = "port")
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Optional, e-mail address where you can be contacted
     */
    @XmlElement(name = "primary_email_address")
    public String getPrimaryEmailAddress() {
        return primaryEmailAddress;
    }

    public void setPrimaryEmailAddress(String primaryEmailAddress) {
        this.primaryEmailAddress = primaryEmailAddress;
    }

    /**
     * Optional, e-mail address for the ConnectEMC Service notifications
     */
    @XmlElement(name = "notify_email_address")
    public String getNotifyEmailAddress() {
        return notifyEmailAddress;
    }

    public void setNotifyEmailAddress(String notifyEmailAddress) {
        this.notifyEmailAddress = notifyEmailAddress;
    }

    /**
     * Optional, From email address for sending email messages
     */
    @XmlElement(name = "email_sender")
    public String getEmailSender() {
        return emailSender;
    }

    public void setEmailSender(String emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Optional, Authentication type for connecting to the SMTP server
     * 
     * @valid LOGIN
     * @valid CRAM_MD5
     * @valid PLAIN
     */
    @XmlElement(name = "smtp_auth_type")
    public String getSmtpAuthType() {
        return smtpAuthType;
    }

    public void setSmtpAuthType(String smtpAuthType) {
        if (smtpAuthType == null || smtpAuthType.isEmpty()
                || smtpAuthType.equalsIgnoreCase(LOGIN)
                || smtpAuthType.equalsIgnoreCase(CRAM_MD5)
                || smtpAuthType.equalsIgnoreCase(PLAIN)) {
            this.smtpAuthType = smtpAuthType;
        } else {
            this.smtpAuthType = PLAIN;
        }
    }

    /**
     * Optional, Username for authenticating with the SMTP server
     */
    @XmlElement(name = "username")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Optional, Password for authenticating with the SMTP server
     */
    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Optional, Use TLS/SSL for the SMTP server connections
     * 
     * @valid NO (DEFAULT)
     * @valid YES
     */
    @XmlElement(name = "start_tls_ind")
    public String getStartTls() {
        return startTls;
    }

    // Must be yes or no, default to no.
    public void setStartTls(String startTls) {
        this.startTls = (startTls != null
                && startTls.trim().equalsIgnoreCase(YES) ? YES.toLowerCase()
                : NO.toLowerCase());
    }

    @XmlElement(name = "enable_tls_cert")
    public String getEnableTlsCert() {
        return enableTlsCert;
    }

    public void setEnableTlsCert(String enableTlsCert) {
        this.enableTlsCert = enableTlsCert;
    }
}

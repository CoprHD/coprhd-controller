/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.eventhandler;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "connectemc_ftps")
public class ConnectEmcFtps extends ConnectEmcEmailFtpsBase {

    private String hostName;
    private String emailServer;
    private String notifyEmailAddress;
    private String emailSender;

    /**
     * Optional, ConnectEMC FTPS Hostname
     */
    @XmlElement(name = "host_name")
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

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
     * Optional, From e-mail address for sending e-mail messages
     */
    @XmlElement(name = "email_sender")
    public String getEmailSender() {
        return emailSender;
    }

    public void setEmailSender(String emailSender) {
        this.emailSender = emailSender;
    }
}

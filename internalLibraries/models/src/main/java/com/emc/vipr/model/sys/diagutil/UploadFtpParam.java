/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement(name = "ftp_param")
public class UploadFtpParam implements Serializable{
    private static final Logger log = LoggerFactory.getLogger(UploadFtpParam.class);
    private String ftp = "";
    private String user = "";
    private String password = "";

    public UploadFtpParam() {}

    @XmlElement(name = "ftp")
    public String getFtp() {
        return ftp;
    }

    public void setFtp(String ftp) {
        this.ftp = ftp;
    }

    @XmlElement(name = "user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        log.info("begin");
        sb.append("ftp address:");
        sb.append(ftp);
        log.info("append ftp1");
        sb.append(",user:");
        sb.append(user);
        log.info("append ftp2");
        sb.append(",password");
        sb.append(password);
        log.info("append ftp3");
        return sb.toString();
    }
}

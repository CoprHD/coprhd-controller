/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;

public class UploadFtpParam {
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
        StringBuffer sb = new StringBuffer();
        sb.append("ftp address:");
        sb.append(ftp);
        sb.append(",user:");
        sb.append(user);
        sb.append(",password");
        sb.append(password);
        return sb.toString();
    }
}

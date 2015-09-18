/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_add")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteAddParam {
    private String name;
    private String vip;
    private String user;
    private String password;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "vip")
    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }

    @XmlElement
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @XmlElement
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteAddParam [name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append("]");
        return builder.toString();
    }
}

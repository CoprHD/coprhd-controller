/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "certificate")
public class TrustedCertificate {

    private boolean userSupplied;
    private String certString;

    public TrustedCertificate() {

    }

    public TrustedCertificate(String certString, boolean userSupplied) {
        this.userSupplied = userSupplied;
        this.certString = certString;
    }

    @XmlElement(name = "user_supplied")
    public boolean getUserSupplied() {
        return userSupplied;
    }

    public void setUserSupplied(boolean userSupplied) {
        this.userSupplied = userSupplied;
    }

    @XmlElement(name = "cert")
    public String getCertString() {
        return certString;
    }

    public void setCertString(String certString) {
        this.certString = certString;
    }
}

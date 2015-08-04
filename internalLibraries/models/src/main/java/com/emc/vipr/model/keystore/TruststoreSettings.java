/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * represents the current settings of the truststore
 */
@XmlRootElement(name = "truststore_settings")
public class TruststoreSettings {

    private boolean acceptAllCertificates;

    @XmlElement(name = "accept_all_certificates")
    public boolean isAcceptAllCertificates() {
        return acceptAllCertificates;
    }

    public void setAcceptAllCertificates(boolean acceptAllCertificates) {
        this.acceptAllCertificates = acceptAllCertificates;
    }
}

/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "trusted_certificates")
public class TrustedCertificates {

    private List<TrustedCertificate> trustedCertificates;

    @XmlElement(name = "certificate")
    public List<TrustedCertificate> getTrustedCertificates() {
        return trustedCertificates;
    }

    public void setTrustedCertificates(List<TrustedCertificate> trustedCertificates) {
        this.trustedCertificates = trustedCertificates;
    }

}

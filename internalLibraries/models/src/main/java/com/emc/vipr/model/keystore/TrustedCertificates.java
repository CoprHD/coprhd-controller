/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

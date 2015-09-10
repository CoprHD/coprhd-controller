/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.XmlElement;

public class KeyAndCertificateChain {

    private String privateKey;
    private String certificateChain;

    @XmlElement(name = "private_key")
    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    @XmlElement(name = "certificate_chain")
    public String getCertificateChain() {
        return certificateChain;
    }

    public void setCertificateChain(String certificateChain) {
        this.certificateChain = certificateChain;
    }

}

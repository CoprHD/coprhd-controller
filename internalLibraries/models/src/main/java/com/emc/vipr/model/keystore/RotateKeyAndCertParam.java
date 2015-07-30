/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "rotate_keycertchain")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RotateKeyAndCertParam {
    private Boolean systemSelfSigned;
    private KeyAndCertificateChain keyCertChain;

    @XmlElement(name = "system_selfsigned")
    public Boolean getSystemSelfSigned() {
        return systemSelfSigned;
    }

    public void setSystemSelfSigned(Boolean systemSelfSigned) {
        this.systemSelfSigned = systemSelfSigned;
    }

    @XmlElement(name = "key_and_certificate")
    public KeyAndCertificateChain getKeyCertChain() {
        return keyCertChain;
    }

    public void setKeyCertChain(KeyAndCertificateChain keyCertChain) {
        this.keyCertChain = keyCertChain;
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "rotate_keycertchain")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ObjectSvcRotateKeyAndCertParam{
    private List<String> ipList;
    private Boolean systemSelfSigned;
    private KeyAndCertificateChain keyCertChain;

    @XmlElementWrapper(name = "ip_addresses")
    @XmlElement(name = "ip_address")
    public List<String> getIpAddresses() {
        return ipList;
    }
    public void setIpAddresses(List<String> ipAddresses) {
        this.ipList = ipAddresses;
    }

    @XmlElement(name="system_selfsigned")
    public Boolean getSystemSelfSigned() {
        return systemSelfSigned;
    }
    public void setSystemSelfSigned(Boolean systemSelfSigned) {
        this.systemSelfSigned = systemSelfSigned;
    }

    @XmlElement(name="key_and_certificate")
    public KeyAndCertificateChain getKeyCertChain() {
        return keyCertChain;
    }
    public void setKeyCertChain(KeyAndCertificateChain keyCertChain) {
        this.keyCertChain = keyCertChain;
    }
}

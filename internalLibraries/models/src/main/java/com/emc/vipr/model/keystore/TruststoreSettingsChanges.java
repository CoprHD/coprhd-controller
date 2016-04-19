/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.keystore;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * represents the changes to be made to the settings of the truststore
 */
@XmlRootElement(name = "truststore_settings_changes")
public class TruststoreSettingsChanges {

    private Boolean acceptAllCertificates;

    @XmlElement(name = "accept_all_certificates")
    public Boolean getAcceptAllCertificates() {
        return acceptAllCertificates;
    }

    public void setAcceptAllCertificates(Boolean acceptAllCertificates) {
        this.acceptAllCertificates = acceptAllCertificates;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("acceptAllCertificates = ");
        builder.append(acceptAllCertificates);

        return builder.toString();

    }

}

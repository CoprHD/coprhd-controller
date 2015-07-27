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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("acceptAllCertificates = ");
        builder.append(acceptAllCertificates);

        return builder.toString();

    }

}

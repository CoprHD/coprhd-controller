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

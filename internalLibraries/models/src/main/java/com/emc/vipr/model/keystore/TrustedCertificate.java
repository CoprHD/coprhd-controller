/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
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

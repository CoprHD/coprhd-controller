/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.eventhandler;

import javax.xml.bind.annotation.XmlElement;

public class ConnectEmcEmailFtpsBase {
    private String safeEncryption;
    // Yes and No indicators.
    protected final static String YES = "Yes";
    protected final static String NO = "No";

    /**
     * Optional, Encrypt ConnectEMC Service data using RSA BSAFE
     * 
     * @valid Yes
     * @valid No
     */
    @XmlElement(name = "bsafe_encryption_ind")
    public String getSafeEncryption() {
        return safeEncryption;
    }

    public void setSafeEncryption(String safeEncryption) {
        this.safeEncryption = (safeEncryption != null && safeEncryption.trim().equalsIgnoreCase(YES) ? YES.toLowerCase() : NO.toLowerCase());
    }
}

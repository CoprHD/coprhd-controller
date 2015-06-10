/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

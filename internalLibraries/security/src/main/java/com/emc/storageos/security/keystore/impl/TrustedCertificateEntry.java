/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.keystore.impl;

import java.io.Serializable;
import java.security.cert.Certificate;
import java.util.Date;

/**
 * 
 */
public class TrustedCertificateEntry implements Serializable {

    private static final long serialVersionUID = 281678890833379205L;

    private Certificate certificate;
    private Date creationDate;

    public TrustedCertificateEntry(Certificate certificate, Date creationDate) {
        this.certificate = certificate;
        this.creationDate = creationDate;
    }

    /**
     * @return the certificate
     */
    public Certificate getCertificate() {
        return certificate;
    }

    /**
     * @param certificate
     *            the certificate to set
     */
    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    /**
     * @return the creationDate
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * @param creationDate
     *            the creationDate to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }
}

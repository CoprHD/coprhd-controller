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
package com.emc.storageos.model.vdc;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * VDC secret key response rest representation
 */
@XmlRootElement(name = "virtual_data_center_secret_key")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualDataCenterSecretKeyRestRep {
    private String encodedKey;

    public VirtualDataCenterSecretKeyRestRep() {}

    // Key must be base 64 encoded, UTF-8 string
    public void setSecretKey(String k) {      
        encodedKey = k;
    }

    @XmlElement(name = "secret_key")
    public String getSecretKey() { 
        return encodedKey;
    }

}

/**
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

package com.emc.storageos.geomodel;

import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.net.URI;
import java.util.HashMap;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class VdcCertParam {
    private URI vdcId;
    private String certificate;

    @XmlElement(name="vdcId")
    public URI getVdcId() {
        return vdcId;
    }
    public void setVdcId(URI vdcId) {
        this.vdcId = vdcId;
    }
    
    @XmlElement(name="certificate")
    public String getCertificate() {
        return certificate;
    }
    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }
}

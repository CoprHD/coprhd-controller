/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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

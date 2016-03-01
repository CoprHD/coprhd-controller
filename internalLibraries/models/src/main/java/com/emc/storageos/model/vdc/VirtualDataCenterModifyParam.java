/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vdc;

import com.emc.vipr.model.keystore.KeyAndCertificateChain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "vdc_update")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualDataCenterModifyParam {
    private String name;
    private String description;

    private String geoCommandEndpoint;
    private String geoDataEndpoint;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     */
    @Deprecated
    @XmlElement(name = "geo_command_endpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }

    /**
     */
    @Deprecated
    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
    }

    /**
     */
    @Deprecated
    @XmlElement(name = "geo_data_endpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }

    /**
     */
    @Deprecated
    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
    }
}

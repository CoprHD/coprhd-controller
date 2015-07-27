/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vdc;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "vdc_add")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualDataCenterAddParam {
    private String name;
    private String description;
    private String secretKey;
    private String apiEndpoint;
    private String geoCommandEndpoint;
    private String geoDataEndpoint;
    private String certificate_chain;
    
    @XmlElement(name="name")
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    @XmlElement(name="description")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    
    @XmlElement(name="api_endpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }
    
    @XmlElement(name="secret_key")
    public String getSecretKey() {
        return secretKey;
    }
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name="certificate_chain")
    public String getCertificateChain() {
        return certificate_chain;
    }
    public void setCertificateChain(String certificate_chain) {
        this.certificate_chain = certificate_chain;
    }
    
    @XmlElement(name="geo_command_endpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }
    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
    }
    
    @XmlElement(name="geo_data_endpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }
    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
    }
    
}

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
    private String secretKey;
    private String apiEndpoint;

    private Boolean rotateKeyCertChain;
    private KeyAndCertificateChain keyCertChain;

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

    @XmlElement(name = "api_endpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    @XmlElement(name = "secret_key")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @XmlElement(name = "rotate_keycertchain")
    public Boolean getRotateKeyCert() {
        return rotateKeyCertChain;
    }

    public void setRotateKeyCert(Boolean rotateKeyCertChain) {
        this.rotateKeyCertChain = rotateKeyCertChain;
    }

    @XmlElement(name = "key_and_certificate")
    public KeyAndCertificateChain getKeyCertChain() {
        return keyCertChain;
    }

    public void setKeyCertChain(KeyAndCertificateChain keyCertChain) {
        this.keyCertChain = keyCertChain;
    }

    /**
     * @deprecated GEO command endpoints have moved to {@link com.emc.vipr.model.object.zone.VdcRestRep#getCmdEndPoints()}
     */
    @Deprecated
    @XmlElement(name = "geo_command_endpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }

    /**
     * @deprecated GEO command endpoints have moved to {@link com.emc.vipr.model.object.zone.VdcRestRep#setCmdEndPoints(String)}
     */
    @Deprecated
    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
    }

    /**
     * @deprecated GEO data endpoints have moved to {@link com.emc.vipr.model.object.zone.VdcRestRep#getDataEndPoints()}
     */
    @Deprecated
    @XmlElement(name = "geo_data_endpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }

    /**
     * @deprecated GEO data endpoints have moved to {@link com.emc.vipr.model.object.zone.VdcRestRep#setDataEndPoints(String)}
     */
    @Deprecated
    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
    }
}

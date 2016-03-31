/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement
public class VdcIpsecPropertiesResponse {

    private String ipsecKey;
    private String vdcConfigVersion;
    private String ipsecStatus;

    @XmlElement(name = "ipsec_key")
    public String getIpsecKey() {
        return ipsecKey;
    }

    public void setIpsecKey(String ipsecKey) {
        this.ipsecKey = ipsecKey;
    }

    @XmlElement(name = "vdc_config_version")
    public String getVdcConfigVersion() {
        return vdcConfigVersion;
    }

    public void setVdcConfigVersion(String vdcConfigVersion) {
        this.vdcConfigVersion = vdcConfigVersion;
    }

    @XmlElement(name = "ipsec_status")
    public String getIpsecStatus() {
        return ipsecStatus;
    }

    public void setIpsecStatus(String ipsecStatus) {
        this.ipsecStatus = ipsecStatus;
    }

}

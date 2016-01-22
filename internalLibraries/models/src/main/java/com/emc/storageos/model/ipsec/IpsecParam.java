/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ipsec_param")
public class IpsecParam {
    private String ipsecKey;
    private long vdcConfigVersion;

    @XmlElement(name="ipsec_key")
    public String getIpsecKey() {
        return ipsecKey;
    }

    public void setIpsecKey(String ipsecKey) {
        this.ipsecKey = ipsecKey;
    }

    @XmlElement(name="vdc_version")
    public long getVdcConfigVersion() {
        return vdcConfigVersion;
    }

    public void setVdcConfigVersion(long vdcConfigVersion) {
        this.vdcConfigVersion = vdcConfigVersion;
    }
}

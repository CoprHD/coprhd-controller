/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="san_zone_member")
public class SanZoneMemberRestRep {
    private String wwn;
    private String alias;
    
    public SanZoneMemberRestRep() {
    }

    public SanZoneMemberRestRep(String wwn, String alias) {
        this.wwn = wwn;
        this.alias = alias;
    }

    @XmlElement
    public String getWwn() {
        return wwn;
    }
    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    @XmlElement
    public String getAlias() {
        return alias;
    }
    public void setAlias(String alias) {
        this.alias = alias;
    }
    
}

/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_config")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteConfigRestRep extends SiteRestRep {
    private String softwareVersion;
    private String dbSchemaVersion;
    private boolean freshInstallation;
    
    @XmlElement(name = "softwareVersion")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    @XmlElement(name = "dbSchemaVersion")
    public String getDbSchemaVersion() {
        return dbSchemaVersion;
    }

    public void setDbSchemaVersion(String dbSchemaVersion) {
        this.dbSchemaVersion = dbSchemaVersion;
    }

    @XmlElement(name = "freshInstallation")
    public boolean isFreshInstallation() {
        return freshInstallation;
    }

    public void setFreshInstallation(boolean freshInstallation) {
        this.freshInstallation = freshInstallation;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteRestRep [uuid=");
        builder.append(this.getUuid());
        builder.append(", name=");
        builder.append(this.getName());
        builder.append(", vip=");
        builder.append(this.getVip());
        builder.append(", hostIPv4AddressMap=");
        builder.append(this.getHostIPv4AddressMap());
        builder.append(", hostIPv6AddressMap=");
        builder.append(this.getHostIPv6AddressMap());
        builder.append(", softwareVersion=");
        builder.append(softwareVersion);
        builder.append(", dbSchemaVersion=");
        builder.append(dbSchemaVersion);
        builder.append(", freshInstallation=");
        builder.append(freshInstallation);
        builder.append("]");
        return builder.toString();
    }
}

/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_precheck")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteAttachPrecheckResponse {
    private String softwareVersion;
    private boolean hasData = false;
    private boolean primarySite = false;
    private boolean attachedToPrimary = false;

    @XmlElement(name = "softwareVersion")
    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    @XmlElement(name = "hasData")
    public boolean isHasData() {
        return hasData;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    @XmlElement(name = "primarySite")
    public boolean isPrimarySite() {
        return primarySite;
    }

    public void setPrimarySite(boolean primarySite) {
        this.primarySite = primarySite;
    }

    @XmlElement(name = "attachedToPrimary")
    public boolean isAttachedToPrimary() {
        return attachedToPrimary;
    }

    public void setAttachedToPrimary(boolean attachedToPrimary) {
        this.attachedToPrimary = attachedToPrimary;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteAttachPrecheckResponse [softwareVersion=");
        builder.append(softwareVersion);
        builder.append(", hasData=");
        builder.append(hasData);
        builder.append(", primarySite=");
        builder.append(primarySite);
        builder.append(", attachedToPrimary=");
        builder.append(attachedToPrimary);
        builder.append("]");
        return builder.toString();
    }
}

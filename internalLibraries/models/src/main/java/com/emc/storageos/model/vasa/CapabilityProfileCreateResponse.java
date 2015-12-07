package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="capability_profile")
public class CapabilityProfileCreateResponse extends VasaCommonRestResponse {

    private Long quotaGB;
    
    private String protocolEndPointType;
    
    private String highAvailability;
    
    private String driveType;

    public Long getQuotaGB() {
        return quotaGB;
    }

    public void setQuotaGB(Long quotaGB) {
        this.quotaGB = quotaGB;
    }

    public String getProtocolEndPointType() {
        return protocolEndPointType;
    }

    public void setProtocolEndPointType(String protocolEndPointType) {
        this.protocolEndPointType = protocolEndPointType;
    }

    public String getHighAvailability() {
        return highAvailability;
    }

    public void setHighAvailability(String highAvailability) {
        this.highAvailability = highAvailability;
    }

    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }
    
}

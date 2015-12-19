package com.emc.storageos.model.vasa;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="capability_profile")
public class CapabilityProfileCreateResponse extends VasaCommonRestResponse {

    private Long quotaGB;
    
    private String protocolEndPointType;
    
    private String highAvailability;
    
    private String driveType;
    
    private String type;
    
    private Set<String> protocols;
    
    private String provisioningType;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElementWrapper(name = "protocols")
    /**
     * The set of supported protocols
     * 
     * @valid FC = Fibre Channel (block)
     * @valid SCSI =  Internet Small Computer System Interface (block)
     * @valid FCoE = Fibre Channel over Ethernet (block)
     * @valid NFS = Network File System (file)
     * @valid NFSv4 = Network File System Version 4 (file)
     * @valid CIFS = Common Internet File System (file)
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }
    
}

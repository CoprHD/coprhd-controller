package com.emc.storageos.model.vasa;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="capability_profile_create")
public class CapabilityProfileCreateRequestParam extends VasaCommonRestRequest{

    private String storageProfileId;
    
    private String storageSystem;
    
    private String storageContainer;
    
    private String highAvailability;
    
    private String provisioningType;
    
    private String driveType;
    
    private Long quotaGB;
    
    private Integer maxNativeSnapshots;
    
    private String protocolEndPointType;
    
    private Set<String> protocols;
    
    //private StringSetMap arrayInfo;
    
    private String type;
    
    public String getStorageProfileId() {
        return storageProfileId;
    }

    public void setStorageProfileId(String storageProfileId) {
        this.storageProfileId = storageProfileId;
    }

    public String getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(String storageSystem) {
        this.storageSystem = storageSystem;
    }

    public String getStorageContainer() {
        return storageContainer;
    }

    public void setStorageContainer(String storageContainer) {
        this.storageContainer = storageContainer;
    }

    public String getHighAvailability() {
        return highAvailability;
    }

    public void setHighAvailability(String highAvailability) {
        this.highAvailability = highAvailability;
    }

    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }

    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    public Long getQuotaGB() {
        return quotaGB;
    }

    public void setQuotaGB(Long quotaGB) {
        this.quotaGB = quotaGB;
    }

    public Integer getMaxNativeSnapshots() {
        return maxNativeSnapshots;
    }

    public void setMaxNativeSnapshots(Integer maxNativeSnapshots) {
        this.maxNativeSnapshots = maxNativeSnapshots;
    }

    public String getProtocolEndPointType() {
        return protocolEndPointType;
    }

    public void setProtocolEndPointType(String protocolEndPointType) {
        this.protocolEndPointType = protocolEndPointType;
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


}

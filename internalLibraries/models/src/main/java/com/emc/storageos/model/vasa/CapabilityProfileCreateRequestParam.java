package com.emc.storageos.model.vasa;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="capability_profile")
public class CapabilityProfileCreateRequestParam extends VasaCommonRestRequest{

    private String storageProfileId;
    
    private String storageSystem;
    
    private String storageContainer;
    
    private String autoTierPolicyName;
    
    private String highAvailability;
    
    private String provisioningType;
    
    private String driveType;
    
    private Long quotaGB;
    
    private Integer maxNativeSnapshots;
    
    private Integer maxNativeContinuousCopies;
    
    private String protocolEndPointType;
    
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

    public String getAutoTierPolicyName() {
        return autoTierPolicyName;
    }

    public void setAutoTierPolicyName(String autoTierPolicyName) {
        this.autoTierPolicyName = autoTierPolicyName;
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

    public Integer getMaxNativeContinuousCopies() {
        return maxNativeContinuousCopies;
    }

    public void setMaxNativeContinuousCopies(Integer maxNativeContinuousCopies) {
        this.maxNativeContinuousCopies = maxNativeContinuousCopies;
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


}

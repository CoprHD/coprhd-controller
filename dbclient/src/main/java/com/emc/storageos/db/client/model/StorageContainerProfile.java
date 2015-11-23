package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("StorageContainerProfile")
public class StorageContainerProfile extends DiscoveredDataObject{

    private String storageProfileId;
    
    private URI storageSystem;
    
    private URI storageContainer;
    
    private String description;
    
    private StringSet protocols;
    
    private String autoTierPolicyName;
    
    private String highAvailability;
    
    private String provisioningType;
    
    private String driveType;
    
    private Long quotaGB;
    
    private Integer maxNativeSnapshots;
    
    private Integer maxNativeContinuousCopies;
    
    private String protocolEndPointType;
    
    private StringSetMap arrayInfo;

    @Name("storageProfileId")
    public String getStorageProfileId() {
        return storageProfileId;
    }

    public void setStorageProfileId(String storageProfileId) {
        this.storageProfileId = storageProfileId;
        setChanged("storageProfileId");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @IndexByKey
    @Name("storageSystem")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageContainer.class)
    @IndexByKey
    @Name("StorageContainer")
    public URI getStorageContainer() {
        return storageContainer;
    }

    public void setStorageContainer(URI storageContainer) {
        this.storageContainer = storageContainer;
        setChanged("storageContainer");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return protocols;
    }

    public void setProtocols(StringSet protocols) {
        this.protocols = protocols;
        setChanged("protocols");
    }

    @Name("autoTierPolicyName")
    public String getAutoTierPolicyName() {
        return autoTierPolicyName;
    }

    public void setAutoTierPolicyName(String autoTierPolicyName) {
        this.autoTierPolicyName = autoTierPolicyName;
        setChanged("autoTierPolicyName");
    }

    @Name("highAvailability")
    public String getHighAvailability() {
        return highAvailability;
    }

    public void setHighAvailability(String highAvailability) {
        this.highAvailability = highAvailability;
        setChanged("highAvailability");
    }

    @Name("provisioningType")
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
        setChanged("provisioningType");
    }

    @Name("driveType")
    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
        setChanged("driveType");
    }

    @Name("quotaGB")
    public Long getQuotaGB() {
        return quotaGB;
    }

    public void setQuotaGB(Long quotaGB) {
        this.quotaGB = quotaGB;
        setChanged("quotaGB");
    }

    @Name("maxNativeSnapshots")
    public Integer getMaxNativeSnapshots() {
        return maxNativeSnapshots;
    }

    public void setMaxNativeSnapshots(Integer maxNativeSnapshots) {
        this.maxNativeSnapshots = maxNativeSnapshots;
        setChanged("maxNativeSnapshots");
    }

    @Name("maxNativeContinuousCopies")
    public Integer getMaxNativeContinuousCopies() {
        return maxNativeContinuousCopies;
    }

    public void setMaxNativeContinuousCopies(Integer maxNativeContinuousCopies) {
        this.maxNativeContinuousCopies = maxNativeContinuousCopies;
        setChanged("maxNativeContinuousCopies");
    }

    @Name("protocolEndPointType")
    public String getProtocolEndPointType() {
        return protocolEndPointType;
    }

    public void setProtocolEndPointType(String protocolEndPointType) {
        this.protocolEndPointType = protocolEndPointType;
        setChanged("protocolEndPointType");
    }

    @Name("arrayInfo")
    public StringSetMap getArrayInfo() {
        return arrayInfo;
    }

    public void setArrayInfo(StringSetMap arrayInfo) {
        this.arrayInfo = arrayInfo;
        setChanged("arrayInfo");
    }
    
    
}

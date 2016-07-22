/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Class represents Storage System Types.
 */
@SuppressWarnings("serial")
@Cf("StorageSystemType")
public class StorageSystemType extends DataObject {
    // Name of Storage Type, like VMAX, VNX, Isilion
    private String storageTypeName;

    // Display Name of Storage Type, like VMAX, VNX, Isilion
    private String storageTypeDispName;

    // Storage type like Block, File or Object
    private String metaType;

    // Storage array is directly manage by CoprHD or thru SMI: Providers
    private Boolean isSmiProvider = false;

    // Storage array URI in string
    private String storageTypeId;

    // By default SSL port is enabled
    private Boolean isDefaultSsl = false;

    private Boolean isDefaultMDM = false;
    private Boolean isOnlyMDM = false;
    // Connect to storage array only thru element manager
    private Boolean isElementMgr = false;

    private Boolean isSecretKey = false;

    private String sslPort;
    private String nonSslPort;
    private String driverClassName;

    // Type of Storage System Types
    public static enum META_TYPE {
        BLOCK, FILE, OBJECT, BLOCK_AND_FILE, ALL
    }

    @Name("storageTypeName")
    public String getStorageTypeName() {
        return storageTypeName;
    }

    public void setStorageTypeName(String name) {
        this.storageTypeName = name;
        setChanged("storageTypeName");
    }

    @Name("storageTypeDispName")
    public String getStorageTypeDispName() {
        return storageTypeDispName;
    }

    public void setStorageTypeDispName(String name) {
        this.storageTypeDispName = name;
        setChanged("storageTypeDispName");
    }

    @Name("metaType")
    public String getMetaType() {
        return metaType;
    }

    public void setMetaType(String metaType) {
        this.metaType = metaType;
        setChanged("metaType");
    }

    @Name("isSmiProvider")
    public Boolean getIsSmiProvider() {
        return isSmiProvider;
    }

    public void setIsSmiProvider(Boolean isSmiProvider) {
        this.isSmiProvider = isSmiProvider;
        setChanged("isSmiProvider");
    }

    @Name("storageTypeId")
    public String getStorageTypeId() {
        return storageTypeId;
    }

    public void setStorageTypeId(String storageId) {
        this.storageTypeId = storageId;
        setChanged("storageTypeId");
    }

    @Name("isDefaultSsl")
    public Boolean getIsDefaultSsl() {
        return isDefaultSsl;
    }

    public void setIsDefaultSsl(Boolean isDefaultSsl) {
        this.isDefaultSsl = isDefaultSsl;
        setChanged("isDefaultSsl");
    }

    @Name("isDefaultMDM")
    public Boolean getIsDefaultMDM() {
        return isDefaultMDM;
    }

    public void setIsDefaultMDM(Boolean isDefaultMDM) {
        this.isDefaultMDM = isDefaultMDM;
        setChanged("isDefaultMDM");
    }

    @Name("isOnlyMDM")
    public Boolean getIsOnlyMDM() {
        return isOnlyMDM;
    }

    public void setIsOnlyMDM(Boolean isOnlyMDM) {
        this.isOnlyMDM = isOnlyMDM;
        setChanged("isOnlyMDM");
    }

    @Name("isElementMgr")
    public Boolean getIsElementMgr() {
        return isElementMgr;
    }

    public void setIsElementMgr(Boolean isElementMgr) {
        this.isElementMgr = isElementMgr;
        setChanged("isElementMgr");
    }

    @Name("isSecretKey")
    public Boolean getIsSecretKey() {
        return isSecretKey;
    }

    public void setIsSecretKey(Boolean isSecretKey) {
        this.isSecretKey = isSecretKey;
        setChanged("isSecretKey");
    }

    @Name("sslPort")
    public String getSslPort() {
        return sslPort;
    }

    public void setSslPort(String sslPort) {
        this.sslPort = sslPort;
        setChanged("sslPort");
    }

    @Name("nonSslPort")
    public String getNonSslPort() {
        return nonSslPort;
    }

    public void setNonSslPort(String nonSslPort) {
        this.nonSslPort = nonSslPort;
        setChanged("nonSslPort");
    }

    @Name("driverClassName")
    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
        setChanged("driverClassName");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((storageTypeName == null) ? 0 : storageTypeName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StorageSystemType other = (StorageSystemType) obj;
        if (storageTypeName == null) {
            if (other.storageTypeName != null)
                return false;
        } else if (!storageTypeName.equals(other.storageTypeName))
            return false;
        return true;
    }
}

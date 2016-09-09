/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.storagesystem.type;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "storagesystem_type")
public class StorageSystemTypeRestRep extends DataObjectRestRep {

    private String storageTypeName;
    private String metaType;
    private String storageTypeId;
    private boolean isSmiProvider = false;
    private boolean isDefaultSsl = false;
    private String storageTypeDispName;
    private boolean isDefaultMDM = false;
    private boolean isOnlyMDM = false;
    private boolean isElementMgr = false;
    private String nonSslPort;
    private String sslPort;
    private String driverClassName;
    private boolean isSecretKey = false;
    private String installStatus;
    private String driverFileName;

    public StorageSystemTypeRestRep() {
    }

    @XmlElement(name = "driver_file_name")
    public String getDriverFileName() {
        return driverFileName;
    }

    public void setDriverFileName(String driverFileName) {
        this.driverFileName = driverFileName;
    }

    @XmlElement(name = "install_status")
    public String getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(String installStatus) {
        this.installStatus = installStatus;
    }

    /**
     * Storage System Type ID internal URI generated by system for unique identification
     *
     */
    @XmlElement(name = "storage_type_id")
    public String getStorageTypeId() {
        return storageTypeId;
    }

    public void setStorageTypeId(String storageTypeId) {
        this.storageTypeId = storageTypeId;
    }

    /**
     * Storage System Type name, example VMAX
     */
    @XmlElement(name = "storage_type_name")
    public String getStorageTypeName() {
        return storageTypeName;
    }

    public void setStorageTypeName(String storageSystemTypeName) {
        this.storageTypeName = storageSystemTypeName;
    }

    /**
     * Storage System Type type, example file, block and object
     */
    @XmlElement(name = "meta_type")
    public String getMetaType() {
        return metaType;
    }

    public void setMetaType(String metaType) {
        this.metaType = metaType;
    }

    /**
     * Is this Storage System Type is managed using SMI Provider. If true CoprHD should connect through Provider only
     */
    @XmlElement(name = "is__smi_provider")
    public boolean getIsSmiProvider() {
        return isSmiProvider;
    }

    public void setIsSmiProvider(boolean isSmiProvider) {
        this.isSmiProvider = isSmiProvider;
    }

    /**
     * Display name for storage system type, example EMC VMAX
     */
    @XmlElement(name = "storage_type_disp_name")
    public String getStorageTypeDispName() {
        return storageTypeDispName;
    }

    public void setStorageTypeDispName(String storageTypeDispName) {
        this.storageTypeDispName = storageTypeDispName;
    }

    /**
     * Storage system type support SSL connection by default. If true provide SSL port
     */
    @XmlElement(name = "is_default_ssl")
    public boolean getIsDefaultSsl() {
        return isDefaultSsl;
    }

    public void setIsDefaultSsl(boolean isDefaultSsl) {
        this.isDefaultSsl = isDefaultSsl;
    }

    /**
     * Is the storage system type support Meta Data Manager as default. This is applicable only for ScaleIO arrays
     */
    @XmlElement(name = "is_default_mdm")
    public boolean getIsDefaultMDM() {
        return isDefaultMDM;
    }

    public void setIsDefaultMDM(boolean isDefaultMDM) {
        this.isDefaultMDM = isDefaultMDM;
    }

    /**
     * Is the storage system type support only Meta Data Manager. This is applicable only for ScaleIO arrays
     */
    @XmlElement(name = "is_only_mdm")
    public boolean getIsOnlyMDM() {
        return isOnlyMDM;
    }

    public void setIsOnlyMDM(boolean isOnlyMDM) {
        this.isOnlyMDM = isOnlyMDM;
    }

    /**
     * Whether the Storage System Type is managed by Element Manager. Applicable for ScaleIO
     */
    @XmlElement(name = "is_element_mgr")
    public boolean getIsElementMgr() {
        return isElementMgr;
    }

    public void setIsElementMgr(boolean isElementMgr) {
        this.isElementMgr = isElementMgr;
    }

    /**
     * Whether the Storage System Type has a secret key.
     */
    @XmlElement(name = "is_secret_key")
    public boolean getIsSecretKey() {
        return isSecretKey;
    }

    public void setIsSecretKey(boolean isSecretKey) {
        this.isSecretKey = isSecretKey;
    }
    
    /**
     * SSL port number, if SSL is supported and enabled
     */
    @XmlElement(name = "ssl_port")
    public String getSslPort() {
        return sslPort;
    }

    public void setSslPort(String sslPort) {
        this.sslPort = sslPort;
    }

    /**
     * Storage System Type port number.
     */
    @XmlElement(name = "non_ssl_port")
    public String getNonSslPort() {
        return nonSslPort;
    }

    public void setNonSslPort(String nonSslPort) {
        this.nonSslPort = nonSslPort;
    }


    /**
     * Storage System Type driver class name. This class is defined in South Bound SDK of CoprHD that device driver developer should
     * implement.
     */
    @XmlElement(name = "driver_class_name")
    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StorageSystemTypeRestRep [storage_type_id=");
        builder.append(storageTypeId);
        builder.append(", storage_type_name=");
        builder.append(storageTypeName);
        builder.append(", storage_type_type=");
        builder.append(metaType);
        builder.append(", isSmiProvider=");
        builder.append(isSmiProvider);
        builder.append(", isDefaultSsl=");
        builder.append(isDefaultSsl);
        builder.append(", nonSslPort=");
        builder.append(nonSslPort);
        builder.append(", sslPort=");
        builder.append(sslPort);
        builder.append(", driverClassName=");
        builder.append(driverClassName);
        builder.append("]");
        return builder.toString();
    }

}

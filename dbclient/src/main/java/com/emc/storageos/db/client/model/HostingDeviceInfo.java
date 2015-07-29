/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.net.URI;
import java.util.Date;

/**
 * Object storage hosting device details
 */
@Cf("HostingDeviceInfo")
@XmlRootElement(name = "hosting_device")
public class HostingDeviceInfo extends DataObject {

    // Type of physical device on which this is hosted
    private String _deviceType;

    // State of the hosting device
    private String _deviceState;

    // Additional info required for initialization of a controller which communicates with the device
    private String _additionalInfo;

    // any info required for the device that must be encrypted
    private String _encryptedInfo;

    // version number to mach additionalInfo
    // allow key controller to determine if update available
    // incremented on DataStore update API
    private Long _additionalInfoVersion = 0L;

    // virtual pool for this hosting device
    private URI _virtualPool;

    // User specified description associated with this device
    private String _deviceDescription;

    private Long _totalSizeInB;

    private Long _totalAvailableInB;

    private Long _totalSizeInGB;

    private Long _totalAvailableInGB;

    // Time when this row was last updated
    private Date _storageCapacityUpdatedTime;

    // IP address of the data node that successfully mounted this hosting device
    private String _dataNodeMountSuccessful;

    // IP address of the data node that ran into failure while mounting this device
    private String _dataNodeMountFailure;

    // Failure message from the data node which ran into failure while mounting this device
    private String _dataNodeFailureMessage;

    // useful in ingestion
    private Boolean _readOnly;

    public static enum DeviceState {
        unknown,
        initializing,
        initialized,
        readytouse,
        error,
        intentToDelete,
        deleting,
        deleted
    }

    public HostingDeviceInfo()
    {
        super();
    }

    /**
     * <p>
     * Additional information specific to the protocol and hosting device
     * </p>
     * <p>
     * read-only, can not modify
     * </p>
     * 
     * @return XML String
     */
    @XmlElement
    @Name("additionalInfo")
    public String getAdditionalInfo() {
        return _additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo)
    {
        _additionalInfo = additionalInfo;
        setChanged("additionalInfo");
    }

    /**
     * IP address of the data node which successfully mounted this device
     * 
     * @return
     */
    @XmlElement
    @Name("dataNodeMountSuccessful")
    public String getDataNodeMountSuccessful() {
        return _dataNodeMountSuccessful;
    }

    public void setDataNodeMountSuccessful(String dataNodeMountSuccessful) {
        _dataNodeMountSuccessful = dataNodeMountSuccessful;
        setChanged("dataNodeMountSuccessful");
    }

    /**
     * IP address of the data node which failed to mount this device
     * 
     * @return
     */
    @XmlElement
    @Name("dataNodeMountFailure")
    public String getDataNodeMountFailure() {
        return _dataNodeMountFailure;
    }

    public void setDataNodeMountFailure(String dataNodeMountFailure) {
        _dataNodeMountFailure = dataNodeMountFailure;
        setChanged("dataNodeMountFailure");
    }

    /**
     * Failure message from the data node which ran into failure while mounting this device
     * 
     * @return
     */
    @XmlElement
    @Name("dataNodeFailureMessage")
    public String getDataNodeFailureMessage() {
        return _dataNodeFailureMessage;
    }

    public void setDataNodeFailureMessage(String dataNodeMountFailure) {
        _dataNodeFailureMessage = dataNodeMountFailure;
        setChanged("dataNodeFailureMessage");
    }

    /**
     * Time when this key pool information was last updated
     * 
     * @return
     */
    @XmlElement
    @Name("storageCapacityUpdatedTime")
    public Date getStorageCapacityUpdatedTime() {
        return _storageCapacityUpdatedTime;
    }

    public void setStorageCapacityUpdatedTime(Date lastUpdated) {
        _storageCapacityUpdatedTime = lastUpdated;
        setChanged("storageCapacityUpdatedTime");
    }

    /**
     * version number associated with additionalInfo
     * 
     * @return
     */
    @XmlElement
    @Name("additionalInfoVersion")
    public Long getAdditionalInfoVersion() {
        return _additionalInfoVersion;
    }

    public void setAdditionalInfoVersion(Long version) {
        _additionalInfoVersion = version;
        setChanged("additionalInfoVersion");
    }

    /**
     * <p>
     * Type of physical device on which this is hosted
     * </p>
     * <p>
     * The following device types are known to web storage
     * </p>
     * <li>UNKNOWN - invalid or not set</li> <li>LOCAL - reserved, do not use</li> <li>NFS - NFS file share created on demand</li> <li>
     * NFS_SERVER - Pre-allocated file share</li> <li>S3 - Amazon S3 format</li> <li>FC - Fibre channel or iSCSI</li> <li>ATMOS - EMC Atmos
     * storage system</li>
     * 
     * @return web storage device type
     */
    @XmlElement
    @Name("deviceType")
    public String getDeviceType() {
        return _deviceType;
    }

    public void setDeviceType(String deviceType)
    {
        _deviceType = deviceType;
        setChanged("deviceType");
    }

    /**
     * <p>
     * State of the hosting device
     * </p>
     * <li>uninitialized - has not been initialized, unavailable for use</li> <li>initializing - being initialized</li> <li>initialized -
     * initialized, ready for use</li> <li>deleting - being deleted, can not be used</li> <li>deleted - deleted, no longer exists</li>
     * 
     * @return
     */
    @XmlElement
    @Name("deviceState")
    public String getDeviceState() {
        return _deviceState != null ? _deviceState : DeviceState.unknown.name();
    }

    public void setDeviceState(String newState) {
        _deviceState = newState;
        setChanged("deviceState");
    }

    /**
     * class of service for this hosting device
     * 
     * @return
     */
    @XmlElement
    @Name("virtualPool")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }

    @XmlElement
    @Name("description")
    public String getDescription() {
        return _deviceDescription;
    }

    public void setDescription(String description) {
        _deviceDescription = description;
        setChanged("description");
    }

    @XmlElement
    @Name("capacity")
    public Long getCapacity() {
        return _totalSizeInB;
    }

    public void setCapacity(Long totalSize) {
        _totalSizeInB = totalSize;
        setChanged("capacity");
    }

    @Name("capacityInGB")
    public Long getCapacityInGB() {
        return _totalSizeInGB;
    }

    public void setCapacityInGB(Long totalSize) {
        _totalSizeInGB = totalSize;
        setChanged("capacityInGB");
    }

    @XmlElement
    @Name("availableStorage")
    public Long getAvailableStorage() {
        return _totalAvailableInB;
    }

    public void setAvailableStorage(Long availableStorage) {
        _totalAvailableInB = availableStorage;
        setChanged("availableStorage");
        setStorageCapacityUpdatedTime(new Date());
    }

    @Name("availableStorageInGB")
    public Long getAvailableStorageInGB() {
        return _totalAvailableInGB;
    }

    public void setAvailableStorageInGB(Long availableStorage) {
        _totalAvailableInGB = availableStorage;
        setChanged("availableStorageInGB");
        setStorageCapacityUpdatedTime(new Date());
    }

    @Encrypt
    @Name("encryptedInfo")
    public String getEncryptedInfo() {
        return _encryptedInfo;
    }

    public void setEncryptedInfo(String encryptedInfo)
    {
        _encryptedInfo = encryptedInfo;
        setChanged("encryptedInfo");
    }

    // internal flag, won't be exposed to user.
    @XmlTransient
    @Name("readOnly")
    public Boolean getReadOnly() {
        return (_readOnly != null) && _readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        _readOnly = readOnly;
        setChanged("readOnly");
    }

}

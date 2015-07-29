/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.valid.EnumType;

/**
 * SMI-S Provider data object
 */
@Deprecated
@Cf("SMISProvider")
public class SMISProvider extends DataObject {

    private static final Logger logger = LoggerFactory.getLogger(SMISProvider.class);

    private StringSet _storageSystems;
    // SMI-S provider IP address
    private String _ipAddress;
    // SMI-S provider port number (5988/5989)
    private Integer _portNumber;
    // SMI-S user name.
    private String _userName;
    // SMI-S password.
    private String _password;
    // SMI-S flag indicates whether or not to use SSL protocol.
    private Boolean _useSSL;
    // IPAddress alternateID is already used inStorageDevice,
    // hence used provider ID : IPAddress:portNumber as ID
    private String _providerID;
    // Provider Description.
    private String _description;
    // Provider manfacturer.
    private String _manufacturer;
    // Provider version.
    private String _versionString;
    // ConnectionStatus tells whether provider is connected to Bourne or not.
    private String _connectionStatus = ConnectionStatus.NOTCONNECTED.toString();

    // Status of a Scan Job
    private String _scanStatus = DataCollectionJobStatus.CREATED.toString();

    // Status Message of a Last Scan Job
    private String _lastScanStatusMessage;

    // Last Scan Time of a Scan Job
    private Long _lastScanTime = 0L;

    // Next Scan Time of a Scan Job
    private Long _nextScanTime = 0L;

    private Long _successScanTime = 0L;

    private String _registrationStatus = RegistrationStatus.UNREGISTERED.toString();

    // used in finding out whether or not the Provider is Compatible
    private String _compatibilityStatus = CompatibilityStatus.UNKNOWN.name();

    // list of decommissioned Systems
    private StringSet _decommissionedSystems;

    /**
     * ConnectionStatus enum.
     */
    public static enum ConnectionStatus {
        CONNECTED,
        NOTCONNECTED
    }

    /*********************************************************
     * AlternateIDIndex - ProviderID (IPAddress-portNumber) *
     * RelationIndex - Empty *
     *********************************************************/

    @Name("ipAddress")
    public String getIPAddress() {
        return _ipAddress;
    }

    public void setIPAddress(String ipAddress) {
        _ipAddress = ipAddress;
        if (null != _portNumber) {
            setProviderID(_ipAddress + "-" + _portNumber);
        }
        setChanged("ipAddress");
    }

    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        _portNumber = portNumber;
        if (null != _ipAddress) {
            setProviderID(_ipAddress + "-" + _portNumber);
        }
        setChanged("portNumber");
    }

    @Name("userName")
    public String getUserName() {
        return _userName;
    }

    public void setUserName(String userName) {
        _userName = userName;
        setChanged("userName");
    }

    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
        setChanged("password");
    }

    @Name("useSSL")
    public Boolean getUseSSL() {
        return (_useSSL != null) && _useSSL;
    }

    public void setUseSSL(Boolean useSSL) {
        _useSSL = useSSL;
        setChanged("useSSL");
    }

    public void setStorageSystems(StringSet storageSystems) {
        _storageSystems = storageSystems;
        setChanged("storageSystems");
    }

    @Name("storageSystems")
    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @IndexByKey
    public StringSet getStorageSystems() {
        return _storageSystems;
    }

    public void setDecommissionedSystems(StringSet decommissionedSystems) {
        _decommissionedSystems = decommissionedSystems;
        setChanged("decommissionedSystems");
    }

    @Name("decommissionedSystems")
    public StringSet getDecommissionedSystems() {
        return _decommissionedSystems;
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

    @Name("manufacturer")
    public String getManufacturer() {
        return _manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        _manufacturer = manufacturer;
        setChanged("manufacturer");
    }

    @Name("version")
    public String getVersionString() {
        return _versionString;
    }

    public void setVersionString(String versionString) {
        _versionString = versionString;
        setChanged("version");
    }

    public void setProviderID(String providerID) {
        _providerID = providerID;
        setChanged("providerID");
    }

    /**
     * AlternateIDIndex - ProviderID (IPAddress-portNumber)
     * RelationIndex - Empty
     * 
     * The reason why IPAddress is not used :
     * IPAddress is being used as a AltId in StorageSystem.
     * If we use IPAddress again in SMISProvider, we would
     * end up having the below Rowkey in AltIdIndex ColumnFamily
     * 
     * 10.24.54.32 - RowKey(IPAddress)
     * Column : urn:SMISProvider:8178828323..
     * Column : urn:StorageSystem:898341992..
     * 
     * Same key , includes both SMISProvider and StorageSystem, and we
     * don't want this.
     * 
     */

    @Name("providerID")
    @AlternateId("AltIdIndex")
    public String getProviderID() {
        return _providerID;
    }

    @EnumType(ConnectionStatus.class)
    @Name("connectionStatus")
    public String getConnectionStatus() {
        return _connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        _connectionStatus = connectionStatus;
        setChanged("connectionStatus");
    }

    @EnumType(DataCollectionJobStatus.class)
    @Name("scanStatus")
    public String getScanStatus() {
        return _scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        _scanStatus = scanStatus;
        setChanged("scanStatus");
    }

    public void setLastScanStatusMessage(String statusMessage) {
        _lastScanStatusMessage = statusMessage;
        setChanged("lastScanStatusMessage");
    }

    @Name("lastScanStatusMessage")
    public String getLastScanStatusMessage() {
        return _lastScanStatusMessage;
    }

    @Name("lastScanTime")
    public Long getLastScanTime() {
        return _lastScanTime;
    }

    public void setLastScanTime(Long lastScanTime) {
        _lastScanTime = lastScanTime;
        setChanged("lastScanTime");
    }

    @Name("nextScanTime")
    public Long getNextScanTime() {
        return _nextScanTime;
    }

    public void setNextScanTime(Long nextScanTime) {
        _nextScanTime = nextScanTime;
        setChanged("nextScanTime");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @EnumType(CompatibilityStatus.class)
    @Name("compatibilityStatus")
    public String getCompatibilityStatus() {
        return _compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        _compatibilityStatus = compatibilityStatus;
        setChanged("compatibilityStatus");
    }

    @Name("successScanTime")
    public Long getSuccessScanTime() {
        return _successScanTime;
    }

    public void setSuccessScanTime(Long time) {
        _successScanTime = time;
        setChanged("successScanTime");
    }

    public void addStorageSystem(DbClient dbClient, StorageSystem storage, boolean activeProvider) throws DatabaseException {

        if (activeProvider) {
            storage.setSmisProviderIP(getIPAddress());
            storage.setSmisPortNumber(getPortNumber());
            storage.setSmisUserName(getUserName());
            storage.setSmisPassword(getPassword());
            storage.setSmisUseSSL(getUseSSL());
            storage.setActiveProviderURI(getId());
        }
        if (storage.getProviders() == null) {
            storage.setProviders(new StringSet());
        }
        storage.getProviders().add(getId().toString());
        dbClient.persistObject(storage);

        if (getStorageSystems() == null) {
            setStorageSystems(new StringSet());
        }
        getStorageSystems().add(storage.getId().toString());
        dbClient.persistObject(this);
    }

    public void removeStorageSystem(DbClient dbClient, StorageSystem storage) throws DatabaseException {

        if (storage.getProviders() != null) {
            storage.getProviders().remove(getId().toString());
        }
        if (storage.getActiveProviderURI().equals(getId())) {
            Iterator<String> iter = storage.getProviders().iterator();
            if (iter.hasNext()) {
                try {
                    storage.setActiveProviderURI(new URI(iter.next()));
                } catch (URISyntaxException ex) {
                    logger.error("URISyntaxException occurred: {}", ex.getMessage());
                }
                catch (URISyntaxException ex)  {}
            }
            else {
                storage.setActiveProviderURI(null);
            }
        }
        dbClient.persistObject(storage);

        if (getStorageSystems() != null) {
            getStorageSystems().remove(storage.getId().toString());
        }
        dbClient.persistObject(this);
    }

    public void removeDecommissionedSystem(DbClient dbClient, String systemNativeGuid) {
        List<URI> oldResources = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getDecommissionedResourceNativeGuidConstraint(systemNativeGuid));
        if (oldResources != null)
        {
            for (URI decomObj : oldResources) {
                _decommissionedSystems.remove(decomObj.toString());
            }
            dbClient.persistObject(this);
        }

    }

    public boolean connected() {
        return ConnectionStatus.valueOf(_connectionStatus) == ConnectionStatus.CONNECTED;
    }

}

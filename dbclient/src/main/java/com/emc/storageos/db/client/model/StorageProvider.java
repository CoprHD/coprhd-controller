/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.SMISProviderToStorageProviderMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.valid.EnumType;

/**
 * StorageProvider data object
 */
@Cf("StorageProvider")
public class StorageProvider extends DataObject {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = -860528426935487712L;

    private static final Logger logger = LoggerFactory.getLogger(StorageProvider.class);

    private StringSet _storageSystems;
    // provider IP address
    private String _ipAddress;
    // Secondary IP addresses
    private StringSet _secondaryIps;
    // provider port number (2001/5988/5989)
    private Integer _portNumber;
    // user name.
    private String _userName;
    // password.
    private String _password;
    // flag indicates whether or not to use SSL protocol.
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
    private String _scanStatus;

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

    private String _interfaceType;

    // list of decommissioned Systems
    private StringSet _decommissionedSystems;

    /*
     * This map will be used to hold any key value pairs required
     * to be fetched during provisioning.
     * 
     * Adding this map to store cinder end point information required
     * during the provisioning. During the cinder discovery, the end
     * point information will be fetched and kept as key value pairs.
     */
    private StringMap keys;

    /**
     * Element manager URL
     */
    private String elementManagerURL;

    /**
     * Secret key. A token to access to storage provider.
     */
    private String secretKey;

    /**
     * Secondary set of credentials. This is used for example in the
     * case of ScaleIO, where there is a password to SSH into the
     * MDM box (primary creds) and the secondary creds will be the
     * ones required to run the SCLI commands
     */
    private String secondaryPassword;
    private String secondaryUsername;

    @Name("elementManagerURL")
    public String getElementManagerURL() {
        return elementManagerURL;
    }

    public void setElementManagerURL(String elementManagerURL) {
        this.elementManagerURL = elementManagerURL;
        setChanged("elementManagerURL");
    }

    @Encrypt
    @Name("secretKey")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        setChanged("secretKey");
    }

    @Name("secondaryUsername")
    public String getSecondaryUsername() {
        return secondaryUsername;
    }

    public void setSecondaryUsername(String username) {
        this.secondaryUsername = username;
        setChanged("secondaryUsername");
    }

    @Encrypt
    @Name("secondaryPassword")
    public String getSecondaryPassword() {
        return secondaryPassword;
    }

    public void setSecondaryPassword(String password) {
        this.secondaryPassword = password;
        setChanged("secondaryPassword");
    }

    public static enum InterfaceType {
        hicommand,
        smis,
        ddmc,
        vplex,
        cinder,
        ibmxiv,
        scaleioapi,
        xtremio,
        ceph;

        /**
         * Gets the supported system types for the given interface type.
         */
        public static List<String> getSystemTypesForInterfaceType(InterfaceType interfaceType) {
            List<String> systemTypes = new ArrayList<String>();
            if (smis.equals(interfaceType)) {
                systemTypes.add(Type.vmax.name());
                systemTypes.add(Type.vnxblock.name());
            } else if (hicommand.equals(interfaceType)) {
                systemTypes.add(Type.hds.name());
            } else if (vplex.equals(interfaceType)) {
                systemTypes.add(Type.vplex.name());
            } else if (cinder.equals(interfaceType)) {
                systemTypes.add(Type.openstack.name());
            } else if (ibmxiv.equals(interfaceType)) {
                systemTypes.add(Type.ibmxiv.name());
            } else if (ddmc.equals(interfaceType)) {
                systemTypes.add(Type.datadomain.name());
            } else if (scaleioapi.equals(interfaceType)) {
                systemTypes.add(Type.scaleio.name());
            } else if (xtremio.equals(interfaceType)) {
                systemTypes.add(Type.xtremio.name());
            } else if (ceph.equals(interfaceType)) {
                systemTypes.add(Type.ceph.name());
            }
            return systemTypes;
        }
    }

    /**
     * ConnectionStatus enum.
     */
    public static enum ConnectionStatus {
        CONNECTED,
        NOTCONNECTED,
        INITIALIZING
    }

    /*********************************************************
     * AlternateIDIndex - ProviderID (IPAddress-portNumber) *
     * RelationIndex - Empty *
     *********************************************************/

    @CustomMigrationCallback(callback = SMISProviderToStorageProviderMigration.class)
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

    @Name("secondaryIps")
    public StringSet getSecondaryIps() {
        if (_secondaryIps == null) {
            return new StringSet();
        }
        return _secondaryIps;
    }

    public void setSecondaryIps(StringSet secondaryIps) {
        _secondaryIps = secondaryIps;
        setChanged("secondaryIps");
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

    @EnumType(InterfaceType.class)
    @Name("interfaceType")
    @AlternateId("AltIdIndex")
    public String getInterfaceType() {
        return _interfaceType;
    }

    public void setInterfaceType(String interfaceType) {
        _interfaceType = interfaceType;
        setChanged("interfaceType");
    }

    @EnumType(DataCollectionJobStatus.class)
    @Name("scanStatus")
    public String getScanStatus() {
        return _scanStatus == null ? DataCollectionJobStatus.CREATED.toString() : _scanStatus;
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

    public enum GlobalKeys {
        SIO_CLI
    }

    @Name("keys")
    public StringMap getKeys() {
        return keys;
    }

    public String getKeyValue(String key) {
        String value = null;
        if (keys != null) {
            value = keys.get(key);
        }
        return (value == null) ? NullColumnValueGetter.getNullStr() : value;
    }

    public void setKeys(StringMap keys) {
        this.keys = keys;
        setChanged("keys");
    }

    public void addKey(String key, String value) {
        if (getKeys() == null) {
            setKeys(new StringMap());
        }

        getKeys().put(key, value);
        setChanged("keys");

    }

    public void removeKey(String key) {
        if (keys != null) {
            getKeys().remove(key);
            setChanged("keys");
        }
    }

    public void removeKeys(String[] keyArray) {
        if (keys != null) {

            for (String key : keyArray)
            {
                getKeys().remove(key);
            }
            setChanged("keys");

        }
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
        dbClient.updateAndReindexObject(this);
    }

    public void removeStorageSystem(DbClient dbClient, StorageSystem storage) throws DatabaseException {

        if (storage.getProviders() != null) {
            storage.getProviders().remove(getId().toString());
        }
        if (storage.getActiveProviderURI().equals(getId())) {
            if (null != storage.getProviders() && !storage.getProviders().isEmpty()) {
                Iterator<String> iter = storage.getProviders().iterator();
                if (iter.hasNext()) {
                    try {
                        storage.setActiveProviderURI(new URI(iter.next()));
                    } catch (URISyntaxException ex) {
                        logger.error("URISyntaxException occurred: {}", ex.getMessage());
                    }
                }
                else {
                    storage.setActiveProviderURI(null);
                }
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
        if (oldResources != null) {
            for (URI decomObj : oldResources) {
                _decommissionedSystems.remove(decomObj.toString());
            }
            dbClient.persistObject(this);
        }

    }

    public boolean connected() {
        return ConnectionStatus.valueOf(_connectionStatus) == ConnectionStatus.CONNECTED;
    }

    public boolean initializing() {
        return ConnectionStatus.valueOf(_connectionStatus) == ConnectionStatus.INITIALIZING;
    }
}

/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;
import com.google.common.base.Strings;

/**
 * StorageDevice data object
 */
@Cf("StorageSystem")
public class StorageSystem extends DiscoveredSystemObject {

    // serial number
    private String _serialNumber;

    // device OS/firmware major version
    private String _majorVersion;

    // device OS/firmware minor version
    private String _minorVersion;

    // virtual array of this storage device
    private URI _virtualArray;

    // management port number
    private Integer _portNumber;

    // management interface user
    // TODO - this needs to be encrypted
    private String _username;

    // management interface password
    // TODO - this needs to be encrypted
    private String _password;

    // management interface IP address
    private String _ipAddress;

    // secondary/backup management interface IP addresses
    private StringSet _secondaryIPs;

    // SMI-S interface IP address
    private String _smisProviderIP;

    // SMI-S port number (5989)
    private Integer _smisPortNumber;

    // SMI-S user.
    private String _smisUserName;

    // SMI-S password.
    private String _smisPassword;

    // SMI-S flag indicates whether or not to use SSL protocol.
    private Boolean _smisUseSSL;

    // ConnectionStatus tells whether provider is connected to Bourne or not.
    // This field will be used for vnxfile array only.
    private String _smisConnectionStatus;

    // Export masks on the storage array, map of mask name => mask id
    private StringMap _exportMasks;

    private StringSet _protocols;
    // Determining, whether an Array is reachable
    private Boolean _reachable;
    // Array Firmware Version
    private String _firmwareVersion;
    /**
     * Holds the Active Provider URI.
     */
    private URI _activeProviderURI;

    /**
     * Holds all the providers which are managing this storagesystem.
     */
    private StringSet _providers;

    // for manually created Storage Systems, this flag will be set to FALSE
    private Boolean _autodiscovered = true;

    // For virtual storage systems, like VPlex, we need to know the
    // backend storage systems to which the virtual storage system
    // is connected.
    private StringSet _associatedStorageSystems;

    private Boolean _autoTieringEnabled = false;

    // System Unique Identifier to be used before creating a Storage System.
    // provider ID : IPAddress:portNumber as ID. This should be used as AlterID
    private String _mgmtAccessPoint;

    // Model Number of Array
    private String _model;

    // Supported Asynchronous Actions
    private StringSet _supportedAsynchronousActions;

    private String _supportedProvisioningType;

    // Whether limit on number of Resources has been set
    private Boolean isResourceLimitSet = false;
    // Max Resources limit
    private Integer maxResources;

    // VirtualArrays where this Protection System is available
    private StringSet _virtualArrays;

    // This field will be updated when an EMCRefresh is called against the array
    private Long _lastRefresh;

    // supported Replication like SRDF, TimeFinder..
    private StringSet supportedReplicationTypes;

    // List of remote storage Systems connected by active RA Group Link.
    private StringSet remotelyConnectedTo;

    // list of SRDF R2 CGs, tied with this system
    private StringSet targetCgs;

    private Boolean usingSmis80 = false;

    // VPLEX serial number ID to cluster ID mapping; required for quick look-ups
    private StringMap vplexAssemblyIdtoClusterId;

    // storage system's ports average metrics. This number is computed via
    // {@link PortMetricProcessor#computeStorageSystemAvgPortMetrics}
    private Double averagePortMetrics;
    
    public static enum SupportedFileReplicationTypes {
        REMOTE("remote"), LOCAL("local");

        private final String _replicationType;

        SupportedFileReplicationTypes(final String replicationType) {
            _replicationType = replicationType;
        }

        public String getReplicationType() {
            return _replicationType;
        }

        public static String getReplicationTypeName(final String replicationTypeIdentifier) {
            for (SupportedFileReplicationTypes repType : copyOfValues) {
                if (repType.getReplicationType().contains(replicationTypeIdentifier)) {
                    return repType.toString();
                }
            }
            return null;
        }

        private static final SupportedFileReplicationTypes[] copyOfValues = values();
    }

    public static enum SupportedProvisioningTypes {
        THICK, THIN, THIN_AND_THICK, NONE
    }

    // Namespace denotes the Element used in Discovery
    public static enum Discovery_Namespaces {
        UNMANAGED_VOLUMES, UNMANAGED_FILESYSTEMS, BLOCK_SNAPSHOTS, UNMANAGED_CGS, ALL
    }

    public static enum SupportedReplicationTypes {
        SRDF("4,5"), LOCAL(""), SRDFMetro("");

        private final String _replicationType;

        SupportedReplicationTypes(final String replicationType) {
            _replicationType = replicationType;
        }

        public String getReplicationType() {
            return _replicationType;
        }

        public static String getReplicationTypeName(final String replicationTypeIdentifier) {
            for (SupportedReplicationTypes repType : copyOfValues) {
                if (repType.getReplicationType().contains(replicationTypeIdentifier)) {
                    return repType.toString();
                }
            }
            return null;
        }

        private static final SupportedReplicationTypes[] copyOfValues = values();
    }

    // Asynchronous Actions enum
    public static enum AsyncActions {
        CreateElementReplica, CreateGroupReplica
    }

    /*************************************************
     * AlternateIDIndex - serialNumber,smisProviderIP* RelationIndex - VirtualArray *
     ************************************************/

    @AlternateId("AltIdIndex")
    @Name("serialNumber")
    public String getSerialNumber() {
        return _serialNumber;
    }

    public void setSerialNumber(final String serialNumber) {
        this._serialNumber = serialNumber;
        setChanged("serialNumber");
    }

    @Name("majorVersion")
    public String getMajorVersion() {
        return _majorVersion;
    }

    public void setMajorVersion(final String majorVersion) {
        this._majorVersion = majorVersion;
        setChanged("majorVersion");
    }

    @Name("minorVersion")
    public String getMinorVersion() {
        return _minorVersion;
    }

    public void setMinorVersion(final String minorVersion) {
        this._minorVersion = minorVersion;
        setChanged("minorVersion");
    }

    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(final String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    @Name("secondaryIPs")
    public StringSet getSecondaryIPs() {
        return _secondaryIPs;
    }

    public void setSecondaryIPs(final StringSet secondaryIPs) {
        _secondaryIPs = secondaryIPs;
        setChanged("secondaryIPs");
    }

    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    public void setPortNumber(final Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    @Name("username")
    public String getUsername() {
        return _username;
    }

    public void setUsername(final String username) {
        this._username = username;
        setChanged("username");
    }

    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    public void setPassword(final String password) {
        this._password = password;
        setChanged("password");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @Name("varray")
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(final URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @AlternateId("AltIdIndex")
    @Name("smisProviderIP")
    public String getSmisProviderIP() {
        return _smisProviderIP;
    }

    public void setSmisProviderIP(final String smisProviderIP) {
        this._smisProviderIP = smisProviderIP;
        setChanged("smisProviderIP");
    }

    @Name("smisPortNumber")
    public Integer getSmisPortNumber() {
        return _smisPortNumber;
    }

    public void setSmisPortNumber(final Integer smisPortNumber) {
        this._smisPortNumber = smisPortNumber;
        setChanged("smisPortNumber");
    }

    @Name("smisUserName")
    public String getSmisUserName() {
        return _smisUserName;
    }

    public void setSmisUserName(final String smisUserName) {
        this._smisUserName = smisUserName;
        setChanged("smisUserName");
    }

    @Encrypt
    @Name("smisPassword")
    public String getSmisPassword() {
        return _smisPassword;
    }

    public void setSmisPassword(final String smisPassword) {
        this._smisPassword = smisPassword;
        setChanged("smisPassword");
    }

    @Name("smisUseSSL")
    public Boolean getSmisUseSSL() {
        return _smisUseSSL != null && _smisUseSSL;
    }

    public void setSmisUseSSL(final Boolean smisUseSSL) {
        this._smisUseSSL = smisUseSSL;
        setChanged("smisUseSSL");
    }

    @Name("smisConnectionStatus")
    public String getSmisConnectionStatus() {
        return _smisConnectionStatus;
    }

    public void setSmisConnectionStatus(String _smisConnectionStatus) {
        this._smisConnectionStatus = _smisConnectionStatus;
        setChanged("smisConnectionStatus");
    }

    @Name("exportMasks")
    public StringMap getExportMasks() {
        return _exportMasks;
    }

    public void setExportMasks(final StringMap exportMasks) {
        _exportMasks = exportMasks;
    }

    @Name("protocols")
    public StringSet getProtocols() {
        return _protocols;
    }

    public void setProtocols(final StringSet protocols) {
        _protocols = protocols;
    }

    @Name("reachable")
    public Boolean getReachableStatus() {
        return _reachable == null ? false : _reachable;
    }

    public void setReachableStatus(final Boolean reachable) {
        _reachable = reachable;
        setChanged("reachable");
    }

    @Name("firmwareVersion")
    public String getFirmwareVersion() {
        return _firmwareVersion;
    }

    public void setFirmwareVersion(final String firmwareVersion) {
        _firmwareVersion = firmwareVersion;
        setChanged("firmwareVersion");
    }

    @Name("activeProviderURI")
    public URI getActiveProviderURI() {
        return _activeProviderURI;
    }

    public void setActiveProviderURI(final URI activeProviderURI) {
        _activeProviderURI = activeProviderURI;
        setChanged("activeProviderURI");
    }

    @Name("providers")
    public StringSet getProviders() {
        return _providers;
    }

    public void setProviders(final StringSet providers) {
        _providers = providers;
        setChanged("providers");
    }

    @Name("autodiscovered")
    public Boolean getAutoDiscovered() {
        return _autodiscovered == null ? false : _autodiscovered;
    }

    public void setAutoDiscovered(final Boolean autodiscovered) {
        _autodiscovered = autodiscovered;
        setChanged("autodiscovered");
    }

    public void setAutoTieringEnabled(final Boolean autoTieringEnabled) {
        _autoTieringEnabled = autoTieringEnabled;
        setChanged("autoTieringEnabled");
    }

    @Name("autoTieringEnabled")
    public Boolean getAutoTieringEnabled() {
        return _autoTieringEnabled == null ? false : _autoTieringEnabled;
    }

    /**
     * Getter for the ids of the backend storage systems associated with a virtual storage system.
     * 
     * @return The set of ids of the backend storage systems associated with a virtual storage
     *         system.
     */
    @Deprecated
    @AlternateId("AssSystemsAltIdIndex")
    @Name("associatedStorageSystems")
    public StringSet getAssociatedStorageSystems() {
        if (_associatedStorageSystems == null) {
            setAssociatedStorageSystems(new StringSet());
        }
        return _associatedStorageSystems;
    }

    /**
     * Setter for the ids of the backend storage systems associated with a virtual storage system.
     * 
     * @param storageSystems
     *            The ids of the backend storage systems associated with a virtual storage system.
     */
    @Deprecated
    public void setAssociatedStorageSystems(final StringSet storageSystems) {
        _associatedStorageSystems = storageSystems;
    }

    /**
     * Adds the id of a storage system to the set of backend storage systems associated with a
     * virtual storage system.
     * 
     * @param storageSystemId
     *            The id of a storage system.
     */
    @Deprecated
    public void addAssociatedStorageSystem(final String storageSystemId) {
        if (_associatedStorageSystems == null) {
            setAssociatedStorageSystems(new StringSet());
        }
        _associatedStorageSystems.add(storageSystemId);
    }

    /**
     * Removes the id of a storage system from the set of backend storage systems associated with a
     * virtual storage system.
     * 
     * @param storageSystemId
     *            The id of a storage system.
     */
    @Deprecated
    public void removeAssociatedStorageSystem(final String storageSystemId) {
        if (_associatedStorageSystems != null) {
            _associatedStorageSystems.remove(storageSystemId);
        }
    }

    public void setModel(final String model) {
        _model = model;
        setChanged("model");
    }

    @Name("model")
    public String getModel() {
        return _model;
    }

    /**
     * Unique for StorageSystem to identify its uniqueness before creating it.
     * 
     * @return
     */
    @Name("mgmtAccessPoint")
    @AlternateId("AltIdIndex")
    public String getMgmtAccessPoint() {
        return _mgmtAccessPoint;
    }

    public void setMgmtAccessPoint(final String mgmtAccessPoint) {
        _mgmtAccessPoint = mgmtAccessPoint;
        setChanged("mgmtAccessPoint");
    }

    public void setSupportedProvisioningType(final String supportedProvisioningType) {
        _supportedProvisioningType = supportedProvisioningType;
        setChanged("supportedProvisionType");
    }

    @EnumType(SupportedProvisioningTypes.class)
    @Name("supportedProvisionType")
    public String getSupportedProvisioningType() {
        return _supportedProvisioningType;
    }

    public void setSupportedAsynchronousActions(final StringSet supportedAsynchronousActions) {
        _supportedAsynchronousActions = supportedAsynchronousActions;
        setChanged("supportedAsynchronousActions");
    }

    @EnumType(AsyncActions.class)
    @Name("supportedAsynchronousActions")
    public StringSet getSupportedAsynchronousActions() {
        return _supportedAsynchronousActions;
    }

    @Name("isResourceLimitSet")
    public Boolean getIsResourceLimitSet() {
        return isResourceLimitSet == null ? false : isResourceLimitSet;
    }

    public void setIsResourceLimitSet(final Boolean isResourceLimitSet) {
        this.isResourceLimitSet = isResourceLimitSet;
        setChanged("isResourceLimitSet");
    }

    @Name("maxResources")
    public Integer getMaxResources() {
        return isResourceLimitSet ? maxResources : -1;
    }

    public void setMaxResources(final Integer maxResources) {
        this.maxResources = maxResources > 0 ? maxResources : 0;
        setChanged("maxResources");
    }

    @Name("virtualArrays")
    @AlternateId("varrayAltIdIndex")
    public StringSet getVirtualArrays() {
        return _virtualArrays;
    }

    public void setVirtualArrays(final StringSet virtualArrays) {
        _virtualArrays = virtualArrays;
        setChanged("virtualArrays");
    }

    @Name("lastRefreshed")
    public Long getLastRefresh() {
        return _lastRefresh != null ? _lastRefresh : 0L;
    }

    public void setLastRefresh(final Long refresh) {
        _lastRefresh = refresh;
        setChanged("lastRefreshed");
    }

    @EnumType(SupportedReplicationTypes.class)
    @Name("supportedreplicationTypes")
    public StringSet getSupportedReplicationTypes() {
        return supportedReplicationTypes;
    }

    public void setSupportedReplicationTypes(final StringSet supportedReplicationTypes) {
        this.supportedReplicationTypes = supportedReplicationTypes;
    }

    @Name("connectedTo")
    public StringSet getRemotelyConnectedTo() {
        return remotelyConnectedTo;
    }

    public void setRemotelyConnectedTo(final StringSet remotelyConnectedTo) {
        this.remotelyConnectedTo = remotelyConnectedTo;
    }

    public boolean containsRemotelyConnectedTo(final URI remoteStorageSystemID) {
        if (this.remotelyConnectedTo == null) {
            return false;
        }

        if (this.remotelyConnectedTo.contains(remoteStorageSystemID.toString())) {
            return true;
        }

        return false;
    }

    @Name("targetR2Cgs")
    public StringSet getTargetCgs() {
        return targetCgs;
    }

    public void setTargetCgs(final StringSet targetCgs) {
        this.targetCgs = targetCgs;
        setChanged("targetR2Cgs");
    }

    public boolean checkIfVmax3() {
        boolean check = false;
        if (deviceIsType(Type.vmax) && !Strings.isNullOrEmpty(_firmwareVersion)) {
            String fwMajorVersion = _firmwareVersion.split("\\.")[0];
            check = (Integer.parseInt(fwMajorVersion) >= 5977);
        }
        return check;
    }

    @Name("usingSmis80")
    public Boolean getUsingSmis80() {
        return usingSmis80 != null && usingSmis80;
    }

    public void setUsingSmis80(Boolean usingSmis80) {
        this.usingSmis80 = usingSmis80;
        setChanged("usingSmis80");
    }

    @Name("averagePortMetrics")
    public Double getAveragePortMetrics() {
        return averagePortMetrics;
    }

    public void setAveragePortMetrics(Double averagePortMetrics) {
        this.averagePortMetrics = averagePortMetrics;
        setChanged("averagePortMetrics");
    }

    @Name("vplexAssemblyIdToClusterId")
    public StringMap getVplexAssemblyIdtoClusterId() {
        return vplexAssemblyIdtoClusterId;
    }

    public void setVplexAssemblyIdtoClusterId(StringMap vplexAssemblyIdtoClusterId) {
        this.vplexAssemblyIdtoClusterId = vplexAssemblyIdtoClusterId;
    }
}

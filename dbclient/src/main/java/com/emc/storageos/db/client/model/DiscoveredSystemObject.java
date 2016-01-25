/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;
import com.google.common.base.Strings;

/**
 * StorageSystem, NetworkSystem and ProtectionSystem share few common properties
 * which had been placed here.
 */
public class DiscoveredSystemObject extends DiscoveredDataObject {

    // type of array e.g. vnxBlock, vnxFile, isilon, vmax, netapp
    private String _systemType;

    // used in finding out whether or not the Array is Compatible
    private String _compatibilityStatus = CompatibilityStatus.UNKNOWN.name();

    // Status of a Discovery Job
    private String _discoveryStatus;

    // Status Message of a Last Discovery Job
    private String _lastDiscoveryStatusMessage;

    // Last Scan or Run Time of a Scan Job
    private Long _lastDiscoveryRunTime = 0L;

    // Next Scan or Run Time of a Scan Job
    @Deprecated
    private Long _nextDiscoveryRunTime = 0L;

    private Long _successDiscoveryTime = 0L;

    private String _meteringStatus = DataCollectionJobStatus.CREATED.name();

    private Long _lastMeteringRunTime = 0L;

    private Long _nextMeteringRunTime = 0L;

    private Long _successMeteringTime = 0L;

    /**
     * Bourne supports Registered and UnRegistered Elements.
     * Registered : StorageSystems, Pools,Ports,.. which are explicitly
     * registered by Bourne
     * UnRegistered : StorageSystems,Ports, which had been discovered,but
     * not still used by Bourne
     * _registrationStatus field denotes whether an object is registered by Bourne.
     */
    private String _registrationStatus = RegistrationStatus.REGISTERED.name();

    @Name("systemType")
    public String getSystemType() {
        return _systemType;
    }

    public void setSystemType(String systemType) {
        _systemType = systemType;
        setChanged("systemType");
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

    public boolean storageSystemIsFile() {
        return Type.isFileStorageSystem(_systemType);
    }

    public boolean storageSystemHasProvider() {
        return Type.isProviderStorageSystem(_systemType);
    }

    public void setDiscoveryStatus(String status) {
        _discoveryStatus = status;
        setChanged("discoveryStatus");
    }

    @EnumType(DataCollectionJobStatus.class)
    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return _discoveryStatus == null ? DataCollectionJobStatus.CREATED.name() : _discoveryStatus;
    }

    public void setLastDiscoveryStatusMessage(String statusMessage) {
        _lastDiscoveryStatusMessage = statusMessage;
        setChanged("lastDiscoveryStatusMessage");
    }

    @Name("lastDiscoveryStatusMessage")
    public String getLastDiscoveryStatusMessage() {
        return _lastDiscoveryStatusMessage;
    }

    public void setLastDiscoveryRunTime(Long lastRunTime) {
        _lastDiscoveryRunTime = lastRunTime;
        setChanged("lastDiscoveryRunTime");
    }

    @Name("lastDiscoveryRunTime")
    public Long getLastDiscoveryRunTime() {
        return _lastDiscoveryRunTime;
    }

    @Deprecated
    public void setNextDiscoveryRunTime(Long nextRunTime) {
        _nextDiscoveryRunTime = nextRunTime;
        setChanged("nextDiscoveryRunTime");
    }

    @Deprecated
    @Name("nextDiscoveryRunTime")
    public Long getNextDiscoveryRunTime() {
        return _nextDiscoveryRunTime;
    }

    public void setMeteringStatus(String status) {
        _meteringStatus = status;
        setChanged("meteringStatus");
    }

    @EnumType(DataCollectionJobStatus.class)
    @Name("meteringStatus")
    public String getMeteringStatus() {
        return _meteringStatus;
    }

    public void setLastMeteringRunTime(Long lastMeteringRunTime) {
        _lastMeteringRunTime = lastMeteringRunTime;
        setChanged("lastMeteringRunTime");
    }

    @Name("lastMeteringRunTime")
    public Long getLastMeteringRunTime() {
        return _lastMeteringRunTime;
    }

    public void setNextMeteringRunTime(Long nextMeteringRunTime) {
        _nextMeteringRunTime = nextMeteringRunTime;
        setChanged("nextMeteringRunTime");
    }

    @Name("nextMeteringRunTime")
    public Long getNextMeteringRunTime() {
        return _nextMeteringRunTime;
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    @Name("successMeteringTime")
    public Long getSuccessMeteringTime() {
        return _successMeteringTime;
    }

    public void setSuccessMeteringTime(Long time) {
        _successMeteringTime = time;
        setChanged("successMeteringTime");
    }

    @Name("successDiscoveryTime")
    public Long getSuccessDiscoveryTime() {
        return _successDiscoveryTime;
    }

    public void setSuccessDiscoveryTime(Long time) {
        _successDiscoveryTime = time;
        setChanged("successDiscoveryTime");
    }

    public boolean deviceIsType(final Type thisType) {
        final String type = getSystemType();
        return !Strings.isNullOrEmpty(type) && type.equals(thisType.name());
    }
}

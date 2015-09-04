/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring;

// Java imports
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple bean class allows us to cache serial numbers for storage devices that
 * are monitored via the CIMStorageMonitor. The serial numbers are stored in a
 * Map and accessed using the key provided when the serial number was added to
 * the Map. The class is used in cases where the serial number for the storage
 * device is not provided in a CIM indication, but the key is. Rather than
 * lookup the storage device in the database, this class is used. We need the
 * serial number for the storage device to form the alternate id for storage
 * volumes and file systems. The alternate id is used to lookup the storage
 * volume/filesystem in the database for the purpose of extracting the Tenant,
 * VirtualPool, Volume URN, etc..., which we associate with event records created in the
 * database.
 */
public class StorageDeviceInfo {

    // A map of serial numbers for the storage storage devices being monitored.
    private ConcurrentHashMap<String, String> _snMap = new ConcurrentHashMap<String, String>();

    /**
     * Default constructor for Spring injection.
     */
    public StorageDeviceInfo() {
    }

    /**
     * Adds an entry for the storage device serial number.
     * 
     * @param snKey The key used to access the serial number.
     * @param sn The storage device serial number.
     */
    public void addSerialNumber(String snKey, String sn) {
        _snMap.put(snKey, sn);
    }

    /**
     * Removes the entry for the storage device serial number.
     * 
     * @param snKey The key used to access the serial number.
     */
    public void removeSerialNumber(String snKey) {
        _snMap.remove(snKey);
    }

    /**
     * Getter for the storage device serial number.
     * 
     * @param snKey The key used to access the serial number.
     * 
     * @return The serial number associated with the passed key, or null if it
     *         does not exist.
     */
    public String getSerialNumber(String snKey) {
        return _snMap.get(snKey);
    }
}

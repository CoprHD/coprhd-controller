package com.emc.storageos.db.client.model.storagedriver;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSetMap;

/**
 * This is the Cassandra table for SB SDK storage driver registry.
 */
@Cf("DriverRegistry")
public class DriverRegistryRecord extends DataObject {
    private String _driverName;
    // registry key
    private String _registryKey;
    // map of name-value pairs for a registry key
    /*
    Example of registry entry for storage drive:
    driverName: scaleioDriver
    key: connectionData
    attributes:
        ipAddress: 10.246.13.155
        port: 8443
        userName: root
        password: ChangeMe
     */
    private StringSetMap _attributes;

    @Name("driverName")
    @AlternateId("AltIdIndex")
    public String getDriverName() {
        return _driverName;
    }

    public void setDriverName(String driverName) {
        _driverName = driverName;
        setChanged("driverName");
    }

    @Name("registryKey")
    public String getRegistryKey() {
        return _registryKey;

    }

    public void setRegistryKey(String registryKey) {
        _registryKey = registryKey;
        setChanged("registryKey");
    }

    @Name("attributes")
    public StringSetMap getAttributes() {
        return _attributes;
    }

    public void setAttributes(StringSetMap attributes) {
        _attributes = attributes;
        setChanged("attributes");
    }
}

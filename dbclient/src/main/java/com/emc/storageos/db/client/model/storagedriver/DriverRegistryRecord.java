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
    private String driverName;
    // registry key
    private String registryKey;
    // map of name-value pairs for a registry key
    /*
    Example of registry entry for storage drive:
    driverName: arrayXDriver
    key: connectionData
    attributes:
        ipAddress: 10.212.13.145
        port: 8567
        userName: name
        password: Password
     */
    private StringSetMap attributes;

    @Name("driverName")
    @AlternateId("AltIdIndex")
    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
        setChanged("driverName");
    }

    @Name("registryKey")
    public String getRegistryKey() {
        return registryKey;

    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
        setChanged("registryKey");
    }

    @Name("attributes")
    public StringSetMap getAttributes() {
        return attributes;
    }

    public void setAttributes(StringSetMap attributes) {
        this.attributes = attributes;
        setChanged("attributes");
    }
}

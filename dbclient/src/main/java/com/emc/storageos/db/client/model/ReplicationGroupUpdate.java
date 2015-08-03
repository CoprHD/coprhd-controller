/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * When we move this from Cassandra to DT, then updates to
 * the replication group will become key updates.
 */
@Cf("ReplicationGroupUpdate")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
@XmlRootElement(name = "replication_group")
public class ReplicationGroupUpdate extends DataObject {
    private StringSet zoneCosSet;

    // status is based on the enum definition ReplicationGroupUpdateEx.ReconfigStateType
    private String rgStatus;

    private String updateType;

    public ReplicationGroupUpdate() {
        super();
    }

    @XmlElement
    @Name("zoneCosSet")
    public StringSet getZoneCosSet() {
        return zoneCosSet;
    }

    public void setZoneCosSet(StringSet zoneCosSet) {
        this.zoneCosSet = zoneCosSet;
        setChanged("zoneCosSet");
    }

    @XmlElement
    @Name("rgStatus")
    public String getRgStatus() {
        return rgStatus;
    }

    public void setRgStatus(String rgStatus) {
        this.rgStatus = rgStatus;
        setChanged("rgStatus");
    }

    @XmlElement
    @Name("updateType")
    public String getUpdateType() {
        return updateType;
    }

    public void setUpdateType(String updateType) {
        this.updateType = updateType;
        setChanged("updateType");
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
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

@Cf("ReplicationGroupInfo")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
@XmlRootElement(name = "replication_group")
public class ReplicationGroupInfo extends  DataObject {
    private StringSet zoneCosSet;

    private Long lastStableUpdate;
    
    private String description;

    public ReplicationGroupInfo() {
        super();
    }

    @XmlElement
    @Name("zoneCosSet")
    public StringSet getZoneCosSet() {
        if (zoneCosSet == null) {
            return new StringSet();
        }
        return zoneCosSet;
    }

    public void setZoneCosSet(StringSet zoneCosSet) {
        this.zoneCosSet = zoneCosSet;
        setChanged("zoneCosSet");
    }

    @XmlElement
    @Name("lastStableUpdate")
    public Long getLastStableUpdate() {
        return lastStableUpdate;
    }

    public void setLastStableUpdate(Long lastStableUpdate) {
        this.lastStableUpdate = lastStableUpdate;
        setChanged("lastStableUpdate");
    }

    @XmlElement
    @Name("description")
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
		setChanged("description");
	}
    
    
}

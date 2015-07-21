/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.VirtualArray;

/**
 * @author cgarber
 *
 */
public abstract class BlockObject extends DataObject {
    
    // virtual array where this volume exists
    private URI _virtualArray;
    private VirtualArray _virtualArrayObj;

    @Name("varray")
    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @Name("varrayObj")
    @Relation(mappedBy="varray")
    public VirtualArray getVirtualArrayObj() {
        return _virtualArrayObj;
    }

    public void setVirtualArrayObj(VirtualArray virtualArrayObj) {
        this._virtualArrayObj = virtualArrayObj;
    }
}

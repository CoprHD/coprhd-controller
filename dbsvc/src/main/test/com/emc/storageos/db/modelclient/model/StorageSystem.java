/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;

/**
 * @author cgarber
 *
 */
@Cf("StorageSystem")
public class StorageSystem extends DataObject {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    // virtual array of this storage device
    private URI _virtualArray;
    private VirtualArray _vArray;
    
    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @Name("varray")
    public URI getVirtualArray() {
        return _virtualArray;
    }
    
    public void setVirtualArray(final URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    /**
     * @return the _vArray
     */
    @Name("varrayObj")
    @Relation(mappedBy="varray")
    public VirtualArray getVArray() {
        return _vArray;
    }

    /**
     * @param _vArray the _vArray to set
     */
    public void setVArray(VirtualArray _vArray) {
        this._vArray = _vArray;
    }
}

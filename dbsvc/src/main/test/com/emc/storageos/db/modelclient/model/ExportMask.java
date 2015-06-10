/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

/**
 * @author cgarber
 *
 */
@Cf("ExportMask")
public class ExportMask extends DataObject {
    
    private URI _storageDevice;
    private String indexedField;
    private String unIndexedField;

    @Name("storageDevice")
    @RelationIndex(cf="RelationIndex", type=StorageDevice.class)
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @Name("indexedField")
    @AlternateId("AltIdIndex")
    public String getIndexedField() {
        return indexedField;
    }

    public void setIndexedField(String indexedField) {
        this.indexedField = indexedField;
        setChanged("indexedField");
    }

    @Name("unIndexedField")
    public String getUnIndexedField() {
        return unIndexedField;
    }

    public void setUnIndexedField(String unIndexedField) {
        this.unIndexedField = unIndexedField;
        setChanged("unIndexedField");
    }

}

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

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;

/**
 * @author cgarber
 *
 */
@Cf("StoragePool")
public class StoragePool extends DataObject {
    
    private URI _storageDevice;
    private StorageSystem _storageDeviceObj;
    
    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this._storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    /**
     * @return the storageDeviceObj
     */
    @Relation(mappedBy="storageDevice")
    @Name("storageDeviceObj")
    public StorageSystem getStorageDeviceObj() {
        return _storageDeviceObj;
    }

    /**
     * @param storageDeviceObj the storageDeviceObj to set
     */
    public void setStorageDeviceObj(StorageSystem storageDeviceObj) {
        this._storageDeviceObj = storageDeviceObj;
    }

}

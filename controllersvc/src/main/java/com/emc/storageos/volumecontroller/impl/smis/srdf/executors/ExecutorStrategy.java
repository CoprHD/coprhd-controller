/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.executors;

import com.emc.storageos.db.client.model.StorageSystem;

import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;
import java.util.Collection;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public interface ExecutorStrategy {
    void execute(Collection<CIMObjectPath> objectPaths, StorageSystem provider) throws WBEMException;
}

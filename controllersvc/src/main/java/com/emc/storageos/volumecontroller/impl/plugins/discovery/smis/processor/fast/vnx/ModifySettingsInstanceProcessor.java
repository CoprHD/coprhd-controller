/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.util.List;
import java.util.Map;
import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;

/**
 * refer SettingsInstanceProcessor before looking into below
 * For each Created Storage Pool Setting Instance, run modifyInstance
 * which would add InitialStorageTierMethodology property.
 * 
 * For each successful Modification of this setting instance, this processor gets invoked
 * This modified Instance still not yet commited to Provider.
 * ModifiedInstance reference is being added to a list in Map,so that
 * commit action happens for all Modified Instances
 * 
 */
public class ModifySettingsInstanceProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(ModifySettingsInstanceProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            CIMInstance modifiedInstance = (CIMInstance) resultObj;
            addInstance(keyMap, operation.getResult(), modifiedInstance);
        } catch (Exception e) {
            _logger.error("Error while modifying Pool Setting", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.application;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class ApplicationStorageGroupProcessor extends Processor {
    private Logger _logger = LoggerFactory
            .getLogger(ApplicationStorageGroupProcessor.class);
    private DbClient _dbClient;
    protected List<Object> _args;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            while (it.hasNext()) {
                CIMObjectPath deviceMaskingGroup = it.next();
                String instanceID = deviceMaskingGroup
                        .getKey(Constants.INSTANCEID).getValue().toString();
                String serialID = (String) keyMap.get(Constants._serialID);
                if (instanceID.contains(serialID)) {
                    addPath(keyMap, operation.getResult(), deviceMaskingGroup);
                    VolumeGroup volumeGroup = checkVolumeGroupExistsInDB(instanceID);
                    if (null == volumeGroup) {
                        volumeGroup = new VolumeGroup();
                        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
                        volumeGroup.setLabel(instanceID);
                        volumeGroup.setDescription("VMAX Application Storage Group");
                        _dbClient.createObject(volumeGroup);
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Storage Group Discovery Failed : ", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }

    /**
     * Check if Storage Group exists in DB.
     * 
     * @param instanceID
     * @return
     * @throws IOException
     */
    protected VolumeGroup checkVolumeGroupExistsInDB(String instanceID)
            throws IOException {
        VolumeGroup volumeGroup = null;
        @SuppressWarnings("deprecation")
        URIQueryResultList volumeGroupResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeGroupByLabel(instanceID),
                volumeGroupResults);
        if (volumeGroupResults.iterator().hasNext()) {
            volumeGroup = _dbClient.queryObject(
                    VolumeGroup.class, volumeGroupResults.iterator().next());
        }
        return volumeGroup;
    }

}

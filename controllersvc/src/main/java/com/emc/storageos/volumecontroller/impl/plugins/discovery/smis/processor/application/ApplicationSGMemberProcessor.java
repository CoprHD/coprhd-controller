package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.application;

import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

public class ApplicationSGMemberProcessor extends ApplicationStorageGroupProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(ApplicationSGMemberProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath deviceMaskingGroup = getObjectPathfromCIMArgument(_args);
            String instanceID = deviceMaskingGroup
                    .getKey(Constants.INSTANCEID).getValue().toString();
            _logger.info(String.format("Processing Member information for Storage Group: %s", instanceID));
            VolumeGroup volumeGroup = checkVolumeGroupExistsInDB(instanceID);
            if (null == volumeGroup) { // This must never be true but just a placeholder
                volumeGroup = new VolumeGroup();
                volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
                volumeGroup.setLabel(instanceID);
                volumeGroup.setDescription("VMAX Application Storage Group");
                _dbClient.createObject(volumeGroup);
            }
            while (it.hasNext()) {
                CIMObjectPath memberOfCollectionPath = it.next();
                if (memberOfCollectionPath.toString().contains(SmisConstants.SE_DEVICE_MASKING_GROUP)) {
                    // We need to add the child Groups here
                    String childGroupInstanceID = memberOfCollectionPath
                            .getKey(Constants.INSTANCEID).getValue().toString();
                } else if (memberOfCollectionPath.toString().contains(SmisConstants.SYMM_STORAGEVOLUME)) {
                    // We need to add the volume device ids here
                    String volumeDeviceID = memberOfCollectionPath
                            .getKey(Constants.DEVICEID).getValue().toString();
                } else {
                    _logger.info(String.format("Unknown Member class information for Storage Group %s found: %s ", instanceID,
                            memberOfCollectionPath.toString()));
                }
            }
        } catch (Exception e) {
            _logger.error("Storage Group Discovery Failed : ", e);
        }
    }

}

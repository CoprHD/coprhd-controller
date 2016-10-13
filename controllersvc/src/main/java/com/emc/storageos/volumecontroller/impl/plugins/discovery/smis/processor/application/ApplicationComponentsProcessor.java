package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.application;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

public class ApplicationComponentsProcessor extends ApplicationStorageGroupProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(ApplicationComponentsProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath lunMaskingView = getObjectPathfromCIMArgument(_args);
            String maskingViewName = lunMaskingView
                    .getKey(Constants.DEVICEID).getValue().toString();
            _logger.info(String.format("Processing association information for Masking View: %s", maskingViewName));
            VolumeGroup volumeGroup = null;
            List<String> initiatorList = new ArrayList<>();
            String targetMaskingGroupInstanceID = null;
            while (it.hasNext()) {
                CIMObjectPath associatedInstancePath = it.next();
                String instanceID = associatedInstancePath
                        .getKey(Constants.INSTANCEID).getValue().toString();
                if (associatedInstancePath.toString().contains(SmisConstants.SE_DEVICE_MASKING_GROUP)) {
                    // We need to get the Volume Group information here
                    volumeGroup = checkVolumeGroupExistsInDB(instanceID);
                    if (null == volumeGroup) { // This must never be true but just a placeholder
                        volumeGroup = new VolumeGroup();
                        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
                        volumeGroup.setLabel(instanceID);
                        volumeGroup.setDescription("VMAX Application Storage Group");
                        _dbClient.createObject(volumeGroup);
                    }
                } else if (associatedInstancePath.toString().contains(SmisConstants.SE_TARGET_MASKING_GROUP)) {
                    // We need to add the volume device ids here
                    targetMaskingGroupInstanceID = instanceID;
                } else if (associatedInstancePath.toString().contains(SmisConstants.SE_STORAGE_HARDWARE_ID)) {
                    initiatorList.add(instanceID);
                }
            }
            // Now process the needful information for the Volume Group here...

        } catch (Exception e) {
            _logger.error("Storage Group Discovery Failed : ", e);
        }
    }

}

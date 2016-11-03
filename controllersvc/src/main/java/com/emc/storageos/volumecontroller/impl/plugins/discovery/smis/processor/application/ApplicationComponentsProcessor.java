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
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

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
            String initiatorList = null;
            String targetMaskingGroupInstanceID = null;
            while (it.hasNext()) {
                CIMObjectPath associatedInstancePath = it.next();
                if (associatedInstancePath.toString().contains(SmisConstants.SE_DEVICE_MASKING_GROUP)) {
                    // We need to get the Volume Group information here
                    String instanceID = associatedInstancePath
                            .getKey(Constants.INSTANCEID).getValue().toString();
                    instanceID = instanceID.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                    volumeGroup = checkVolumeGroupExistsInDB(instanceID, _dbClient);
                    if (null == volumeGroup) { // This must never be true but just a placeholder
                        volumeGroup = new VolumeGroup();
                        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
                        volumeGroup.setLabel(instanceID);
                        volumeGroup.addRoles(_roles);
                        volumeGroup.setMigrationType(_migrationType);
                        volumeGroup.setMigrationGroupBy(_migrationGroupBy);
                        volumeGroup.setDescription(_description);
                        volumeGroup.setMigrationStatus(VolumeGroup.MigrationStatus.NONE.toString());
                        _dbClient.createObject(volumeGroup);
                    }
                    volumeGroup.setMigrationStatus(VolumeGroup.MigrationStatus.MIGRATIONREADY.toString());
                } else if (associatedInstancePath.toString().contains(SmisConstants.SE_TARGET_MASKING_GROUP)) {
                    targetMaskingGroupInstanceID = associatedInstancePath
                            .getKey(Constants.INSTANCEID).getValue().toString()
                            .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                } else if (associatedInstancePath.toString().contains(SmisConstants.SE_STORAGE_HARDWARE_ID)) {
                    if (initiatorList != null) {
                        initiatorList = String.format("%s%s%s", initiatorList, Constants.ID_DELIMITER,
                                associatedInstancePath
                                        .getKey(Constants.INSTANCEID).getValue().toString()
                                        .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS));
                    } else {
                        initiatorList = associatedInstancePath
                                .getKey(Constants.INSTANCEID).getValue().toString()
                                .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                    }
                }
            }
            String maskViewList = volumeGroup.getApplicationOptions().get("Associated Mask View List");
            if (maskViewList == null) {
                volumeGroup.getApplicationOptions().put("Associated Mask View List", maskingViewName);
            } else {
                volumeGroup.getApplicationOptions().put("Associated Mask View List",
                        String.format("%s%s%s", maskViewList, Constants.ID_DELIMITER, maskingViewName));
            }
            if (initiatorList != null) {
                volumeGroup.getApplicationOptions().put(String.format(" Masking View %s has the following initiators", maskingViewName),
                        initiatorList);
            }
            if (targetMaskingGroupInstanceID != null) {
                volumeGroup.getApplicationOptions().put(String.format("Masking View %s is associated to Port Group", maskingViewName),
                        targetMaskingGroupInstanceID);
            }
            _dbClient.updateObject(volumeGroup);
        } catch (Exception e) {
            _logger.error("Processing association information for Masking Views failed : ", e);
        }
    }

}

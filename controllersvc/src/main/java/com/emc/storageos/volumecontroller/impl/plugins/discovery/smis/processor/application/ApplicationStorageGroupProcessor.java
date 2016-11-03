package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.application;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

public class ApplicationStorageGroupProcessor extends Processor {
    private Logger _logger = LoggerFactory
            .getLogger(ApplicationStorageGroupProcessor.class);
    private DbClient _dbClient;
    protected List<Object> _args;
    URI _storageSystemURI = null;
    String _migrationType = VolumeGroup.MigrationType.VMAX.toString();
    String _migrationGroupBy = VolumeGroup.MigrationGroupBy.STORAGEGROUP.toString();
    String _description = "VMAX Application Storage Group";
    StringSet _roles = new StringSet(Arrays.asList(VolumeGroup.VolumeGroupRole.MOBILITY.toString()));

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            String serialID = (String) keyMap.get(Constants._serialID);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            _logger.info(String.format("Discovering Storage Groups"));
            Set<String> bookKeepingList = new HashSet<String>();
            while (it.hasNext()) {
                CIMObjectPath deviceMaskingGroup = it.next();
                String instanceID = deviceMaskingGroup
                        .getKey(Constants.INSTANCEID).getValue().toString();
                instanceID = instanceID.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                bookKeepingList.add(instanceID);
                if (instanceID.contains(serialID)) {
                    addPath(keyMap, operation.getResult(), deviceMaskingGroup);
                    VolumeGroup volumeGroup = checkVolumeGroupExistsInDB(instanceID, _dbClient);
                    if (null == volumeGroup) {
                        volumeGroup = new VolumeGroup();
                        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
                        volumeGroup.setLabel(instanceID);
                        volumeGroup.addRoles(_roles);
                        volumeGroup.setMigrationType(_migrationType);
                        volumeGroup.setMigrationGroupBy(_migrationGroupBy);
                        volumeGroup.setDescription(_description);
                        volumeGroup.setMigrationStatus(VolumeGroup.MigrationStatus.NONE.toString());
                        volumeGroup.setApplicationOptions(new StringMap());
                        _dbClient.createObject(volumeGroup);
                    } else {
                        volumeGroup.getApplicationOptions().clear();
                        _dbClient.updateObject(volumeGroup);
                    }
                }
            }
            //Bookkeeping
            performVolumeGroupsBookKeeping(bookKeepingList, _dbClient, serialID);
            
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
    protected VolumeGroup checkVolumeGroupExistsInDB(String instanceID, DbClient dbClient)
            throws IOException {
        VolumeGroup volumeGroup = null;
        URIQueryResultList volumeGroupResults = new URIQueryResultList();
        dbClient.queryByConstraint(PrefixConstraint.Factory.getFullMatchConstraint(VolumeGroup.class, "label", instanceID),
                volumeGroupResults);
        if (volumeGroupResults.iterator().hasNext()) {
            volumeGroup = dbClient.queryObject(
                    VolumeGroup.class, volumeGroupResults.iterator().next());
        }
        return volumeGroup;
    }

    /**
     * if the StorageGroup had been deleted from the Array, the re-discovery cycle should set the VolumeGroup to inactive.
     * 
     * @param volumeGroupIds
     * @param dbClient
     * @param serialID
     * @throws IOException
     */
    private void performVolumeGroupsBookKeeping(Set<String> volumeGroupIds, DbClient dbClient, String serialID)
            throws IOException {

        String prefixConstraint = String.format("%s+%s", Constants.SYMMETRIX_U, serialID);
        URIQueryResultList volumeGroupResults = new URIQueryResultList();
        dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(VolumeGroup.class, prefixConstraint),
                volumeGroupResults);
        while (volumeGroupResults.iterator().hasNext()) {
            VolumeGroup volumeGroupInDB = dbClient.queryObject(VolumeGroup.class, volumeGroupResults.iterator().next());
            if (null == volumeGroupInDB) {
                continue;
            }
            if (!volumeGroupIds.contains(volumeGroupInDB.getLabel())) {
                // Only remove if MigrationStatus is NONE
                _logger.info("Volume Group not found", volumeGroupInDB);
                if (VolumeGroup.MigrationStatus.NONE.toString() == volumeGroupInDB.getMigrationStatus()) {
                    _logger.info("Removing the VolumeGroup as its migrationStatus is NONE", volumeGroupInDB);
                    dbClient.removeObject(volumeGroupInDB);
                }
            }
        }

    }
}

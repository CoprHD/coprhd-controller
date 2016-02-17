/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.client.WBEMClient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 
 * StorageProcessor class to process data about meta members for a meta volume and set this data
 * in volume instance.
 * This class is used in storage volume rediscovery and ummanaged volume discovery contexts.
 */
public class MetaVolumeMembersProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(MetaVolumeMembersProcessor.class);
    private static final String[] META_MEMBER_SIZE_INFO = new String[] { SmisConstants.CP_CONSUMABLE_BLOCKS, SmisConstants.CP_BLOCK_SIZE };
    private List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        try {

            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);

            CIMObjectPath[] metaMembersPaths = (CIMObjectPath[]) getFromOutputArgs((CIMArgument[]) resultObj, "OutElements");
            _logger.debug(String.format("Processing meta members: %s", Arrays.toString(metaMembersPaths)));

            // Get volume from db
            _logger.debug(String.format("Args size: %s", _args.size()));
            Object[] arguments = (Object[]) _args.get(0);
            CIMArgument theElement = ((CIMArgument[]) arguments[2])[1];
            _logger.info(String.format("TheElement: %s, type %s", theElement.getValue().toString(), theElement.getValue().getClass()
                    .toString()));
            CIMObjectPath theElementPath = (CIMObjectPath) theElement.getValue();

            UnManagedVolume preExistingVolume = null;
            String isMetaVolume = "true";
            String nativeGuid;

            // Check if storage volume exists in db (the method is called from re-discovery context).
            nativeGuid = getVolumeNativeGuid(theElementPath);
            Volume storageVolume = checkStorageVolumeExistsInDB(nativeGuid, dbClient);
            if (null == storageVolume || storageVolume.getInactive()) {
                // Check if unmanaged volume exists in db (the method is called from unmanaged volumes discovery context).
                nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(theElementPath);
                _logger.debug("Volume nativeguid :" + nativeGuid);
                preExistingVolume = checkUnManagedVolumeExistsInDB(nativeGuid, dbClient);
                if (null == preExistingVolume) {
                    _logger.debug("Volume Info Object not found :" + nativeGuid);
                    return;
                }
                isMetaVolume = preExistingVolume.getVolumeCharacterstics().
                        get(UnManagedVolume.SupportedVolumeCharacterstics.IS_METAVOLUME.toString());
            } else {
                _logger.debug("Volume managed by Bourne :" + storageVolume.getNativeGuid());
                isMetaVolume = storageVolume.getIsComposite().toString();
            }

            if (isMetaVolume.equalsIgnoreCase("false")) {
                _logger.error(String.format("MetaVolumeMembersProcessor called for regular volume: %s", nativeGuid));
                return;
            }

            Integer membersCount = metaMembersPaths.length;

            // get meta member size. use second member --- the first member will show size of meta volume itself.
            CIMObjectPath metaMemberPath = metaMembersPaths[1];
            CIMInstance cimVolume = client.getInstance(metaMemberPath, false,
                    false, META_MEMBER_SIZE_INFO);

            CIMProperty consumableBlocks = cimVolume.getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
            CIMProperty blockSize = cimVolume.getProperty(SmisConstants.CP_BLOCK_SIZE);
            // calculate size = consumableBlocks * block size
            Long size =
                    Long.valueOf(consumableBlocks.getValue().toString()) * Long.valueOf(blockSize.getValue().toString());

            // set meta member count and meta members size for meta volume (required for volume expansion)
            if (null != preExistingVolume) {
                StringSet metaMembersCount = new StringSet();
                metaMembersCount.add(membersCount.toString());
                preExistingVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.META_MEMBER_COUNT.toString(), metaMembersCount);

                StringSet metaMemberSize = new StringSet();
                metaMemberSize.add(size.toString());
                preExistingVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.META_MEMBER_SIZE.toString(), metaMemberSize);

                // persist unmanaged volume in db
                dbClient.persistObject(preExistingVolume);
            } else {
                storageVolume.setMetaMemberCount(membersCount);
                storageVolume.setMetaMemberSize(size);
                storageVolume.setTotalMetaMemberCapacity(membersCount * size);
                // persist volume in db
                dbClient.persistObject(storageVolume);
            }

            _logger.info(String.format("Meta member info: meta member count --- %s, blocks --- %s, block size --- %s, size --- %s .",
                    membersCount,
                    consumableBlocks.getValue().toString(),
                    blockSize.getValue().toString(), size));

        } catch (Exception e) {
            _logger.error("Processing meta volume  information failed :", e);
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;
    }
}
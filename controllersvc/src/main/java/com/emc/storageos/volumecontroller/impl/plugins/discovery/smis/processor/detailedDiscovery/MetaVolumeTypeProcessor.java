/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 
 * StorageProcessor class to process data about meta type for a meta volume and set this data
 * in unmanaged volume instance.
 */
public class MetaVolumeTypeProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(MetaVolumeMembersProcessor.class);
    private static final String[] STRIPE_EXTENTS_NUMBER = new String[] { SmisConstants.CP_EXTENT_STRIPE_LENGTH };

    private List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        try {

            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            CIMObjectPath metaVolumePath = getObjectPathfromCIMArgument(_args);
            _logger.info(String.format("Processing EMC_Meta for meta volume: %s", metaVolumePath));

            UnManagedVolume preExistingVolume = null;
            String isMetaVolume = "true";
            String nativeGuid;

            // Check if storage volume exists in db (the method is called from re-discovery context).
            nativeGuid = getVolumeNativeGuid(metaVolumePath);
            Volume storageVolume = checkStorageVolumeExistsInDB(nativeGuid, dbClient);
            if (null == storageVolume || storageVolume.getInactive()) {
                // Check if unmanaged volume exists in db (the method is called from unmanaged volumes discovery context).
                nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(metaVolumePath);
                _logger.debug("Meta volume nativeguid :" + nativeGuid);
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
                _logger.error(String.format("MetaVolumeTypeProcessor called for regular volume: %s", nativeGuid));
                return;
            }

            final Iterator<?> it = (Iterator<?>) resultObj;
            if (it.hasNext()) {
                final CIMObjectPath symmMetaPath = (CIMObjectPath) it.next();
                _logger.debug(String.format("Processing EMC_Meta: %s", symmMetaPath));
                CIMInstance cimMeta = client.getInstance(symmMetaPath, false,
                        false, STRIPE_EXTENTS_NUMBER);

                CIMProperty stripeLengthProperty = cimMeta.getProperty(SmisConstants.CP_EXTENT_STRIPE_LENGTH);
                Long stripeLength = Long.valueOf(stripeLengthProperty.getValue().toString());

                String metaVolumeType;
                if (stripeLength < 1) {
                    _logger.error(String.format("Stripe length for EMC_Meta is less than 1: %s", stripeLength));
                    return;
                }
                else if (stripeLength == 1) {
                    // this is concatenated meta volume
                    _logger.debug(String.format("Stripe length for EMC_Meta is : %s. Type is concatenated.", stripeLength));
                    metaVolumeType = Volume.CompositionType.CONCATENATED.toString();
                } else {
                    // this is striped meta volume
                    _logger.debug(String.format("Stripe length for EMC_Meta is : %s. Type is striped.", stripeLength));
                    metaVolumeType = Volume.CompositionType.STRIPED.toString();
                }

                _logger.info(String.format("Meta volume: %s, type: %s", metaVolumePath, metaVolumeType));
                if (null == preExistingVolume) {
                    // storage volume update
                    storageVolume.setCompositionType(metaVolumeType);
                    // persist volume in db
                    dbClient.persistObject(storageVolume);
                } else {
                    // unmanaged volume update
                    StringSet metaVolumeTypeSet = new StringSet();
                    metaVolumeTypeSet.add(metaVolumeType);
                    preExistingVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.META_VOLUME_TYPE.toString(),
                            metaVolumeTypeSet);

                    // If meta volume is striped vmax volume, make sure that we remove vpools with fast expansion from matched vpool list
                    // for this volume.
                    if (Volume.CompositionType.STRIPED.toString().equalsIgnoreCase(metaVolumeType)) {
                        URI storageSystemUri = preExistingVolume.getStorageSystemUri();
                        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemUri);
                        if (DiscoveredDataObject.Type.vmax.toString().equalsIgnoreCase(storageSystem.getSystemType())) {
                            _logger.info("Check matched vpool list for vmax striped meta volume and remove fastExpansion vpools.");
                            StringSet matchedVirtualPools = preExistingVolume.getSupportedVpoolUris();
                            if (matchedVirtualPools != null && !matchedVirtualPools.isEmpty()) {
                                _logger.debug("Matched Pools :" + Joiner.on("\t").join(matchedVirtualPools));
                                StringSet newMatchedPools = new StringSet();
                                boolean needToReplace = false;
                                for (String vPoolUriStr : matchedVirtualPools) {
                                    URI vPoolUri = new URI(vPoolUriStr);
                                    VirtualPool virtualPool = dbClient.queryObject(VirtualPool.class, vPoolUri);
                                    // null check since supported vPool list in UnManagedVolume may contain inactive vPool
                                    if (virtualPool != null && !virtualPool.getFastExpansion()) {
                                        newMatchedPools.add(vPoolUriStr);
                                    } else {
                                        needToReplace = true;
                                    }
                                }
                                if (needToReplace) {
                                    matchedVirtualPools.replace(newMatchedPools);
                                    _logger.info("Replaced VPools : {}", Joiner.on("\t").join(preExistingVolume.getSupportedVpoolUris()));
                                }
                            }
                        }
                    }

                    // persist volume in db
                    dbClient.updateAndReindexObject(preExistingVolume);
                }
            }
        } catch (Exception e) {
            _logger.error("Processing meta volume type information failed :", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;
    }
}
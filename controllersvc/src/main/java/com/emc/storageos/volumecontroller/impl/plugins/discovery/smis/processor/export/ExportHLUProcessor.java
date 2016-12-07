/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class ExportHLUProcessor extends StorageProcessor {
    private Logger logger = LoggerFactory.getLogger(ExportHLUProcessor.class);
    private List<Object> args;
    private DbClient dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        CloseableIterator<CIMInstance> protocolControllerForUnitInstances = null;
        EnumerateResponse<CIMInstance> protocolControllerForUnitInstanceChunks = null;

        dbClient = (DbClient) keyMap.get(Constants.dbClient);
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        CIMObjectPath maskingViewPath = null;
        try {
            maskingViewPath = getObjectPathfromCIMArgument(args, keyMap);
            logger.info("Masking view: {}", maskingViewPath.toString());
            UnManagedExportMask uem = getUnManagedExportMask(maskingViewPath);
            if (uem == null) {
                logger.error("Skipping HLU discovery as the unmananged export mask with path {} doesn't exist in ViPR",
                        maskingViewPath.toString());
                return;
            }

            protocolControllerForUnitInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
            protocolControllerForUnitInstances = protocolControllerForUnitInstanceChunks.getResponses();
            Map<String, String> knownVolumes = new HashMap<String, String>();
            Map<String, String> unmanagedVolumes = new HashMap<String, String>();

            processMaskHLUs(protocolControllerForUnitInstances, knownVolumes, unmanagedVolumes);

            while (!protocolControllerForUnitInstanceChunks.isEnd()) {
                logger.info("Processing Next ProtocolControllerForUnit Chunk of size {}", BATCH_SIZE);
                protocolControllerForUnitInstanceChunks = client.getInstancesWithPath(maskingViewPath,
                        protocolControllerForUnitInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processMaskHLUs(protocolControllerForUnitInstanceChunks.getResponses(), knownVolumes, unmanagedVolumes);
            }

            // update UnManagedExportMask with new data and persist
            boolean updated = false;
            if (!knownVolumes.isEmpty()) {
                uem.getKnownVolumeUrisWithHLU().replace(knownVolumes);
                updated = true;
            }
            if (!unmanagedVolumes.isEmpty()) {
                uem.getUnmanagedVolumeUrisWithHLU().replace(unmanagedVolumes);
                updated = true;
            }
            if (updated) {
                dbClient.updateObject(uem);
            }
        } catch (Exception e) {
            logger.error("Extracting HLU for exported Volumes failed", e);
        } finally {
            if (null != protocolControllerForUnitInstances) {
                protocolControllerForUnitInstances.close();
            }
            if (null != protocolControllerForUnitInstanceChunks) {
                try {
                    client.closeEnumeration(maskingViewPath, protocolControllerForUnitInstanceChunks.getContext());
                } catch (Exception e) {
                    logger.debug("Exception occurred while closing enumeration", e);
                }
            }
        }
    }

    /**
     * Extract HLU for ProtocolControllerForUnit Instances.
     *
     * @param protocolControllerForUnitInstances the ProtocolControllerForUnit instances
     * @param knownVolumes the known volumes
     * @param unmanagedVolumes the unmanaged volumes
     */
    private void processMaskHLUs(CloseableIterator<CIMInstance> protocolControllerForUnitInstances,
            Map<String, String> knownVolumes, Map<String, String> unmanagedVolumes) {
        while (protocolControllerForUnitInstances.hasNext()) {
            CIMInstance protocolControllerForUnitInstance = protocolControllerForUnitInstances.next();
            try {
                String deviceNumber = protocolControllerForUnitInstance
                        .getPropertyValue(SmisConstants.CP_DEVICE_NUMBER).toString();
                Integer hlu = Integer.parseInt(deviceNumber, 16);

                String volume = protocolControllerForUnitInstance.getPropertyValue(SmisConstants.CP_DEPENDENT).toString();
                CIMObjectPath volumePath = new CIMObjectPath(volume);
                logger.debug("Volume path: {}", volumePath.toString());
                // Check if storage volume exists in DB
                String nativeGuid = getVolumeNativeGuid(volumePath);
                Volume storageVolume = checkStorageVolumeExistsInDB(nativeGuid, dbClient);
                if (storageVolume != null) {
                    logger.debug("Volume: {}, HLU: {}", storageVolume.getId(), hlu);
                    knownVolumes.put(storageVolume.getId().toString(), hlu.toString());
                } else {
                    // Check if unmanaged volume exists in DB
                    nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
                    UnManagedVolume umv = checkUnManagedVolumeExistsInDB(nativeGuid, dbClient);
                    if (umv != null) {
                        logger.debug("Unmanaged volume: {}, HLU: {}", umv.getId(), hlu);
                        unmanagedVolumes.put(umv.getId().toString(), hlu.toString());
                    } else {
                        logger.debug("Neither Volume nor UnManaged Volume found for {}", nativeGuid);
                    }
                }
            } catch (Exception e) {
                logger.debug("Exception occurred while processing ProtocolControllerForUnit {}",
                        protocolControllerForUnitInstance.getObjectPath().toString());
            }
        }
    }

    /**
     * Returns an UnManagedExportMask if it exists for the requested CIMObjectPath
     * 
     * @param cimObjectPath the CIMObjectPath for the Unmanaged Export on the storage array
     * @return an UnManagedExportMask object to use
     */
    protected UnManagedExportMask getUnManagedExportMask(CIMObjectPath cimObjectPath) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedExportMaskPathConstraint(cimObjectPath.toString()), result);
        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            UnManagedExportMask uem = dbClient.queryObject(UnManagedExportMask.class, it.next());
            if (uem != null && !uem.getInactive()) {
                return uem;
            }
        }
        return null;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }

}

/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;

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
        @SuppressWarnings("unchecked")
        Map<String, StringSet> volumeToExportMasksHLUMap =
                (Map<String, StringSet>) keyMap.get(Constants.UN_VOLUME_EXPORT_MASK_HLUS_MAP);
        CIMObjectPath maskingViewPath = null;
        try {
            maskingViewPath = getObjectPathfromCIMArgument(args, keyMap);
            logger.info("Masking view: {}", maskingViewPath.toString());
            UnManagedExportMask uem = getUnManagedExportMask(maskingViewPath);
            if (uem == null) {
                logger.info("Skipping HLU discovery as there is no unmananged export mask found for masking path {}",
                        maskingViewPath.toString());
                return;
            }

            protocolControllerForUnitInstanceChunks = (EnumerateResponse<CIMInstance>) resultObj;
            protocolControllerForUnitInstances = protocolControllerForUnitInstanceChunks.getResponses();

            processMaskHLUs(protocolControllerForUnitInstances, uem, volumeToExportMasksHLUMap);

            while (!protocolControllerForUnitInstanceChunks.isEnd()) {
                logger.info("Processing Next ProtocolControllerForUnit Chunk of size {}", BATCH_SIZE);
                protocolControllerForUnitInstanceChunks = client.getInstancesWithPath(maskingViewPath,
                        protocolControllerForUnitInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processMaskHLUs(protocolControllerForUnitInstanceChunks.getResponses(), uem, volumeToExportMasksHLUMap);
            }

        } catch (Exception e) {
            String errMsg = "Extracting HLU for exported Volumes failed: " + e.getMessage();
            logger.error(errMsg, e);
            throw SmisException.exceptions.hluRetrievalFailed(errMsg, e);
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
     * @param uem the UnMananged ExportMask
     * @param volumeToExportMasksHLUMap
     */
    private void processMaskHLUs(CloseableIterator<CIMInstance> protocolControllerForUnitInstances,
            UnManagedExportMask uem, Map<String, StringSet> volumeToExportMasksHLUMap) {
        while (protocolControllerForUnitInstances.hasNext()) {
            CIMInstance protocolControllerForUnitInstance = protocolControllerForUnitInstances.next();
            String deviceNumber = protocolControllerForUnitInstance
                    .getPropertyValue(SmisConstants.CP_DEVICE_NUMBER).toString();
            Integer hlu = Integer.parseInt(deviceNumber, 16);

            String volume = protocolControllerForUnitInstance.getPropertyValue(SmisConstants.CP_DEPENDENT).toString();
            CIMObjectPath volumePath = new CIMObjectPath(volume);
            logger.debug("Volume path: {}", volumePath.toString());

            // Check if unmanaged volume exists in DB
            String nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
            String hluEntry = uem.getMaskName() + "=" + hlu.toString();
            logger.info("HLU {} found for Unmanaged volume {}", hlu, nativeGuid);
            StringSet volumeHLUs = volumeToExportMasksHLUMap.get(nativeGuid);
            if (volumeHLUs == null) {
                volumeHLUs = new StringSet();
                volumeToExportMasksHLUMap.put(nativeGuid, volumeHLUs);
            }
            volumeHLUs.add(hluEntry);
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

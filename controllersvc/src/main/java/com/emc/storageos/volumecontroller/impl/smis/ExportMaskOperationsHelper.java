/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CIM_PROTOCOL_CONTROLLER_FOR_UNIT;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_DEPENDENT;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_DEVICE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_DEVICE_NUMBER;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.PS_DEVICE_NUMBER;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.ANTECEDENT;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.LUNMASKING;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;

public class ExportMaskOperationsHelper {
    private static Logger _log = LoggerFactory.getLogger(ExportMaskOperationsHelper.class);

    public static void populateDeviceNumberFromProtocolControllers(DbClient dbClient,
            CimConnection cimConnection,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            CIMObjectPath[] protocolControllers,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        setHLUFromProtocolControllers(dbClient, cimConnection, exportMaskURI, volumeURIHLUs,
                Arrays.asList(protocolControllers), taskCompleter);
    }

    public static boolean volumeURIHLUsHasNullHLU(VolumeURIHLU[] volumeURIHLUs) {
        boolean hasNullHLU = false;
        if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
            for (VolumeURIHLU vuh : volumeURIHLUs) {
                if (vuh.getHLU() == null || vuh.getHLU().equals(ExportGroup.LUN_UNASSIGNED_STR)) {
                    hasNullHLU = true;
                    break;
                }
            }
        }
        return hasNullHLU;
    }
    
   

    /**
     * During an export group operation e.g. creating one with initiators and volumes or when
     * adding volume(s) to an existing export group the user has the option of supplying HLUs
     * (Host Lun Unit) for the corresponding volumes. If the user does not supply HLUs, the
     * underlying array generates them. This helper function displays those array generated
     * HLUs during a GET/volume/exports operation. If the user has supplied the HLUs, this
     * function does nothing.
     * 
     * @throws DeviceControllerException
     **/
    public static void setHLUFromProtocolControllers(DbClient dbClient,
            CimConnection cimConnection,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            Collection<CIMObjectPath> protocolControllers,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        long startTime = System.currentTimeMillis();
        boolean hasNullHLU = volumeURIHLUsHasNullHLU(volumeURIHLUs);
        if (!hasNullHLU || protocolControllers.isEmpty()) {
            return;
        }
        try {
            ExportMask mask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            Map<String, URI> deviceIdToURI = new HashMap<String, URI>();
            Map<URI, BlockObject> uritoDevice = new HashMap<URI, BlockObject>();
            for (VolumeURIHLU vuh : volumeURIHLUs) {
                BlockObject volume = BlockObject.fetch(dbClient, vuh.getVolumeURI());
                uritoDevice.put(volume.getId(), volume);
                // We are only concerned with those BlockObjects associated
                // with the ExportMask that do not yet have an HLU set
                if (!mask.checkIfVolumeHLUSet(vuh.getVolumeURI())) {
                    deviceIdToURI.put(volume.getNativeId(), volume.getId());
                }
            }
            boolean requiresUpdate = false;
            CloseableIterator<CIMInstance> protocolControllerForUnitIter;
            for (CIMObjectPath protocolController : protocolControllers) {
                _log.info(String.format("setHLUFromProtocolControllers -- protocolController=%s", protocolController.toString()));
                protocolControllerForUnitIter = null;
                try {
                    protocolControllerForUnitIter = cimConnection.getCimClient()
                            .referenceInstances(protocolController,
                                    CIM_PROTOCOL_CONTROLLER_FOR_UNIT, null, false, PS_DEVICE_NUMBER);
                    while (protocolControllerForUnitIter.hasNext()) {
                        CIMInstance pcu = protocolControllerForUnitIter.next();
                        CIMObjectPath pcuPath = pcu.getObjectPath();
                        CIMProperty<CIMObjectPath> dependentVolumePropery =
                                (CIMProperty<CIMObjectPath>) pcuPath.getKey(CP_DEPENDENT);
                        CIMObjectPath dependentVolumePath = dependentVolumePropery.getValue();
                        String deviceId = dependentVolumePath.getKey(CP_DEVICE_ID).getValue().toString();
                        URI volumeURI = deviceIdToURI.get(deviceId);
                        if (volumeURI != null) {
                            String deviceNumber = CIMPropertyFactory.getPropertyValue(pcu, CP_DEVICE_NUMBER);
                            _log.info(String.format("setHLUFromProtocolControllers -- volumeURI=%s --> %s", volumeURI.toString(),
                                    deviceNumber));
                            mask.addVolume(volumeURI, (int) Long.parseLong(deviceNumber, 16));
                            
                            CIMInstance volumeInstance = cimConnection.getCimClient().getInstance(dependentVolumePath, false, false,SmisConstants.PS_EMCWWN);
                            if (volumeInstance != null) {
                                String wwn = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_WWN_NAME);
                                BlockObject obj = uritoDevice.get(volumeURI);
                                _log.info("Updating wwn {} for volume {} with old wwn {} ", new Object[] {wwn,volumeURI,obj.getWWN()});
                                obj.setWWN(wwn.toUpperCase());
                                dbClient.updateObject(obj);
                            }
                            
                            requiresUpdate = true;
                        }
                    }
                } finally {
                    if (protocolControllerForUnitIter != null) {
                        protocolControllerForUnitIter.close();
                    }
                }
            }
            if (requiresUpdate) {
                dbClient.persistObject(mask);
            }
        } catch (Exception e) {
            _log.error("Unexpected error: setHLUFromProtocolControllers failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("setHLUFromProtocolControllers", e.getMessage());
            taskCompleter.error(dbClient, error);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("setHLUFromProtocolControllers took %f seconds", (double) totalTime / (double) 1000));
        }
    }

    /**
     * This method is invoked specifically on AddVolumeToMaskingView jobs, which in turn
     * gets HLU's for processed volumes alone.
     * 
     * @param dbClient
     * @param cimConnection
     * @param exportMaskURI
     * @param volumeURIHLUs
     * @param volumePaths
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    public static void setHLUFromProtocolControllersOnAddVolume(DbClient dbClient,
            CimConnection cimConnection, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs,
            CIMObjectPath[] volumePaths, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        long startTime = System.currentTimeMillis();
        boolean hasNullHLU = volumeURIHLUsHasNullHLU(volumeURIHLUs);
        if (!hasNullHLU || volumePaths.length == 0) {
            return;
        }
        try {
            ExportMask mask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            Map<String, URI> deviceIdToURI = new HashMap<String, URI>();
            Map<URI, BlockObject> uritoDevice = new HashMap<URI, BlockObject>();
            for (VolumeURIHLU vuh : volumeURIHLUs) {
                BlockObject volume = BlockObject.fetch(dbClient, vuh.getVolumeURI());
                deviceIdToURI.put(volume.getNativeId(), volume.getId());
                uritoDevice.put(volume.getId(), volume);
            }
            boolean requiresUpdate = false;
            CloseableIterator<CIMInstance> protocolControllerForUnitIter;
            for (CIMObjectPath volumePath : volumePaths) {
                _log.info(String.format("setHLUFromProtocolControllers -- protocolController=%s", volumePath.toString()));
                protocolControllerForUnitIter = null;
                try {
                    protocolControllerForUnitIter = cimConnection.getCimClient()
                            .referenceInstances(volumePath, CIM_PROTOCOL_CONTROLLER_FOR_UNIT, null, false, PS_DEVICE_NUMBER);
                    while (protocolControllerForUnitIter.hasNext()) {
                        CIMInstance pcu = protocolControllerForUnitIter.next();
                        CIMObjectPath pcuPath = pcu.getObjectPath();
                        CIMObjectPath maskingViewPath = (CIMObjectPath) pcuPath.getKey(ANTECEDENT).getValue();
                        // Provider returns multiple references with same relationship , hence looking for class name
                        if (!maskingViewPath.toString().contains(LUNMASKING)) {
                            _log.info("Skipping CIMPath other than masking view path {}", pcuPath);
                            continue;
                        }
                        String deviceId = volumePath.getKey(CP_DEVICE_ID).getValue().toString();
                        URI volumeURI = deviceIdToURI.get(deviceId);
                        if (volumeURI != null) {
                            String deviceNumber = CIMPropertyFactory.getPropertyValue(pcu,
                                    CP_DEVICE_NUMBER);
                            _log.info(String.format("setHLUFromProtocolControllers -- volumeURI=%s --> %s", volumeURI.toString(),
                                    deviceNumber));
                            mask.addVolume(volumeURI, (int) Long.parseLong(deviceNumber, 16));
                            
                            CIMInstance volumeInstance = cimConnection.getCimClient().getInstance(volumePath, false, false,SmisConstants.PS_EMCWWN);
                            if (volumeInstance != null) {
                                String wwn = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_WWN_NAME);
                                BlockObject obj = uritoDevice.get(volumeURI);
                                _log.info("Add Volume :Updating wwn {} for volume {} with old wwn {} ", new Object[] {wwn,volumeURI,obj.getWWN()});
                                obj.setWWN(wwn.toUpperCase());
                                dbClient.updateObject(obj);
                            }
                            
                            requiresUpdate = true;
                        }
                    }
                } finally {
                    if (protocolControllerForUnitIter != null) {
                        protocolControllerForUnitIter.close();
                    }
                }
            }
            if (requiresUpdate) {
                dbClient.updateObject(mask);
            }
        } catch (Exception e) {
            _log.error("Unexpected error: setHLUFromProtocolControllers failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "setHLUFromProtocolControllers", e.getMessage());
            taskCompleter.error(dbClient, error);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("setHLUFromProtocolControllersDuringAddVolume took %f seconds", (double) totalTime / (double) 1000));
        }
    }
}

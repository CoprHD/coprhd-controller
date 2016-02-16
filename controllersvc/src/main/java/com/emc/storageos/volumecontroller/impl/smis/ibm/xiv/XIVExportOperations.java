/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperationsHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisConstants;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

/*
 * (non-Javadoc)
 * Handle all export use cases, like 
 *  create an ExportMask
 *  delete an ExportMask
 *  add volumes to an ExportMask
 *  remove Volumes from an ExportMask
 *  add Initiators to an ExportMask
 *  remove Initiators from an ExportMask
 *  find ExportMasks by initiators
 *  refresh an ExportMask
 *  
 * StorageHardwareID - initiator port
 * SystemSpecificCollection - host
 */
public class XIVExportOperations implements ExportMaskOperations {
    private static Logger _log = LoggerFactory
            .getLogger(XIVExportOperations.class);
    private XIVSmisCommandHelper _helper;
    private DbClient _dbClient;
    private IBMCIMObjectPathFactory _cimPath;

    @Autowired
    private NetworkDeviceController _networkDeviceController;

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(XIVSmisCommandHelper helper) {
        _helper = helper;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /*
     * (non-Javadoc) Creates an ExportMask with the given initiators & volumes.
     * 
     * The export mask may have already been created.
     * 
     * @param targetURIList not used
     * 
     * @param initiatorList shouldn't be null/empty. Initiators in the list may
     * have been created.
     */
    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} createExportMask START...", storage.getLabel());
        _log.info("createExportMask: mask id: {}", exportMaskURI);
        _log.info("createExportMask: volume-HLU pairs: {}", volumeURIHLUs);
        _log.info("createExportMask: tartets: {}", targetURIList);
        _log.info("createExportMask: initiators: {}", initiatorList);

        try {
            CIMInstance controllerInst = null;
            boolean createdBySystem = true;
            Map<String, Initiator> initiatorMap = _helper.getInitiatorMap(initiatorList);
            String[] initiatorNames = initiatorMap.keySet().toArray(new String[] {});
            List<Initiator> userAddedInitiators = new ArrayList<Initiator>();
            Map<String, CIMObjectPath> existingHwStorageIds = getStorageHardwareIds(storage);
            // note - the initiator list maybe just a subset of all initiators on a host, need to
            // get all the initiators from the host, and check here
            // a special case is that there is a host on array side with i1 and i2,
            // while there is a host with initiator i2 and i3 on ViPR side,
            // we will not be able to match the two hosts if there is common initiator(s)
            // if an HBA get moved from one host to another, it need to be removed on array side manually
            List<Initiator> allInitiators = CustomQueryUtility
                    .queryActiveResourcesByConstraint(_dbClient,
                            Initiator.class, ContainmentConstraint.Factory
                                    .getContainedObjectsConstraint(
                                            initiatorList.get(0).getHost(),
                                            Initiator.class, "host"));
            for (Initiator initiator : allInitiators) {
                String normalizedPortName = Initiator.normalizePort(initiator
                        .getInitiatorPort());
                CIMObjectPath initiatorPath = existingHwStorageIds.get(normalizedPortName);
                if (initiatorPath != null) {
                    _log.info(String.format("Initiator %s already exists",
                            initiator.getInitiatorPort()));
                    createdBySystem = false;

                    // get controller instance
                    controllerInst = getSCSIProtocolControllerInstanceByHwId(storage, initiatorPath);
                    if (controllerInst == null) {
                        _log.debug("createExportMask failed. No protocol controller created.");
                        ServiceError error = DeviceControllerErrors.smis
                                .noProtocolControllerCreated();
                        taskCompleter.error(_dbClient, error);
                        _log.info("{} createExportMask END...", storage.getLabel());
                        return;
                    }

                    // get initiators
                    Map<String, CIMObjectPath> initiatorPortPaths = _helper
                            .getInitiatorsFromScsiProtocolController(storage, controllerInst.getObjectPath());
                    Set<String> existingInitiatorPorts = initiatorPortPaths.keySet();
                    // check if initiators need to be added
                    List<String> initiatorsToAdd = new ArrayList<String>();
                    for (String port : initiatorNames) {
                        if (!existingInitiatorPorts.contains(port)) {
                            initiatorsToAdd.add(port);
                            userAddedInitiators.add(initiatorMap.get(port));
                        }
                    }

                    if (!initiatorsToAdd.isEmpty()) {
                        // add initiator to host on array side
                        CIMObjectPath specificCollectionPath = getSystemSpecificCollectionPathByHwId(storage, initiatorPath);
                        CIMArgument[] outArgs = new CIMArgument[5];
                        _helper.addHardwareIDsToCollection(storage, specificCollectionPath, initiatorsToAdd.toArray(new String[] {}),
                                outArgs);
                        if (outArgs[0] == null) {
                            Set<String> hwIds = hasHwIdsInCollection(storage, specificCollectionPath);
                            if (!hwIds.containsAll(initiatorsToAdd)) {
                                throw new Exception("Failed to add initiator: " + Joiner.on(',').join(initiatorsToAdd));
                            }
                        }
                    }

                    // don't care other initiators, they should belong to the
                    // same host/controller on both ViPR and array sides
                    break;
                }
            }

            Host host = _dbClient.queryObject(Host.class, initiatorList.get(0)
                    .getHost());
            String label = host.getLabel();

            // no matched initiator on array side, now try to find host with the given name
            if (controllerInst == null) {
                String query = String.format(
                        "Select * From %s Where ElementName=\"%s\"",
                        IBMSmisConstants.CP_SYSTEM_SPECIFIC_COLLECTION, label);
                CIMObjectPath hostPath = CimObjectPathCreator.createInstance(
                        IBMSmisConstants.CP_SYSTEM_SPECIFIC_COLLECTION,
                        Constants.IBM_NAMESPACE, null);
                List<CIMInstance> hostInstances = _helper.executeQuery(storage,
                        hostPath, query, "WQL");
                if (!hostInstances.isEmpty()) {
                    CIMObjectPath specificCollectionPath = hostInstances.get(0).getObjectPath();
                    if (!hasHwIdInCollection(storage, specificCollectionPath)) {
                        createdBySystem = false;
                        userAddedInitiators = initiatorList;
                        // re-use the empty host
                        CIMArgument[] outArgs = new CIMArgument[5];
                        _helper.addHardwareIDsToCollection(storage, specificCollectionPath, initiatorNames, outArgs);
                        if (outArgs[0] == null) {
                            Set<String> hwIds = hasHwIdsInCollection(storage, specificCollectionPath);
                            if (!hwIds.containsAll(new ArrayList<String>(Arrays.asList(initiatorNames)))) {
                                throw new Exception("Failed to add initiator: " + Joiner.on(',').join(initiatorNames));
                            }
                        }

                        controllerInst = getSCSIProtocolControllerInstanceByIdCollection(storage, specificCollectionPath);
                        if (controllerInst == null) {
                            _log.debug("createExportMask failed. No protocol controller created.");
                            ServiceError error = DeviceControllerErrors.smis
                                    .noProtocolControllerCreated();
                            taskCompleter.error(_dbClient, error);
                            _log.info("{} createExportMask END...", storage.getLabel());
                            return;
                        }
                    }
                }
            }

            // create new protocol controller
            if (controllerInst == null) {
                // create host first so that the desired host label could be used
                CIMObjectPath sysSpecificCollectionPath = getSystemSpecificCollectionPath(
                        storage, label, initiatorNames);
                if (sysSpecificCollectionPath == null) {
                    _log.debug("createExportMask failed. No host created.");
                    ServiceError error = DeviceControllerErrors.smis
                            .noProtocolControllerCreated();
                    taskCompleter.error(_dbClient, error);
                    _log.info("{} createExportMask END...", storage.getLabel());
                    return;
                }

                controllerInst = getSCSIProtocolControllerInstanceByIdCollection(
                        storage, sysSpecificCollectionPath);
            }

            if (controllerInst != null) {
                String elementName = CIMPropertyFactory.getPropertyValue(controllerInst, SmisConstants.CP_ELEMENT_NAME);
                // set host tag is needed
                if (label.equals(elementName)) {
                    _helper.unsetTag(host, storage.getSerialNumber());
                } else {
                    _helper.setTag(host, storage.getSerialNumber(), elementName);
                }

                CIMObjectPath controller = controllerInst.getObjectPath();
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                        exportMaskURI);
                if (!createdBySystem) {
                    exportMask.setCreatedBySystem(createdBySystem);
                    exportMask.addToUserCreatedInitiators(userAddedInitiators);
                }
                exportMask.setMaskName(elementName); // SCSIProtocolController.ElementName
                                                     // is the same as
                                                     // SystemSpecificCollection.ElementName
                exportMask.setLabel(elementName);
                CIMProperty<String> deviceId = (CIMProperty<String>) controller
                        .getKey(IBMSmisConstants.CP_DEVICE_ID);
                exportMask.setNativeId(deviceId.getValue());
                _dbClient.persistObject(exportMask);

                CIMArgument[] inArgs = _helper.getExposePathsInputArguments(
                        volumeURIHLUs, null, controller);
                CIMArgument[] outArgs = new CIMArgument[5];
                // don't care if the volumes/initiators have already been in the
                // mask
                _helper.invokeMethod(storage,
                        _cimPath.getControllerConfigSvcPath(storage),
                        IBMSmisConstants.EXPOSE_PATHS, inArgs, outArgs);
                CIMObjectPath[] protocolControllers = _cimPath
                        .getProtocolControllersFromOutputArgs(outArgs);
                CIMObjectPath protocolController = protocolControllers[0];

                // for debug only
                if (_log.isDebugEnabled()) {
                    List<String> targetEndpoints = getTargetEndpoints(
                            protocolController, storage);
                    _log.debug(String.format(
                            "ProtocolController %s with target ports: %s",
                            protocolController.getObjectName(), Joiner.on(',')
                                    .join(targetEndpoints)));
                }

                CimConnection cimConnection = _helper.getConnection(storage);
                // Call populateDeviceNumberFromProtocolControllers only after
                // initiators
                // have been added. HLU's will not be reported till the Device
                // is Host visible
                ExportMaskOperationsHelper
                        .populateDeviceNumberFromProtocolControllers(_dbClient,
                                cimConnection, exportMaskURI, volumeURIHLUs,
                                protocolControllers, taskCompleter);
                taskCompleter.ready(_dbClient);
            } else {
                _log.debug("createExportMask failed. No protocol controller created.");
                ServiceError error = DeviceControllerErrors.smis
                        .noProtocolControllerCreated();
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            _log.error("Unexpected error: createExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} createExportMask END...", storage.getLabel());
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskURI,
            List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} deleteExportMask START...", storage.getLabel());
        try {
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                    exportMaskURI);
            StringSet initiators = exportMask.getInitiators();
            Host host = null;
            if (initiators != null) {
                Iterator<String> itr = initiators.iterator();
                if (itr.hasNext()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(itr.next()));
                    host = _dbClient.queryObject(Host.class, initiator.getHost());
                }
            }

            String nativeId = exportMask.getNativeId();
            if (Strings.isNullOrEmpty(nativeId)) {
                _log.warn(String
                        .format("ExportMask %s does not have a nativeID, "
                                + "indicating that this export may not have been created "
                                + "successfully. Marking the delete operation ready.",
                                exportMaskURI.toString()));
            } else if (!exportMask.getCreatedBySystem()) {
                _log.info("Export mask {} is not created by system", exportMask.getLabel());
                // shouldn't remove the mask
                // check user added volumes, remove them from mask
                // check user added initiators, remove them from mask
                String[] volumesToRemove = null;
                StringMap volumeMap = exportMask.getUserAddedVolumes();
                if (volumeMap != null && !volumeMap.isEmpty()) {
                    volumesToRemove = volumeMap.keySet().toArray(
                            new String[] {});
                }

                String[] initiatorsToRemove = null;
                StringMap initiatorMap = exportMask.getUserAddedInitiators();
                if (initiatorMap != null && !initiatorMap.isEmpty()) {
                    initiatorsToRemove = initiatorMap.keySet().toArray(
                            new String[] {});
                }

                CIMArgument[] inArgs = _helper.getHidePathsInputArguments(
                        storage, exportMask, volumesToRemove,
                        initiatorsToRemove);
                _helper.invokeMethod(storage,
                        _cimPath.getControllerConfigSvcPath(storage),
                        IBMSmisConstants.HIDE_PATHS, inArgs);
            } else {
                CIMObjectPath protocolController = _cimPath
                        .getSCSIProtocolControllerPath(storage, nativeId);
                CIMInstance instance = _helper.checkExists(storage,
                        protocolController, true, true);
                if (instance != null) {
                    CIMArgument[] inArgs = _helper
                            .getDeleteProtocolControllerInputArguments(protocolController);
                    _helper.invokeMethod(storage,
                            _cimPath.getControllerConfigSvcPath(storage),
                            IBMSmisConstants.DELETE_PROTOCOL_CONTROLLER, inArgs);

                    if (host == null) {
                        // get hosts from initiators
                        Map<String, CIMObjectPath> initiatorPortPaths = _helper
                                .getInitiatorsFromScsiProtocolController(storage, instance.getObjectPath());
                        Set<String> initiatorPorts = initiatorPortPaths.keySet();
                        for (String initiatorPort : initiatorPorts) {
                            Initiator initiator = ExportUtils.getInitiator(
                                    WWNUtility.getWWNWithColons(initiatorPort),
                                    _dbClient);
                            if (initiator != null) {
                                host = _dbClient.queryObject(Host.class, initiator.getHost());
                                break;
                            }
                        }
                    }
                }
            }

            // Perform post-mask-delete cleanup steps
            if (host != null) {
                _helper.unsetTag(host, storage.getSerialNumber());
            }

            ExportUtils.cleanupAssociatedMaskResources(_dbClient, exportMask);

            exportMask.setMaskName(NullColumnValueGetter.getNullURI().toString());
            exportMask.setLabel(NullColumnValueGetter.getNullURI().toString());
            exportMask.setNativeId(NullColumnValueGetter.getNullURI().toString());
            exportMask.setResource(NullColumnValueGetter.getNullURI().toString());

            _dbClient.updateAndReindexObject(exportMask);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: deleteExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "deleteExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} deleteExportMask END...", storage.getLabel());
    }

    @Override
    public void addVolume(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} addVolume START...", storage.getLabel());
        try {
            CIMArgument[] inArgs = _helper.getExposePathsInputArguments(
                    storage, exportMaskURI, volumeURIHLUs, null);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage,
                    _cimPath.getControllerConfigSvcPath(storage),
                    IBMSmisConstants.EXPOSE_PATHS, inArgs, outArgs);
            CIMObjectPath[] protocolControllers = _cimPath
                    .getProtocolControllersFromOutputArgs(outArgs);

            CimConnection cimConnection = _helper.getConnection(storage);
            ExportMaskOperationsHelper
                    .populateDeviceNumberFromProtocolControllers(_dbClient,
                            cimConnection, exportMaskURI, volumeURIHLUs,
                            protocolControllers, taskCompleter);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: addVolume failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "addVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} addVolume END...", storage.getLabel());
    }

    @Override
    public void removeVolume(StorageSystem storage, URI exportMaskURI,
            List<URI> volumeURIList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        _log.info("{} removeVolume START...", storage.getLabel());
        try {
            CIMArgument[] inArgs = _helper.getHidePathsInputArguments(storage,
                    exportMaskURI, volumeURIList, null);
            _helper.invokeMethod(storage,
                    _cimPath.getControllerConfigSvcPath(storage), IBMSmisConstants.HIDE_PATHS,
                    inArgs);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: removeVolume failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "removeVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} removeVolume END...", storage.getLabel());
    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMaskURI,
            List<Initiator> initiatorList, List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addInitiator START...", storage.getLabel());
        try {
            CIMArgument[] inArgs = _helper.getExposePathsInputArguments(
                    storage, exportMaskURI, null, initiatorList);
            _helper.invokeMethod(storage,
                    _cimPath.getControllerConfigSvcPath(storage),
                    IBMSmisConstants.EXPOSE_PATHS, inArgs);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: addInitiator failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "addInitiator", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} addInitiator END...", storage.getLabel());
    }

    @Override
    // TOD -test
            public
            void removeInitiator(StorageSystem storage, URI exportMaskURI,
                    List<Initiator> initiatorList, List<URI> targets,
                    TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeInitiator START...", storage.getLabel());

        try {
            if (initiatorList != null && !initiatorList.isEmpty()) {
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                        exportMaskURI);
                CIMObjectPath controllerPath = _cimPath
                        .getSCSIProtocolControllerPath(storage,
                                exportMask.getNativeId());
                if (controllerPath != null) {
                    Map<String, CIMObjectPath> hwIdPaths = _helper
                            .getInitiatorsFromScsiProtocolController(storage,
                                    controllerPath);
                    CIMObjectPath hwIdManagementSvc = _cimPath
                            .getStorageHardwareIDManagementService(storage);
                    String[] initiatorNames = _helper.getInitiatorNames(initiatorList);
                    for (String initiator : initiatorNames) {
                        CIMObjectPath hwIdPath = hwIdPaths.get(initiator);
                        if (hwIdPath != null) {
                            try {
                                CIMArgument[] deleteHwIdIn = _helper
                                        .getDeleteStorageHardwareIDInputArgs(storage, hwIdPath);
                                _helper.invokeMethod(storage, hwIdManagementSvc,
                                        SmisConstants.DELETE_STORAGE_HARDWARE_ID, deleteHwIdIn);
                            } catch (WBEMException e) {
                                _log.error("deleteStorageHWIDs -- WBEMException: ", e);
                            } catch (Exception e) {
                                _log.error("deleteStorageHWIDs -- Exception: " + e);
                            }
                        } else {
                            _log.info("Initiator {} is not on array", initiator);
                        }
                    }

                    CIMObjectPath idCollectionPath = getIdCollectionBySCSIProtocolController(
                            storage, controllerPath);
                    if (!hasHwIdInCollection(storage, idCollectionPath)) {
                        // update host label
                        Host host = _dbClient.queryObject(Host.class,
                                initiatorList.get(0).getHost());
                        _helper.unsetTag(host, storage.getSerialNumber());
                    }
                } else {
                    _log.error("Protocol controller is null");
                    ServiceError error = DeviceControllerErrors.smis.noProtocolControllerCreated();
                    taskCompleter.error(_dbClient, error);
                    return;
                }
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: removeInitiator failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed(
                    "removeInitiator", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} removeInitiator END...", storage.getLabel());
    }

    /**
     * This call can be used to look up the passed in initiator/port names and
     * find (if any) to which export masks they belong on the 'storage' array.
     * 
     * 
     * @param storage
     *            [in] - StorageSystem object representing the array
     * @param initiatorNames
     *            [in] - normalized Port identifiers (WWPN or iSCSI name) (all initiators of all hosts involved)
     * @param mustHaveAllPorts
     *            [in] NOT APPLICABLE FOR XIV
     * @return Map of port name to Set of ExportMask URIs
     */
    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) {
        long startTime = System.currentTimeMillis();
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();
        CloseableIterator<CIMInstance> lunMaskingIter = null;
        try {
            StringBuilder builder = new StringBuilder();
            lunMaskingIter = _helper.getSCSIProtocolControllers(storage);
            while (lunMaskingIter.hasNext()) {
                CIMInstance instance = lunMaskingIter.next();
                String name = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_NAME);
                String deviceId = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_DEVICE_ID);

                // Get initiators for the masking instance
                CIMObjectPath controllerPath = instance.getObjectPath();
                Map<String, CIMObjectPath> initiatorPortPaths = _helper
                        .getInitiatorsFromScsiProtocolController(storage, controllerPath);
                Set<String> initiatorPorts = initiatorPortPaths.keySet();
                // Find out if the port is in this masking container
                List<String> matchingInitiators = new ArrayList<String>();
                for (String port : initiatorNames) {
                    if (initiatorPorts.contains(port)) {
                        matchingInitiators.add(port);
                    }
                }
                builder.append(String.format("XM:%s I:{%s}%n", name, Joiner.on(',').join(initiatorPorts)));
                if (!matchingInitiators.isEmpty()) {
                    // Look up ExportMask by deviceId/name and storage URI
                    boolean foundMaskInDb = false;
                    ExportMask exportMask = null;
                    URIQueryResultList uriQueryList = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getExportMaskByNameConstraint(name), uriQueryList);
                    while (uriQueryList.iterator().hasNext()) {
                        URI uri = uriQueryList.iterator().next();
                        exportMask = _dbClient.queryObject(ExportMask.class,
                                uri);
                        if (exportMask != null
                                && !exportMask.getInactive()
                                && exportMask.getStorageDevice().equals(
                                        storage.getId())) {
                            foundMaskInDb = true;
                            // We're expecting there to be only one export mask
                            // of a
                            // given name for any storage array.
                            break;
                        }
                    }
                    // If there was no export mask found in the database,
                    // then create a new one
                    if (!foundMaskInDb) {
                        exportMask = new ExportMask();
                        exportMask.setLabel(name);
                        exportMask.setMaskName(name);
                        exportMask.setNativeId(deviceId);
                        exportMask.setStorageDevice(storage.getId());
                        exportMask.setId(URIUtil.createId(ExportMask.class));
                        exportMask.setCreatedBySystem(false);

                        // Grab the storage ports that have been allocated for this existing mask
                        // the list will be empty if there is no real connectivity between the host and the array
                        List<String> storagePorts = _helper
                                .getStoragePortsFromScsiProtocolController(
                                        storage, controllerPath);
                        List<String> storagePortURIs = storagePortNamesToURIs(storagePorts);
                        if (storagePortURIs.isEmpty()) {
                            _log.info("No storage port in the mask " + name);
                        }
                        else {
                            exportMask.setStoragePorts(storagePortURIs);
                            builder.append(String.format("   ----> SP { %s }\n"
                                    + "         URI{ %s }\n",
                                    Joiner.on(',').join(storagePorts),
                                    Joiner.on(',').join(storagePortURIs)));
                        }
                    }

                    // Get volumes for the masking instance
                    Map<String, Integer> volumeWWNs = _helper
                            .getVolumesFromScsiProtocolController(storage, controllerPath);
                    builder.append(String.format("XM:%s V:{%s}%n", name, Joiner.on(',').join(volumeWWNs.keySet())));

                    // Update the tracking containers
                    exportMask.addToExistingVolumesIfAbsent(volumeWWNs);
                    exportMask
                            .addToExistingInitiatorsIfAbsent(matchingInitiators);
                    builder.append(String.format(
                            "XM %s is matching. " + "EI: { %s }, EV: { %s }%n",
                            name,
                            Joiner.on(',').join(
                                    exportMask.getExistingInitiators()),
                            Joiner.on(',').join(
                                    exportMask.getExistingVolumes().keySet())));
                    if (foundMaskInDb) {
                        ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, exportMask);
                        _dbClient.updateAndReindexObject(exportMask);
                    } else {
                        _dbClient.createObject(exportMask);
                    }

                    // update hosts
                    Initiator initiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(matchingInitiators.get(0)), _dbClient);
                    Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                    String label = host.getLabel();
                    if (label.equals(name)) {
                        _helper.unsetTag(host, storage.getSerialNumber());
                    } else {
                        _helper.setTag(host, storage.getSerialNumber(), name);
                    }

                    for (String it : matchingInitiators) {
                        Set<URI> maskURIs = matchingMasks.get(it);
                        if (maskURIs == null) {
                            maskURIs = new HashSet<URI>();
                            matchingMasks.put(it, maskURIs);
                        }
                        maskURIs.add(exportMask.getId());
                    }
                }
            }
            _log.info(builder.toString());
        } catch (Exception e) {
            String msg = "Error when attempting to query LUN masking information: "
                    + e.getMessage();
            _log.error(
                    MessageFormat
                            .format("Encountered an SMIS error when attempting to query existing exports: {0}",
                                    msg), e);

            throw SmisException.exceptions.queryExistingMasksFailure(msg, e);
        } finally {
            if (lunMaskingIter != null) {
                lunMaskingIter.close();
            }
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("findExportMasks took %f seconds", (double) totalTime / (double) 1000));
        }

        return matchingMasks;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        try {
            CIMInstance instance = _helper.getSCSIProtocolController(storage,
                    mask);
            if (instance != null) {
                StringBuilder builder = new StringBuilder();
                String name = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_NAME);
                // Get volumes and initiators for the masking instance
                CIMObjectPath controllerPath = instance.getObjectPath();
                Map<String, Integer> discoveredVolumes = _helper
                        .getVolumesFromScsiProtocolController(storage, controllerPath);
                Map<String, CIMObjectPath> discoveredPortPaths = _helper
                        .getInitiatorsFromScsiProtocolController(storage, instance.getObjectPath());
                Set<String> discoveredPorts = discoveredPortPaths.keySet();
                Set existingInitiators = (mask.getExistingInitiators() != null) ? mask
                        .getExistingInitiators() : Collections.emptySet();
                Set existingVolumes = (mask.getExistingVolumes() != null) ? mask
                        .getExistingVolumes().keySet() : Collections.emptySet();

                builder.append(String.format("%nXM object: %s I{%s} V:{%s}%n",
                        name, Joiner.on(',').join(existingInitiators), Joiner
                                .on(',').join(existingVolumes)));

                builder.append(String.format(
                        "XM discovered: %s I:{%s} V:{%s}%n", name,
                        Joiner.on(',').join(discoveredPorts), Joiner.on(',')
                                .join(discoveredVolumes.keySet())));

                // Check the initiators and update the lists as necessary
                boolean addInitiators = false;
                List<String> initiatorsToAdd = new ArrayList<String>();
                for (String port : discoveredPorts) {
                    String normalizedPort = Initiator.normalizePort(port);
                    if (!mask.hasExistingInitiator(normalizedPort)
                            && !mask.hasUserInitiator(normalizedPort)) {
                        initiatorsToAdd.add(normalizedPort);
                        addInitiators = true;
                    }
                }

                boolean removeInitiators = false;
                List<String> initiatorsToRemove = new ArrayList<String>();
                if (mask.getExistingInitiators() != null
                        && !mask.getExistingInitiators().isEmpty()) {
                    initiatorsToRemove.addAll(mask.getExistingInitiators());
                    initiatorsToRemove.removeAll(discoveredPorts);
                    removeInitiators = !initiatorsToRemove.isEmpty();
                }

                // Check the volumes and update the lists as necessary
                Map<String, Integer> volumesToAdd = ExportMaskUtils.diffAndFindNewVolumes(mask, discoveredVolumes);
                boolean addVolumes = !volumesToAdd.isEmpty();

                boolean removeVolumes = false;
                List<String> volumesToRemove = new ArrayList<String>();
                if (mask.getExistingVolumes() != null
                        && !mask.getExistingVolumes().isEmpty()) {
                    volumesToRemove.addAll(mask.getExistingVolumes().keySet());
                    volumesToRemove.removeAll(discoveredVolumes.keySet());
                    removeVolumes = !volumesToRemove.isEmpty();
                }

                boolean changeName = false;
                if (!mask.getMaskName().equals(name)) {
                    changeName = true;
                    mask.setLabel(name);
                    mask.setMaskName(name);

                    // update host label
                    StringSet initiators = mask.getInitiators();
                    if (initiators != null) {
                        Iterator<String> itr = initiators.iterator();
                        if (itr.hasNext()) {
                            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(itr.next()));
                            Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                            String label = host.getLabel();
                            if (label.equals(name)) {
                                _helper.unsetTag(host, storage.getSerialNumber());
                            } else {
                                _helper.setTag(host, storage.getSerialNumber(), name);
                            }
                        }
                    }
                }

                builder.append(String.format(
                        "XM refresh: %s initiators; add:{%s} remove:{%s}%n",
                        name, Joiner.on(',').join(initiatorsToAdd),
                        Joiner.on(',').join(initiatorsToRemove)));
                builder.append(String.format(
                        "XM refresh: %s volumes; add:{%s} remove:{%s}%n", name,
                        Joiner.on(',').join(volumesToAdd.keySet()),
                        Joiner.on(',').join(volumesToRemove)));

                // Any changes indicated, then update the mask and persist it
                if (addInitiators || removeInitiators || addVolumes
                        || removeVolumes || changeName) {
                    builder.append("XM refresh: There are changes to mask, "
                            + "updating it...\n");
                    mask.removeFromExistingInitiators(initiatorsToRemove);
                    mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                    mask.removeFromExistingVolumes(volumesToRemove);
                    mask.addToExistingVolumesIfAbsent(volumesToAdd);
                    ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
                    _dbClient.updateAndReindexObject(mask);
                } else {
                    builder.append("XM refresh: There are no changes to the mask\n");
                }
                _networkDeviceController.refreshZoningMap(mask,
                        initiatorsToRemove, Collections.EMPTY_LIST, (addInitiators || removeInitiators), true);
                _log.info(builder.toString());
            }
        } catch (Exception e) {
            boolean throwException = true;
            if (e instanceof WBEMException) {
                WBEMException we = (WBEMException) e;
                // Only throw exception if code is not CIM_ERROR_NOT_FOUND
                throwException = (we.getID() != WBEMException.CIM_ERR_NOT_FOUND);
            }
            if (throwException) {
                String msg = "Error when attempting to query LUN masking information: "
                        + e.getMessage();
                _log.error(
                        MessageFormat
                                .format("Encountered an SMIS error when attempting to refresh existing exports: {0}",
                                        msg), e);

                throw SmisException.exceptions.refreshExistingMaskFailure(msg,
                        e);
            }
        }

        return mask;
    }

    /**
     * Take in a list of storage port names (hex digits separated by colons),
     * then returns a list of URIs representing the StoragePort URIs they
     * represent.
     * 
     * @param storagePorts
     *            [in] - Storage port name, hex digits separated by colons
     * @return List of StoragePort URIs
     */
    private List<String> storagePortNamesToURIs(List<String> storagePorts) {
        List<String> storagePortURIStrings = new ArrayList<String>();
        for (String port : storagePorts) {
            URIQueryResultList uriQueryList = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortEndpointConstraint(port), uriQueryList);
            if (uriQueryList.iterator().hasNext()) {
                storagePortURIStrings.add(uriQueryList.iterator().next()
                        .toString());
            }
        }

        return storagePortURIStrings;
    }

    /**
     * Returns a list of CIM_StorageHardwareID.ElementName values
     * from the provider managing the specified 'storage' array.
     * 
     * The values will be normalized. That is, in the case of WWN, it will be
     * all upper-case with colons (if any) removed.
     * 
     * @param storage
     *            [in] - StorageSystem object
     * @return Map of String(initiator port name) to CIMObjectPath representing
     *         the Initiator in SMI-S
     * @throws Exception
     */
    private Map<String, CIMObjectPath> getStorageHardwareIds(
            StorageSystem storage) throws Exception {
        Map<String, CIMObjectPath> idsMap = new HashMap<>();
        CloseableIterator<CIMInstance> seHwIter = null;
        try {
            // Multiple arrays can be managed by a single SMI-S instance. The
            // CIM_StorageHardwareID is
            // global to the provider, so we need to get the
            // CIM_StorageHardware_ID object that are
            // associated with a specific array.
            CIMObjectPath hwManagementIDSvcPath = _cimPath
                    .getStorageHardwareIDManagementService(storage);
            seHwIter = _helper.getAssociatorInstances(storage,
                    hwManagementIDSvcPath, null,
                    IBMSmisConstants.CP_STORAGE_HARDWARE_ID, null, null,
                    SmisConstants.PS_ELEMENT_NAME);
            while (seHwIter.hasNext()) {
                CIMInstance instance = seHwIter.next();
                String port = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                String elementName = Initiator.normalizePort(port);
                idsMap.put(elementName, instance.getObjectPath());
            }
        } finally {
            if (seHwIter != null) {
                seHwIter.close();
            }
        }
        return idsMap;
    }

    /*
     * Returns SystemSpecificCollection associated with the initiator
     */
    private CIMObjectPath getSystemSpecificCollectionPathByHwId(
            StorageSystem storage, CIMObjectPath hardwareIDPath)
            throws Exception {
        CloseableIterator<CIMObjectPath> spcIter = null;
        try {
            spcIter = _helper.getAssociatorNames(storage, hardwareIDPath,
                    IBMSmisConstants.CP_SYS_SPECIFIC_COLLECTION_TO_SHWID,
                    IBMSmisConstants.CP_SYSTEM_SPECIFIC_COLLECTION,
                    IBMSmisConstants.CP_MEMBER, IBMSmisConstants.CP_COLLECTION);
            while (spcIter.hasNext()) {
                return spcIter.next();
            }
        } finally {
            if (spcIter != null) {
                spcIter.close();
            }
        }

        return null;
    }

    /*
     * Returns SCSIProtocolController associated with the initiator
     */
    private CIMInstance getSCSIProtocolControllerInstanceByHwId(
            StorageSystem storage, CIMObjectPath hardwareIDPath)
            throws Exception {
        CloseableIterator<CIMInstance> spcIter = null;
        try {
            spcIter = _helper.getAssociatorInstances(storage, hardwareIDPath,
                    IBMSmisConstants.CP_SHWID_TO_SPC,
                    IBMSmisConstants.CP_SCSI_PROTOCOL_CONTROLLER,
                    IBMSmisConstants.ANTECEDENT, IBMSmisConstants.CP_DEPENDENT, IBMSmisConstants.PS_ELEMENT_NAME);
            while (spcIter.hasNext()) {
                return spcIter.next();
            }
        } finally {
            if (spcIter != null) {
                spcIter.close();
            }
        }

        return null;
    }

    /*
     * create SystemSpecificCollection for the initiators
     * 
     * Try to use the given element name first, if not success, retry without
     * element name
     */
    private CIMObjectPath getSystemSpecificCollectionPath(
            StorageSystem storage, String elementName, String[] initiators)
            throws Exception {
        @SuppressWarnings("rawtypes")
        CIMArgument[] outArgs = new CIMArgument[5];
        CIMObjectPath hwIdManagementSvc = _cimPath
                .getStorageHardwareIDManagementService(storage);
        _helper.createHardwareIDCollection(storage, hwIdManagementSvc,
                elementName, initiators, outArgs);
        if (outArgs[0] == null) {
            // must be return code 45504 (Host name already exists)
            // now let system generate element name
            _helper.createHardwareIDCollection(storage, hwIdManagementSvc,
                    null, initiators, outArgs);
        }

        return (CIMObjectPath) _cimPath.getFromOutputArgs(outArgs,
                IBMSmisConstants.CP_HARDWARE_ID_COLLECTION);
    }

    /*
     * Returns hardware Id in the collection
     */
    private Set<String> hasHwIdsInCollection(
            StorageSystem storage, CIMObjectPath sysSpecificCollectionPath)
            throws Exception {
        Set<String> hwIds = new HashSet<String>();
        CloseableIterator<CIMInstance> shwIdIter = null;
        try {
            shwIdIter = _helper.getAssociatorInstances(storage,
                    sysSpecificCollectionPath,
                    IBMSmisConstants.CP_SYS_SPECIFIC_COLLECTION_TO_SHWID,
                    IBMSmisConstants.CP_STORAGE_HARDWARE_ID,
                    IBMSmisConstants.CP_COLLECTION, IBMSmisConstants.CP_MEMBER, SmisConstants.PS_ELEMENT_NAME);
            while (shwIdIter.hasNext()) {
                CIMInstance instance = shwIdIter.next();
                String port = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                String elementName = Initiator.normalizePort(port);
                hwIds.add(elementName);
            }
        } finally {
            if (shwIdIter != null) {
                shwIdIter.close();
            }
        }

        return hwIds;
    }

    /*
     * Returns true if there is associated hardware Id in the collection
     */
    private boolean hasHwIdInCollection(
            StorageSystem storage, CIMObjectPath sysSpecificCollectionPath)
            throws Exception {
        CloseableIterator<CIMObjectPath> shwIdIter = null;
        try {
            shwIdIter = _helper.getAssociatorNames(storage,
                    sysSpecificCollectionPath,
                    IBMSmisConstants.CP_SYS_SPECIFIC_COLLECTION_TO_SHWID,
                    IBMSmisConstants.CP_STORAGE_HARDWARE_ID,
                    IBMSmisConstants.CP_COLLECTION, IBMSmisConstants.CP_MEMBER);
            while (shwIdIter.hasNext()) {
                return true;
            }
        } finally {
            if (shwIdIter != null) {
                shwIdIter.close();
            }
        }

        return false;
    }

    /*
     * Returns SCSIProtocolController associated with the storage hardware Id
     * collection
     */
    private CIMInstance getSCSIProtocolControllerInstanceByIdCollection(
            StorageSystem storage, CIMObjectPath sysSpecificCollectionPath)
            throws Exception {
        CloseableIterator<CIMObjectPath> shwIdIter = null;
        try {
            shwIdIter = _helper.getAssociatorNames(storage,
                    sysSpecificCollectionPath,
                    IBMSmisConstants.CP_SYS_SPECIFIC_COLLECTION_TO_SHWID,
                    IBMSmisConstants.CP_STORAGE_HARDWARE_ID,
                    IBMSmisConstants.CP_COLLECTION, IBMSmisConstants.CP_MEMBER);
            while (shwIdIter.hasNext()) {
                return getSCSIProtocolControllerInstanceByHwId(storage,
                        shwIdIter.next());
            }
        } finally {
            if (shwIdIter != null) {
                shwIdIter.close();
            }
        }

        return null;
    }

    /*
     * Returns storage hardware Id collection associated with the controller
     */
    private CIMObjectPath getIdCollectionBySCSIProtocolController(
            StorageSystem storage, CIMObjectPath scsiProtocolControllerPath)
            throws Exception {
        CloseableIterator<CIMObjectPath> shwIdIter = null;
        try {
            shwIdIter = _helper.getAssociatorNames(storage,
                    scsiProtocolControllerPath,
                    IBMSmisConstants.CP_SHWID_TO_SPC,
                    IBMSmisConstants.CP_STORAGE_HARDWARE_ID,
                    IBMSmisConstants.CP_DEPENDENT, IBMSmisConstants.ANTECEDENT);
            while (shwIdIter.hasNext()) {
                return this.getSystemSpecificCollectionPathByHwId(storage,
                        shwIdIter.next());
            }
        } finally {
            if (shwIdIter != null) {
                shwIdIter.close();
            }
        }

        return null;
    }

    /**
     * Looks up the targets that are associated with the protocol controller (if any).
     * 
     * @param protocolController [in] - CIMObjectPath representing protocol controller to lookup target endpoints (StoragePorts) for
     * @param storage [in] - StorageSystem object representing the array
     * @return List or StoragePort URIs that were found to be end points for the protocol controller
     * @throws Exception
     */
    private List<String> getTargetEndpoints(CIMObjectPath protocolController,
            StorageSystem storage) throws Exception {
        List<String> endpoints = new ArrayList<String>();
        CloseableIterator<CIMInstance> fcPortIter = null;
        try {
            fcPortIter = _helper.getAssociatorInstances(storage, protocolController,
                    IBMSmisConstants.CP_PROTOCOL_CONTROLLER_FOR_PORT, IBMSmisConstants.CP_LOGICALPORT,
                    null, null, IBMSmisConstants.PS_PERMANENT_ADDRESS);
            while (fcPortIter.hasNext()) {
                CIMInstance instance = fcPortIter.next();
                String targetPortId = CIMPropertyFactory.getPropertyValue(
                        instance, IBMSmisConstants.CP_PERMANENT_ADDRESS);
                List<StoragePort> storagePorts = CustomQueryUtility
                        .queryActiveResourcesByAltId(_dbClient,
                                StoragePort.class, IBMSmisConstants.PORT_NETWORK_ID,
                                WWNUtility.getWWNWithColons(targetPortId));
                for (StoragePort port : storagePorts) {
                    endpoints.add(port.getNativeGuid());
                }
            }
        } finally {
            if (fcPortIter != null) {
                fcPortIter.close();
            }
        }

        _log.info(String.format("SPC %s has these target endpoints: [ %s ]",
                protocolController.toString(), Joiner.on(',').join(endpoints)));
        return endpoints;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }
}

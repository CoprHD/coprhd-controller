/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm.xiv;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.Job.JobStatus;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.CIMArgumentFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMCIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.ibm.IBMSmisSynchSubTaskJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisJob;

/**
 * Helper for SMI-S commands.
 */
public class XIVSmisCommandHelper implements IBMSmisConstants {
    private static final Logger _log = LoggerFactory
            .getLogger(XIVSmisCommandHelper.class);
    public static final ConcurrentHashMap<String, CIMObjectPath> CIM_OBJECT_PATH_HASH_MAP =
            new ConcurrentHashMap<String, CIMObjectPath>();
    private static final int SYNC_WRAPPER_WAIT = 5000;
    private static final int SYNC_WRAPPER_TIME_OUT = 600000; // 10 minutes
    private static final int POLL_CYCLE_LIMIT = 100;
    private static final int INVALID_RETURN_CODE = -1;
    private static final int CIM_SUCCESS_CODE = 0;
    private static final int CIM_DUPLICATED_CG_NAME_CODE = 45314; // Consistency Group name already exists
    private static final int CIM_ONLY_ALLOWED_ON_EMPTY_CG_CODE = 45316; // This operation is only allowed on an empty Consistency Group.
    private static final int CIM_MAPPING_NOT_DEFINED = 45635; // The requested mapping is not defined
    private static final int CIM_DUPLICATED_HOST_NAME = 45504; // Host name already exists
    private static final int CIM_OPERATION_PARTIALLY_SUCCEEDED = 32769; // Operation partially succeeded
    private static final int CIM_MAX_RETRY_COUNT = 25;
    private static final int CIM_RETRY_WAIT_INTERVAL = 5000; // 5 seconds
    private static final String CIM_BAD_REQUEST = "HTTP 400 - Bad Request (CIMError: \"request-not-well-formed\", OpenPegasus Error: \"Bad opening element: on line 1\")";
    private static final int MAXIMUM_LUN = 511;
    private static final String INVALID_LUN_ERROR_MSG = "Logical unit number provided (%d) is larger than allowed (%d).";

    CIMArgumentFactory _cimArgument = null;
    CIMPropertyFactory _cimProperty = null;
    IBMCIMObjectPathFactory _cimPath = null;
    CIMConnectionFactory _cimConnection = null;
    DbClient _dbClient = null;
    // TODO - place holder for now
    ControllerLockingService _locker;

    public void setCimArgumentFactory(CIMArgumentFactory cimArgumentFactory) {
        _cimArgument = cimArgumentFactory;
    }

    public void setCimPropertyFactory(CIMPropertyFactory cimPropertyFactory) {
        _cimProperty = cimPropertyFactory;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }

    public void setCimObjectPathFactory(
            IBMCIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        _locker = locker;
    }

    /**
     * Return CIM connection.
     */
    public CimConnection getConnection(StorageSystem storageDevice) {
        return _cimConnection.getConnection(storageDevice);
    }

    /*
     * Validate connection
     */
    public boolean validateStorageProviderConnection(String ipAddress,
            Integer portNumber) {
        boolean isConnectionValid = false;
        try {
            CimConnection connection = _cimConnection.getConnection(ipAddress, portNumber.toString());
            isConnectionValid = (connection != null && _cimConnection.checkConnectionliveness(connection));
        } catch (IllegalStateException ise) {
            _log.error(ise.getMessage());
        }
        return isConnectionValid;
    }

    /**
     * Invoke CIM method.
     */
    @SuppressWarnings("rawtypes")
    public void invokeMethod(StorageSystem storageDevice,
            CIMObjectPath objectPath, String methodName, CIMArgument[] inArg)
            throws Exception {
        invokeMethod(storageDevice, objectPath, methodName, inArg,
                new CIMArgument[5]);
    }

    /**
     * Invoke CIM method.
     */
    @SuppressWarnings("rawtypes")
    public void invokeMethod(StorageSystem storageDevice,
            CIMObjectPath objectPath, String methodName, CIMArgument[] inArgs,
            CIMArgument[] outArgs) throws Exception {
        CimConnection connection = getConnection(storageDevice);
        WBEMClient client = connection.getCimClient();
        int index = 0;
        StringBuilder inputInfoBuffer = new StringBuilder();
        inputInfoBuffer.append("\nSMI-S Provider: ")
                .append(connection.getHost())
                .append(" -- Attempting invokeMethod ").append(methodName)
                .append(" on\n").append("  objectPath=")
                .append(objectPath.toString()).append(" with arguments: \n");
        for (CIMArgument arg : inArgs) {
            inputInfoBuffer.append("    inArg[").append(index++).append("]=")
                    .append(arg.toString()).append('\n');
        }
        _log.info(inputInfoBuffer.toString());
        long start = System.nanoTime();

        // workaround for CTRL-8024 (CIMError: "request-not-well-formed" ...)
        // retry CIM_MAX_RETRY_COUNT times if encounter the error
        // the invoke method will return quickly with the error, so extensive retry (e.g., 25 times), won't be a big overhead
        Object obj = null;
        int retryCount = 0;
        while (true) {
            try {
                _log.info("Invoke method {}, attempt {}", methodName, retryCount);
                obj = client.invokeMethod(objectPath, methodName, inArgs, outArgs);
            } catch (WBEMException e) {
                if (CIM_BAD_REQUEST.equals(e.getMessage())) {
                    if (retryCount < CIM_MAX_RETRY_COUNT) {
                        _log.warn("Encountered 'request-not-well-formed' error. Retry...");
                        retryCount++;

                        try {
                            Thread.sleep(CIM_RETRY_WAIT_INTERVAL);
                        } catch (InterruptedException ie) {
                            _log.warn("Thread: " + Thread.currentThread().getName() + " interrupted.");
                            throw e;
                        }

                        continue;
                    }

                    _log.warn("Exhausted {} retries", CIM_MAX_RETRY_COUNT);
                }

                // other WBEMException, or reach the max retry count
                throw e;
            }

            // no exception
            break;
        }

        String total = String.format("%2.6f",
                ((System.nanoTime() - start) / 1000000000.0));
        StringBuilder outputInfoBuffer = new StringBuilder();
        outputInfoBuffer.append("\nSMI-S Provider: ")
                .append(connection.getHost())
                .append(" -- Completed invokeMethod ").append(methodName)
                .append(" on\n").append("  objectPath=")
                .append(objectPath.toString()).append("\n  Returned: ")
                .append(obj.toString()).append(" with output arguments: \n");

        int returnCode = NumberUtils.toInt(obj.toString(), INVALID_RETURN_CODE);
        for (CIMArgument arg : outArgs) {
            if (arg != null) {
                if (returnCode == CIM_SUCCESS_CODE) {
                    outputInfoBuffer.append("    outArg=").append(arg.toString())
                            .append('\n');
                }
                else {
                    outputInfoBuffer.append("    outArg=").append(arg.getName())
                            .append("=")
                            .append(arg.getValue())
                            .append(" (Type ").append(arg.getDataType())
                            .append(")\n");
                }
            }
        }

        outputInfoBuffer.append("  Execution time: ").append(total)
                .append(" seconds.\n");
        _log.info(outputInfoBuffer.toString());

        if (returnCode == CIM_MAPPING_NOT_DEFINED && methodName.equals(HIDE_PATHS)) {
            // ignore the error
        } else if (returnCode == this.CIM_DUPLICATED_HOST_NAME && methodName.equals(CREATE_HARDWARE_ID_COLLECTION)) {
            outArgs[0] = null;
        } else if (returnCode == this.CIM_OPERATION_PARTIALLY_SUCCEEDED && methodName.equals(ADD_HARDWARE_IDS_TO_COLLECTION)) {
            outArgs[0] = null;
        } else if (returnCode == CIM_DUPLICATED_CG_NAME_CODE) {
            throw new Exception(DUPLICATED_CG_NAME_ERROR);
        } else if (returnCode == CIM_ONLY_ALLOWED_ON_EMPTY_CG_CODE && methodName.equals(REMOVE_MEMBERS)) {
            // sometimes CIM call returns with code 45316 (This operation is
            // only allowed on an empty Consistency Group.)
            // it is a wrong error, also it appears that the volume is removed
            // from cg

            // throw exception, so that caller can send error if volumes are not deleted by checking CG members
            // after this call
            throw new Exception("Failed with return code: " + obj);
        } else if (returnCode != CIM_SUCCESS_CODE) {
            throw new Exception("Failed with return code: " + obj);
        }
    }

    /**
     * Construct input arguments for creating volumes.
     */
    public CIMArgument[] getCreateVolumesInputArguments(
            StorageSystem storageDevice, StoragePool pool, List<String> labels,
            Long capacity, int count, boolean isThinlyProvisioned) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            list.add(_cimArgument.stringArray(CP_ELEMENT_NAMES,
                    labels.toArray(new String[labels.size()])));
            // Use thick/thin volume type
            int volumeType = isThinlyProvisioned ? STORAGE_VOLUME_TYPE_THIN
                    : STORAGE_VOLUME_VALUE;
            list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));

            CIMProperty[] goalPropKeys = { _cimProperty.string(CP_INSTANCE_ID,
                    SYSTEM_BLOCK_SIZE) };
            CIMObjectPath goalPath = CimObjectPathCreator.createInstance(
                    DATA_TYPE_SETTING, Constants.IBM_NAMESPACE, goalPropKeys);
            list.add(_cimArgument.reference(CP_GOAL, goalPath));

            CIMProperty[] inPoolPropKeys = { _cimProperty.string(
                    CP_INSTANCE_ID, pool.getNativeId()) };

            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(
                    pool.getPoolClassName(), Constants.IBM_NAMESPACE,
                    inPoolPropKeys);
            list.add(_cimArgument.reference(CP_IN_POOL, inPoolPath));
            list.add(_cimArgument.uint32(CP_QUANTITY, count));
            list.add(_cimArgument.uint64(CP_SIZE, capacity));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: "
                    + storageDevice.getLabel());
        }

        return list.toArray(new CIMArgument[list.size()]);
    }

    /*
     * Construct input arguments for expanding volume.
     */
    public CIMArgument[] getExpandVolumeInputArguments(
            StorageSystem storageDevice, Volume volume, Long size) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(
                    storageDevice, volume);
            list.add(_cimArgument.reference(CP_THE_ELEMENT, volumePath));
            list.add(_cimArgument.uint64(CP_SIZE, size));
            CIMProperty[] goalPropKeys = { _cimProperty.string(CP_INSTANCE_ID,
                    SYSTEM_BLOCK_SIZE) };
            CIMObjectPath goalPath = CimObjectPathCreator.createInstance(
                    DATA_TYPE_SETTING, Constants.IBM_NAMESPACE, goalPropKeys);
            list.add(_cimArgument.reference(CP_GOAL, goalPath));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: "
                    + storageDevice.getLabel());
        }

        return list.toArray(new CIMArgument[list.size()]);
    }

    /**
     * Construct input arguments for deleting volumes.
     */
    public CIMArgument[] getDeleteVolumesInputArguments(
            StorageSystem storageDevice, String[] volumeNames) {
        CIMObjectPath[] volumePaths;
        try {
            volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNames);
        } catch (Exception e) {
            throw new IllegalStateException("Problem deleting volumes: "
                    + volumeNames.toString() + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] { _cimArgument.referenceArray(CP_THE_ELEMENTS,
                volumePaths) };
    }

    /**
     * This method is a wrapper for the getInstance. If the object is not found,
     * it returns a null value instead of throwing an exception.
     *
     * @param storage
     *            [required] - StorageSystem object to which an SMI-S connection
     *            would be made
     * @param objectPath
     *            [required]
     * @param propagated
     *            [required]
     * @param includeClassOrigin
     *            [required]
     * @return CIMInstance object that represents the existing object
     * @throws Exception
     */
    public CIMInstance checkExists(StorageSystem storage, Volume volume,
            boolean propagated, boolean includeClassOrigin) throws Exception {
        CIMInstance instance = null;
        CIMObjectPath objectPath = _cimPath.getBlockObjectPath(storage, volume);
        try {
            if (objectPath != null) {
                _log.debug(String
                        .format("checkExists(storage=%s, objectPath=%s, propagated=%s, includeClassOrigin=%s)",
                                storage.getSerialNumber(),
                                objectPath.toString(),
                                String.valueOf(propagated),
                                String.valueOf(includeClassOrigin)));
                instance = getInstance(storage, objectPath, propagated,
                        includeClassOrigin, null);
            }
        } catch (WBEMException e) {
            // If we get an error indicating the object is not found, then
            // it's okay, we want to return null for this method
            if (e.getID() != WBEMException.CIM_ERR_NOT_FOUND) {
                throw e;
            }
        } catch (Exception e) {
            _log.error("checkExists call encountered an exception", e);
            throw e;
        }
        return instance;
    }

    /**
     * Executes query
     *
     * @param storageSystem
     * @param query
     * @param queryLanguage
     * @return list of matched instances
     */
    public List<CIMInstance> executeQuery(StorageSystem storageSystem,
            CIMObjectPath objectPath, String query, String queryLanguage) {
        return _cimPath.executeQuery(storageSystem, objectPath, query, queryLanguage);
    }

    @SuppressWarnings("rawtypes")
    public void createHardwareIDCollection(StorageSystem storage, CIMObjectPath hwIdManagementSvc,
            String elementName, String[] initiators, CIMArgument[] outArgs)
            throws Exception {
        CIMArgument[] inArgs = getCreateHardwareIDCollectionInputArgs(elementName, initiators);
        invokeMethod(storage, hwIdManagementSvc,
                IBMSmisConstants.CREATE_HARDWARE_ID_COLLECTION, inArgs, outArgs);
    }

    /**
     * Construct input arguments for creating hardware Id collection.
     */
    @SuppressWarnings("rawtypes")
    private CIMArgument[] getCreateHardwareIDCollectionInputArgs(String elementName, String[] initiators)
            throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        if (elementName != null) {
            argsList.add(_cimArgument.string(CP_ELEMENT_NAME, elementName));
        }

        if (initiators != null && initiators.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_HARDWARE_IDS,
                    initiators));
        }

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    @SuppressWarnings("rawtypes")
    public void addHardwareIDsToCollection(StorageSystem storage,
            CIMObjectPath collectionPath, String[] initiators, CIMArgument[] outArgs)
            throws Exception {
        CIMArgument[] inArgs = getAddHardwareIDsToCollectionInputArgs(collectionPath, initiators);
        CIMObjectPath hwIdManagementSvc = _cimPath
                .getStorageHardwareIDManagementService(storage);
        invokeMethod(storage, hwIdManagementSvc,
                IBMSmisConstants.ADD_HARDWARE_IDS_TO_COLLECTION, inArgs, outArgs);
    }

    /**
     * Construct input arguments for adding storage hardware ID.
     */
    @SuppressWarnings("rawtypes")
    private CIMArgument[] getAddHardwareIDsToCollectionInputArgs(CIMObjectPath collectionPath, String[] initiators)
            throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.reference(CP_HARDWARE_ID_COLLECTION, collectionPath));
        argsList.add(_cimArgument.stringArray(CP_HARDWARE_IDS, initiators));

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    /**
     * Construct input arguments for exposing paths.
     */
    public CIMArgument[] getExposePathsInputArguments(VolumeURIHLU[] volumeURIHLUs,
            String[] initiators,
            CIMObjectPath protocolController) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
            String[] lunNames = new String[volumeURIHLUs.length];
            List<String> deviceNumbers = new ArrayList<String>();
            UnsignedInteger16[] deviceAccesses = new UnsignedInteger16[volumeURIHLUs.length];
            for (int i = 0; i < volumeURIHLUs.length; i++) {
                lunNames[i] = getBlockObjectAlternateName(volumeURIHLUs[i].getVolumeURI());
                String hlu = volumeURIHLUs[i].getHLU();
                // Add the HLU to the list only if it is non-null and not the
                // LUN_UNASSIGNED value (as a hex string).
                if (hlu != null &&
                        !hlu.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                    int hluDec = Integer.parseInt(hlu, 16);
                    if (hluDec > MAXIMUM_LUN) {
                        String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
                        _log.error(errMsg);
                        throw new Exception(errMsg);
                    }
                    deviceNumbers.add(Integer.toString(hluDec));
                }
                deviceAccesses[i] = READ_WRITE_UINT16;
            }
            argsList.add(_cimArgument.uint16Array(CP_DEVICE_ACCESSES, deviceAccesses));
            argsList.add(_cimArgument.stringArray(CP_LU_NAMES, lunNames));
            if (!deviceNumbers.isEmpty()) {
                String[] numbers = {};
                argsList.add(_cimArgument.stringArray(CP_DEVICE_NUMBERS, deviceNumbers.toArray(numbers)));
            }
        }

        if (initiators != null && initiators.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_INITIATOR_PORT_IDS, initiators));
        }

        if (protocolController != null) {
            argsList.add(_cimArgument.referenceArray(CP_PROTOCOL_CONTROLLERS, new CIMObjectPath[] { protocolController }));
        }

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    /**
     * Construct input arguments for exposing paths with given export mask.
     */
    public CIMArgument[] getExposePathsInputArguments(StorageSystem storage,
            URI exportMask,
            VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList) throws Exception {
        CIMObjectPath protocolController = _cimPath.getSCSIProtocolControllerPath(storage, getExportMaskNativeId(exportMask));
        return getExposePathsInputArguments(volumeURIHLUs, getInitiatorNames(initiatorList), protocolController);
    }

    /**
     * Construct input arguments for hiding paths.
     */
    public CIMArgument[] getHidePathsInputArguments(StorageSystem storage,
            ExportMask exportMask, String[] volumeNames, String[] initiatorPortIDs) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        if (volumeNames != null && volumeNames.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_LU_NAMES, volumeNames));
        }

        if (initiatorPortIDs != null && initiatorPortIDs.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_INITIATOR_PORT_IDS, initiatorPortIDs));
        }
        CIMObjectPath protocolController = _cimPath.getSCSIProtocolControllerPath(storage, exportMask.getNativeId());
        if (protocolController != null) {
            argsList.add(_cimArgument.referenceArray(CP_PROTOCOL_CONTROLLERS, new CIMObjectPath[] { protocolController }));
        }

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    /**
     * Construct input arguments for hiding paths.
     */
    public CIMArgument[] getHidePathsInputArguments(StorageSystem storage,
            URI exportMask,
            List<URI> volumeURIList,
            List<Initiator> initiatorList) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        String[] volumeNames = null;
        String[] initiatorPortIDs = null;
        if (volumeURIList != null && !volumeURIList.isEmpty()) {
            volumeNames = getBlockObjectAlternateNames(volumeURIList);
            argsList.add(_cimArgument.stringArray(CP_LU_NAMES, volumeNames));
        }
        if (initiatorList != null && !initiatorList.isEmpty()) {
            initiatorPortIDs = getInitiatorNames(initiatorList);
            argsList.add(_cimArgument.stringArray(CP_INITIATOR_PORT_IDS, initiatorPortIDs));
        }
        CIMObjectPath protocolController = _cimPath.getSCSIProtocolControllerPath(storage, getExportMaskNativeId(exportMask));
        if (protocolController != null) {
            argsList.add(_cimArgument.referenceArray(CP_PROTOCOL_CONTROLLERS, new CIMObjectPath[] { protocolController }));
        }

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    /**
     * Construct input arguments for deleting protocol controller.
     */
    public CIMArgument[] getDeleteProtocolControllerInputArguments(CIMObjectPath protocolController) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.reference(CP_PROTOCOL_CONTROLLER, protocolController));

        return argsList.toArray(new CIMArgument[argsList.size()]);
    }

    /**
     * Construct input arguments for deleting storage hardware ID.
     */
    public CIMArgument[] getDeleteStorageHardwareIDInputArgs(StorageSystem storage, CIMObjectPath
            hwIdPath)
            throws Exception {
        return new CIMArgument[] {
                _cimArgument.reference(CP_HARDWARE_ID, hwIdPath) };
    }

    /**
     * Returns a CloseableIterator for SCSIProtocolController
     * CIMInstance objects.
     *
     * @param storage
     *            [in] - StorageSystem object. Used to look up SMIS connection.
     * @return CloseableIterator of CIMInstance objects
     * @throws Exception
     */
    public CloseableIterator<CIMInstance> getSCSIProtocolControllers(
            StorageSystem storage) throws Exception {
        return getInstances(storage, Constants.IBM_NAMESPACE, CP_SCSI_PROTOCOL_CONTROLLER, true, false, false,
                PS_CNTRL_NAME_AND_ID);
    }

    /*
     * Return protocol controller instance represented by the export mask.
     */
    public CIMInstance getSCSIProtocolController(StorageSystem storage,
            ExportMask exportMask)
            throws Exception {
        return getInstance(storage,
                _cimPath.getSCSIProtocolControllerPath(storage, exportMask.getNativeId()),
                false, true, null);
    }

    /**
     * Returns a map of the volume WWNs to their HLU values for a masking
     * container on the array.
     *
     * @param storage
     *            [in] - StorageSystem that the masking belongs to
     * @param controllerPath
     *            [in] - CIMObjectPath of IBMTSDS_SCSIProtocolController, holding a representation
     *            of an array masking container.
     * @return - a map of the volume WWNs to their HLU values for an instance of
     *         LunMasking container on the array.
     */
    public Map<String, Integer> getVolumesFromScsiProtocolController(StorageSystem storage,
            CIMObjectPath controllerPath) {
        Map<String, Integer> wwnToHLU = new HashMap<String, Integer>();
        CloseableIterator<CIMInstance> iterator = null;
        CloseableIterator<CIMInstance> protocolControllerForUnitIter = null;
        try {
            Map<String, Integer> deviceIdToHLU = new HashMap<String, Integer>();
            WBEMClient client = getConnection(storage).getCimClient();
            protocolControllerForUnitIter =
                    client.referenceInstances(controllerPath,
                            CIM_PROTOCOL_CONTROLLER_FOR_UNIT, null, false,
                            PS_DEVICE_NUMBER);
            while (protocolControllerForUnitIter.hasNext()) {
                CIMInstance pcu = protocolControllerForUnitIter.next();
                CIMObjectPath pcuPath = pcu.getObjectPath();
                CIMProperty<CIMObjectPath> dependentVolumePropery =
                        (CIMProperty<CIMObjectPath>) pcuPath.getKey(CP_DEPENDENT);
                CIMObjectPath dependentVolumePath = dependentVolumePropery.getValue();
                String deviceId = dependentVolumePath.getKey(CP_DEVICE_ID).getValue()
                        .toString();
                String deviceNumber = CIMPropertyFactory.getPropertyValue(pcu,
                        CP_DEVICE_NUMBER);
                Integer decimalHLU = (int) Long.parseLong(deviceNumber, 16);
                deviceIdToHLU.put(deviceId, decimalHLU);
            }
            iterator = client.associatorInstances(controllerPath, null,
                    CP_STORAGE_VOLUME, null, null, false, PS_NAME);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String deviceId = cimInstance.getObjectPath().getKey(CP_DEVICE_ID)
                        .getValue().toString();
                String wwn = CIMPropertyFactory.getPropertyValue(cimInstance,
                        CP_NAME);
                wwnToHLU.put(wwn.toUpperCase(), deviceIdToHLU.get(deviceId));
            }
        } catch (WBEMException we) {
            _log.error("Caught an error will attempting to get volume list from " +
                    "masking instance", we);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            if (protocolControllerForUnitIter != null) {
                protocolControllerForUnitIter.close();
            }
        }
        return wwnToHLU;
    }

    /**
     * Returns a map of normalized port name to port path for the masking.
     *
     * @param storage
     *            [in] - StorageSystem that the masking belongs to
     * @param controllerPath
     *            [in] - CIMObjectPath of IBMTSDS_SCSIProtocolController, holding a representation
     *            of an array masking container.
     * @return - a map of port name to port path for the container.
     */
    public Map<String, CIMObjectPath> getInitiatorsFromScsiProtocolController(StorageSystem storage,
            CIMObjectPath controllerPath) {
        Map<String, CIMObjectPath> initiatorPortPaths = new HashMap<String, CIMObjectPath>();
        CloseableIterator<CIMInstance> iterator = null;
        try {
            WBEMClient client = getConnection(storage).getCimClient();
            iterator = client.associatorInstances(controllerPath, CP_SHWID_TO_SPC,
                    CP_STORAGE_HARDWARE_ID, null, null, false, PS_STORAGE_ID);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String initiator = CIMPropertyFactory.getPropertyValue(cimInstance,
                        CP_STORAGE_ID);
                initiatorPortPaths.put(Initiator.normalizePort(initiator), cimInstance.getObjectPath());
            }
        } catch (WBEMException we) {
            _log.error("Caught an error while attempting to get initiator list from " +
                    "masking instance", we);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return initiatorPortPaths;
    }

    /**
     * Given a CIMInstance of a IBMTSDS_SCSIProtocolController return a list of storage ports that
     * it references.
     *
     * @param storage
     *            [in] - StorageSystem that the masking belongs to
     * @param controllerPath
     *            [in] - CIMObjectPath of IBMTSDS_SCSIProtocolController, holding a representation
     *            of an array masking container.
     * @return List of port name String values. The WWNs will have colons separating the hex digits.
     */
    public List<String> getStoragePortsFromScsiProtocolController(StorageSystem storage,
            CIMObjectPath controllerPath) {
        List<String> storagePorts = new ArrayList<String>();
        CloseableIterator<CIMObjectPath> iterator = null;
        try {
            WBEMClient client = getConnection(storage).getCimClient();
            iterator = client.associatorNames(controllerPath, null,
                    CIM_PROTOCOL_ENDPOINT, null, null);
            while (iterator.hasNext()) {
                CIMObjectPath endpointPath = iterator.next();
                String portName = endpointPath.getKeyValue(CP_NAME).toString();
                String fixedName = Initiator.toPortNetworkId(portName);
                storagePorts.add(fixedName);
            }
        } catch (WBEMException we) {
            _log.error("Caught an error while attempting to get storage ports from " +
                    "masking instance", we);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return storagePorts;
    }

    /*
     * Return associated instances.
     */
    public CloseableIterator<CIMInstance> getAssociatorInstances(
            StorageSystem storageDevice, CIMObjectPath path, String assocClass,
            String resultClass, String role, String resultRole, String[] prop)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().associatorInstances(
                path, null, resultClass, null, null, false, prop);
    }
    
    public CloseableIterator<CIMInstance> getReferenceInstances(
            StorageSystem storageDevice, CIMObjectPath path, String resultClass, String role, String[] prop)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().referenceInstances(path, resultClass, role, false, prop);
    }

    /*
     * Return CIM instance if there is one, otherwise return null.
     */
    public CIMInstance checkExists(StorageSystem storage,
            CIMObjectPath objectPath,
            boolean propagated,
            boolean includeClassOrigin) throws Exception {
        return checkExists(storage, objectPath, propagated, includeClassOrigin, null);
    }

    /**
     * Wrapper for the getInstance. If the object is not found, it returns a null
     * value instead of throwing an exception.
     *
     * @param storage
     *            [required] - StorageSystem object to which an SMI-S connection would be made
     * @param objectPath
     *            [required]
     * @param propagated
     *            [required]
     * @param includeClassOrigin
     *            [required]
     * @param propertyList
     *            - An array of property names used to filter what is contained in the instances
     *            returned.
     * @return CIMInstance object that represents the existing object
     * @throws Exception
     */
    private CIMInstance checkExists(StorageSystem storage,
            CIMObjectPath objectPath,
            boolean propagated,
            boolean includeClassOrigin, String[] propertyList) throws Exception {
        CIMInstance instance = null;
        try {
            if (objectPath != null) {
                _log.debug(String.format("checkExists(storage=%s, objectPath=%s, propagated=%s, includeClassOrigin=%s)",
                        storage.getSerialNumber(), objectPath.toString(),
                        String.valueOf(propagated), String.valueOf(includeClassOrigin)));
                instance = getInstance(storage, objectPath, propagated, includeClassOrigin, propertyList);
            }
        } catch (WBEMException e) {
            // If we get an error indicating the object is not found, then
            // it's okay, we want to return null for this method
            if (e.getID() != WBEMException.CIM_ERR_NOT_FOUND) {
                throw e;
            }
        } catch (Exception e) {
            _log.error("checkExists call encountered an exception", e);
            throw e;
        }
        return instance;
    }

    /**
     * Wrapper for the WBEMClient enumerateInstances method.
     *
     * @param storage
     *            - StorageArray reference, will be used to lookup SMI-S connection
     * @param namespace
     *            - Namespace to use
     * @param className
     *            - Name of the class on the provider to query
     * @param deep
     *            - If true, this specifies that, for each returned Instance of the Class, all
     *            properties of the Instance must be present (subject to constraints imposed by the
     *            other parameters), including any which were added by subclassing the specified
     *            Class. If false, each returned Instance includes only properties defined for the
     *            specified Class in path.
     * @param localOnly
     *            - If true, only elements values that were instantiated in the instance is
     *            returned.
     * @param includeClassOrigin
     *            - The class origin attribute is the name of the class that first defined the
     *            property. If true, the class origin attribute will be present for each property on
     *            all returned CIMInstances. If false, the class origin will not be present.
     * @param propertyList
     *            - An array of property names used to filter what is contained in the instances
     *            returned. Each instance returned only contains elements for the properties of the
     *            names specified. Duplicate and invalid property names are ignored and the request
     *            is otherwise processed normally. An empty array indicates that no properties
     *            should be returned. A null value indicates that all properties should be returned.
     * @return - CloseableIterator of CIMInstance values representing the instances of the specified
     *         class.
     * @throws Exception
     */
    private CloseableIterator<CIMInstance> getInstances(StorageSystem storage,
            String namespace,
            String className,
            boolean deep,
            boolean localOnly,
            boolean includeClassOrigin,
            String[] propertyList)
            throws Exception {
        CloseableIterator<CIMInstance> cimInstances;
        CimConnection connection = _cimConnection.getConnection(storage);
        WBEMClient client = connection.getCimClient();
        String classKey = namespace + className;
        CIMObjectPath cimObjectPath =
                CIM_OBJECT_PATH_HASH_MAP.get(classKey);
        if (cimObjectPath == null) {
            cimObjectPath = CimObjectPathCreator.createInstance(className, namespace);
            CIM_OBJECT_PATH_HASH_MAP.putIfAbsent(classKey, cimObjectPath);
        }
        cimInstances = client.enumerateInstances(cimObjectPath, deep, localOnly,
                includeClassOrigin, propertyList);
        return cimInstances;
    }

    /*
     * Return names of initiator instances.
     */
    public String[] getInitiatorNames(List<Initiator> initiatorList) throws Exception {
        List<String> initiatorNameList = new ArrayList<String>();
        if (initiatorList != null && !initiatorList.isEmpty()) {
            for (Initiator initiator : initiatorList) {
                initiatorNameList.add(Initiator.normalizePort(initiator
                        .getInitiatorPort()));
            }
        }

        return initiatorNameList.toArray(new String[initiatorNameList.size()]);
    }

    /*
     * Return mapping of initiator name and initiator instance.
     */
    public Map<String, Initiator> getInitiatorMap(List<Initiator> initiatorList) throws Exception {
        Map<String, Initiator> initiatorMap = new HashMap<String, Initiator>();
        if (initiatorList != null && !initiatorList.isEmpty()) {
            for (Initiator initiator : initiatorList) {
                initiatorMap.put(Initiator.normalizePort(initiator
                        .getInitiatorPort()), initiator);
            }
        }

        return initiatorMap;
    }

    /*
     * Return CIM instance of the given object path.
     */
    public CIMInstance getInstance(StorageSystem storage,
            CIMObjectPath objectPath, boolean propagated,
            boolean includeClassOrigin, String[] propertyList) throws Exception {
        CimConnection connection = _cimConnection.getConnection(storage);
        WBEMClient client = connection.getCimClient();

        // CTRL-9069 workaround CIM_BAD_REQUEST error
        CIMInstance instance = null;
        int retryCount = 0;
        while (true) {
            try {
                _log.info("Calling getInstance, attempt {}", retryCount);
                instance = client.getInstance(objectPath, propagated, includeClassOrigin, propertyList);
            } catch (WBEMException e) {
                if (CIM_BAD_REQUEST.equals(e.getMessage())) {
                    if (retryCount < CIM_MAX_RETRY_COUNT) {
                        _log.warn("Encountered 'request-not-well-formed' error. Retry...");
                        retryCount++;

                        try {
                            Thread.sleep(CIM_RETRY_WAIT_INTERVAL);
                        } catch (InterruptedException ie) {
                            _log.warn("Thread: " + Thread.currentThread().getName() + " interrupted.");
                            throw e;
                        }

                        continue;
                    }

                    _log.warn("Exhausted {} retries", CIM_MAX_RETRY_COUNT);
                }

                // other WBEMException, or reach the max retry count
                throw e;
            }

            // no exception
            break;
        }

        return instance;
    }

    /**
     * Loop through the URI list and return a list of nativeIds for each of the
     * BlockObject objects to which the URI applies.
     *
     * @param uris
     *            - Collection of URIs
     * @return Returns a list of nativeId String values
     * @throws Exception
     */
    public String[] getBlockObjectAlternateNames(Collection<URI> uris) throws Exception {
        String[] results = {};
        Set<String> names = new HashSet<String>();
        for (URI uri : uris) {
            names.add(getBlockObjectAlternateName(uri));
        }
        return names.toArray(results);
    }

    /**
     * This method will take a URI and return alternateName for the BlockObject object to which the
     * URI applies.
     *
     * @param uri
     *            - URI
     * @return Returns a nativeId String value
     * @throws DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri
     *             if URI is not a Volume/BlockSnapshot URI
     */
    private String getBlockObjectAlternateName(URI uri) throws Exception {
        String nativeId;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            nativeId = volume.getAlternateName();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            nativeId = blockSnapshot.getAlternateName();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
            nativeId = blockMirror.getAlternateName();
        } else {
            throw DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return nativeId;
    }

    /*
     * Returns native ID of the export mask.
     */
    private String getExportMaskNativeId(URI exportMaskURI) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                exportMaskURI);
        return exportMask.getNativeId();
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getCreateElementReplicaSnapInputArguments(
            StorageSystem storageDevice, Volume volume, boolean createInactive,
            String label) {
        return getCreateElementReplicaInputArguments(storageDevice, volume,
                null, createInactive, label, SNAPSHOT_VALUE);
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getCreateElementReplicaInputArguments(
            StorageSystem storageDevice, Volume volume, StoragePool pool,
            boolean createInactive, String label, int syncType) {
        int waitForCopyState = (createInactive) ? INACTIVE_VALUE
                : ACTIVATE_VALUE;
        CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice,
                volume);
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, syncType));
        args.add(_cimArgument.reference(CP_SOURCE_ELEMENT, volumePath));
        if (waitForCopyState == ACTIVATE_VALUE) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE,
                    waitForCopyState));
        }

        if (pool != null) {
            addTargetPoolToArgs(storageDevice, pool, args);
        }
        return args.toArray(new CIMArgument[] {});
    }

    @SuppressWarnings("rawtypes")
    private void addTargetPoolToArgs(StorageSystem storageSystem,
            StoragePool pool, List<CIMArgument> args) {
        CIMProperty[] inPoolPropKeys = { _cimProperty.string(CP_INSTANCE_ID,
                pool.getNativeId()) };
        CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(
                pool.getPoolClassName(),
                _cimConnection.getNamespace(storageSystem), inPoolPropKeys);
        args.add(_cimArgument.reference(CP_TARGET_POOL, inPoolPath));
    }

    public String getConsistencyGroupName(BlockObject bo, StorageSystem storageSystem) {
        if (bo.getConsistencyGroup() == null) {
            return null;
        }
        final BlockConsistencyGroup group =
                _dbClient.queryObject(BlockConsistencyGroup.class, bo.getConsistencyGroup());
        return getConsistencyGroupName(group, storageSystem);
    }

    public String getConsistencyGroupName(final BlockConsistencyGroup group,
            final StorageSystem storageSystem) {
        String groupName = null;

        if (group != null && storageSystem != null) {
            groupName = group.getCgNameOnStorageSystem(storageSystem.getId());
        }

        return groupName;
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getDeleteSnapshotSynchronousInputArguments(
            CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, DELETE_SNAPSHOT),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath) };
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getCreateGroupReplicaInputArguments(
            StorageSystem storage, CIMObjectPath cgPath,
            boolean createInactive, String label) {
        final CIMArgument[] basicArgs = new CIMArgument[] {
                _cimArgument.uint16("Consistency", 3),
                _cimArgument.uint16("Mode", 3),
                _cimArgument.uint16(CP_SYNC_TYPE, SNAPSHOT_VALUE),
                _cimArgument.reference(CP_SOURCE_GROUP, cgPath) };
        final List<CIMArgument> args = new ArrayList<CIMArgument>(
                Arrays.asList(basicArgs));
        // If active, add the RelationshipName
        if (!createInactive) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, ACTIVATE_VALUE));
            args.add(_cimArgument.string(RELATIONSHIP_NAME, label));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getReturnGroupSyncToPoolInputArguments(
            CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RETURN_TO_RESOURCE_POOL),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath) };
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getDeleteListSynchronizationInputArguments(
            StorageSystem storage, CIMObjectPath[] syncObjectPaths) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RETURN_TO_RESOURCE_POOL),
                _cimArgument
                        .referenceArray(CP_SYNCHRONIZATION, syncObjectPaths) };
    }

    /**
     * Convenience method that wraps SMI-S replication service operation
     *
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param methodName
     *            [required] - String of method name
     * @param inArgs
     *            [required] - CIMArgument array containing operation's
     *            arguments
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    public void callReplicationSvc(StorageSystem storage, String methodName,
            CIMArgument[] inArgs, CIMArgument[] outArgs) throws Exception {
        CIMObjectPath replicationSvcPath = _cimPath
                .getReplicationSvcPath(storage);
        invokeMethod(storage, replicationSvcPath, methodName, inArgs, outArgs);
    }

    /**
     * Convenience method that wraps SMI-S ModifyReplicatSynchronization
     * operation
     *
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param inArgs
     *            [required] - CIMArgument array containing operation's
     *            arguments
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    public void callModifyReplica(StorageSystem storage, CIMArgument[] inArgs)
            throws Exception {
        callReplicationSvc(storage, MODIFY_REPLICA_SYNCHRONIZATION, inArgs,
                new CIMArgument[5]);
    }

    /**
     * This method will loop through the URI list and return a list of nativeIds
     * for each of the BlockObject objects to which the URI applies.
     *
     * @param uris
     *            - Collection of URIs
     * @return Returns a list of nativeId String values
     * @throws DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri
     *             f URI is not a Volume/BlockSnapshot URI
     */
    public String[] getBlockObjectNativeIds(Collection<URI> uris)
            throws Exception {
        String[] results = {};
        Set<String> nativeIds = new HashSet<String>();
        for (URI uri : uris) {
            String nativeId;
            if (URIUtil.isType(uri, Volume.class)) {
                Volume volume = _dbClient.queryObject(Volume.class, uri);
                nativeId = volume.getNativeId();
            } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
                BlockSnapshot blockSnapshot = _dbClient.queryObject(
                        BlockSnapshot.class, uri);
                nativeId = blockSnapshot.getNativeId();
            } else if (URIUtil.isType(uri, BlockMirror.class)) {
                BlockMirror blockMirror = _dbClient.queryObject(
                        BlockMirror.class, uri);
                nativeId = blockMirror.getAlternateName();
            } else {
                throw DeviceControllerException.exceptions
                        .notAVolumeOrBlocksnapshotUri(uri);
            }
            nativeIds.add(nativeId);
        }
        return nativeIds.toArray(results);
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getRestoreFromSnapshotInputArguments(
            CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true) };
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getCloneInputArguments(String label,
            CIMObjectPath sourceVolumePath, StorageSystem storageDevice,
            StoragePool pool, boolean createInactive) {
        int waitForCopyState = (createInactive) ? PREPARED_VALUE
                : ACTIVATE_VALUE;
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        args.add(_cimArgument.reference(CP_SOURCE_ELEMENT, sourceVolumePath));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, CLONE_VALUE));

        if (waitForCopyState == ACTIVATE_VALUE) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE,
                    waitForCopyState));
        }

        if (pool != null) {
            addTargetPoolToArgs(storageDevice, pool, args);
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    @SuppressWarnings("rawtypes")
    private CIMArgument[] getAddMembersInputArguments(CIMObjectPath cgPath,
            CIMObjectPath[] volumePaths) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths),
                _cimArgument.reference(CP_REPLICATION_GROUP, cgPath) };
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getRemoveMembersInputArguments(CIMObjectPath cgPath,
            CIMObjectPath[] volumePaths) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths),
                _cimArgument.reference(CP_REPLICATION_GROUP, cgPath),
                _cimArgument.bool(CP_DELETE_ON_EMPTY_ELEMENT, true) };
    }

    /**
     * Wrapper for WBEM.associatorNames routine
     *
     * @param storageDevice
     *            [required]
     * @param path
     *            [required]
     * @param assocClass
     *            [optional] - assocClass - This string MUST either contain a
     *            valid CIM Association class name or be null. It filters the
     *            Objects returned to contain only Objects associated to the
     *            source Object via this CIM Association class or one of its
     *            subclasses.
     * @param resultClass
     *            [optional] - This string MUST either contain a valid CIM Class
     *            name or be null. It filters the Objects returned to contain
     *            only the Objects of this Class name or one of its subclasses.
     * @param role
     *            [optional] - role - This string MUST either contain a valid
     *            Property name or be null. It filters the Objects returned to
     *            contain only Objects associated to the source Object via an
     *            Association class in which the source Object plays the
     *            specified role. (i.e. the Property name in the Association
     *            class that refers to the source Object matches this value) If
     *            "Antecedent" is specified, then only Associations in which the
     *            source Object is the "Antecedent" reference are examined.
     * @param resultRole
     *            [optional] - This string MUST either contain a valid Property
     *            name or be null. It filters the Objects returned to contain
     *            only Objects associated to the source Object via an
     *            Association class in which the Object returned plays the
     *            specified role. (i.e. the Property name in the Association
     *            class that refers to the Object returned matches this value)
     *            If "Dependent" is specified, then only Associations in which
     *            the Object returned is the "Dependent" reference are examined.
     * @return CloseableIterator - iterator that can be used to enumerate the
     *         associatorNames
     * @throws WBEMException
     */
    public CloseableIterator<CIMObjectPath> getAssociatorNames(
            StorageSystem storageDevice, CIMObjectPath path, String assocClass,
            String resultClass, String role, String resultRole)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().associatorNames(
                path, assocClass, resultClass, role, resultRole);
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getCreateReplicationGroupInputArguments(StorageSystem storage,
            String groupName, List<URI> blockObjects) throws Exception {
        String[] blockObjectNames = getBlockObjectNativeIds(blockObjects);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storage,
                blockObjectNames);
        return new CIMArgument[] { _cimArgument
                .string(CP_GROUP_NAME, groupName), _cimArgument.referenceArray(CP_MEMBERS, members) };
    }

    @SuppressWarnings("rawtypes")
    public CIMArgument[] getDeleteReplicationGroupInputArguments(
            StorageSystem storage, CIMObjectPath groupPath) {
        return new CIMArgument[] {
                _cimArgument.reference(CP_REPLICATION_GROUP, groupPath),
                _cimArgument.bool(CP_REMOVE_ELEMENTS, true) };
    }

    /**
     * Convenience method that wraps SMI-S AddMembers operation
     *
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param blockObjects
     *            [required] - list of block object URIs
     * @param cgPath
     *            [required] - CIMObjectPath of CG group
     * @throws Exception
     */
    public void addVolumesToConsistencyGroup(StorageSystem storage,
            final List<URI> blockObjects, CIMObjectPath cgPath)
            throws Exception {
        String[] blockObjectNames = getBlockObjectNativeIds(blockObjects);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storage,
                blockObjectNames);
        @SuppressWarnings("rawtypes")
        CIMArgument[] addMembersInput = getAddMembersInputArguments(cgPath,
                members);
        callReplicationSvc(storage, ADD_MEMBERS, addMembersInput,
                new CIMArgument[5]);
    }

    /*
     * Returns null if CG doesn't exist, or members of the CG
     */
    public Set<String> getCGMembers(StorageSystem storage, CIMObjectPath cgPath) throws Exception {
        Set<String> members = new HashSet<String>();

        CIMInstance cgPathInstance = checkExists(storage, cgPath, false, false);
        if (cgPathInstance == null) {
            return null;
        }

        CloseableIterator<CIMObjectPath> assocVolNamesIter = null;
        try {
            assocVolNamesIter = getAssociatorNames(storage, cgPath,
                    null, IBMSmisConstants.CIM_STORAGE_VOLUME, null, null);
            while (assocVolNamesIter.hasNext()) {
                CIMObjectPath assocVolPath = assocVolNamesIter.next();
                String deviceId = assocVolPath.getKeyValue(IBMSmisConstants.CP_DEVICE_ID).toString();
                members.add(deviceId); // may have a timing issue, sometimes vol is returned, but is gone from CG
            }
        } finally {
            if (assocVolNamesIter != null) {
                assocVolNamesIter.close();
            }
        }

        return members;
    }

    /*
     * Get snapshot group members. Note the members may not be available.
     *
     * Called from IBMSmisSynchSubTaskJob
     */
    public List<CIMObjectPath> getSGMembers(StorageSystem storageDevice,
            String sgName) throws WBEMException {
        CimConnection connection = getConnection(storageDevice);
        WBEMClient client = connection.getCimClient();
        CloseableIterator<CIMObjectPath> syncVolumeIter = null;
        List<CIMObjectPath> objectPaths = new ArrayList<CIMObjectPath>();
        try {
            // find out the real object path for the SG
            // workaround for provider bug (CTRL-8947, provider doesn't keep InstanceID of snapshot group constant)
            CIMObjectPath sgPath = _cimPath.getSnapshotGroupPath(storageDevice, sgName);
            if (sgPath == null) {
                return objectPaths;
            }

            syncVolumeIter = client.associatorNames(sgPath,
                    IBMSmisConstants.CP_SNAPSHOT_GROUP_TO_ORDERED_MEMBERS,
                    IBMSmisConstants.CP_STORAGE_VOLUME, null, null);
            while (syncVolumeIter.hasNext()) {
                CIMObjectPath syncVolumePath = syncVolumeIter.next();
                objectPaths.add(syncVolumePath);
                if (_log.isDebugEnabled()) {
                    _log.debug("syncVolumePath - " + syncVolumePath);
                }
            }
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
        }

        return objectPaths;
    }

    /*
     * Use IBMSmisSynchSubTaskJob to check removed CG members
     */
    public Set<String> getCGMembers(StorageSystem storageSystem,
            CIMObjectPath cgPath, Set<String> removedMembers)
            throws SmisException {
        IBMSmisSynchSubTaskJob job = new IBMSmisSynchSubTaskJob(
                cgPath, storageSystem, IBMSmisSynchSubTaskJob.JobName.GetRemovedCGMembers,
                null, 0, removedMembers);
        waitForSmisJob(storageSystem, job, POLL_CYCLE_LIMIT);
        return job.getCGMembers();
    }

    /*
     * Use IBMSmisSynchSubTaskJob to get snapshot members
     */
    public List<CIMObjectPath> getSGMembers(StorageSystem storageSystem,
            CIMObjectPath replicationGroupPath, String sgName, int expectedObjCount)
            throws SmisException {
        IBMSmisSynchSubTaskJob job = new IBMSmisSynchSubTaskJob(
                replicationGroupPath, storageSystem, IBMSmisSynchSubTaskJob.JobName.GetNewSGMembers,
                sgName, expectedObjCount, null);
        waitForSmisJob(storageSystem, job, POLL_CYCLE_LIMIT);
        return job.getSGMemberPaths();
    }

    private JobStatus waitForSmisJob(StorageSystem storageDevice,
            SmisJob job, int pollCycleLimit) throws SmisException {
        JobStatus status = JobStatus.IN_PROGRESS;
        JobContext jobContext = new JobContext(_dbClient, _cimConnection, null,
                null, null, null, null, this);
        long startTime = System.currentTimeMillis();
        int pollCycleCount = 0;
        while (true) {
            JobPollResult result = job.poll(jobContext, SYNC_WRAPPER_WAIT);
            pollCycleCount++;
            if (result.getJobStatus().equals(JobStatus.IN_PROGRESS)) {
                if (pollCycleCount > pollCycleLimit) {
                    throw new SmisException(
                            "Reached maximum number of poll " + pollCycleLimit);
                } else if (System.currentTimeMillis() - startTime > SYNC_WRAPPER_TIME_OUT) {
                    throw new SmisException(
                            "Timed out waiting on smis job to complete after "
                                    + (System.currentTimeMillis() - startTime)
                                    + " milliseconds");
                } else {
                    try {
                        Thread.sleep(SYNC_WRAPPER_WAIT);
                    } catch (InterruptedException e) {
                        _log.error("Thread waiting for smis job to complete was interrupted and will be resumed");
                    }
                }
            } else {
                status = result.getJobStatus();
                if (!status.equals(JobStatus.SUCCESS)) {
                    throw new SmisException("Smis job failed: "
                            + result.getErrorDescription());
                }
                break;
            }
        }

        return status;
    }

    public CIMObjectPath[] getGroupSyncObjectPaths(StorageSystem storage,
            CIMObjectPath cgPath) throws WBEMException {
        CimConnection connection = getConnection(storage);
        WBEMClient client = connection.getCimClient();
        CloseableIterator<CIMObjectPath> groupSyncIter = null;
        List<CIMObjectPath> objPaths = new ArrayList<CIMObjectPath>();
        try {
            groupSyncIter = client.referenceNames(cgPath,
                    CP_GROUP_SYNCHRONIZED, CP_SYSTEM_ELEMENT);
            while (groupSyncIter.hasNext()) {
                objPaths.add(groupSyncIter.next());
            }
        } finally {
            if (groupSyncIter != null) {
                groupSyncIter.close();
            }
        }

        return objPaths.toArray(new CIMObjectPath[objPaths.size()]);
    }

    public String getReplicationGroupName(CIMObjectPath replicationGroupPath) {
        String instanceId = (String) replicationGroupPath.getKey(
                IBMSmisConstants.CP_INSTANCE_ID).getValue();
        return instanceId.substring(instanceId.lastIndexOf("-") + 1);
    }

    public void setTag(DataObject object, String scope, String label) {
        if (label == null) { // shouldn't happen
            label = "";
        }

        ScopedLabel newScopedLabel = new ScopedLabel(scope, label);
        ScopedLabelSet tagSet = object.getTag();
        if (tagSet == null) {
            tagSet = new ScopedLabelSet();
            tagSet.add(newScopedLabel);
            object.setTag(tagSet);
        } else if (tagSet.contains(newScopedLabel)) {
            return;
        } else {
            removeLabel(tagSet, scope);
            tagSet.add(newScopedLabel);
        }

        _dbClient.persistObject(object);
    }

    public void unsetTag(DataObject object, String scope) {
        ScopedLabelSet tagSet = object.getTag();
        if (tagSet == null) {
            return;
        }

        removeLabel(tagSet, scope);
        _dbClient.persistObject(object);
    }

    private void removeLabel(ScopedLabelSet tagSet, String scope) {
        ScopedLabel oldScopedLabel = null;
        Iterator<ScopedLabel> itr = tagSet.iterator();
        while (itr.hasNext()) {
            ScopedLabel scopedLabel = itr.next();
            if (scope.equals(scopedLabel.getScope())) {
                oldScopedLabel = scopedLabel;
                break;
            }
        }

        if (oldScopedLabel != null) {
            tagSet.remove(oldScopedLabel);
        }
    }
}

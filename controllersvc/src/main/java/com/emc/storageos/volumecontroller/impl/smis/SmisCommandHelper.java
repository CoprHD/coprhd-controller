/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.google.common.collect.Collections2.transform;
import static java.util.Arrays.asList;
import static javax.cim.CIMDataType.STRING_T;
import static javax.cim.CIMDataType.UINT16_T;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.cim.UnsignedInteger32;
import javax.cim.UnsignedInteger64;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.LinkStatus;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.ExportMaskNameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StorageGroupPolicyLimitsParam;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy.IG_TYPE;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisJob;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

import sun.net.util.IPAddressUtil;

/**
 * Helper for Smis commands
 */
public class SmisCommandHelper implements SmisConstants {
    private static final int GET_VOLUMES_BAD_HLU_RETRY_SLEEP_MSEC = 10000;
    private static final int GET_VOLUMES_BAD_HLU_RETRIES = 20;
    private static final Logger _log = LoggerFactory.getLogger(SmisCommandHelper.class);
    public static final ConcurrentHashMap<String, CIMObjectPath> CIM_OBJECT_PATH_HASH_MAP = new ConcurrentHashMap<String, CIMObjectPath>();
    public static final String VMAX_PREFIX = "vmax";
    public static final String VNX_PREFIX = "vnx";
    private static final int SYNC_WRAPPER_WAIT = 5000;
    private static final Long DEFAULT_SYNC_WRAPPER_TIME_OUT = 12000000L; // set to 200 minutes to handle striped meta volumes with
                                                               // BCV helper
                                                               // expansion (it may take long time)
    private static final String SYNC_WRAPPER_TIMEOUT_WRAPPER_CONFIG_KEY = "controller_sync_wrapper_timeout_minutes";
    private static final String EMC_IS_BOUND = "EMCIsBound";
    private static final int MAX_REFRESH_LOCK_WAIT_TIME = 300;
    private static final long REFRESH_THRESHOLD = 120000;
    private static final CIMObjectPath _cop = CimObjectPathCreator.createInstance(
            Constants.PROFILECLASS, CimConstants.DFLT_CIM_CONNECTION_INTEROP_NS);
    public static final int PARKING_SLO_SG_LOCK_WAIT_SECS = 3600;
    public static final UnsignedInteger32 SINGLE_DEVICE_FOR_EACH_CONFIG = new UnsignedInteger32(1);
    CIMArgumentFactory _cimArgument = null;
    CIMPropertyFactory _cimProperty = null;
    CIMObjectPathFactory _cimPath = null;
    CIMConnectionFactory _cimConnection = null;
    DbClient _dbClient = null;
    ControllerLockingService _locker;
    private ExportMaskNameGenerator _nameGenerator;
    private CoordinatorClient _coordinator;

    public void setCimArgumentFactory(CIMArgumentFactory cimArgumentFactory) {
        _cimArgument = cimArgumentFactory;
    }

    public void setCimPropertyFactory(CIMPropertyFactory cimPropertyFactory) {
        _cimProperty = cimPropertyFactory;
    }

    public void setCimConnectionFactory(CIMConnectionFactory connectionFactory) {
        _cimConnection = connectionFactory;
    }

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        _locker = locker;
    }

    public void setNameGenerator(ExportMaskNameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public boolean checkConnectionliveness(StorageSystem storageDevice) {
        boolean isLive = false;
        try {
            CimConnection connection = getConnection(storageDevice);
            WBEMClient client = connection.getCimClient();
            // Call the provider to get computer systems.
            client.enumerateInstanceNames(_cop);
            isLive = true;
        } catch (Exception wbemEx) {
            _log.error("Invalid connection found for Provider: {}", storageDevice.getActiveProviderURI());
        }
        return isLive;
    }

    public Object invokeMethod(StorageSystem storageDevice, CIMObjectPath objectPath,
            String methodName, CIMArgument[] inArgs,
            CIMArgument[] outArgs) throws WBEMException {
        CimConnection connection = getConnection(storageDevice);
        WBEMClient client = connection.getCimClient();
        int index = 0;
        StringBuilder inputInfoBuffer = new StringBuilder();
        inputInfoBuffer.append("\nSMI-S Provider: ").append(connection.getHost()).append(" -- Attempting invokeMethod ").append(methodName)
                .append(" on\n").append("  objectPath=").append(objectPath.toString()).append(" with arguments: \n");
        if (inArgs != null) {
            for (CIMArgument arg : inArgs) {
                if (arg != null) {
                    inputInfoBuffer.append("    inArg[").append(index++).append("]=").append(arg.toString()).append('\n');
                }
            }
        }

        InvokeTestFailure.internalOnlyInvokeSmisTestFailure(methodName, InvokeTestFailure.ARTIFICIAL_FAILURE_015);
        _log.info(inputInfoBuffer.toString());
        long start = System.nanoTime();
        Object obj = client.invokeMethod(objectPath, methodName, inArgs, outArgs);
        String total = String.format("%2.6f", ((System.nanoTime() - start) / 1000000000.0));
        String str = protectedToString(obj);
        StringBuilder outputInfoBuffer = new StringBuilder();
        outputInfoBuffer.append("\nSMI-S Provider: ").append(connection.getHost()).append(" -- Completed invokeMethod ").append(methodName)
                .append(" on\n").append("  objectPath=").append(objectPath.toString()).append("\n  Returned: ").append(str)
                .append(" with output arguments: \n");
        for (CIMArgument arg : outArgs) {
            if (arg != null) {
                str = protectedToString(arg);
                outputInfoBuffer.append("    outArg=").append(str).append('\n');
            }
        }
        outputInfoBuffer.append("  Execution time: ").append(total).append(" seconds.\n");
        _log.info(outputInfoBuffer.toString());
        return obj;
    }

    public void deleteMaskingGroup(StorageSystem storage, String groupName,
            SmisCommandHelper.MASKING_GROUP_TYPE groupType) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage,
                groupName, groupType);
        deleteMaskingGroup(storage, maskingGroupPath, groupType,
                groupName);
    }

    public void deleteMaskingGroup(
            StorageSystem storage, CIMObjectPath maskingGroupPath,
            SmisCommandHelper.MASKING_GROUP_TYPE groupType, String groupName) throws Exception {
        _log.debug("{} Delete {} Masking Group START...", storage.getSerialNumber(),
                groupType.name());
        _log.info("{} Deleting Masking Group {}", maskingGroupPath, groupName);
        CIMInstance maskingGroupInstance = null;
        try {
            maskingGroupInstance = getInstance(storage, maskingGroupPath, false,
                    false, new String[] {});
        } catch (WBEMException we) {
            _log.debug("{} Problem when trying to get masking group...",
                    storage.getSerialNumber(), we);
            if (we.getID() != WBEMException.CIM_ERR_NOT_FOUND) {
                throw we;
            }
        }
        if (maskingGroupInstance == null) {
            _log.info("{} Masking group already deleted ...", storage.getSerialNumber());
            return;
        }
        CIMArgument[] inArgs = getDeleteMaskingGroupInputArguments(storage,
                groupName, groupType);
        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage), "DeleteGroup", inArgs, outArgs, null);
            _log.debug("{} Delete {} Masking Group END...", storage.getSystemType(),
                    groupType.name());
        } catch (WBEMException we) {
            _log.debug("{} Problem when trying to delete masking group ...",
                    storage.getSerialNumber(), we);
            if (we.getID() != WBEMException.CIM_ERR_NOT_FOUND) {
                throw we;
            } else {
                _log.debug("{} Masking group already deleted ...",
                        storage.getSerialNumber());
            }
        }
    }

    /**
     * Makes a call to SMIS provider and returns the response back to the caller. If the SMIS call
     * is a asynchronous, it waits for the SMIS job to complete before returning. This is done to
     * allow callers to make consecutive asynchronous calls without the need for a workflow.
     * <em>This function should be used for asynchronous SMIS calls only and will throw an exception
     * if the call did not return a job path.</em>
     *
     * @param inArgs
     *            input arguments
     * @param outArgs
     *            output arguments
     * @param job
     *            for handling special cases of intermediate and final cim job results. Null should
     *            be used when no special handling is needed.
     * @return the return value of the method invocation
     * @throws WBEMException
     *             when a error occurs in the SMIS provider
     * @throws SmisException
     *             when an error occurs while waiting for the asyn job to complete or if the smis
     *             call did not return a job.
     */
    public Object invokeMethodSynchronously(StorageSystem storageDevice,
            CIMObjectPath objectPath, String methodName, CIMArgument[] inArgs,
            CIMArgument[] outArgs, SmisJob job) throws WBEMException,
            SmisException {
        Object obj = invokeMethod(storageDevice, objectPath, methodName, inArgs, outArgs);
        CIMObjectPath cimJobPath = _cimPath
                .getCimObjectPathFromOutputArgs(outArgs, "Job");
        // if this is an async call, wait for the job to complete
        if (cimJobPath != null) {
            try {
                waitForAsyncSmisJob(storageDevice, cimJobPath, job);
            } catch (Exception ex) {
                _log.error(
                        "Exception occurred while waiting on async job {} to complete",
                        cimJobPath);
                if (ex instanceof SmisException) {
                    throw (SmisException) ex;
                } else {
                    throw new SmisException(
                            "Exception occurred while waiting on async "
                                    + "job to complete.",
                            ex);
                }
            }
        } else {
            throw new SmisException(MessageFormat.format(
                    "No job was created for method {0}, object {1}, on storage device {2}",
                    methodName, objectPath.getObjectName(),
                    storageDevice.getLabel()));
        }
        return obj;
    }

    private void waitForAsyncSmisJob(StorageSystem storageDevice,
            CIMObjectPath cimJobPath, SmisJob job) throws SmisException {
        if (job == null) {
            TaskCompleter taskCompleter = new TaskCompleter() {
                @Override
                public void ready(DbClient dbClient) throws DeviceControllerException {
                }

                @Override
                public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
                }

                @Override
                protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
                }
            };
            job = new SmisJob(cimJobPath, storageDevice.getId(), taskCompleter, "");
        } else {
            job.setCimJob(cimJobPath);
        }
        JobContext jobContext = new JobContext(_dbClient, _cimConnection, null, null, null, null, this);
        long startTime = System.currentTimeMillis();
        Long sync_wrapper_time_out = InvokeTestFailure.internalOnlyOverrideSyncWrapperTimeOut(DEFAULT_SYNC_WRAPPER_TIME_OUT);

        boolean usesConfigTimeoutValue = false;
        if (StorageSystem.Type.vmax.name().equalsIgnoreCase(storageDevice.getSystemType())) {
            Long syncWrapperTimeoutInInMilliSeconds = ControllerUtils.MINUTE_TO_MILLISECONDS * Long
                    .valueOf(ControllerUtils.getPropertyValueFromCoordinator(_coordinator, SYNC_WRAPPER_TIMEOUT_WRAPPER_CONFIG_KEY));
            usesConfigTimeoutValue = true;
            sync_wrapper_time_out = InvokeTestFailure.internalOnlyOverrideSyncWrapperTimeOut(syncWrapperTimeoutInInMilliSeconds);
            _log.info("SMIS job Sync timeout in ms: {}", sync_wrapper_time_out);
        }
        while (true) {
            JobPollResult result = job.poll(jobContext, SYNC_WRAPPER_WAIT);
            if (!result.isJobInTerminalState()) {
                if (System.currentTimeMillis() - startTime > sync_wrapper_time_out) {
                    String timeoutMessage = "Timed out waiting on smis job to complete after " +
                            (System.currentTimeMillis() - startTime) + " milliseconds.";
                    if (usesConfigTimeoutValue) {
                        timeoutMessage = timeoutMessage
                                + " Please retry this operation after increasing the value of 'Task timeout for VMAX SMIS job in minutes' in the configuration: System > General Configuration > Controller section.";
                    }
                    throw new SmisException(timeoutMessage);
                } else {
                    try {
                        Thread.sleep(SYNC_WRAPPER_WAIT);
                    } catch (InterruptedException e) {
                        _log.error("Thread waiting for smis job to complete was interrupted and "
                                + "will be resumed");
                    }
                }
            } else {
                if (result.isJobInTerminalFailedState()) {
                    throw new SmisException(
                            "Smis job failed: " + result.getErrorDescription());
                }
                break;
            }
        }
    }

    /**
     * Executes query for component of a given storage system (volume, pool, etc...)
     *
     * @param storageSystem
     * @param query
     * @param queryLanguage
     * @return
     * @throws WBEMException
     */
    public List<CIMInstance> executeQuery(StorageSystem storageSystem, String query, String queryLanguage) throws WBEMException {

        CloseableIterator<CIMInstance> iterator = null;
        CimConnection connection = _cimConnection.getConnection(storageSystem);
        WBEMClient client = connection.getCimClient();
        CIMObjectPath objectPath = _cimPath.getStorageSystem(storageSystem);
        _log.info(String.format("Executing query: %s, objectPath: %s, query language: %s", query, objectPath, queryLanguage));
        List<CIMInstance> instanceList = new ArrayList<CIMInstance>();
        try {
            iterator = client.execQuery(objectPath, query, queryLanguage);
            while (iterator.hasNext()) {
                CIMInstance instance = iterator.next();
                instanceList.add(instance);
            }

        } catch (WBEMException we) {
            _log.error("Caught an error will attempting to execute query and process query result. Query: " + query, we);
        } finally {
            closeCIMIterator(iterator);
        }

        return instanceList;
    }

    /**
     * remove Volumes from Storage Group.
     *
     * @param storage
     *            the storage
     * @param groupName
     *            the group name
     * @param volumeURIList
     *            the volume uri list
     * @param forceFlag
     *            the force flag
     * @throws Exception
     */
    public void removeVolumesFromStorageGroup(
            StorageSystem storage, String groupName, List<URI> volumeURIList,
            boolean forceFlag) throws Exception {
        _log.info(
                "{} removeVolume from Storage group {} START...",
                storage.getSerialNumber(), groupName);
        CIMArgument[] inArgs = getRemoveVolumesFromMaskingGroupInputArguments(
                storage, groupName, volumeURIList, forceFlag);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "RemoveMembers", inArgs, outArgs, null);
        _log.info(
                "{} removeVolume from Storage group {} END...",
                storage.getSerialNumber(), groupName);
    }

    /**
     * Removes the volume from storage groups if the volume is not in any Masking View.
     * This will be called just before deleting the volume (for VMAX2).
     *
     * @param storageSystem
     *            the storage system
     * @param volume
     *            the volume
     */
    public void removeVolumeFromStorageGroupsIfVolumeIsNotInAnyMV(StorageSystem storage, BlockObject bo) {
        /**
         * If Volume is not associated with any MV, then remove the volume from its associated SGs.
         */
        CloseableIterator<CIMObjectPath> mvPathItr = null;
        CloseableIterator<CIMInstance> sgInstancesItr = null;
        boolean isSGInAnyMV = true;
        try {
            _log.info("Checking if device {} needs to be removed from Storage Groups which is not in any Masking View",
                    bo.getNativeGuid());
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storage, bo);
            // See if Volume is associated with MV
            mvPathItr = getAssociatorNames(storage, volumePath, null, SYMM_LUN_MASKING_VIEW,
                    null, null);
            if (!mvPathItr.hasNext()) {
                isSGInAnyMV = false;
            }

            if (!isSGInAnyMV) {
                _log.info("Device {} is not in any Masking View, hence removing it from Storage Groups if any",
                        bo.getNativeGuid());
                boolean forceFlag = ExportUtils.useEMCForceFlag(_dbClient, bo.getId());
                // Get all the storage groups associated with this volume
                sgInstancesItr = getAssociatorInstances(storage, volumePath, null,
                        SmisConstants.SE_DEVICE_MASKING_GROUP, null, null, PS_ELEMENT_NAME);
                while (sgInstancesItr.hasNext()) {
                    CIMInstance sgPath = sgInstancesItr.next();
                    String storageGroupName = CIMPropertyFactory.getPropertyValue(sgPath, SmisConstants.CP_ELEMENT_NAME);
                    // Double check: check if SG is part of MV
                    if (!checkStorageGroupInAnyMaskingView(storage, sgPath.getObjectPath())) {
                        // if last volume, dis-associate FAST
                        int sgVolumeCount = getVMAXStorageGroupVolumeCount(storage, storageGroupName);
                        if (sgVolumeCount == 1) {
                            WBEMClient client = getConnection(storage).getCimClient();
                            removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, sgPath.getObjectPath());
                        }

                        removeVolumesFromStorageGroup(storage, storageGroupName, Collections.singletonList(bo.getId()), forceFlag);

                        // If there was only one volume in the SG, it would be empty after removing that last volume.
                        if (sgVolumeCount == 1) {
                            _log.info("Deleting Empty Storage Group {}", storageGroupName);
                            deleteMaskingGroup(storage, storageGroupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                        }
                    }
                }
            } else {
                _log.info("Found that Device {} is part of Masking View {}", bo.getNativeGuid(), mvPathItr.next());
            }
        } catch (Exception e) {
            _log.warn("Exception while trying to remove device {} from Storage Groups which is not in any Masking View",
                    bo.getNativeGuid(), e);
        } finally {
            closeCIMIterator(mvPathItr);
            closeCIMIterator(sgInstancesItr);
        }
    }

    public void removeVolumeGroupFromAutoTieringPolicy(
            StorageSystem storage, CIMObjectPath volumeGroupPath)
            throws Exception {
        _log.debug("{} removeVolumeGroupFromAutoTierPolicy START...",
                storage.getSerialNumber());
        String policyName = getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage, volumeGroupPath);
        if (isFastPolicy(policyName)) {
            _log.debug("disassociate {} from fast policy: {}", volumeGroupPath, policyName);
            CIMObjectPath[] volumeGroupPaths = new CIMObjectPath[] { volumeGroupPath };
            CIMArgument[] inArgs = getRemoveVolumeGroupFromTierInputArguments(
                    storage, policyName, volumeGroupPaths);
            CIMArgument[] outArgs = new CIMArgument[5];
            invokeMethod(storage, _cimPath.getTierPolicySvcPath(storage),
                    "ModifyStorageTierPolicyRule", inArgs, outArgs);
        }
        _log.debug("{} removeVolumeGroupFromAutoTierPolicy END...",
                storage.getSerialNumber());
    }

    public CimConnection getConnection(StorageSystem storageDevice) {
        return _cimConnection.getConnection(storageDevice);
    }

    public CIMArgument[] getCreateElementReplicaSnapInputArguments(
            StorageSystem storageDevice, Volume volume, boolean createInactive,
            String label) {
        return getCreateElementReplicaInputArguments(storageDevice, volume,
                null, createInactive, label, SNAPSHOT_VALUE);
    }

    public CIMArgument[] getCreateElementReplicaVPSnapInputArguments(StorageSystem storageDevice, Volume volume,
            boolean createInactive, String label,
            CIMInstance replicationSetting) {
        CIMArgument[] baseArgs = getCreateElementReplicaInputArguments(storageDevice, volume, null, createInactive,
                label, SNAPSHOT_VALUE);
        List<CIMArgument> args = new ArrayList<>(Arrays.asList(baseArgs));
        args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, replicationSetting));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getCreateElementReplicaSnapInputArgumentsWithTargetAndSetting(
            StorageSystem storageDevice, Volume volume, String targetId, CIMInstance replicaSettingData,
            boolean createInactive, String label) {
        CIMArgument[] baseArguments = getCreateElementReplicaInputArguments(storageDevice, volume,
                null, createInactive, label, SNAPSHOT_VALUE);
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.addAll(Arrays.asList(baseArguments));
        args.add(_cimArgument.reference(CP_TARGET_ELEMENT, _cimPath.getVolumePath(storageDevice, targetId)));
        args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, replicaSettingData));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getCreateElementReplicaMirrorInputArguments(StorageSystem storageDevice, Volume volume,
            StoragePool pool, boolean createInactive, String label, CIMObjectPath volumeGroupPath, CIMInstance replicaSettingData) {
        CIMArgument[] baseArguments = getCreateElementReplicaInputArguments(storageDevice, volume, pool, createInactive,
                label, MIRROR_VALUE);

        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.addAll(Arrays.asList(baseArguments));
        if (volumeGroupPath != null) {
            args.add(_cimArgument.referenceArray(CP_COLLECTIONS, new CIMObjectPath[] { volumeGroupPath }));
        }
        args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, replicaSettingData));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getCreateElementReplicaMirrorInputArguments(StorageSystem storageDevice, BlockObject volume,
            StoragePool pool, boolean createInactive,
            String label) {
        return getCreateElementReplicaInputArguments(storageDevice, volume, pool, createInactive,
                label, MIRROR_VALUE);
    }

    public CIMArgument[] getCreateElementReplicaInputArguments(
            StorageSystem storageDevice, BlockObject volume, StoragePool pool, boolean createInactive,
            String label, int syncType) {
        int waitForCopyState = (createInactive) ? INACTIVE_VALUE : ACTIVATE_VALUE;
        CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, volume);
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, syncType));
        args.add(_cimArgument.reference(CP_SOURCE_ELEMENT, volumePath));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState));
        if (pool != null) {
            addTargetPoolToArgs(storageDevice, pool, args);
        }
        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getFractureMirrorInputArgumentsWithCopyState(CIMObjectPath storageSync,
            Boolean sync) {
        int operation = (sync != null && sync) ? SPLIT_VALUE : FRACTURE_VALUE;
        int copyState = (operation == SPLIT_VALUE) ? SPLIT : FRACTURED;
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, operation),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, copyState),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getFractureMirrorInputArguments(CIMObjectPath storageSync,
            Boolean sync) {
        int FRACTURE_OR_SPLIT = (sync != null && sync) ? SPLIT_VALUE : FRACTURE_VALUE;
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FRACTURE_OR_SPLIT),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getFailoverSyncPairInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FAILOVER_SYNC_PAIR),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync) };
    }

    public CIMArgument[] getFailoverSyncPairInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FAILOVER_SYNC_PAIR),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getFractureInputArguments(Collection<CIMObjectPath> syncPaths,
            Boolean sync) {
        int FRACTURE_OR_SPLIT = (sync != null && sync) ? SPLIT_VALUE : FRACTURE_VALUE;
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FRACTURE_OR_SPLIT),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getDetachSRDFInputArguments(CIMObjectPath syncPath) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncPath)
        };
    }

    public CIMArgument[] getDetachSRDFInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getAddSyncPairInputArguments(CIMObjectPath groupSync,
            Object settings, CIMObjectPath... syncPairs) {
        List<CIMArgument> args = new ArrayList<>();

        args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, settings));
        args.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        args.add(_cimArgument.uint16(CP_OPERATION, ADD_SYNC_PAIR));
        args.add(_cimArgument.bool(CP_FORCE, true));
        args.add(_cimArgument.reference(CP_SYNCHRONIZATION, groupSync));
        args.add(_cimArgument.referenceArray(CP_SYNCPAIR, syncPairs));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getRemoveSyncPairInputArguments(CIMObjectPath storageSync, CIMObjectPath syncPair,
            Object repSettingInstance) {
        CIMObjectPath[] paths = new CIMObjectPath[] { syncPair };
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, REMOVE_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync),
                _cimArgument.referenceArray(CP_SYNCPAIR, paths),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance) };
    }

    public CIMArgument[] getSyncPairFailBackInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FAILBACK_SYNC_PAIR),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync) };
    }

    public CIMArgument[] getSRDFPauseLinkInputArguments(Collection<CIMObjectPath> syncPaths, Object repSettingInstance,
            boolean sync) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        args.add(_cimArgument.uint16(CP_OPERATION, (sync ? FRACTURE_VALUE : SUSPEND_SYNC_PAIR)));
        args.add(_cimArgument.bool(CP_FORCE, true));
        args.add(_cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})));
        args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getSRDFActivePauseLinkInputArguments(Collection<CIMObjectPath> syncPaths, Object repSettingInstance,
            boolean sync) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SUSPENDED));
        args.add(_cimArgument.uint16(CP_OPERATION, (sync ? FRACTURE_VALUE : SUSPEND_SYNC_PAIR)));
        args.add(_cimArgument.bool(CP_FORCE, true));
        args.add(_cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})));
        args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance));

        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getSRDFSplitInputArguments(Collection<CIMObjectPath> syncPaths, Object repSettingInstance) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FRACTURE_VALUE),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})),
                _cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance) };
    }

    public CIMArgument[] getSRDFPauseGroupInputArguments(CIMObjectPath storageSync, Object repSettingInstance,
            boolean sync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, (sync ? SPLIT_VALUE : SUSPEND_SYNC_PAIR)),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync),
                _cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance)
        };
    }

    public CIMArgument[] getSwapPairSuspendInputArguments(Collection<CIMObjectPath> syncPaths, Object repSettingInstance) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, SWAP_SYNC_PAIR),
                _cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})) };
    }

    public CIMArgument[] getASyncPairFailBackInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FAILBACK_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getSwapPairSuspendInputArguments(CIMObjectPath storageSync, Object repSettingInstance) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, SWAP_SYNC_PAIR),
                _cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync) };
    }

    public CIMArgument[] getSyncPairResumeInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync) };
    }

    public CIMArgument[] getASyncPairFailBackInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, FAILBACK_SYNC_PAIR),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})) };
    }

    public CIMArgument[] getASyncPairSuspendInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, SUSPEND_SYNC_PAIR),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getActivePairSuspendInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, SUSPEND_SYNC_PAIR),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SUSPENDED),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getASyncSwapInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, SWAP_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getSyncPairResumeInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {})) };
    }

    public CIMArgument[] getASyncPairResumeInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getActivePairResumeInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getSRDFProtectionInputArguments(CIMObjectPath volume) {
        return new CIMArgument[] { _cimArgument.uint16(CP_ACCESS, READ_WRITE_DISABLED),
                _cimArgument.uint16(CP_ELEMENTTYPE, ELEMENT_TYPE),
                _cimArgument.reference(CP_ELEMENT, volume) };
    }

    public CIMArgument[] getReSyncASyncPairResumeInputArguments(CIMObjectPath groupSync) {
        return new CIMArgument[] { _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESYNC_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, groupSync) };
    }

    public CIMArgument[] getDetachMirrorInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResumeSynchronizationInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResetToAdaptiveCopyModeInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESET_TO_ADAPTIVE_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResetToSyncCopyModeInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESET_TO_SYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResetToAsyncCopyModeInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESET_TO_ASYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getActivateConsistencyInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, ACTIVATE_CONSISTENCY_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResumeSnapshotSynchronizationInputArguments(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getResumeSyncListInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getResumeActiveListInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED),
                _cimArgument.uint16(CP_OPERATION, RESUME_SYNC_PAIR),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getResumeSynchronizationInputArgumentsWithCopyState(CIMObjectPath storageSync) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESYNC_VALUE),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED),
                _cimArgument.reference(CP_SYNCHRONIZATION, storageSync)
        };
    }

    public CIMArgument[] getDetachSynchronizationInputArguments(StorageSystem storage, Volume volume) {
        CIMObjectPath syncObject;
        try {
            syncObject = _cimPath.getSyncObject(storage, volume);
        } catch (Exception e) {
            throw new IllegalArgumentException("Problem getting input arguments");
        }
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObject)
        };
    }

    public CIMArgument[] getDetachSynchronizationInputArguments(CIMObjectPath syncObject) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObject)
        };
    }

    public CIMArgument[] getDeleteMirrorInputArguments(StorageSystem storage, CIMObjectPath mirrorPath) {
        return new CIMArgument[] {
                _cimArgument.reference(CP_THE_ELEMENT, mirrorPath)
        };
    }

    /**
     * Create input arguments for ReturnElementsToStoragePool operation
     *
     * @param theElements
     *            Array of path to be returned to the storage pool
     * @param options
     *            Additional options: 2 (Continue on nonexistent element), 3 (Return error on
     *            nonexistent element)
     * @return An array of CIMArgument based on the given parameters
     */
    public CIMArgument[] getReturnElementsToStoragePoolArguments(CIMObjectPath[] theElements, int options) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_THE_ELEMENTS, theElements),
                _cimArgument.uint16(CP_OPTIONS, options)
        };
    }

    public CIMArgument[] getReturnElementsToStoragePoolArguments(CIMObjectPath[] theElements) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_THE_ELEMENTS, theElements)
        };
    }

    public CIMArgument[] getVNXCopyToTargetGroupInputArguments(CIMObjectPath settingsState,
            CIMObjectPath targetGroup) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, COPY_TO_TARGET_VALUE),
                _cimArgument.reference(CP_SETTINGS_STATE, settingsState),
                _cimArgument.reference(CP_TARGET_GROUP, targetGroup)
        };
    }

    public CIMArgument[] getVNXCopyToTargetInputArguments(CIMObjectPath settingsState,
            CIMObjectPath target) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, COPY_TO_TARGET_VALUE),
                _cimArgument.reference(CP_SETTINGS_STATE, settingsState),
                _cimArgument.reference(CP_TARGET_ELEMENT, target)
        };
    }

    public CIMArgument[] getActivateSnapshotInputArguments(StorageSystem storageSystem, BlockSnapshot snapshot) {
        CIMObjectPath syncObject;
        try {
            syncObject = _cimPath.getSyncObject(storageSystem, snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        argsList.add(_cimArgument.uint16(CP_OPERATION, ACTIVATE_VALUE));
        argsList.add(_cimArgument.reference(CP_SYNCHRONIZATION, syncObject));
        // Set a flag that will allow the database synchronization set to be skipped, so
        // that the activate runs faster, to meet our 10 second window.
        argsList.add(_cimArgument.bool(CP_EMC_SKIP_REFRESH, true));
        CIMArgument[] result = {};
        return argsList.toArray(result);
    }

    public CIMArgument[] getActivateGroupSnapshotInputArguments(StorageSystem storageSystem, CIMObjectPath cgPath) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        argsList.add(_cimArgument.uint16(CP_OPERATION, ACTIVATE_VALUE));
        argsList.add(_cimArgument.reference(CP_SYNCHRONIZATION, cgPath));
        argsList.add(_cimArgument.bool(CP_EMC_SKIP_REFRESH, true));
        CIMArgument[] result = {};
        return argsList.toArray(result);
    }

    public CIMArgument[] getCreateSynchronizationAspectInput(CIMObjectPath cgPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SKIP_REFRESH, true),
                _cimArgument.uint16(CP_SYNC_TYPE, SNAPSHOT_VALUE),
                _cimArgument.reference(CP_SOURCE_ELEMENT, cgPath)
        };
    }

    public CIMArgument[] getCreateGroupSynchronizationAspectInput(CIMObjectPath cgPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SKIP_REFRESH, true),
                _cimArgument.uint16(CP_SYNC_TYPE, SNAPSHOT_VALUE),
                _cimArgument.reference(CP_SOURCE_GROUP, cgPath)
        };
    }

    public CIMArgument[] getCreateMetaVolumesInputArguments(StorageSystem storageDevice, StoragePool pool, String label, Long capacity,
            int count,
            boolean isThinlyProvisioned, String metaType, Integer metaMemberCount, CIMInstance poolSetting) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            CIMProperty[] inPoolPropKeys = {
                    _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageDevice, pool.getNativeId()))
            };
            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                    _cimConnection.getNamespace(storageDevice),
                    inPoolPropKeys);
            CIMObjectPath[] inPoolPaths = new CIMObjectPath[] { inPoolPath };

            // Use thick/thin volume type
            int volumeType = isThinlyProvisioned ? THIN_STORAGE_VOLUME : STORAGE_VOLUME_VALUE;
            list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));
            list.add(_cimArgument.referenceArray(CP_EMC_IN_POOLS, inPoolPaths));
            list.add(_cimArgument.uint64(CP_SIZE, capacity));
            if (count > 1) {
                list.add(_cimArgument.uint32(CP_EMC_NUMBER_OF_DEVICES, count));
            }
            if (label != null) {
                list.add(_cimArgument.string(CP_ELEMENT_NAME, label));
            }
            // Set composite type
            int compositeType = metaType.equalsIgnoreCase(Volume.CompositionType.STRIPED.toString()) ? STRIPED_META_VOLUME_TYPE
                    : CONCATENATED_META_VOLUME_TYPE;
            list.add(_cimArgument.uint16(CP_COMPOSITE_TYPE, compositeType));

            // Set number of members
            list.add(_cimArgument.uint32(CP_EMCNUMBEROFMEMBERS, metaMemberCount));

            if (poolSetting != null) {
                list.add(_cimArgument.reference(CP_GOAL, poolSetting.getObjectPath()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: " + storageDevice.getSerialNumber());
        }

        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public List<CIMArgument> getCreateVolumesInputArgumentsasList(StorageSystem storageDevice, StoragePool pool,
            List<String> labels,
            Long capacity,
            int count,
            boolean isThinlyProvisioned) {
        if (storageDevice.getUsingSmis80()) {
            return getCreateVolumesInputArgumentsasList80(storageDevice, pool, labels, capacity, count,
                    isThinlyProvisioned);
        } else {
            return getCreateVolumesInputArgumentsasList40(storageDevice, pool, labels.get(0), capacity, count,
                    isThinlyProvisioned);
        }
    }

    public List<CIMArgument> getCreateVolumesInputArgumentsasList40(StorageSystem storageDevice, StoragePool pool,
            String label,
            Long capacity,
            int count,
            boolean isThinlyProvisioned) {
        ArrayList<CIMArgument> list = new ArrayList<>();
        try {
            CIMProperty[] inPoolPropKeys = {
                    _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageDevice, pool.getNativeId()))
            };
            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                    _cimConnection.getNamespace(storageDevice),
                    inPoolPropKeys);
            if (!storageDevice.checkIfVmax3()) {
                // Use thick/thin volume type
                int volumeType = isThinlyProvisioned ? STORAGE_VOLUME_TYPE_THIN : STORAGE_VOLUME_VALUE;
                list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));
            }
            // Adding Goal parameter if volumes need to be FAST Enabled
            list.add(_cimArgument.reference(CP_IN_POOL, inPoolPath));
            list.add(_cimArgument.uint64(CP_SIZE, capacity));
            list.add(_cimArgument.uint32(CP_EMC_NUMBER_OF_DEVICES, count));
            if (label != null) {
                list.add(_cimArgument.string(CP_ELEMENT_NAME, label));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: " + storageDevice.getSerialNumber());
        }
        return list;
    }

    public List<CIMArgument> getCreateVolumesInputArgumentsasList80(StorageSystem storageDevice, StoragePool pool,
            List<String> labels,
            Long capacity,
            int count,
            boolean isThinlyProvisioned) {
        ArrayList<CIMArgument> list = new ArrayList<>();
        try {
            CIMProperty[] inPoolPropKeys = {
                    _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageDevice, pool.getNativeId()))
            };
            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                    _cimConnection.getNamespace(storageDevice),
                    inPoolPropKeys);

            // Use thick/thin volume type
            Integer volumeType = isThinlyProvisioned ? STORAGE_VOLUME_TYPE_THIN : STORAGE_VOLUME_VALUE;

            // Create array values

            // Adding Goal parameter if volumes need to be FAST Enabled
            list.add(_cimArgument.referenceArray(CP_IN_POOL,
                    toMultiElementArray(count, inPoolPath)));
            list.add(_cimArgument.uint64Array(CP_SIZE,
                    toMultiElementArray(count, new UnsignedInteger64(Long.toString(capacity)))));
            list.add(_cimArgument.uint32Array(CP_EMC_NUMBER_OF_DEVICE_FOR_EACH_CONFIG,
                    toMultiElementArray(count, SINGLE_DEVICE_FOR_EACH_CONFIG)));

            if (labels != null) {
                String[] labelsArray = labels.toArray(new String[] {}); // Convert labels to array
                list.add(_cimArgument.stringArray(CP_ELEMENT_NAMES, labelsArray));
            }
            // Add CIMArgument instances to the list
            if (!storageDevice.checkIfVmax3()) {
                list.add(_cimArgument.uint16Array(CP_ELEMENT_TYPE,
                        toMultiElementArray(count, new UnsignedInteger16(volumeType))));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: " + storageDevice.getSerialNumber());
        }
        return list;
    }

    public CIMArgument[] getCreateVolumesBasedOnSettingInputArguments(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMInstance storageSetting, String label, int count, long capacity) {
        if (storage.getUsingSmis80()) {
            return getCreateVolumesBasedOnSettingInputArguments80(storage, poolPath, storageSetting, label, count,
                    capacity);
        } else {
            return getCreateVolumesBasedOnSettingInputArguments40(storage, poolPath, storageSetting, label, count,
                    capacity);
        }
    }

    public CIMArgument[] getCreateVolumesBasedOnSettingInputArguments40(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMInstance storageSetting, String label, int count, long capacity) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_ELEMENT_TYPE, STORAGE_VOLUME_VALUE),
                _cimArgument.uint32(CP_EMC_NUMBER_OF_DEVICES, count),
                _cimArgument.uint64(CP_SIZE, capacity),
                _cimArgument.reference(CP_IN_POOL, poolPath),
                _cimArgument.reference(CP_GOAL, storageSetting.getObjectPath()) };
    }

    public CIMArgument[] getCreateVolumesBasedOnSettingInputArguments80(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMInstance storageSetting, String label, int count, long capacity) {
        return new CIMArgument[] {
                _cimArgument.uint16Array(CP_ELEMENT_TYPE,
                        toMultiElementArray(count, new UnsignedInteger16(STORAGE_VOLUME_VALUE))),
                _cimArgument.uint32Array(CP_EMC_NUMBER_OF_DEVICE_FOR_EACH_CONFIG,
                        toMultiElementArray(count, SINGLE_DEVICE_FOR_EACH_CONFIG)),
                _cimArgument.uint64Array(CP_SIZE,
                        toMultiElementArray(count, new UnsignedInteger64(Long.toString(capacity)))),
                _cimArgument.referenceArray(CP_IN_POOL,
                        toMultiElementArray(count, poolPath)),
                _cimArgument.referenceArray(CP_GOAL,
                        toMultiElementArray(count, storageSetting.getObjectPath()))
        };
    }

    public CIMArgument[] getCreateVolumesBasedOnVolumeGroupInputArguments(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMObjectPath volumeGroupPath, String label, int count, long capacity) {
        if (storage.getUsingSmis80() != null && storage.getUsingSmis80()) {
            return getCreateVolumesBasedOnVolumeGroupInputArguments80(storage, poolPath, volumeGroupPath, label, count,
                    capacity);
        } else {
            return getCreateVolumesBasedOnVolumeGroupInputArguments40(storage, poolPath, volumeGroupPath, label, count,
                    capacity);
        }
    }

    public CIMArgument[] getCreateVolumesBasedOnVolumeGroupInputArguments40(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMObjectPath volumeGroupPath, String label, int count, long capacity) {

        List<CIMArgument> list = new ArrayList<>();
        list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, STORAGE_VOLUME_VALUE));
        if (label != null) {
            list.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        }
        list.add(_cimArgument.uint32(CP_EMC_NUMBER_OF_DEVICES, count));
        list.add(_cimArgument.uint64(CP_SIZE, capacity));
        list.add(_cimArgument.reference(CP_IN_POOL, poolPath));
        if (volumeGroupPath != null) {
            list.add(_cimArgument.referenceArray(CP_EMC_COLLECTIONS, toMultiElementArray(count, volumeGroupPath)));
        }
        return list.toArray(new CIMArgument[list.size()]);
    }

    public CIMArgument[] getCreateVolumesBasedOnVolumeGroupInputArguments80(
            StorageSystem storage, CIMObjectPath poolPath,
            CIMObjectPath volumeGroupPath, String label, int count, long capacity) {

        List<CIMArgument> list = new ArrayList<>();

        list.add(_cimArgument.uint16Array(CP_ELEMENT_TYPE,
                toMultiElementArray(count, new UnsignedInteger16(STORAGE_VOLUME_VALUE))));
        list.add(_cimArgument.uint32Array(CP_EMC_NUMBER_OF_DEVICE_FOR_EACH_CONFIG,
                toMultiElementArray(count, SINGLE_DEVICE_FOR_EACH_CONFIG)));
        list.add(_cimArgument.uint64Array(CP_SIZE,
                toMultiElementArray(count, new UnsignedInteger64(Long.toString(capacity)))));
        list.add(_cimArgument.referenceArray(CP_IN_POOL,
                toMultiElementArray(count, poolPath)));
        if (volumeGroupPath != null) {
            list.add(_cimArgument.referenceArray(CP_EMC_COLLECTIONS, toMultiElementArray(count, volumeGroupPath)));
        }

        if (label != null) {
            list.add(_cimArgument.stringArray(CP_ELEMENT_NAMES,
                    toMultiElementArray(count, label)));
        }
        return list.toArray(new CIMArgument[list.size()]);
    }

    public CIMArgument[] getCreateVolumesInputArguments(StorageSystem storageDevice, StoragePool pool, String label,
            Long capacity,
            int count,
            boolean isThinlyProvisioned,
            CIMInstance poolSetting,
            boolean isBoundToPool) {

        List<String> labels = new ArrayList<>(asList(toMultiElementArray(count, label)));
        return getCreateVolumesInputArguments(storageDevice, pool, labels, capacity, count, isThinlyProvisioned,
                poolSetting, isBoundToPool);
    }

    public CIMArgument[] getCreateVolumesInputArguments(StorageSystem storageDevice, StoragePool pool,
            List<String> labels,
            Long capacity,
            int count,
            boolean isThinlyProvisioned,
            CIMInstance poolSetting,
            boolean isBoundToPool) {

        List<CIMArgument> list = getCreateVolumesInputArgumentsasList(storageDevice, pool,
                labels, capacity, count, isThinlyProvisioned);
        if (!isBoundToPool) {
            if (storageDevice.getUsingSmis80()) {
                list.add(_cimArgument.boolArray(CP_EMC_BIND_ELEMENTS, toMultiElementArray(count, false)));
            } else {
                list.add(_cimArgument.bool(CP_EMC_BIND_ELEMENTS, false));
            }

        }
        if (poolSetting != null) {
            if (storageDevice.getUsingSmis80()) {
                list.add(_cimArgument.referenceArray(CP_GOAL, toMultiElementArray(count, poolSetting.getObjectPath())));
            } else {
                list.add(_cimArgument.reference(CP_GOAL, poolSetting.getObjectPath()));
            }
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateVolumesInputArguments(StorageSystem storageDevice, StoragePool pool,
            List<String> labels, Long capacity, int count,
            boolean isThinlyProvisioned, boolean isBoundToPool,
            CIMObjectPath volumeGroupPath, boolean fullyAllocated) {

        List<CIMArgument> list = getCreateVolumesInputArgumentsasList(storageDevice, pool, labels, capacity, count,
                isThinlyProvisioned);

        if (!isBoundToPool) {
            if (storageDevice.getUsingSmis80()) {
                list.add(_cimArgument.boolArray(CP_EMC_BIND_ELEMENTS, toMultiElementArray(count, false)));
            } else {
                list.add(_cimArgument.bool(CP_EMC_BIND_ELEMENTS, false));
            }
        }
        if (storageDevice.checkIfVmax3()) {
            if (volumeGroupPath != null) {
                list.add(_cimArgument.referenceArray(CP_EMC_COLLECTIONS, toMultiElementArray(count, volumeGroupPath)));
            }

            // set volumeType for fully-thin or fully-allocated
            int volumeType = fullyAllocated ? STORAGE_VOLUME_FULLY_ALLOCATED : STORAGE_VOLUME_TYPE_THIN;
            if (storageDevice.getUsingSmis80()) {
                list.add(_cimArgument.uint16Array(CP_ELEMENT_TYPE,
                        toMultiElementArray(count, new UnsignedInteger16(volumeType))));
            } else {
                list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));
            }
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateVolumesInputArgumentsOnFastEnabledPool(StorageSystem storageDevice, StoragePool pool,
            String label,
            Long capacity,
            int count,
            boolean isThinlyProvisioned,
            String fastPolicyName) throws IOException {

        if (storageDevice.getUsingSmis80()) {
            List<String> labels = new ArrayList<>(asList(toMultiElementArray(count, label)));
            return getCreateVolumesInputArgumentsOnFastEnabledPool(storageDevice, pool, labels, capacity, count,
                    isThinlyProvisioned, fastPolicyName);
        } else {
            return getCreateVolumesInputArgumentsOnFastEnabledPool(storageDevice, pool, asList(label), capacity, count,
                    isThinlyProvisioned, fastPolicyName);
        }

    }

    public CIMArgument[] getCreateVolumesInputArgumentsOnFastEnabledPool(StorageSystem storageDevice, StoragePool pool,
            List<String> labels,
            Long capacity,
            int count,
            boolean isThinlyProvisioned,
            String fastPolicyName) throws IOException {

        List<CIMArgument> list = getCreateVolumesInputArgumentsasList(storageDevice, pool, labels, capacity, count,
                isThinlyProvisioned);
        CIMArgument[] result = {};

        if (!storageDevice.getAutoTieringEnabled()) {
            return list.toArray(result);
        }

        String poolSettingId = null;
        if (null == fastPolicyName) {
            poolSettingId = pool.getStartHighThenAutoTierId();
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_NO_MOVEMENT.toString().equalsIgnoreCase(
                fastPolicyName)) {
            poolSettingId = pool.getNoDataMovementId();
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.toString().equalsIgnoreCase(
                fastPolicyName)) {
            poolSettingId = pool.getAutoTierSettingId();
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_HIGHEST_AVAILABLE.toString()
                .equalsIgnoreCase(fastPolicyName)) {
            poolSettingId = pool.getHighAvailableTierId();
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_LOWEST_AVAILABLE.toString()
                .equalsIgnoreCase(fastPolicyName)) {
            poolSettingId = pool.getLowAvailableTierId();
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.toString()
                .equalsIgnoreCase(fastPolicyName)) {
            poolSettingId = pool.getStartHighThenAutoTierId();
        }

        if (null != poolSettingId) {
            if (storageDevice.getUsingSmis80()) {
                list.add(_cimArgument.referenceArray(CP_GOAL,
                        toMultiElementArray(count, _cimPath.getPoolSettingPath(storageDevice, poolSettingId))));
            } else {
                list.add(_cimArgument.reference(CP_GOAL, _cimPath.getPoolSettingPath(storageDevice, poolSettingId)));
            }
        }

        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getBindVolumeInputArguments(StorageSystem storageDevice, StoragePool pool, Volume volume,
            long thinVolumePreAllocateSize) throws IOException {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            CIMProperty[] inPoolPropKeys = {
                    _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageDevice, pool.getNativeId()))
            };
            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                    _cimConnection.getNamespace(storageDevice), inPoolPropKeys);
            list.add(_cimArgument.reference(CP_IN_POOL, inPoolPath));
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, volume);
            list.add(_cimArgument.reference(CP_THE_ELEMENT, volumePath));
            if (thinVolumePreAllocateSize > 0) {
                list.add(_cimArgument.uint64(CP_SIZE, thinVolumePreAllocateSize));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments for volume bind: " + storageDevice.getSerialNumber());
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreatePoolSettingArguments() {
        return new CIMArgument[] { _cimArgument.uint16(CP_SETTING_TYPE, DEFAULT_SETTING_TYPE) };
    }

    public CIMProperty[] getModifyPoolSettingArguments(Long size) {
        return new CIMProperty[] { _cimProperty.uint64(CP_THIN_VOLUME_INITIAL_RESERVE, size) };
    }

    public CIMProperty[] getModifyStorageTierMethodologyIdInputArguments(int storageTierMethodology) {
        return new CIMProperty[] { _cimProperty.uint16(EMC_STORAGE_TIER_METHODOLOGY, storageTierMethodology) };
    }

    public int getVolumeStorageTierMethodologyId(StorageSystem storage, CIMObjectPath volumePath) throws Exception {
        CIMInstance cimInstance = this.getInstance(storage, volumePath, false, false, PS_EMC_STORAGE_TIER_METHODOLOGY);
        String tierMethodologyId = cimInstance.getPropertyValue(EMC_STORAGE_TIER_METHODOLOGY).toString();
        return Integer.parseInt(tierMethodologyId);
    }

    public CIMArgument[] getExpandVolumeInputArguments(StorageSystem storageDevice, StoragePool pool, Volume volume, Long size) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, volume);
            CIMInstance volumeInstance = getInstance(storageDevice, volumePath, false, false, new String[] { CP_THINLY_PROVISIONED });
            String isThinVolume = CIMPropertyFactory.getPropertyValue(volumeInstance, CP_THINLY_PROVISIONED);
            list.add(_cimArgument.reference(CP_THE_ELEMENT, volumePath));
            list.add(_cimArgument.uint64(CP_SIZE, size));
            // Check volume type and use thick/thin element type based on the type of the volume
            int volumeType = STORAGE_VOLUME_VALUE;
            if (isThinVolume != null && isThinVolume.equals("true")) {
                volumeType = STORAGE_VOLUME_TYPE_THIN;
            }
            list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: " + storageDevice.getSerialNumber());
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateMetaVolumeMembersInputArguments(StorageSystem storageDevice, StoragePool pool,
            int count, Long capacity, boolean isThinlyProvisioned) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            CIMProperty[] inPoolPropKeys = {
                    _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageDevice, pool.getNativeId()))
            };
            CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                    _cimConnection.getNamespace(storageDevice),
                    inPoolPropKeys);
            // Use thick/thin volume type
            int volumeType = isThinlyProvisioned ? STORAGE_VOLUME_TYPE_THIN : STORAGE_VOLUME_VALUE;
            list.add(_cimArgument.uint16(CP_ELEMENT_TYPE, volumeType));
            // do not bind thin members (SMI-S requires thin members to be unbound)
            if (volumeType == STORAGE_VOLUME_TYPE_THIN) {
                list.add(_cimArgument.bool(CP_EMC_BIND_ELEMENTS, false));
            }
            list.add(_cimArgument.reference(CP_IN_POOL, inPoolPath));
            list.add(_cimArgument.uint64(CP_SIZE, capacity));
            list.add(_cimArgument.uint32(CP_EMC_NUMBER_OF_DEVICES, count));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: " + storageDevice.getSerialNumber());
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateMetaVolumeInputArguments(StorageSystem storageDevice, String label, Volume metaHead,
            List<String> metaMembers, String metaType, boolean isExpand) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            // Set InElements
            ArrayList<String> volumeNamesList = new ArrayList<String>();
            volumeNamesList.add(metaHead.getNativeId()); // meta head should be first
            volumeNamesList.addAll(metaMembers);
            String[] stringArray = {};
            CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNamesList.toArray(stringArray));
            list.add(_cimArgument.referenceArray(CP_IN_ELEMENTS, volumePaths));
            // Set composite type
            int compositeType = metaType.equalsIgnoreCase(Volume.CompositionType.STRIPED.toString()) ? STRIPED_META_VOLUME_TYPE
                    : CONCATENATED_META_VOLUME_TYPE;
            list.add(_cimArgument.uint16(CP_COMPOSITE_TYPE, compositeType));
            // Get information about if meta head is bound to pool.
            CIMInstance cimVolume = null;
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, metaHead);
            cimVolume = getInstance(storageDevice, volumePath, false,
                    false, new String[] { EMC_IS_BOUND });
            String isBoundStr = cimVolume.getPropertyValue(EMC_IS_BOUND).toString();
            Boolean isBound = Boolean.parseBoolean(isBoundStr);
            // For vnx stripe meta volume create: to preserve data on the base lun should set
            // EMCPreserveData to true.
            // Is needed only for expand operation. Also do this only if meta head is bound to pool.
            if (storageDevice.getSystemType().equals(StorageSystem.Type.vnxblock.toString()) &&
                    compositeType == STRIPED_META_VOLUME_TYPE && isExpand) {
                // Check that meta head is bound to pool (have data)
                if (isBound) {
                    list.add(_cimArgument.bool(CP_EMC_PRESERVE_DATA, true));
                }
            }
            // ER: We do not unconditionally bind volume here because this code is also used for
            // expand use cases which should not change binding
            // state of volumes.
            // list.add(_cimArgument.bool(CP_EMC_BIND_ELEMENTS, true));
            // Should preserve original binding state of device to pool in this call. Details, see
            // OPT: 431394 .
            // Other reason is that we need to set thin meta pre-allocate size when we bind thin vmax meta to pool.
            list.add(_cimArgument.bool(CP_EMC_BIND_ELEMENTS, isBound));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments for meta volume create: " + storageDevice.getSerialNumber());
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getExpandMetaVolumeInputArguments(StorageSystem storageDevice, Volume metaHead, List<String> newMetaMembers) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            // Set meta head.
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, metaHead);
            list.add(_cimArgument.reference(CP_THE_ELEMENT, volumePath));
            // Set InElements
            ArrayList<String> volumeNamesList = new ArrayList<String>();
            volumeNamesList.addAll(newMetaMembers);
            String[] stringArray = {};
            CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNamesList.toArray(stringArray));
            list.add(_cimArgument.referenceArray(CP_IN_ELEMENTS, volumePaths));

            // Get information about if meta head is bound to pool.
            CIMInstance cimVolume = null;
            cimVolume = getInstance(storageDevice, volumePath, false,
                    false, new String[] { EMC_IS_BOUND });
            String isBoundStr = cimVolume.getPropertyValue(EMC_IS_BOUND).toString();
            Boolean isBound = Boolean.parseBoolean(isBoundStr);
            // For vmax striped meta volume expand: to preserve data on the source volume set
            // EMCPreserveData to true. For vnx striped mate volume expand, data is preserved automatically and
            // EMCPreserveData
            // is not required.
            // Also do this only if meta head is bound to pool.
            String metaType = metaHead.getCompositionType();
            int compositeType = metaType.equalsIgnoreCase(Volume.CompositionType.STRIPED.toString()) ? STRIPED_META_VOLUME_TYPE
                    : CONCATENATED_META_VOLUME_TYPE;
            list.add(_cimArgument.uint16(CP_COMPOSITE_TYPE, compositeType));
            if (storageDevice.getSystemType().equals(StorageSystem.Type.vmax.toString()) &&
                    compositeType == STRIPED_META_VOLUME_TYPE) {
                // Check that meta head is bound to pool (have data)
                if (isBound) {
                    list.add(_cimArgument.bool(CP_EMC_PRESERVE_DATA, true));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments for meta volume expand: " + storageDevice.getSerialNumber());
        }
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getDeleteVolumesInputArguments(StorageSystem storageDevice, String[] volumeNames) {
        CIMObjectPath[] volumePaths;
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNames);
        } catch (Exception e) {
            throw new IllegalStateException("Problem deleting volumes: " + volumeNames.toString() + "on array: "
                    + storageDevice.getSerialNumber());
        }

        list.add(_cimArgument.referenceArray(CP_THE_ELEMENTS, volumePaths));
        if (storageDevice.checkIfVmax3()) {
            // Additional options: 2 (Continue on nonexistent element),
            // 3 (Return error on nonexistent element)
            list.add(_cimArgument.uint16(CP_OPTIONS, 2));
        }

        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateVolumeGroupInputArguments(StorageSystem storageDevice, String groupName, String[] volumeNames) {
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        CIMObjectPath[] volumePaths;
        try {
            if (volumeNames != null) {
                volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNames);
                list.add(_cimArgument.referenceArray(CP_MEMBERS, volumePaths));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem creating volume group: " + groupName + "on array: " + storageDevice.getSerialNumber());
        }

        list.add(_cimArgument.string(CP_GROUP_NAME, groupName));
        list.add(_cimArgument.uint16(CP_TYPE, VOLUME_GROUP_TYPE));

        list.add(_cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE));
        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMArgument[] getCreateVolumeGroupInputArguments(StorageSystem storageDevice, String groupName, String slo, String srp,
            String workload, String[] volumeNames, boolean disableCompression) {
        CIMObjectPath[] volumePaths;
        ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
        try {
            if (volumeNames != null) {
                volumePaths = _cimPath.getVolumePaths(storageDevice, volumeNames);
                list.add(_cimArgument.referenceArray(CP_MEMBERS, volumePaths));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Problem creating volume group: " + groupName + "on array: " + storageDevice.getSerialNumber());
        }

        list.add(_cimArgument.string(CP_GROUP_NAME, groupName));
        list.add(_cimArgument.uint16(CP_TYPE, VOLUME_GROUP_TYPE));
        if (slo != null && !Constants.NONE.equalsIgnoreCase(slo)) {
            list.add(_cimArgument.string(CP_EMC_SLO, slo));
        }
        if (workload != null && !Constants.NONE.equalsIgnoreCase(workload)) {
            list.add(_cimArgument.string(CP_EMC_WORKLOAD, workload));
        }
        if (srp != null && !Constants.NONE.equalsIgnoreCase(srp)) {
            list.add(_cimArgument.string(CP_EMC_SRP, srp));
        }
        if (disableCompression) {
            list.add(_cimArgument.bool(CP_EMC_DISABLE_COMPRESSION, Boolean.TRUE));
        }

        CIMArgument[] result = {};
        result = list.toArray(result);
        return result;
    }

    public CIMProperty[] getV3FastSettingProperties(String fastSetting) {
        return new CIMProperty[] {
                _cimProperty.string(CP_FAST_SETTING, fastSetting)
        };
    }

    public CIMProperty[] getV3CompressionProperties(boolean compression) {
        return new CIMProperty[] {
                _cimProperty.bool(CP_EMC_COMPRESSION, compression)
        };
    }

    public CIMArgument[] getCascadedStorageGroupInputArguments(StorageSystem storageDevice, String groupName,
            CIMObjectPath[] storageGroupPaths) {
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, VOLUME_GROUP_TYPE),
                _cimArgument.referenceArray(CP_MEMBERS, storageGroupPaths),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE)
        };
    }

    public CIMArgument[] getVolumeGroupToTierInputArguments(StorageSystem storageDevice, String policyName,
            CIMObjectPath[] storageGroupPaths) {
        CIMObjectPath tierPolicyRulePath;
        try {
            tierPolicyRulePath = _cimPath.getTierPolicyRulePath(storageDevice, policyName);
        } catch (Exception e) {
            throw new IllegalStateException("Problem creating tier policy Rule group: " + policyName + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] {
                _cimArgument.reference(CP_POLICY_RULE, tierPolicyRulePath),
                _cimArgument.uint16(CP_OPERATION, TIERING_GROUP_TYPE),
                _cimArgument.referenceArray(CP_IN_ELEMENTS, storageGroupPaths),
        };
    }

    /**
     * Get the fast policies associated with the storage group
     *
     * @param storage
     *            storage system
     * @param storageGroup
     *            storage group name
     * @return the fast policies associated with the storage group
     * @throws Exception
     */
    private StringSet findTierPoliciesForSingleStorageGroup(StorageSystem storage, String storageGroup) throws Exception {
        CloseableIterator<CIMObjectPath> tierPolicyRuleItr = null;
        StringSet policies = new StringSet();
        try {
            CIMObjectPath storageGroupPath = _cimPath.getStorageGroupObjectPath(storageGroup, storage);
            if (storage.checkIfVmax3()) {
                CIMInstance instance = getInstance(storage, storageGroupPath, false, true, SmisConstants.PS_HOST_IO);
                String policyName = SmisUtils.getSLOPolicyName(instance);
                if (policyName != null) {
                    policies.add(policyName);
                }
            } else {
                tierPolicyRuleItr = getAssociatorNames(storage, storageGroupPath, null,
                        CIM_TIER_POLICY_RULE, null, null);
                while (tierPolicyRuleItr.hasNext()) {
                    CIMObjectPath tierPolicyRulePath = tierPolicyRuleItr.next();
                    String policyRuleName = tierPolicyRulePath.getKey(Constants.POLICYRULENAME)
                            .getValue().toString();
                    if (policyRuleName != null) {
                        policies.add(policyRuleName);
                    }
                }
            }
        } finally {
            closeCIMIterator(tierPolicyRuleItr);
        }

        return policies;
    }

    /**
     * Get the fast policies associated with the storage group
     *
     * @param storage
     *            storage system
     * @param storageGroup
     *            storage group name
     * @return the fast policies associated with the storage group
     * @throws Exception
     */
    public StringSet findTierPoliciesForStorageGroup(StorageSystem storage, String storageGroup) throws Exception {
        CloseableIterator<CIMInstance> cimInstanceItr;
        StringSet policies = new StringSet();

        CIMObjectPath storageGroupPath = _cimPath.getStorageGroupObjectPath(storageGroup, storage);
        if (this.isCascadedSG(storage, storageGroupPath)) {
            cimInstanceItr = getAssociatorInstances(storage, storageGroupPath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                    PS_ELEMENT_NAME);
            while (cimInstanceItr.hasNext()) {
                CIMInstance childGroupInstance = cimInstanceItr.next();
                String returnedgroupName = CIMPropertyFactory.getPropertyValue(childGroupInstance,
                        CP_ELEMENT_NAME);
                policies.addAll(findTierPoliciesForSingleStorageGroup(storage, returnedgroupName));
            }
        } else {
            policies.addAll(findTierPoliciesForSingleStorageGroup(storage, storageGroup));
        }

        return policies;
    }

    /**
     * Check if volume is part of more than one masking view.
     *
     * @param storage the storage system
     * @param bo the block object
     * @return true, if successful
     */
    public boolean isVolumePartOfMoreThanOneExport(StorageSystem storage, BlockObject bo) {
        CloseableIterator<CIMObjectPath> mvPathItr = null;
        try {
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storage, bo);
            // get masking view associations
            mvPathItr = getAssociatorNames(storage, volumePath, null, SYMM_LUN_MASKING_VIEW,
                    null, null);
            if (mvPathItr.hasNext()) {
                mvPathItr.next();
                if (mvPathItr.hasNext()) {
                    return true;
                }
            }
        } catch (Exception e) {
            _log.warn("Exception while trying to check if volumes are part of multiple Masking Views", e);
        } finally {
            closeCIMIterator(mvPathItr);
        }
        return false;
    }

    /**
     * Utility method to see if the storage group is used in a masking view.
     * Will not look past its own SG. (for instance, it will not look at the parent's SG)
     *
     * @param storage
     *            storage system
     * @param storageGroupPath
     *            storage group
     * @return true if this SG is directly assigned to a masking view
     * @throws Exception
     */
    private boolean checkStorageGroupInAnyMaskingView(StorageSystem storage, CIMObjectPath storageGroupPath) throws Exception {
        CloseableIterator<CIMObjectPath> cimPathItr = null;
        try {
            _log.info("Verifying SG is already part of masking view");
            cimPathItr = getAssociatorNames(storage, storageGroupPath, null, SYMM_LUN_MASKING_VIEW, null, null);
            if (cimPathItr != null && cimPathItr.hasNext()) {
                _log.info("SG already part of masking view found");
                return true;
            }
        } catch (Exception e) {
            _log.error("Failed trying to check if storage group is in a parent storage group", storageGroupPath.getObjectName(), e);
            throw e;
        } finally {
            closeCIMIterator(cimPathItr);
        }
        return false;
    }

    public Map<String, Set<String>> findAnyStorageGroupsCanBeReUsed(StorageSystem storage,
            ListMultimap<String, VolumeURIHLU> expectedVolumeNativeGuids, StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam)
            throws WBEMException {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        CloseableIterator<CIMObjectPath> tierPolicyRuleItr = null;
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        // concurrent hash map used to avoid concurrent modification exception
        // only
        Map<String, Set<String>> groupPaths = new ConcurrentHashMap<String, Set<String>>();
        try {
            boolean isVmax3 = storage.checkIfVmax3();
            CIMObjectPath deviceMaskingGroupPath = CimObjectPathCreator.createInstance(
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(),
                    ROOT_EMC_NAMESPACE);
            _log.info("Trying to get all Storage Groups");

            if (isVmax3) {
                CIMObjectPath controllerConfigSvcPath = _cimPath.getControllerConfigSvcPath(storage);
                groupInstanceItr = getAssociatorInstances(storage, controllerConfigSvcPath, null,
                        SE_DEVICE_MASKING_GROUP, null, null, PS_V3_STORAGE_GROUP_PROPERTIES);
            } else {
                groupInstanceItr = getEnumerateInstances(storage, deviceMaskingGroupPath,
                        PS_ELEMENT_NAME);
            }
            /**
             * used in finding out whether any volume already exists in a Storage Group, but this
             * storage Group not part of the selected list.i.e. in case of fast , then these volumes
             * cannot be part of a different storage group. hence, we cannot proceed with creating
             * export group.
             **/
            Set<String> volumesInExistingGroups = new HashSet<String>();
            while (groupInstanceItr.hasNext()) {
                CIMInstance groupInstance = groupInstanceItr.next();
                CIMObjectPath groupPath = groupInstance.getObjectPath();
                // skipping device masking groups other than belong to expected
                // storage system
                if (!groupPath.toString().contains(storage.getSerialNumber())) {
                    continue;
                }
                _log.debug("Trying to find group {} belongs to expected policy {}", groupPath,
                        storageGroupPolicyLimitsParam.getAutoTierPolicyName());
                String fastSetting = null;
                if (isVmax3) {
                    fastSetting = CIMPropertyFactory.getPropertyValue(groupInstance, CP_FAST_SETTING);
                } else {
                    tierPolicyRuleItr = getAssociatorNames(storage, groupPath, null,
                            CIM_TIER_POLICY_RULE, null, null);
                }

                String groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);

                /**
                 * if storage group does not math with desired the properties (policyName, Bandwidth,IOPS), skip
                 */
                StorageGroupPolicyLimitsParam existStorageGroupPolicyLimitsParam = createStorageGroupPolicyLimitsParam(storage,
                        groupInstance);
                if (!existStorageGroupPolicyLimitsParam.equals(storageGroupPolicyLimitsParam)) {
                    continue;
                }

                Set<String> returnedNativeGuids = new HashSet<String>();
                // loop through all the volumes of this storage group
                _log.debug("Looping through all volumes in storage group {}", groupName);
                volumePathItr = getAssociatorNames(storage, groupPath, null, CIM_STORAGE_VOLUME, null, null);
                while (volumePathItr.hasNext()) {
                    returnedNativeGuids.add(getVolumeNativeGuid(volumePathItr.next()));
                }
                if (returnedNativeGuids.isEmpty()) {
                    continue;
                }
                // these volumes are at least part of a storage group
                volumesInExistingGroups.addAll(returnedNativeGuids);

                if (isVmax3) {
                    if (!fastSetting.equals(storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                        continue;
                    }
                    if (fastSetting.equals(storageGroupPolicyLimitsParam.getAutoTierPolicyName())
                            && groupName.startsWith(Constants.STORAGE_GROUP_PREFIX)) {
                        continue;
                    }
                }

                // Flag will be true if the group is non-FAST
                boolean isNonFastGroup = (tierPolicyRuleItr != null) ? !tierPolicyRuleItr.hasNext() : false;

                // if the storage group is associated with fast policies, then
                // if the given policy name is not
                // present in that associated policies list, then ignore this
                // storage group.
                if (!isVmax3
                        && !checkPolicyExistsInReturnedList(groupName, tierPolicyRuleItr,
                                storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                    if (isNonFastGroup) {
                        // Ignore volumes that are in non FAST groups (we only care about volumes that could be in
                        // multiple FAST groups).
                        volumesInExistingGroups.removeAll(returnedNativeGuids);
                    }
                    continue;
                }
                Set<String> diff = Sets.difference(returnedNativeGuids, expectedVolumeNativeGuids
                        .asMap().keySet());
                /**
                 * If diff is 0, it means the same set of volumes reside in one of the Storage
                 * Groups associated with this Policy, hence, this storage Group can be reused
                 * during this export operation to different initiator set.
                 */
                if (diff.isEmpty()) {
                    // check whether this storage group is part of any existing parent group
                    // if its not part of any group, then select this
                    // even though this group is part of existing expected parent group, we need to
                    // skip
                    // as there is no action needed.This scenario is possible,only if the same value
                    // is exported using the same export group again.
                    // if thats the case, we throw Error
                    // "Volumes already part of existing storage groups"
                    _log.info("Trying to find parent cascaded groups any of reusable storage group {} ", groupName);
                    if (checkStorageGroupAlreadyPartOfExistingParentGroups(storage, groupPath)) {
                        _log.info(
                                "Even though this group {} contains subset of given volumes {}, it cannot be reused, as it is part of another cascading group",
                                groupPath,
                                Joiner.on("\t").join(returnedNativeGuids));
                        continue;
                    }
                    _log.info("Found Group {} with subset of expected volumes {}", groupName, Joiner.on("\t").join(returnedNativeGuids));
                    runStorageGroupSelectionProcess(groupPaths, returnedNativeGuids, groupName);
                    // add the new group
                }
            }
            /**
             * In case of fast policy, if we find a volume which is part of Storage Group, but this
             * volume is not in the list of final selected Storage Groups volumes, then it means, we
             * cannot proceed with creating an Export Group.
             */
            if (groupPaths.size() > 0) {
                Set<String> volumesPartOfSelectedStorageGroups = constructVolumeNativeGuids(groupPaths
                        .values());
                Set<String> remainingVolumes = new HashSet<String>();
                Sets.difference(expectedVolumeNativeGuids.asMap().keySet(),
                        volumesPartOfSelectedStorageGroups).copyInto(remainingVolumes);
                _log.debug("Remaining volumes which doesn't fit into existing storage groups {}", Joiner.on("\t").join(remainingVolumes));
                // volumes which are not part of selected Storage Groups from
                // expected list
                if (!remainingVolumes.isEmpty()) {
                    // volumes which are already part of storage group.
                    _log.debug("Trying to find , if any of the remaining volumes is present in existing storage groups");
                    remainingVolumes.retainAll(volumesInExistingGroups);
                    // there are volumes which are present in existing storage
                    // groups.
                    if (!remainingVolumes.isEmpty()) {
                        throw DeviceControllerException.exceptions
                                .volumesAlreadyPartOfStorageGroups(Joiner.on("\t").join(remainingVolumes));
                    }
                }
            }
        } finally {
            closeCIMIterator(groupInstanceItr);
            closeCIMIterator(volumePathItr);
            closeCIMIterator(tierPolicyRuleItr);
        }
        return groupPaths;
    }

    /**
     * This method is used for VMAX3 storage system to find exiting storage group with a
     * specified SLO for parking volumes.
     *
     * @param forProvider
     *            The storage provider where the query executes
     * @param storage
     *            The reference to storage system
     * @param policyName
     *            The EMCFastSetting name with which storage group is associated
     * @param associatedToView
     *            Boolean which specifies whether storage group should be associated with masking view or not
     *
     * @return returns map of storage group and volumes nativeguids in a storage group
     * @throws WBEMException
     */
    public Map<CIMObjectPath, Set<String>> findAnySLOStorageGroupsCanBeReUsed(StorageSystem forProvider, StorageSystem storage,
            String policyName, boolean associatedToView) throws WBEMException {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        Map<CIMObjectPath, Set<String>> groupPaths = new ConcurrentHashMap<CIMObjectPath, Set<String>>();
        try {
            CIMObjectPath controllerConfigSvcPath = _cimPath.getControllerConfigSvcPath(storage);

            _log.info("Trying to get all Storage Groups");
            groupInstanceItr = getAssociatorInstances(forProvider, controllerConfigSvcPath, null,
                    SE_DEVICE_MASKING_GROUP, null, null, PS_V3_STORAGE_GROUP_PROPERTIES);

            while (groupInstanceItr.hasNext()) {
                CIMInstance groupInstance = groupInstanceItr.next();
                CIMObjectPath groupPath = groupInstance.getObjectPath();
                String groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);
                String fastSetting = CIMPropertyFactory.getPropertyValue(groupInstance, CP_FAST_SETTING);
                String groupAssociatedToView = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ASSOCIATED_TO_VIEW);
                Set<String> returnedNativeGuids = new HashSet<String>();
                if (fastSetting.equals(policyName) && associatedToView == Boolean.parseBoolean(groupAssociatedToView)
                        && groupName.startsWith(Constants.STORAGE_GROUP_PREFIX)) {
                    // loop through all the volumes of this storage group
                    _log.debug("Looping through all volumes in storage group {}", groupName);
                    volumePathItr = getAssociatorNames(forProvider, groupPath, null, CIM_STORAGE_VOLUME,
                            null, null);
                    while (volumePathItr.hasNext()) {
                        returnedNativeGuids.add(getVolumeNativeGuid(volumePathItr.next()));
                    }
                    groupPaths.put(groupPath, returnedNativeGuids);
                }
            }
        } finally {
            closeCIMIterator(groupInstanceItr);
            closeCIMIterator(volumePathItr);
        }
        return groupPaths;
    }

    private boolean checkStorageGroupAlreadyPartOfExistingParentGroups(StorageSystem storage,
            CIMObjectPath groupPath) throws WBEMException {
        CloseableIterator<CIMObjectPath> cimPathItr = getAssociatorNames(storage, groupPath, null,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null);
        // even if storage group is part of expected parent storage group already, there is no
        // action needed
        // as its already part of parent group.
        boolean result = cimPathItr != null && cimPathItr.hasNext();
        closeCIMIterator(cimPathItr);
        return result;
    }

    private <E> void closeCIMIterator(CloseableIterator<E> itr) {
        if (null != itr) {
            itr.close();
        }
    }

    private boolean checkPolicyExistsInReturnedList(String groupName, CloseableIterator<CIMObjectPath> tierPolicyRuleItr,
            String policyName) {
        if (!tierPolicyRuleItr.hasNext()) {
            _log.info("Group is not associated with any Fast Policy");
            return false;
        }
        try {
            while (tierPolicyRuleItr.hasNext()) {
                CIMObjectPath tierPolicyRulePath = tierPolicyRuleItr.next();
                String policyRuleName = tierPolicyRulePath.getKey(Constants.POLICYRULENAME)
                        .getValue().toString();
                _log.info("Found Storage Group " + groupName + " is associated with policy " + policyRuleName);
                if (policyName.equalsIgnoreCase(policyRuleName)) {
                    _log.info("Storage Group " + groupName + " is associated with expected policy " + policyRuleName);
                    return true;
                }
            }
        } finally {
            closeCIMIterator(tierPolicyRuleItr);
        }
        return false;
    }

    /**
     * Algorithm used in wisely choosing the right storage Groups. UseCase 1 : 1. V1, V2 belongs to
     * Storage Group S1 2. V1,V3,V4 from SG2 comes up, the algorithm removes the old entry and
     * updates the new SG2. UseCase 2: 1. V1,V2 from SG1 2. V3,V4 from SG2, algorithm adds this
     * entry. Use Case 3: 1. V1.V2 from SG1 2. V1,V3 from SG2, algorithm replaces the old with new
     * group. Use case 4: 1.V1,V2 from SG1 2. V1 from SG2, no action
     *
     * @param groupPaths
     * @param returnedNativeGuids
     */
    private void runStorageGroupSelectionProcess(
            Map<String, Set<String>> groupPaths, Set<String> returnedNativeGuids,
            String groupName) {
        _log.info("Running Storage Group selection process");
        if (groupPaths.size() == 0) {
            _log.info("Adding volumes to given group as its first time in loop");
            groupPaths.put(groupName, returnedNativeGuids);
            return;
        }
        Iterator<Entry<String, Set<String>>> groupPathItr = groupPaths.entrySet()
                .iterator();
        while (groupPathItr.hasNext()) {
            Entry<String, Set<String>> entry = groupPathItr.next();
            Set<String> diff = Sets.difference(returnedNativeGuids, entry.getValue());
            _log.debug("diff between returned volumes {} against existing {}",
                    Joiner.on("\t").join(returnedNativeGuids),
                    Joiner.on("\t").join(entry.getValue()));
            _log.info("Diff {}", Joiner.on("\t").join(diff));
            if (returnedNativeGuids.size() == diff.size()) {
                _log.debug("Adding volumes to given group {}", groupName);
                groupPaths.put(groupName, returnedNativeGuids);
            } else if (returnedNativeGuids.size() >= entry.getValue().size()) {
                // remove existing group
                _log.info("Removing Storage Group {} and adding {} from existing map", entry.getKey(), groupName);
                groupPaths.remove(entry.getKey());
                groupPaths.put(groupName, returnedNativeGuids);
            }
        }
    }

    public CIMArgument[] getRemoveVolumeGroupFromTierInputArguments(StorageSystem storageDevice, String policyName,
            CIMObjectPath[] storageGroupPaths) {
        CIMObjectPath tierPolicyRulePath;
        try {
            tierPolicyRulePath = _cimPath.getTierPolicyRulePath(storageDevice, policyName);
        } catch (Exception e) {
            throw new IllegalStateException("Problem creating tier policy Rule group: " + policyName + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] {
                _cimArgument.reference(CP_POLICY_RULE, tierPolicyRulePath),
                _cimArgument.uint16(CP_OPERATION, TIERING_POLICY_REMOVE_TYPE),
                _cimArgument.referenceArray(CP_IN_ELEMENTS, storageGroupPaths),
        };
    }

    public CIMArgument[] getCreateTargetPortGroupInputArguments(StorageSystem storageDevice, String groupName, List<URI> targetURIList) {
        CIMObjectPath[] targetPortPaths;
        try {
            targetPortPaths = _cimPath.getTargetPortPaths(storageDevice, targetURIList);
        } catch (Exception e) {
            _log.error(String.format("Problem creating target port group: %s on array %s",
                    groupName, storageDevice.getSerialNumber()), e);
            throw new IllegalStateException("Problem creating target port group: " + groupName + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, TARGET_PORT_GROUP_TYPE),
                _cimArgument.referenceArray(CP_MEMBERS, targetPortPaths),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE)
        };
    }

    public CIMArgument[] getCreateInitiatorGroupInputArguments(StorageSystem storageDevice, String groupName,
            String[] initiatorNames, boolean consistentLUNs) {
        CIMObjectPath[] initiatorPaths;
        try {
            initiatorPaths = _cimPath.getInitiatorPaths(storageDevice, initiatorNames);
        } catch (Exception e) {
            throw new IllegalStateException("Problem creating initiator group: " + groupName + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, INITIATOR_GROUP_TYPE),
                _cimArgument.referenceArray(CP_MEMBERS, initiatorPaths),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE),
                _cimArgument.bool(CP_CONSISTENT_LUNS, consistentLUNs)
        };
    }

    public CIMArgument getConsistenLUNsArgument(boolean value) {
        CIMArgument arg = new CIMArgument(CP_CONSISTENT_LUNS, CIMDataType.BOOLEAN_T, new Boolean(value));
        return arg;
    }

    public CIMArgument[] getCreateEmptyIGWithInitiatorGroupsInputArguments(String groupName) {
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, INITIATOR_GROUP_TYPE),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE)
        };
    }

    public CIMArgument[] getCreateInitiatorGroupWithInitiatorGroupsInputArguments(String groupName,
            List<CIMObjectPath> igPaths, boolean consistentLUNs) {
        CIMObjectPath[] initiatorGroupPaths = igPaths.toArray(new CIMObjectPath[igPaths.size()]);
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, INITIATOR_GROUP_TYPE),
                _cimArgument.referenceArray(CP_MEMBERS, initiatorGroupPaths),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.TRUE),
                _cimArgument.bool(CP_CONSISTENT_LUNS, consistentLUNs)
        };
    }

    public CIMArgument[] getCreateMaskingViewInputArguments(CIMObjectPath volumeGroup,
            CIMObjectPath targetPortGroup,
            CIMObjectPath initiatorGroup,
            String[] deviceNumbers,
            String maskingViewName,
            boolean forceFlag) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.string(CP_ELEMENT_NAME, maskingViewName));
        argsList.add(_cimArgument.reference(CP_DEVICE_MASKING_GROUP, volumeGroup));
        argsList.add(_cimArgument.reference(CP_TARGET_MASKING_GROUP, targetPortGroup));
        argsList.add(_cimArgument.reference(CP_INITIATOR_MASKING_GROUP, initiatorGroup));
        if (deviceNumbers != null && deviceNumbers.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_DEVICE_NUMBERS, deviceNumbers));
        }
        if (forceFlag) {
            argsList.add(_cimArgument.bool(CP_EMC_FORCE, Boolean.TRUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getDeleteMaskingViewInputArguments(StorageSystem storage, URI exportMaskURI, boolean forceFlag) throws Exception {
        String groupName = getExportMaskName(exportMaskURI);
        return getDeleteMaskingViewInputArguments(storage, groupName, forceFlag);
    }

    public CIMArgument[] getDeleteMaskingViewInputArguments(StorageSystem storage, String maskName, boolean forceFlag) throws Exception {
        CIMObjectPath protocolControllerPath = _cimPath.getMaskingViewPath(storage, maskName);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.reference(CP_PROTOCOL_CONTROLLER, protocolControllerPath));
        if (forceFlag) {
            argsList.add(_cimArgument.bool(CP_EMC_FORCE, Boolean.TRUE));
        }
        if (storage.getUsingSmis80()) {
            // see COP-13573
            argsList.add(_cimArgument.bool(CP_EMC_UNMAP_ELEMENTS, Boolean.TRUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getDeleteMaskingGroupInputArguments(StorageSystem storage, String groupName, MASKING_GROUP_TYPE groupType)
            throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName, groupType);
        return new CIMArgument[] {
                _cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath),
                _cimArgument.bool(CP_FORCE, Boolean.TRUE)
        };
    }

    public CIMArgument[] getDeleteInitiatorMaskingGroup(StorageSystem storage,
            CIMObjectPath igPath)
            throws Exception {
        return new CIMArgument[] {
                _cimArgument.reference(CP_MASKING_GROUP, igPath),
                _cimArgument.bool(CP_FORCE, Boolean.TRUE)
        };
    }

    public CIMArgument[] getRemoveVolumesFromMaskingGroupInputArguments(StorageSystem storage,
            String groupName,
            List<URI> volumeURIList,
            boolean forceFlag) throws Exception {
        String[] members = getBlockObjectAlternateNames(volumeURIList);
        CIMObjectPath[] memberPaths = _cimPath.getVolumePaths(storage, members);
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        return getRemoveAndUnmapMaskingGroupMembersInputArguments(maskingGroupPath, memberPaths, storage, forceFlag);
    }

    public CIMArgument[] getRemoveTargetPortsFromMaskingGroupInputArguments(StorageSystem storage,
            String groupName,
            List<URI> targetURIList) throws Exception {
        CIMObjectPath[] memberPaths = _cimPath.getTargetPortPaths(storage, targetURIList);
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
        return getAddOrRemoveMaskingGroupMembersInputArguments(maskingGroupPath, memberPaths, false);
    }

    public CIMArgument[] getRemoveInitiatorsFromMaskingGroupInputArguments(StorageSystem storage,
            String groupName,
            List<Initiator> initiatorList) throws Exception {
        String[] memberNames = getInitiatorNames(initiatorList, storage);
        CIMObjectPath[] memberPaths = _cimPath.getInitiatorPaths(storage, memberNames);
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
        return getAddOrRemoveMaskingGroupMembersInputArguments(maskingGroupPath, memberPaths, false);
    }

    public CIMArgument[] getRemoveInitiatorsFromMaskingGroupInputArguments(StorageSystem storage,
            CIMObjectPath igPath,
            List<Initiator> initiatorList) throws Exception {
        String[] memberNames = getInitiatorNames(initiatorList, storage);
        CIMObjectPath[] memberPaths = _cimPath.getInitiatorPaths(storage, memberNames);
        return getAddOrRemoveMaskingGroupMembersInputArguments(igPath, memberPaths, false);
    }

    public CIMArgument[] getRemoveIGFromCIG(CIMObjectPath igPath, CIMObjectPath cigPath) {
        CIMObjectPath[] igPaths = new CIMObjectPath[] { igPath };
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, igPaths),
                _cimArgument.reference(CP_MASKING_GROUP, cigPath)
        };
    }

    public CIMArgument[] getRemoveAndUnmapMaskingGroupMembersInputArguments(CIMObjectPath maskingGroupPath, CIMObjectPath[] memberPaths,
            StorageSystem storage, boolean forceFlag) {
        CIMArgument[] args = getAddOrRemoveMaskingGroupMembersInputArguments(maskingGroupPath, memberPaths, forceFlag);
        // Only for 8.0.3 and up. !!! (see COP-13573)
        if (storage.getUsingSmis80()) {
            args = addElement(args, _cimArgument.bool(CP_EMC_UNMAP_ELEMENTS, Boolean.TRUE));
        }
        return args;

    }

    public CIMArgument[] getAddOrRemoveMaskingGroupMembersInputArguments(CIMObjectPath maskingGroupPath, CIMObjectPath[] members,
            boolean forceFlag) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.referenceArray(CP_MEMBERS, members));
        argsList.add(_cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath));
        if (forceFlag) {
            argsList.add(_cimArgument.bool(CP_EMC_FORCE, Boolean.TRUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public String getStorageGroupName(URI exportMaskURI) throws Exception {
        return getExportMaskName(exportMaskURI);
    }

    public void addVolumesToStorageGroup(
            VolumeURIHLU[] volumeURIHLUs, StorageSystem storage, String storageGroupName, SmisJob job, boolean forceFlag)
            throws Exception {
        // The 'DeviceNumbers' parameter that's used in the AddMembers call
        // needs to match up with the list of volumes in the 'Members'
        // parameter. The containers here are used to make sure that happens.
        List<URI> uriList = new ArrayList<>();
        List<String> deviceNumberList = new ArrayList<>();
        for (VolumeURIHLU volURIHlu : volumeURIHLUs) {
            String hlu = volURIHlu.getHLU();
            // Add the HLU to the list only if it is non-null and not the
            // LUN_UNASSIGNED value (as a hex string).
            if (hlu != null &&
                    !hlu.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                deviceNumberList.add(hlu);
            }
            uriList.add(volURIHlu.getVolumeURI());
        }
        String[] HLUs = (!deviceNumberList.isEmpty()) ? deviceNumberList.toArray(new String[deviceNumberList.size()]) : null;
        CIMArgument[] inArgs = getAddVolumesToMaskingGroupInputArguments(storage,
                storageGroupName, uriList, HLUs, forceFlag);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "AddMembers", inArgs, outArgs, job);
    }

    /**
     * Move volumes from one storage group to another.
     * Provider from v8.0.3 supports moveMembers for V3 array. It is a non-disruptive IO call.
     *
     * without disrupting the host's ability to view the volumes, at least one of the following conditions must be met:
     * - Each storage group must be a child of the same parent storage group, and the parent group must be associated
     * with a masking view.
     * - Each storage group must be associated with a masking view, and both masking views must contain a common
     * initiator group and a
     * common port group.
     * In this must both contain the same set of ports, or the target port group can contain a superset of the ports in
     * the source port
     * group.
     * - The source storage group is not in a masking view.
     */
    public void moveVolumesFromOneStorageGroupToAnother(StorageSystem storage,
            CIMObjectPath sourceMaskingGroup, CIMObjectPath targetMaskingGroup,
            List<URI> volumeURIs, SmisJob job) throws Exception {
        Set<String> volumeDeviceIds = new HashSet<String>();
        for (URI volURI : volumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volURI);
            volumeDeviceIds.add(volume.getNativeId());
        }
        CIMArgument[] inArgs = getMoveVolumesBetweenMaskingGroupInputArguments(storage,
                sourceMaskingGroup, targetMaskingGroup, volumeDeviceIds);
        CIMArgument[] outArgs = new CIMArgument[1];
        invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "EMCMoveMembers", inArgs, outArgs, job);
    }

    /**
     * Converts a stand alone storage group in the masking view to cascaded.
     * This method is supported from SMI-S v8.4
     *
     * @param storage the storage system
     * @param storageGroupPath the storage group path
     * @param storageGroupName the storage group name
     * @throws WBEMException the WBEM exception
     */
    public void convertStandAloneStorageGroupToCascaded(StorageSystem storage,
            CIMObjectPath storageGroupPath, String storageGroupName) throws WBEMException {
        // This method is supported from SMI-S v8.4
        StorageProvider storageProvider = _dbClient.queryObject(StorageProvider.class,
                storage.getActiveProviderURI());
        String providerVersion = storageProvider.getVersionString();
        if (VersionChecker.verifyVersionDetailsPostTrim(SMIS_PROVIDER_VERSION_8_4, providerVersion) >= 0) {
            String ChildStorageGroupName = String.format("%s_ChildSG", storageGroupName);
            CIMArgument[] inArgs = getConvertStandAloneStorageGroupToCascadedInputArguments(
                    storage, storageGroupPath, ChildStorageGroupName);
            CIMArgument[] outArgs = new CIMArgument[5];
            invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                    "EMCConvertMaskingGroup", inArgs, outArgs);
        } else {
            _log.info("SMI-S Provider version {} does not support converting"
                    + " Stand alone storage group to Cascaded.", providerVersion);
        }
    }

    /**
     * Get the Volume CIM path, given a Volume object
     *
     * @param storage
     *            storage system
     * @param volume
     *            volume persisted
     * @return CIMObjectPath corresponding to that volume
     * @throws Exception
     */
    public CIMObjectPath getVolumeMember(StorageSystem storage, Volume volume) throws Exception {
        List<URI> volumeURIList = new ArrayList<URI>();
        volumeURIList.add(volume.getId());
        String[] volumeNames = getBlockObjectAlternateNames(volumeURIList);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storage, volumeNames);
        return members[0];
    }

    /**
     * Get List of Volume CIM Paths
     *
     * @param storage
     * @param boUris
     * @return List<CIMObjectPath>
     * @throws Exception
     */
    public List<CIMObjectPath> getVolumeMembers(StorageSystem storage, List<URI> boUris) throws Exception {
        String[] volumeNames = getBlockObjectAlternateNames(boUris);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storage, volumeNames);
        return Arrays.asList(members);
    }

    /**
     * Helper method to set/unset the volume with the "RecoverPoint" tag.
     * The boolean parameter flag determines if the operation is to set or unset the flag on the volume.
     *
     * @param storageSystem
     * @param volume
     * @param flag
     * @return tagSet - boolean indicating whether the recoverpoint tag was successfully enabled or disabled
     * @throws Exception
     */
    public boolean doApplyRecoverPointTag(final StorageSystem storageSystem,
            Volume volume, boolean flag) throws Exception {
        boolean tagSet = false;
        // Set/Unset the RP tag (if applicable)
        if (volume != null && storageSystem != null && volume.checkForRp() && storageSystem.getSystemType() != null
                && storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())
                && !storageSystem.checkIfVmax3()) {
            List<CIMObjectPath> volumePathList = new ArrayList<CIMObjectPath>();
            volumePathList.add(_cimPath.getBlockObjectPath(storageSystem, volume));

            _log.info(String.format("Volume [%s](%s) will be %s for RP",
                    volume.getLabel(), volume.getId(),
                    (flag ? "tagged" : "untagged")));
            tagSet = setRecoverPointTag(storageSystem, volumePathList, flag);
        } else {
            _log.info(String.format("Volume [%s](%s) is not valid for RP tagging operation", volume.getLabel(), volume.getId()));
            tagSet = true;
        }
        return tagSet;
    }

    /**
     * Method will add or remove the EMCRecoverPointEnabled flag from the device masking group for
     * VMAX.
     *
     * @param deviceGroupPath
     *            [in] - CIMObjectPath referencing the volume
     */
    private boolean setRecoverPointTagInternal(StorageSystem storage, List<CIMObjectPath> volumeMemberList, boolean tag) throws Exception {
        boolean tagSet = false;
        try {
            _log.info("Attempting to {} RecoverPoint tag on Volume: {}", tag ? "enable" : "disable", Joiner.on(",").join(volumeMemberList));
            CimConnection connection = _cimConnection.getConnection(storage);
            WBEMClient client = connection.getCimClient();

            if (storage.getUsingSmis80()) {
                CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storage);
                CIMArgument[] inArgs = getRecoverPointInputArguments(storage, volumeMemberList, tag);
                CIMArgument[] outArgs = new CIMArgument[5];
                SmisJob job = null;
                invokeMethodSynchronously(storage, configSvcPath, EMC_SETUNSET_RECOVERPOINT,
                        inArgs, outArgs, job);
            } else {
                for (CIMObjectPath volumeMember : volumeMemberList) {
                    CIMInstance toUpdate = new CIMInstance(volumeMember,
                            new CIMProperty[] {
                                    _cimProperty.bool(EMC_RECOVERPOINT_ENABLED, tag)
                            });
                    _log.debug("Params: " + toUpdate.toString());
                    client.modifyInstance(toUpdate, CP_EMC_RECOVERPOINT_ENABLED);
                }
            }
            _log.info(String.format("RecoverPoint tag has been successfully %s Volume", tag ? "applied to" : "removed from"));
            tagSet = true;
        } catch (WBEMException e) {
            if (e.getMessage().contains("is already set to the requested state")) {
                _log.info("Found the volume was already in the proper RecoverPoint tag state");
                tagSet = true;
            } else {
                _log.error(String.format("Encountered an error while trying to %s the RecoverPoint tag", tag ? "enable" : "disable"), e);
            }
        }
        return tagSet;
    }

    /**
     * Method will attempt to add or remove the EMCRecoverPointEnabled tag on VMAX volumes for up to 1 minute
     *
     * @param storage
     *            - VMAX storage system under consideration
     * @param volumeMemberList
     *            - volumes to add or remove EMCRecoverPointEnabled tag on
     * @param tag
     *            - enable or disable the tag
     * @return whether the tag was successfully enabled or disabled
     * @throws Exception
     */
    public boolean setRecoverPointTag(StorageSystem storage, List<CIMObjectPath> volumeMemberList, boolean tag) throws Exception {
        final int MAX_WAIT_TOTAL_TRIES = 120;
        final int MAX_WAIT_RETRY_MILLISECONDS = 5000;
        int setTagTries = MAX_WAIT_TOTAL_TRIES;
        boolean tagSet = false; // set to true to stay in the loop
        if (storage.checkIfVmax3()) {
            _log.error("Unable to set RecoverPoint Tag for VMAX V3 and beyond");
            return tagSet;
        }

        while (!tagSet && setTagTries-- > 0) {
            if ((MAX_WAIT_TOTAL_TRIES - setTagTries) != 1) {
                _log.info("Briefly sleeping before attempting to set RecoverPoint tag (Attempt #{} / {})", MAX_WAIT_TOTAL_TRIES
                        - setTagTries, MAX_WAIT_TOTAL_TRIES);
                try {
                    Thread.sleep(MAX_WAIT_RETRY_MILLISECONDS);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }
            tagSet = setRecoverPointTagInternal(storage, volumeMemberList, tag);
        }
        if (!tagSet) {
            _log.info("Unable to set RecoverPoint tag after {} attempts)", MAX_WAIT_TOTAL_TRIES);
        }
        return tagSet;
    }

    public CIMArgument[] getRecoverPointInputArguments(StorageSystem storageDevice, List<CIMObjectPath> volumeMemberList, boolean tag)
            throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        if (tag) {
            argsList.add(_cimArgument.uint16(CP_OPERATION, 1));
        } else {
            argsList.add(_cimArgument.uint16(CP_OPERATION, 2));
        }

        CIMObjectPath[] volumeMemberPaths = {};
        volumeMemberPaths = volumeMemberList.toArray(volumeMemberPaths);

        argsList.add(_cimArgument.referenceArray(CP_THE_ELEMENTS, volumeMemberPaths));
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getAddVolumesToMaskingGroupInputArguments(StorageSystem storageDevice, String storageGroupName,
            List<URI> volumeURIList, String[] deviceNumbers, boolean forceFlag) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storageDevice,
                ControllerUtils.extractGroupName(storageGroupName), MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        String[] volumeNames = getBlockObjectAlternateNames(volumeURIList);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storageDevice, volumeNames);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.referenceArray(CP_MEMBERS, members));
        argsList.add(_cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath));
        if (deviceNumbers != null && deviceNumbers.length > 0) {
            argsList.add(_cimArgument.stringArray(CP_DEVICE_NUMBERS, deviceNumbers));
        }
        if (forceFlag) {
            argsList.add(_cimArgument.bool(CP_EMC_FORCE, Boolean.TRUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getAddVolumesToMaskingGroupInputArguments(StorageSystem storageDevice, CIMObjectPath groupPath,
            Set<String> volumeDeviceIds) throws Exception {
        String[] volumeNames = volumeDeviceIds.toArray(new String[volumeDeviceIds.size()]);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storageDevice, volumeNames);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.referenceArray(CP_MEMBERS, members));
        argsList.add(_cimArgument.reference(CP_MASKING_GROUP, groupPath));
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getMoveVolumesBetweenMaskingGroupInputArguments(StorageSystem storageDevice, CIMObjectPath sourceGroupPath,
            CIMObjectPath targetGroupPath, Set<String> volumeDeviceIds) throws Exception {
        String[] volumeNames = volumeDeviceIds.toArray(new String[volumeDeviceIds.size()]);
        CIMObjectPath[] members = _cimPath.getVolumePaths(storageDevice, volumeNames);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.referenceArray(CP_MEMBERS, members));
        argsList.add(_cimArgument.reference(CP_SOURCE_MASKING_GROUP, sourceGroupPath));
        argsList.add(_cimArgument.reference(CP_TARGET_MASKING_GROUP, targetGroupPath));
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getConvertStandAloneStorageGroupToCascadedInputArguments(
            StorageSystem storageDevice, CIMObjectPath storageGroupPath, String ChildStorageGroupName) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.reference(CP_DEVICE_MASKING_GROUP, storageGroupPath));
        argsList.add(_cimArgument.uint16(CP_OPERATION, CONVERT_STANDALONE_SG_TO_CASCADED));
        argsList.add(_cimArgument.bool(CP_HOST_IOLIMIT_PARENT, Boolean.TRUE));
        argsList.add(_cimArgument.string(CP_CHILD_STORAGE_GROUP_NAME, ChildStorageGroupName));
        argsList.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, Boolean.TRUE));
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] modifyCascadedStorageGroupInputArguments(
            StorageSystem storageDevice, String storageGroupName,
            CIMObjectPath[] childGroupPaths, boolean forceFlag) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storageDevice,
                storageGroupName, MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.referenceArray(CP_MEMBERS, childGroupPaths));
        argsList.add(_cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath));
        if (forceFlag) {
            argsList.add(_cimArgument.bool(CP_EMC_FORCE, Boolean.TRUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getRemoveGroupsFromMaskingViewInputArguments(
            StorageSystem storage, String groupName,
            CIMObjectPath groupPath) throws Exception {
        CIMObjectPath protocolControllerPath = _cimPath.getMaskingViewPath(storage, groupName);
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.reference(CP_PROTOCOL_CONTROLLER, protocolControllerPath));
        argsList.add(_cimArgument.reference(CP_MASKING_GROUP, groupPath));
        argsList.add(_cimArgument.uint16(CP_OPERATION, REMOVE_GROUP_TYPE));
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getAddInitiatorsToMaskingGroupInputArguments(StorageSystem storageDevice,
            CIMObjectPath maskingGroupPath,
            List<Initiator> initiatorList) throws Exception {
        String[] initiatorNames = getInitiatorNames(initiatorList, storageDevice);
        CIMObjectPath[] members = _cimPath.getInitiatorPaths(storageDevice, initiatorNames);
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, members),
                _cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath)
        };
    }

    public CIMArgument[] getAddTargetsToMaskingGroupInputArguments(StorageSystem storageDevice,
            CIMObjectPath maskingGroupPath,
            String exportMaskName,
            List<URI> targetURIList) throws Exception {
        try {
            CIMObjectPath[] targetPortPaths = _cimPath.getTargetPortPaths(storageDevice, targetURIList);
            return new CIMArgument[] {
                    _cimArgument.referenceArray(CP_MEMBERS, targetPortPaths),
                    _cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath)
            };
        } catch (Exception e) {
            throw new IllegalStateException("Problem adding target ports to export mask : " + exportMaskName + "on array: "
                    + storageDevice.getSerialNumber());
        }
    }

    public CIMArgument[] getCreateStorageHardwareIDArgs(Initiator initiator) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        String storageID = null;
        String nodeID = null;
        argsList.add(_cimArgument.string(CP_EMC_HOST_NAME, initiator.getHostName()));
        if (initiator.getProtocol().equals("FC")) {
            storageID = WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorPort());
            nodeID = WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorNode());
            argsList.add(_cimArgument.string(CP_EMC_NODE_ID, nodeID));
            argsList.add(_cimArgument.string(CP_STORAGE_ID, storageID));
            argsList.add(_cimArgument.uint16(CP_ID_TYPE, PORT_WWN_ID_TYPE_VALUE));
        } else {
            storageID = initiator.getInitiatorPort();
            argsList.add(_cimArgument.string(CP_STORAGE_ID, storageID));
            argsList.add(_cimArgument.uint16(CP_ID_TYPE, PORT_ISCSI_ID_TYPE_VALUE));
        }
        CIMArgument[] args = {};
        return argsList.toArray(args);
    }

    public CIMArgument[] getCreateOrGrowStorageGroupInputArguments(StorageSystem storage,
            URI exportMask,
            VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList,
            List<URI> targetURIList) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
            String[] lunNames = new String[volumeURIHLUs.length];
            List<String> deviceNumbers = new ArrayList<String>();
            UnsignedInteger16[] deviceAccesses = new UnsignedInteger16[volumeURIHLUs.length];
            String elementName = getExportMaskName(exportMask);
            argsList.add(_cimArgument.string(CP_EMC_ELEMENT_NAME, elementName));
            for (int i = 0; i < volumeURIHLUs.length; i++) {
                lunNames[i] = getBlockObjectAlternateName(volumeURIHLUs[i].getVolumeURI());
                String hlu = volumeURIHLUs[i].getHLU();
                // Add the HLU to the list only if it is non-null and not the
                // LUN_UNASSIGNED value (as a hex string).
                if (hlu != null &&
                        !hlu.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                    deviceNumbers.add(hlu);
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
        if (initiatorList != null && !initiatorList.isEmpty()) {
            String[] initiatorPortIDs = getInitiatorNamesForClariion(initiatorList);
            argsList.add(_cimArgument.stringArray(CP_INITIATOR_PORT_IDS, initiatorPortIDs));
        }
        if (targetURIList != null && !targetURIList.isEmpty()) {
            CIMObjectPath[] targetEndpoints = _cimPath.getTargetPortPaths(storage, targetURIList);
            Set<String> uniqueTargetPortIDs = new HashSet<String>();
            for (CIMObjectPath path : targetEndpoints) {
                uniqueTargetPortIDs.add(path.getKey(CP_NAME).getValue().toString());
            }
            argsList.add(_cimArgument.stringArray(CP_TARGET_PORT_IDS, uniqueTargetPortIDs.toArray(new String[] {})));
        }
        CIMObjectPath[] protocolControllers = _cimPath.getClarProtocolControllers(storage, getExportMaskNativeId(exportMask));
        if (protocolControllers != null && protocolControllers.length > 0) {
            argsList.add(_cimArgument.referenceArray(CP_PROTOCOL_CONTROLLERS, protocolControllers));
        }
        CIMArgument[] retArgs = {};
        return argsList.toArray(retArgs);
    }

    public CIMArgument[] getDeleteOrShrinkStorageGroupInputArguments(StorageSystem storage,
            URI exportMask,
            List<URI> volumeURIList,
            List<Initiator> initiatorList,
            boolean bDeleteStorageGroup) throws Exception {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        String[] volumeNames = null;
        String[] initiatorPortIDs = null;
        if (volumeURIList != null && !volumeURIList.isEmpty()) {
            volumeNames = getBlockObjectAlternateNames(volumeURIList);
            argsList.add(_cimArgument.stringArray(CP_LU_NAMES, volumeNames));
        }
        if (initiatorList != null && !initiatorList.isEmpty()) {
            initiatorPortIDs = getInitiatorNamesForClariion(initiatorList);
            argsList.add(_cimArgument.stringArray(CP_INITIATOR_PORT_IDS, initiatorPortIDs));
        }
        CIMObjectPath[] protocolControllers = _cimPath.getClarProtocolControllers(storage, getExportMaskNativeId(exportMask));
        if (protocolControllers != null && protocolControllers.length > 0) {
            if (bDeleteStorageGroup) {
                argsList.add(_cimArgument.reference(CP_PROTOCOL_CONTROLLER, protocolControllers[0]));
            } else {
                argsList.add(_cimArgument.referenceArray(CP_PROTOCOL_CONTROLLERS, protocolControllers));
            }
        }
        CIMArgument[] retArgs = {};
        return argsList.toArray(retArgs);
    }

    public CIMArgument[] getCreateReplicationGroupCreateInputArguments(StorageSystem storage, String groupName,
            CIMObjectPath[] volumePaths) throws Exception {
        if (storage.getUsingSmis80()) {
            // DeleteOnEmptyElement property set to true is not allowed
            if (groupName != null) {
                return new CIMArgument[] {
                        _cimArgument.string(CP_GROUP_NAME, groupName),
                        _cimArgument.referenceArray(CP_MEMBERS, volumePaths)
                };
            } else {
                return new CIMArgument[] {
                        _cimArgument.referenceArray(CP_MEMBERS, volumePaths)
                };
            }
        }

        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.bool(CP_DELETE_ON_EMPTY_ELEMENT, true),
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths)
        };
    }

    public CIMArgument[] getCreateReplicationGroupWithMembersInputArguments(StorageSystem storage, String groupName,
            CIMObjectPath[] volumePaths) throws Exception {
        if (storage.checkIfVmax3()) {
            return new CIMArgument[] {
                    _cimArgument.referenceArray(CP_MEMBERS, volumePaths)
            };
        }

        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths)
        };
    }

    public CIMArgument[] getCreateReplicationGroupInputArguments(String groupName) throws Exception {
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName)
        };
    }

    public CIMArgument[] getDeactivateSnapshotSynchronousInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DEACTIVATE_SNAPSHOT),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath)
        };
    }

    public CIMArgument[] getDeleteSnapshotSynchronousInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DELETE_SNAPSHOT),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath)
        };
    }

    public CIMArgument[] getReturnGroupSyncToPoolInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RETURN_TO_RESOURCE_POOL),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath)
        };
    }

    public CIMArgument[] getRestoreFromReplicaInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, RESTORED_COPY_STATE),
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true)
        };
    }

    public CIMArgument[] getRestoreFromReplicaInputArgumentsWithForce(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true)
        };

    }

    public CIMArgument[] getRestoreFromSettingsStateInputArguments(CIMObjectPath settingsStatePath, boolean waitForCopyState) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.uint16(CP_OPERATION, RESTORE_FROM_SYNC_SETTINGS));
        args.add(_cimArgument.reference(CP_SETTINGS_STATE, settingsStatePath));
        if (waitForCopyState) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, RESTORED_COPY_STATE));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    public CIMArgument[] getDeleteReplicationGroupInputArguments(StorageSystem storage, String groupName) {
        CIMObjectPath groupPath = _cimPath.getReplicationGroupPath(storage, groupName);
        return getDeleteReplicationGroupInputArguments(storage, groupPath, true);
    }

    public CIMArgument[] getDeleteReplicationGroupInputArguments(StorageSystem storage, CIMObjectPath groupPath, boolean removeElements) {
        return new CIMArgument[] {
                _cimArgument.reference(CP_REPLICATION_GROUP, groupPath),
                _cimArgument.bool(CP_REMOVE_ELEMENTS, removeElements)
        };
    }

    public CIMArgument[] getRestoreFromSnapshotInputArguments(StorageSystem storage, Volume to, BlockSnapshot from)
            throws Exception {
        CIMObjectPath volumePath = _cimPath.getVolumePath(storage, to.getNativeId());

        if (from.getSettingsInstance() == null) {
            throw DeviceControllerException.exceptions.snapSettingsInstanceNull(from.getSnapsetLabel(), from.getId().toString());
        }

        CIMObjectPath syncSettingsPath = _cimPath.getSyncSettingsPath(storage, volumePath, from.getSettingsInstance());

        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_SYNC_SETTINGS),
                _cimArgument.reference(CP_SETTINGS_STATE, syncSettingsPath)
        };
    }

    public CIMArgument[] getDeleteSettingsForSnapshotInputArguments(StorageSystem storage, Volume to, BlockSnapshot from)
            throws Exception {
        String[] deviceIds = getBlockObjectNativeIds(Arrays.asList(to.getId()));
        CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storage, deviceIds);
        CIMObjectPath volumePath = volumePaths[0];
        CIMObjectPath syncSettingsPath = _cimPath.getSyncSettingsPath(storage, volumePath, from.getSettingsInstance());
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, DELETE_FROM_SYNC_SETTINGS),
                _cimArgument.reference(CP_SETTINGS_STATE, syncSettingsPath)
        };
    }

    public CIMArgument[] getDeleteSettingsForSnapshotInputArguments(CIMObjectPath settingsPath, boolean synchronous)
            throws Exception {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, synchronous),
                _cimArgument.uint16(CP_OPERATION, DELETE_FROM_SYNC_SETTINGS),
                _cimArgument.reference(CP_SETTINGS_STATE, settingsPath)
        };
    }

    public CIMArgument[] getCreateGroupReplicaInputArgumentsForVNX(StorageSystem storage, CIMObjectPath cgPath,
            boolean createInactive, String label, int syncType) {
        final int waitForCopyState = createInactive ? INACTIVE_VALUE : ACTIVATE_VALUE;
        final CIMArgument[] basicArgs = new CIMArgument[] { _cimArgument.uint16(CP_SYNC_TYPE, syncType),
                _cimArgument.reference(CP_SOURCE_GROUP, cgPath),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState) };
        final List<CIMArgument> args = new ArrayList<CIMArgument>(asList(basicArgs));
        // If active, add the RelationshipName
        if (!createInactive) {
            args.add(_cimArgument.string(RELATIONSHIP_NAME, label));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    public CIMArgument[] getCreateGroupReplicaInputArgumentsForVMAX(
            StorageSystem storage, CIMObjectPath cgPath,
            boolean createInactive, String label, CIMObjectPath targetGroupPath,
            CIMObjectPath targetVPSnapPoolPath,
            CIMInstance replicaSettingConsistentPointInTime,
            SYNC_TYPE syncType) {
        final CIMArgument[] basicArgs = new CIMArgument[] {
                _cimArgument.uint16(CP_SYNC_TYPE, syncType.getValue()),
                _cimArgument.reference(CP_SOURCE_GROUP, cgPath) };
        final List<CIMArgument> args = new ArrayList<CIMArgument>(asList(basicArgs));
        if (null != targetGroupPath) {
            args.add(_cimArgument.reference(CP_TARGET_GROUP, targetGroupPath));
        }
        if (null != targetVPSnapPoolPath) {
            args.add(_cimArgument.reference(CP_TARGET_POOL, targetVPSnapPoolPath));
        }
        // If active, add the RelationshipName
        if (!createInactive) {
            // Ensure that it does not exceed MAX_VMAX_RELATIONSHIP_NAME
            int maxRelNameLength = storage.getUsingSmis80() ? MAX_SMI80_RELATIONSHIP_NAME : MAX_VMAX_RELATIONSHIP_NAME;
            final String relationshipName = (label.length() > maxRelNameLength) ? label.substring(0, maxRelNameLength) : label;
            args.add(_cimArgument.string(RELATIONSHIP_NAME, relationshipName));
            if (syncType == SYNC_TYPE.CLONE || syncType == SYNC_TYPE.SNAPSHOT || syncType == SYNC_TYPE.MIRROR) {
                args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, ACTIVATE_VALUE));
            }
        }
        // If inactive, add the WaitForCopyState
        else {
            int waitForCopyState = -1;
            if (syncType == SYNC_TYPE.SNAPSHOT) {
                waitForCopyState = INACTIVE_VALUE;
            } else if (syncType == SYNC_TYPE.CLONE) {
                waitForCopyState = PREPARED_VALUE;
            }
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState));
        }
        if (replicaSettingConsistentPointInTime != null) {
            args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, replicaSettingConsistentPointInTime));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    public CIMArgument[] getAddMembersInputArguments(CIMObjectPath cgPath, CIMObjectPath[] volumePaths) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths),
                _cimArgument.reference(CP_REPLICATION_GROUP, cgPath)
        };
    }

    public CIMArgument[] getRemoveMembersInputArguments(CIMObjectPath cgPath, CIMObjectPath[] volumePaths) {
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, volumePaths),
                _cimArgument.reference(CP_REPLICATION_GROUP, cgPath)
        };
    }

    public CIMArgument[] getCreateDefaultStoragePoolSettingsArguments() {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_SETTING_TYPE, DEFAULT_SETTING_TYPE_VALUE)
        };
    }

    /**
     * Convenience method that wraps SMI-S ModifyReplicatSynchronization operation
     *
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param inArgs
     *            [required] - CIMArgument array containing operation's arguments
     * @return CIM_Job CIMObjectPath will be returned representing the job associated with the
     *         operation.
     * @throws WBEMException
     */
    public CIMObjectPath callModifyReplica(StorageSystem storage,
            CIMArgument[] inArgs)
            throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethod(storage, replicationSvcPath, MODIFY_REPLICA_SYNCHRONIZATION, inArgs, outArgs);
        return _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
    }

    public void callModifyReplicaSynchronously(StorageSystem storage,
            CIMArgument[] inArgs, SmisJob job) throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath
                .getControllerReplicationSvcPath(storage);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethodSynchronously(storage, replicationSvcPath,
                MODIFY_REPLICA_SYNCHRONIZATION, inArgs, outArgs, job);
    }

    public CIMObjectPath callModifyListReplica(StorageSystem storage, CIMArgument[] inArgs)
            throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethod(storage, replicationSvcPath, SmisConstants.MODIFY_LIST_REPLICA_SYNCHRONIZATION,
                inArgs, outArgs);
        return _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
    }

    public CIMObjectPath protect(StorageSystem storage,
            CIMArgument[] inArgs)
            throws WBEMException {
        CIMObjectPath protectionSvcPath = _cimPath.getStorageProtectionSvcPath(storage);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethodSynchronously(storage, protectionSvcPath, PROTECT, inArgs, outArgs, null);
        return _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
    }

    /**
     * Convenience method that wraps SMI-S ModifyReplicatSynchronization operation
     *
     * @param storage
     *            [required] - StorageSystem object representing array
     * @param inArgs
     *            [required] - CIMArgument array containing operation's arguments
     * @param outArgs
     *            [required] - output arguments to be filled in by the operation
     * @return Object - return result object
     * @throws WBEMException
     */
    public Object callModifyReplica(StorageSystem storage,
            CIMArgument[] inArgs, CIMArgument[] outArgs)
            throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
        return invokeMethod(storage, replicationSvcPath, MODIFY_REPLICA_SYNCHRONIZATION, inArgs, outArgs);
    }

    /**
     * Convenience method that wraps SMI-S ModifySettingsDefineState operation
     *
     * @param storage
     *            [required] - StorageSystem object referencing the array
     * @param inArgs
     *            [required] - input arugments for the ModifySettingsDefineState operation
     * @return Object - return result object
     * @throws WBEMException
     */
    public CIMObjectPath callModifySettingsDefineState(StorageSystem storage, CIMArgument[] inArgs)
            throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethod(storage, replicationSvcPath, MODIFY_SETTINGS_DEFINE_STATE, inArgs, outArgs);
        return _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
    }

    /**
     * Convenience method that wraps SMI-S ModifySettingsDefineState operation
     *
     * @param storage
     *            [required] - StorageSystem object referencing the array
     * @param inArgs
     *            [required] - input arugments for the ModifySettingsDefineState operation
     * @param outArgs
     *            [required] - output arguments to be filled in by the operation
     * @return Object - return result object
     * @throws WBEMException
     */
    public Object callModifySettingsDefineState(StorageSystem storage, CIMArgument[] inArgs, CIMArgument[] outArgs)
            throws WBEMException {
        CIMObjectPath replicationSvcPath = _cimPath.getControllerReplicationSvcPath(storage);
        return invokeMethod(storage, replicationSvcPath, MODIFY_SETTINGS_DEFINE_STATE, inArgs, outArgs);
    }

    /**
     * This method will take a URI and return a nativeId for the BlockObject object to which the URI
     * applies.
     *
     * @param uri
     *            - URI
     * @return Returns a nativeId String value
     * @throws DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri
     *             if URI is not a Volume/BlockSnapshot URI
     */
    public String getBlockObjectNativeId(URI uri) throws Exception {
        String nativeId;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            nativeId = volume.getNativeId();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            nativeId = blockSnapshot.getNativeId();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
            nativeId = blockMirror.getNativeId();
        } else {
            throw DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return nativeId;
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
    public String getBlockObjectAlternateName(URI uri) throws Exception {
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

    /**
     * This method will loop through the URI list and return a list of nativeIds for each of the
     * BlockObject objects to which the URI applies.
     *
     * @param uris
     *            - Collection of URIs
     * @return Returns a list of nativeId String values
     * @throws DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri
     *             f URI is not a Volume/BlockSnapshot URI
     */
    public String[] getBlockObjectNativeIds(Collection<URI> uris) throws Exception {
        String[] results = {};
        Set<String> nativeIds = new HashSet<String>();
        for (URI uri : uris) {
            String nativeId;
            if (URIUtil.isType(uri, Volume.class)) {
                Volume volume = _dbClient.queryObject(Volume.class, uri);
                nativeId = volume.getNativeId();
            } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
                BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
                nativeId = blockSnapshot.getNativeId();
            } else if (URIUtil.isType(uri, BlockMirror.class)) {
                BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
                nativeId = blockMirror.getAlternateName();
            } else {
                throw DeviceControllerException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
            }
            nativeIds.add(nativeId);
        }
        return nativeIds.toArray(results);
    }

    /**
     * This method will loop through the URI list and return a list of nativeIds for each of the
     * BlockObject objects to which the URI applies.
     *
     * @param uris
     *            - Collection of URIs
     * @return Returns a list of nativeId String values
     * @throws Exception
     */
    public String[] getBlockObjectAlternateNames(Collection<URI> uris) throws Exception {
        String[] results = {};
        List<String> names = new ArrayList<String>();
        for (URI uri : uris) {
            String alternateName = getBlockObjectAlternateName(uri);
            if (NullColumnValueGetter.isNotNullValue(alternateName) && !names.contains(alternateName)) {
                names.add(alternateName);
            }
        }
        return names.toArray(results);
    }

    public String[] getInitiatorNames(List<Initiator> initiatorList, StorageSystem storageDevice) throws Exception {
        List<String> initiatorNameList = new ArrayList<String>();
        String[] initiatorNames = {};
        for (Initiator initiator : initiatorList) {
            String initiatorName = null;
            if (initiator.getProtocol().equals("FC")) {
                if (storageDevice.getUsingSmis80()) {
                    initiatorName = "W+".concat(WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorPort()));
                } else {
                    initiatorName = "W+"
                            .concat(WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorNode().concat(initiator.getInitiatorPort())));
                }
            } else {
                initiatorName = "I+".concat(initiator.getInitiatorPort());
            }
            initiatorNameList.add(SmisUtils.translate(storageDevice, initiatorName));
        }
        initiatorNames = initiatorNameList.toArray(initiatorNames);
        return initiatorNames;
    }

    public String getExportMaskName(URI exportMaskURI) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        return exportMask.getMaskName();
    }

    public String getExportMaskResource(URI exportMaskURI) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        return exportMask.getResource();
    }

    public StringSet getExportMaskDeviceDataMapParameter(URI exportMaskURI, String parameter) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask.getDeviceDataMap() == null) {
            return new StringSet();
        }
        return exportMask.getDeviceDataMap().get(parameter);
    }

    public String getTieringPolicyName(String autoTierPolicyUri) throws Exception {
        if (NONE.equalsIgnoreCase(autoTierPolicyUri)) {
            return NONE;
        }
        AutoTieringPolicy policy = _dbClient.queryObject(AutoTieringPolicy.class, URI.create(autoTierPolicyUri));
        return policy.getPolicyName();
    }

    public void setProtocolControllerNativeId(URI exportMaskURI, CIMObjectPath protocolController) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (protocolController != null) {
            CIMProperty<String> storageGroupID = (CIMProperty<String>) protocolController.getKey(CP_DEVICE_ID);
            exportMask.setNativeId(storageGroupID.getValue());
        } else {
            exportMask.setNativeId(null);
        }
        _dbClient.persistObject(exportMask);
    }

    public CIMInstance getInstance(StorageSystem storage,
            CIMObjectPath objectPath,
            boolean propagated,
            boolean includeClassOrigin,
            String[] propertyList) throws WBEMException {
        CIMInstance cimInstance = null;
        CimConnection connection = _cimConnection.getConnection(storage);
        WBEMClient client = connection.getCimClient();
        try {
            cimInstance = client.getInstance(objectPath, propagated, includeClassOrigin, propertyList);
        } catch (WBEMException we) {
            throw we;
        }
        return cimInstance;
    }

    /**
     * This is a wrapper for the WBEMClient enumerateInstances method.
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
    public CloseableIterator<CIMInstance> getInstances(StorageSystem storage,
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
        CIMObjectPath cimObjectPath = CIM_OBJECT_PATH_HASH_MAP.get(classKey);
        if (cimObjectPath == null) {
            cimObjectPath = CimObjectPathCreator.createInstance(className, namespace);
            CIM_OBJECT_PATH_HASH_MAP.putIfAbsent(classKey, cimObjectPath);
        }
        cimInstances = client.enumerateInstances(cimObjectPath, deep, localOnly,
                includeClassOrigin, propertyList);
        return cimInstances;
    }

    private String getExportMaskNativeId(URI exportMaskURI) throws Exception {
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        return exportMask.getNativeId();
    }

    private String[] getInitiatorNamesForClariion(List<Initiator> initiatorList) throws Exception {
        List<String> initiatorNameList = new ArrayList<String>();
        String[] initiatorNames = {};
        for (Initiator initiator : initiatorList) {
            String initiatorName = null;
            if (initiator.getProtocol().equals("FC")) {
                initiatorName = WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorNode()).concat(":")
                        .concat(WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorPort()));
            } else {
                initiatorName = initiator.getInitiatorPort();
            }
            initiatorNameList.add(initiatorName);
        }
        return initiatorNameList.toArray(initiatorNames);
    }

    /**
     * This method is a wrapper for the getInstance. If the object is not found, it returns a null
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
     * @return CIMInstance object that represents the existing object
     * @throws Exception
     */
    public CIMInstance checkExists(StorageSystem storage,
            CIMObjectPath objectPath,
            boolean propagated,
            boolean includeClassOrigin) throws Exception {
        CIMInstance instance = null;
        try {
            if (objectPath != null && !objectPath.equals(NULL_CIM_OBJECT_PATH)) {
                _log.debug(String.format("checkExists(storage=%s, objectPath=%s, propagated=%s, includeClassOrigin=%s)",
                        storage.getSerialNumber(), objectPath.toString(),
                        String.valueOf(propagated), String.valueOf(includeClassOrigin)));
                instance = getInstance(storage, objectPath, propagated, includeClassOrigin, null);
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

    public boolean checkVolumeGroupAssociatedWithPolicy(StorageSystem storage,
            CIMObjectPath volumechildGroupPath, String expectedPolicyName) throws WBEMException {
        String policyNameOnGroup = null;
        if (storage.checkIfVmax3()) {
            policyNameOnGroup = getVMAX3FastSettingAssociatedWithVolumeGroup(storage, volumechildGroupPath);
        } else {
            policyNameOnGroup = getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage, volumechildGroupPath);
        }
        return expectedPolicyName.equalsIgnoreCase(policyNameOnGroup);
    }

    public String getAutoTieringPolicyNameAssociatedWithVolumeGroup(StorageSystem storage,
            CIMObjectPath volumechildGroupPath) throws WBEMException {
        String policyName = Constants.NONE;
        CloseableIterator<CIMObjectPath> tierPolicyPathItr = null;
        _log.debug("Finding out Fast Policy Associated with Storage Group {}", volumechildGroupPath);
        try {
            tierPolicyPathItr = getReference(storage, volumechildGroupPath,
                    CP_TIERPOLICY_APPLIES_TO_ELEMENT, null);
            while (tierPolicyPathItr.hasNext()) {
                CIMObjectPath tierPolicySetAppliesToElementPath = tierPolicyPathItr.next();
                CIMObjectPath tierPolicyPath = (CIMObjectPath) tierPolicySetAppliesToElementPath
                        .getKey(CP_POLICY_SET).getValue();
                policyName = tierPolicyPath.getKey(CP_POLICY_NAME).getValue().toString();
                _log.debug("Found Policy Name {} associated with Storage Group {}", policyName, volumechildGroupPath);
                break;
            }
        } finally {
            closeCIMIterator(tierPolicyPathItr);
        }
        return policyName;
    }

    /**
     * Gets the VMAX3 fast setting associated with volume group (storage group).
     * If there is no fast setting, it returns NONE string.
     *
     * @param storage the storage system
     * @param volumechildGroupPath the volume storage group path
     * @return the VMAX3 fast setting associated with volume group
     * @throws WBEMException the WBEM exception
     */
    public String getVMAX3FastSettingAssociatedWithVolumeGroup(StorageSystem storage,
            CIMObjectPath volumechildGroupPath) throws WBEMException {
        _log.debug("Finding out Fast Policy Associated with Storage Group {}", volumechildGroupPath);
        CIMInstance groupInstance = getInstance(storage, volumechildGroupPath, false, false, PS_V3_FAST_SETTING_PROPERTIES);
        String fastSetting = getVMAX3FastSettingAssociatedWithVolumeGroup(groupInstance);
        _log.debug("Found Fast setting {} associated with Storage Group {}", fastSetting, volumechildGroupPath);
        return fastSetting;
    }

    /**
     * Gets the VMAX3 fast setting associated with volume group (storage group).
     * If there is no fast setting, it returns NONE string.
     *
     * @param groupInstance the group instance
     * @return the VMAX3 fast setting associated with volume group
     */
    public String getVMAX3FastSettingAssociatedWithVolumeGroup(CIMInstance groupInstance) {
        String fastSetting = Constants.NONE;
        String fastSettingOnSG = CIMPropertyFactory.getPropertyValue(groupInstance, CP_FAST_SETTING);
        if (!fastSettingOnSG.isEmpty()) {
            fastSetting = fastSettingOnSG;
        }
        return fastSetting;
    }

    /**
     * Returns whether this object is a cascaded object or not.
     *
     * @param storage
     *            storage device
     * @param path
     *            path of IG, SG, or PG
     * @return true if the object has child references
     * @throws WBEMException
     */
    public boolean isCascadedSG(StorageSystem storage, CIMObjectPath path) throws WBEMException {
        CloseableIterator<CIMObjectPath> pathItr = null;
        try {
            if (checkExists(storage, path, false, false) != null) {
                pathItr = getReference(storage, path, SE_MEMBER_OF_COLLECTION_DMG_DMG, null);
                if (!pathItr.hasNext()) {
                    // There are no references in this SG, it is a standalone.
                    return false;
                }

                while (pathItr.hasNext()) {
                    CIMObjectPath objPath = pathItr.next();
                    if (objPath != null) {
                        CIMProperty prop = objPath.getKey(MEMBER);
                        if (prop != null) {
                            CIMObjectPath comparePath = (CIMObjectPath) prop.getValue();
                            // comparePath tends to have the IP address prepended, path does not.
                            // comparePath:
                            // //10.247.99.71/root/emc:SE_DeviceMaskingGroup.InstanceID="SYMMETRIX+000195701573+ingcl-12_GOLD_SG"
                            // path:
                            // /root/emc:SE_DeviceMaskingGroup.InstanceID="SYMMETRIX+000195701573+ingcl-12_GOLD_SG"
                            if (comparePath != null && comparePath.toString().endsWith(path.toString())) {
                                // There is a cascaded storage group out there that we're the child of.
                                // But we're still a child.
                                return false;
                            }
                        }
                    }
                }
            } else {
                _log.info("Instance not found for path {}. Assuming cascaded.", path);
            }
        } catch (Exception e) {
            _log.info("Got exception trying to retrieve cascade status of SG.  Assuming cascaded: ", e);
        } finally {
            closeCIMIterator(pathItr);
        }
        return true;
    }

    /**
     * Returns whether this object is a stand alone or not.
     *
     * @param storage
     *            storage device
     * @param path
     *            path of SG
     * @return true if the object has no storage group references
     * @throws WBEMException
     */
    public boolean isStandAloneSG(StorageSystem storage, CIMObjectPath path) throws WBEMException {
        CloseableIterator<CIMObjectPath> pathItr = null;
        try {
            if (checkExists(storage, path, false, false) != null) {
                pathItr = getReference(storage, path, SE_MEMBER_OF_COLLECTION_DMG_DMG, null);
                if (!pathItr.hasNext()) {
                    // There are no references in this SG, it is a standalone.
                    return true;
                }
            } else {
                _log.info("Instance not found for path {}. Assuming cascaded.", path);
            }
        } catch (Exception e) {
            _log.info("Got exception trying to retrieve stand alone status of SG. Assuming cascaded: ", e);
        } finally {
            closeCIMIterator(pathItr);
        }
        return false;
    }

    /**
     * Find if IG is cascaded
     *
     * @param storage
     * @param path
     * @return
     * @throws WBEMException
     */
    public boolean isCascadedIG(StorageSystem storage, CIMObjectPath path) throws WBEMException {
        CloseableIterator<CIMObjectPath> pathItr = null;
        try {
            if (checkExists(storage, path, false, false) != null) {
                pathItr = getReference(storage, path, SE_MEMBER_OF_COLLECTION_IMG_IMG, null);
                if (!pathItr.hasNext()) {
                    // There are no references in this IG, it is a standalone.
                    return false;
                }

                while (pathItr.hasNext()) {
                    CIMObjectPath objPath = pathItr.next();
                    if (objPath != null) {
                        CIMProperty prop = objPath.getKey(MEMBER);
                        if (prop != null) {
                            CIMObjectPath comparePath = (CIMObjectPath) prop.getValue();
                            if (comparePath != null && comparePath.toString().endsWith(path.toString())) {
                                return false;
                            }
                        }
                    }
                }
            } else {
                _log.info("Instance not found for path {}. Assuming non-cascaded.", path);
                return false;
            }
        } catch (Exception e) {
            _log.info("Got exception trying to retrieve cascade status of IG.  Assuming cascaded: ", e);
        } finally {
            closeCIMIterator(pathItr);
        }
        return true;
    }

    /**
     * Wrapper for WBEM.referenceNames routine
     *
     * @param storageDevice
     *            [required] - StorageSystem object to which an SMI-S connection would be made
     * @param path
     *            [required] - CIMObjectPath defining the source CIM Object whose referring Objects
     *            are to be returned. This argument may contain either a Class name or the modelpath
     *            of an Instance.
     * @param resultClass
     *            [optional] - This string MUST either contain a valid CIM Class name or be null. It
     *            filters the Objects returned to contain only the Objects of this Class name or one
     *            of its subclasses.
     * @param role
     *            [optional] - This string MUST either contain a valid Property name or be null. It
     *            filters the Objects returned to contain only Objects referring to the source
     *            Object via a Property with the specified name. If "Antecedent" is specified, then
     *            only Associations in which the source Object is the "Antecedent" reference are
     *            returned
     * @return - If successful, a CloseableIterator referencing zero or more CIMObjectPaths of
     *         CIMClasses or CIMInstances meeting the specified criteria.
     * @throws WBEMException
     */
    public CloseableIterator<CIMObjectPath> getReference(StorageSystem storageDevice, CIMObjectPath path,
            String resultClass, String role)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().referenceNames(path, resultClass, role);
    }

    /**
     * Wrapper for WBEM.associatorNames routine
     *
     * @param storageDevice
     *            [required]
     * @param path
     *            [required]
     * @param assocClass
     *            [optional] - assocClass - This string MUST either contain a valid CIM Association
     *            class name or be null. It filters the Objects returned to contain only Objects
     *            associated to the source Object via this CIM Association class or one of its
     *            subclasses.
     * @param resultClass
     *            [optional] - This string MUST either contain a valid CIM Class name or be null. It
     *            filters the Objects returned to contain only the Objects of this Class name or one
     *            of its subclasses.
     * @param role
     *            [optional] - role - This string MUST either contain a valid Property name or be
     *            null. It filters the Objects returned to contain only Objects associated to the
     *            source Object via an Association class in which the source Object plays the
     *            specified role. (i.e. the Property name in the Association class that refers to
     *            the source Object matches this value) If "Antecedent" is specified, then only
     *            Associations in which the source Object is the "Antecedent" reference are
     *            examined.
     * @param resultRole
     *            [optional] - This string MUST either contain a valid Property name or be null. It
     *            filters the Objects returned to contain only Objects associated to the source
     *            Object via an Association class in which the Object returned plays the specified
     *            role. (i.e. the Property name in the Association class that refers to the Object
     *            returned matches this value) If "Dependent" is specified, then only Associations
     *            in which the Object returned is the "Dependent" reference are examined.
     * @return CloseableIterator - iterator that can be used to enumerate the associatorNames
     * @throws WBEMException
     */
    public CloseableIterator<CIMObjectPath> getAssociatorNames(StorageSystem storageDevice, CIMObjectPath path,
            String assocClass, String resultClass, String role, String resultRole)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().associatorNames(path, assocClass, resultClass, role, resultRole);
    }

    public CloseableIterator<CIMInstance> getAssociatorInstances(StorageSystem storageDevice, CIMObjectPath path,
            String assocClass, String resultClass, String role, String resultRole, String[] prop)
            throws WBEMException {
        return getConnection(storageDevice).getCimClient().associatorInstances(path, null, resultClass, null, null, false, prop);
    }

    public CloseableIterator<CIMObjectPath> getEnumerateInstanceNames(
            StorageSystem storageDevice, CIMObjectPath path) throws WBEMException {
        return getConnection(storageDevice).getCimClient().enumerateInstanceNames(path);
    }

    public CloseableIterator<CIMInstance> getEnumerateInstances(
            StorageSystem storageDevice, CIMObjectPath path, String[] prop) throws WBEMException {
        return getConnection(storageDevice).getCimClient().enumerateInstances(path, true, false, false, prop);
    }

    public boolean isStorageGroupSizeGreaterThanGivenVolumes(String groupName, StorageSystem storage, int size) throws Exception {
        return (getVMAXStorageGroupVolumeCount(storage, groupName) > size);
    }

    public int getVMAXStorageGroupVolumeCount(StorageSystem storage, String groupName)
            throws Exception {
        int count = 0;
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMObjectPath> volumeItr = null;
        try {
            volumeItr = getAssociatorNames(storage, maskingGroupPath, null,
                    CIM_STORAGE_VOLUME, null, null);
            while (volumeItr.hasNext()) {
                volumeItr.next();
                count++;
            }
        } finally {
            closeCIMIterator(volumeItr);
        }
        return count;
    }

    public boolean findStorageGroupsAssociatedWithMultipleParents(
            StorageSystem storage, String groupName) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMObjectPath> cimPathItr = null;
        try {
            _log.debug("Trying to find the parent Storage Groups, on which this group {} resides", groupName);
            cimPathItr = getAssociatorNames(storage, maskingGroupPath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null);
            while (cimPathItr.hasNext()) {
                // actual parent Group is already deleted in previous step , hence if you find a parent then return
                // true,which
                // skips deleting this child group.
                _log.debug("At least 1 parent Group other than expected parent exists for given child Group {}", groupName);
                return true;
            }
        } finally {
            closeCIMIterator(cimPathItr);
        }
        return false;
    }

    public boolean findStorageGroupAChildOfParent(
            StorageSystem storage, String childgroupName, String parentGroupName) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, parentGroupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        try {
            cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                    PS_ELEMENT_NAME);
            while (cimInstanceItr.hasNext()) {
                CIMInstance childGroupInstance = cimInstanceItr.next();
                String returnedgroupName = CIMPropertyFactory.getPropertyValue(childGroupInstance,
                        CP_ELEMENT_NAME);
                if (childgroupName.equalsIgnoreCase(returnedgroupName)) {
                    return true;
                }
            }
        } finally {
            closeCIMIterator(cimInstanceItr);
        }
        return false;
    }

    public boolean findStorageGroupsAssociatedWithOtherMaskingViews(
            StorageSystem storage, String groupName) throws Exception {
        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMObjectPath> cimPathItr = null;
        try {
            _log.debug("Trying to find the masking views, on which this group {} resides", groupName);
            cimPathItr = getAssociatorNames(storage, maskingGroupPath, null, SYMM_LUN_MASKING_VIEW,
                    null, null);
            while (cimPathItr.hasNext()) {
                _log.debug("Storage Group {} is part of existing masking view", groupName);
                return true;
            }
        } finally {
            closeCIMIterator(cimPathItr);
        }
        return false;
    }

    /**
     * Checks whether the given storage group (SG) is part of an MV. If the SG is a child of CSG, then it will check if the CSG is part of
     * an MV.
     * 
     * Note: CSG is used in cluster export.
     * 
     * @param storage the storage system
     * @param groupName the storage group name (SG)
     * @return true if the SG or its CSG is part of an MV; false otherwise.
     * @throws Exception
     */
    public boolean isSGOrItsCSGPartOfMaskingView(StorageSystem storage, String groupName) throws Exception {

        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMObjectPath> cimPathItr = null;
        CloseableIterator<CIMObjectPath> pathItr = null;
        try {
            _log.info("Trying to find the masking views, on which this group {} resides", groupName);

            if(isStandAloneSG(storage, maskingGroupPath)) {
                cimPathItr = getAssociatorNames(storage, maskingGroupPath, null, SYMM_LUN_MASKING_VIEW,
                        null, null);
                if (cimPathItr.hasNext()) {
                    _log.info("Storage Group {} is part of existing masking view: {}",
                            groupName, cimPathItr.next().toString());
                    return true;
                }
            } else {
                /*
                 * The SG is nested under CSG. Query MVs using the child SG does not return anything.
                 * Hence, we have to get the CSG from the child SG and then use the CSG to query the MVs.
                 */
                // Try to get the CSG
                pathItr = getReference(storage, maskingGroupPath, SE_MEMBER_OF_COLLECTION_DMG_DMG, null);
                while (pathItr.hasNext()) {
                    CIMObjectPath objPath = pathItr.next();
                    if (objPath != null) {
                        CIMProperty prop = objPath.getKey(COLLECTION);
                        if (prop != null) {
                            CIMObjectPath csgPath = (CIMObjectPath) prop.getValue();
                            _log.debug("CSG of {}: {}", groupName, csgPath.toString());
                            cimPathItr = getAssociatorNames(storage, csgPath, null, SYMM_LUN_MASKING_VIEW,
                                    null, null);
                            if (cimPathItr.hasNext()) {
                                _log.info("The parent storage group {} is part of existing masking view: {}",
                                        csgPath.toString(), cimPathItr.next().toString());
                                return true;
                            }
                        }

                    }
                }
            }
        } finally {
            closeCIMIterator(cimPathItr);
            closeCIMIterator(pathItr);
        }
        return false;
    }

    /**
     * Group volumes by storage group which has host IO limit set.
     *
     * @param storage the storage
     * @param volumeURIs the volume uris
     * @return the sgToVolumesMap storage group to volumes map
     * @throws WBEMException the WBEM exception
     */
    public Map<String, List<URI>> groupVolumesByStorageGroupWithHostIOLimit(StorageSystem storage,
            Set<URI> volumeURIs) throws Exception {
        CloseableIterator<CIMObjectPath> mvPathItr = null;
        CloseableIterator<CIMObjectPath> sgItr = null;
        Map<String, List<URI>> sgToVolumesMap = new HashMap<String, List<URI>>();
        List<String> processedGroups = new ArrayList<String>();
        try {
            _log.info("Trying to find existing Storage groups for given volumes which have Host IO Limit set..");
            List<? extends BlockObject> blockObjects = BlockObject.fetchAll(_dbClient, volumeURIs);
            for (BlockObject bo : blockObjects) {
                CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storage, bo);
                // See if Volume is associated with MV
                mvPathItr = getAssociatorNames(storage, volumePath, null, SYMM_LUN_MASKING_VIEW, null, null);
                if (mvPathItr.hasNext()) {
                    sgItr = getAssociatorNames(storage, volumePath, null, SE_DEVICE_MASKING_GROUP, null, null);
                    while (sgItr.hasNext()) {
                        CIMObjectPath sgPath = sgItr.next();
                        String instanceId = sgPath.getKey(CP_INSTANCE_ID).getValue().toString();
                        if (!processedGroups.contains(instanceId)) {
                            CIMInstance sgInstance = getInstance(storage, sgPath, false, false,
                                    new String[] { CP_ELEMENT_NAME, EMC_MAX_BANDWIDTH, EMC_MAX_IO });

                            String groupName = CIMPropertyFactory.getPropertyValue(sgInstance, CP_ELEMENT_NAME);
                            String hostIOLimitBandwidth = CIMPropertyFactory.getPropertyValue(sgInstance, EMC_MAX_BANDWIDTH);
                            String hostIOLimitIOPs = CIMPropertyFactory.getPropertyValue(sgInstance, EMC_MAX_IO);
                            if ((!StringUtils.isEmpty(hostIOLimitBandwidth) && Integer.parseInt(hostIOLimitBandwidth) > 0)
                                    || (!StringUtils.isEmpty(hostIOLimitIOPs) && Integer.parseInt(hostIOLimitIOPs) > 0)) {
                                // SG has Host IO Limit set
                                List<URI> sgVolumes = sgToVolumesMap.get(groupName);
                                if (sgVolumes == null) {
                                    sgVolumes = new ArrayList<URI>();
                                    sgToVolumesMap.put(groupName, sgVolumes);
                                }
                                sgVolumes.add(bo.getId());
                            }
                            processedGroups.add(instanceId);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            _log.error("Error occurred while looking for volumes' storage groups for Host IO Limit", ex);
            throw ex;
        } finally {
            closeCIMIterator(mvPathItr);
            closeCIMIterator(sgItr);
        }
        return sgToVolumesMap;
    }

    /**
     * Use case : Group volumes based on existing child Storage Groups they belong to Algo : Loop
     * through each Child Storage Group { Loop through each Volume in child Group ,if given volume
     * found, then the volume gets landed in child Group bucket } Result : Volumes gets landed in
     * proper child Storage Group buckets.
     *
     * @param storage
     * @param groupName
     * @param volumeUris
     * @return
     * @throws Exception
     */
    public Map<String, List<URI>> groupVolumesBasedOnExistingGroups(StorageSystem storage,
            String groupName, List<URI> volumeUris) throws Exception {
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        CloseableIterator<CIMObjectPath> volumeItr = null;
        Map<String, List<URI>> volumeGroup = new HashMap<String, List<URI>>();
        try {
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
            /** if no child groups found, then group all volumes to parent Group */
            if (!this.isCascadedSG(storage, maskingGroupPath)) {
                Map<String, URI> volumePaths = getNativeGuidToVolumeUriMap(volumeUris, storage);
                volumeItr = groupVolumeBasedOnStorageGroup(storage,
                        volumeGroup, volumePaths, maskingGroupPath);
                return volumeGroup;
            }
            /** Generate given Volume NativeGuids to Volume Uri map */
            Map<String, URI> volumePaths = getNativeGuidToVolumeUriMap(volumeUris, storage);
            cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null, PS_ELEMENT_NAME);
            while (cimInstanceItr.hasNext()) {
                CIMInstance instance = cimInstanceItr.next();
                volumeItr = groupVolumeBasedOnStorageGroup(storage,
                        volumeGroup, volumePaths, instance.getObjectPath());
            }
        } finally {
            closeCIMIterator(cimInstanceItr);
            closeCIMIterator(volumeItr);
        }
        return volumeGroup;
    }

    private CloseableIterator<CIMObjectPath> groupVolumeBasedOnStorageGroup(
            StorageSystem storage, Map<String, List<URI>> volumeGroup,
            Map<String, URI> volumePaths, CIMObjectPath storageGroupPath)
            throws Exception {
        String groupName;
        CloseableIterator<CIMObjectPath> volumeItr;
        CIMInstance storageGroupInstance = getInstance(storage, storageGroupPath, false, false, PS_ELEMENT_NAME);
        groupName = CIMPropertyFactory.getPropertyValue(storageGroupInstance, CP_ELEMENT_NAME);
        /** Loop through volumes of storage groups */
        volumeItr = getAssociatorNames(storage, storageGroupPath, null, CIM_STORAGE_VOLUME, null, null);
        while (volumeItr.hasNext()) {
            CIMObjectPath volumePath = volumeItr.next();
            String nativeGuid = getVolumeNativeGuid(volumePath);
            /**
             * if given volume is found, then it lands in this child Group bucket
             */
            if (volumePaths.containsKey(nativeGuid)) {
                if (volumeGroup.get(groupName) == null) {
                    volumeGroup.put(groupName, new ArrayList<URI>());
                }
                volumeGroup.get(groupName).add(volumePaths.get(nativeGuid));
            }
        }
        return volumeItr;
    }

    public Map<StorageGroupPolicyLimitsParam, List<String>> groupStorageGroupsByAssociation(
            StorageSystem storage, String groupName)
            throws Exception {
        Map<StorageGroupPolicyLimitsParam, List<String>> groupNames = new HashMap<StorageGroupPolicyLimitsParam, List<String>>();

        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        try {
            _log.info("Trying to find child Storage Groups for given Parent Group {}", groupName);
            if (storage.checkIfVmax3()) {
                cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                        PS_V3_STORAGE_GROUP_PROPERTIES);
                if (!cimInstanceItr.hasNext()) {
                    _log.info("Non-Cascaded Storage Group found {}", groupName);
                    CIMInstance maskingGroupInstance = getInstance(storage, maskingGroupPath, false,
                            false, PS_V3_STORAGE_GROUP_PROPERTIES);
                    String policyName = CIMPropertyFactory.getPropertyValue(maskingGroupInstance, CP_FAST_SETTING);
                    if (policyName == null || policyName.isEmpty()) {
                        policyName = Constants.NONE;
                    }
                    StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(policyName);
                    groupNames.put(storageGroupPolicyLimitsParam, new ArrayList<String>());
                    groupNames.get(storageGroupPolicyLimitsParam).add(groupName);
                } else {
                    StorageGroupPolicyLimitsParam storageGroupNonePolicyLimitsParam = new StorageGroupPolicyLimitsParam(Constants.NONE);
                    // add cascaded parent Group as well, for deletion
                    groupNames.put(storageGroupNonePolicyLimitsParam, new ArrayList<String>());
                    groupNames.get(storageGroupNonePolicyLimitsParam).add(groupName);
                }
                while (cimInstanceItr.hasNext()) {
                    CIMInstance groupInstance = cimInstanceItr.next();
                    groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);
                    String policyName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_FAST_SETTING);
                    StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createStorageGroupPolicyLimitsParam(storage,
                            groupInstance);
                    if (null == groupNames.get(storageGroupPolicyLimitsParam)) {
                        groupNames.put(storageGroupPolicyLimitsParam, new ArrayList<String>());
                    }
                    groupNames.get(storageGroupPolicyLimitsParam).add(groupName);
                }
            } else {
                cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                        PS_HOST_IO);
                if (!cimInstanceItr.hasNext()) {
                    _log.info("Non-Cascaded Storage Group found {}", groupName);
                    CIMInstance groupInstance = getInstance(storage, maskingGroupPath, false, false, PS_HOST_IO);
                    StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createStorageGroupPolicyLimitsParam(storage,
                            groupInstance);
                    groupNames.put(storageGroupPolicyLimitsParam, new ArrayList<String>());
                    groupNames.get(storageGroupPolicyLimitsParam).add(groupName);
                } else {
                    StorageGroupPolicyLimitsParam storageGroupNonePolicyLimitsParam = new StorageGroupPolicyLimitsParam(Constants.NONE);
                    // add cascaded parent Group as well, for deletion
                    groupNames.put(storageGroupNonePolicyLimitsParam, new ArrayList<String>());
                    // No need to include CSG, as its already handled
                }
                while (cimInstanceItr.hasNext()) {
                    CIMInstance groupInstance = cimInstanceItr.next();
                    String storageGroupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);

                    StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createStorageGroupPolicyLimitsParam(storage,
                            groupInstance);
                    if (null == groupNames.get(storageGroupPolicyLimitsParam)) {
                        groupNames.put(storageGroupPolicyLimitsParam, new ArrayList<String>());
                    }
                    groupNames.get(storageGroupPolicyLimitsParam).add(storageGroupName);
                }
            }
        } finally {
            closeCIMIterator(cimInstanceItr);
        }
        _log.info("Constructed Groups : {}", Joiner.on('\n').withKeyValueSeparator(" -> ").join(groupNames));
        return groupNames;
    }

    /**
     * Group volumes based on policy, bandwidth, and IOPs as dictated by the ViPR database objects.
     *
     * @param storage
     *            storage device
     * @param volumeUris
     *            volume URIs
     * @return a map of storage groups to volumes using that policy, bandwidth, and IOPs
     * @throws Exception
     */
    public Map<StorageGroupPolicyLimitsParam, List<URI>> groupVolumesBasedOnFastPolicy(StorageSystem storage,
            List<URI> volumeUris) throws Exception {
        Map<StorageGroupPolicyLimitsParam, List<URI>> volumeGroup = new HashMap<StorageGroupPolicyLimitsParam, List<URI>>();

        Map<URI, VirtualPool> uriVirtualPoolMap = new HashMap<URI, VirtualPool>();
        for (URI volumeURI : volumeUris) {
            String policyName = Constants.NONE;
            Integer hostIOLimitBandwidth = null;
            Integer hostIOLimitIOPs = null;
            if (URIUtil.isType(volumeURI, Volume.class)) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                VirtualPool virtualPool = uriVirtualPoolMap.get(volume.getVirtualPool());
                if (virtualPool == null) {
                    virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    uriVirtualPoolMap.put(volume.getVirtualPool(), virtualPool);
                }

                String volumePolicyName = ControllerUtils.getAutoTieringPolicyName(volume.getId(), _dbClient);
                if (volumePolicyName != null) {
                    policyName = volumePolicyName;
                }
                hostIOLimitBandwidth = virtualPool.getHostIOLimitBandwidth();
                hostIOLimitIOPs = virtualPool.getHostIOLimitIOPs();
            }

            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(policyName,
                    hostIOLimitBandwidth, hostIOLimitIOPs, storage);
            if (volumeGroup.get(storageGroupPolicyLimitsParam) == null) {
                volumeGroup.put(storageGroupPolicyLimitsParam, new ArrayList<URI>());
            }
            volumeGroup.get(storageGroupPolicyLimitsParam).add(volumeURI);
            _log.info("Adding volumeURI {} to policy {}", volumeURI, storageGroupPolicyLimitsParam);
        }
        return volumeGroup;
    }

    public Map<String, List<URI>> groupVolumesBasedOnFastPolicy(StorageSystem storage,
            String policyName, List<URI> volumeUris) throws Exception {
        Map<String, List<URI>> volumeGroup = new HashMap<String, List<URI>>();
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        CloseableIterator<CIMObjectPath> volumeItr = null;
        try {
            CIMObjectPath policyRulepath = _cimPath.getTierPolicyRulePath(storage, policyName);
            if (Constants.NONE.equalsIgnoreCase(policyName)) {
                volumeGroup.put(Constants.NONE, volumeUris);
                return volumeGroup;
            }
            cimInstanceItr = getAssociatorInstances(storage, policyRulepath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                    PS_ELEMENT_NAME);
            /** if no child groups found, then group all volumes to parent Group */
            if (!cimInstanceItr.hasNext()) {
                volumeGroup.put(Constants.NONE, volumeUris);
                return volumeGroup;
            }
            /** Generate given Volume NativeGuids to Volume Uri map */
            Map<String, URI> volumePaths = getNativeGuidToVolumeUriMap(volumeUris, storage);
            while (cimInstanceItr.hasNext()) {
                CIMInstance groupInstance = cimInstanceItr.next();
                String groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);
                /** Loop through volumes of storage groups */
                volumeItr = getAssociatorNames(storage, groupInstance.getObjectPath(), null, CIM_STORAGE_VOLUME, null, null);
                while (volumeItr.hasNext()) {
                    CIMObjectPath volumePath = volumeItr.next();
                    String nativeGuid = getVolumeNativeGuid(volumePath);
                    /**
                     * if given volume is found, then it lands in thhis child Group bucket
                     */
                    if (volumePaths.containsKey(nativeGuid)) {
                        if (!volumeGroup.containsKey(groupName)) {
                            volumeGroup.put(groupName, new ArrayList<URI>());
                        }
                        volumeGroup.get(groupName).add(volumePaths.get(nativeGuid));
                    }
                }
            }
        } finally {
            closeCIMIterator(volumeItr);
            closeCIMIterator(cimInstanceItr);
        }
        return volumeGroup;
    }

    /**
     * Generate NativeGuid-->Volume uri mapping using volume uris list.
     *
     * @param volumeUris
     * @param storage
     * @return
     * @throws IOException
     */
    private Map<String, URI> getNativeGuidToVolumeUriMap(
            List<URI> volumeUris, StorageSystem storage) throws IOException {
        Map<String, URI> volumePaths = new HashMap<String, URI>();
        for (URI volumeUri : volumeUris) {
            String nativeGuid = null;
            if (URIUtil.isType(volumeUri, Volume.class)) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeUri);
                nativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, volume);
            } else if (URIUtil.isType(volumeUri, BlockSnapshot.class)) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class,
                        volumeUri);
                nativeGuid = NativeGUIDGenerator.generateNativeGuid(storage, snapshot);
            } else if (URIUtil.isType(volumeUri, BlockMirror.class)) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class,
                        volumeUri);
                nativeGuid = NativeGUIDGenerator.generateNativeGuid(storage, mirror);
            }
            volumePaths.put(nativeGuid, volumeUri);
        }
        return volumePaths;
    }

    /**
     * Algo : FastPolicy F1 ---> V1, V2 FastPolicy F2 ---> V3, V4 Use case : Add these volumes to
     * existing Storage Group S1. 1. Find out the child Groups for given parent Group S1. 2. For
     * each child Group, if its associated with Fast, get its policy name. CH1 --->F1 Now the
     * resultant group would be CH1--->V1,V2 3. After looping through all child Storage Groups, if
     * you still didn't encounter F2, then it means we need to create a new Storage Group CH2 and
     * associate with F2. 4. Final result would be Volumes get grouped by child Storage Group Names.
     *
     * @param storage
     *            storage system
     * @param parentGroupName
     *            parent storage group name
     * @param policyToVolumeMap
     *            map of FAST policy to volume objects
     * @return group of storage group to volume objects
     * @throws Exception
     */
    public Map<String, Collection<VolumeURIHLU>> groupVolumesByStorageGroup(StorageSystem storage,
            String parentGroupName, DataSource sgDataSource, String sgCustomTemplateName,
            ListMultimap<StorageGroupPolicyLimitsParam, VolumeURIHLU> policyLimitsParamToVolumeGroup,
            CustomConfigHandler customConfigHandler)
            throws Exception {
        Map<String, Collection<VolumeURIHLU>> volumeGroup = new HashMap<String, Collection<VolumeURIHLU>>();
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        try {
            boolean isVmax3 = storage.checkIfVmax3();
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, parentGroupName,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

            // If we were sent a single non-cascading storage group, keep the algorithm simple.
            // Make sure the policy matches ours, if so grab the volumes.
            if (!isCascadedSG(storage, maskingGroupPath)) {
                // TODO: Add VMAX3 check and get fast policy for VMAX3 volume.
                String policyName = getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage,
                        maskingGroupPath);

                volumeGroup.put(parentGroupName, new ArrayList<VolumeURIHLU>());
                for (Entry<StorageGroupPolicyLimitsParam, Collection<VolumeURIHLU>> policyToVolumeEntry : policyLimitsParamToVolumeGroup
                        .asMap().entrySet()) {

                    if (policyName != null && policyName.equalsIgnoreCase(Constants.NONE.toString())) {
                        policyName = null;
                    }

                    String volumePolicy = policyToVolumeEntry.getKey().getAutoTierPolicyName();
                    if (volumePolicy != null && volumePolicy.equalsIgnoreCase(Constants.NONE.toString())) {
                        volumePolicy = null;
                    }

                    if ((policyName == null && volumePolicy == null) ||
                            (policyName != null && policyName.equalsIgnoreCase(volumePolicy))) {
                        volumeGroup.get(parentGroupName).addAll(policyToVolumeEntry.getValue());
                    }

                    // Exception to the rule: If we are looking to place a FAST volume, and we have a flat
                    // (non-cascaded) SG,
                    // We want to be in the map as well.
                    if (volumePolicy != null && policyName == null) {
                        volumeGroup.get(parentGroupName).addAll(policyToVolumeEntry.getValue());
                    }
                }

                if (!volumeGroup.get(parentGroupName).isEmpty()) {
                    _log.info("Storage Group  {} is not a cascading group, hence grouping all volumes under parent group.",
                            parentGroupName);
                    return volumeGroup;
                }
            }

            _log.info("Trying to find child Storage Groups for given Parent Group {}", parentGroupName);

            /** get list of child storage groups */
            if (isVmax3) {
                cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                        PS_V3_STORAGE_GROUP_PROPERTIES);
            } else {
                cimInstanceItr = getAssociatorInstances(storage, maskingGroupPath, null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                        new String[] { CP_ELEMENT_NAME, EMC_MAX_BANDWIDTH, EMC_MAX_IO });
            }

            Set<StorageGroupPolicyLimitsParam> storageGroupPolicyLimitsParamSet = new HashSet<StorageGroupPolicyLimitsParam>();
            /**
             * Loop through each Storage Group, find if its associated with fast Policy, limit bandwidth, and limit IO.
             * Try to look
             * up the given policyToVolumeMap data, to find out the right storage group bucket for
             * these volumes
             */
            Map<String, Integer> preferedChildGroupMap = new HashMap<String, Integer>();
            Map<StorageGroupPolicyLimitsParam, String> preferedPolicyLimitsParamToChildGroup = new HashMap<StorageGroupPolicyLimitsParam, String>();

            while (cimInstanceItr.hasNext()) {
                CIMInstance childGroupInstance = cimInstanceItr.next();
                String groupName = CIMPropertyFactory.getPropertyValue(childGroupInstance, CP_ELEMENT_NAME);

                /**
                 * Get the properties (policyName, Bandwidth,IOPS) associated with this Storage Group
                 */
                StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createStorageGroupPolicyLimitsParam(storage,
                        childGroupInstance);

                _log.info("Group Name {} is associated with Fast Policy : {}", groupName,
                        storageGroupPolicyLimitsParam.getAutoTierPolicyName());
                // Count the number of volumes in this group. Lowest number of volumes in the storage
                // group for that child storage group wins.
                Integer numVolumes = getVMAXStorageGroupVolumeCount(storage, groupName);
                String policyLimitAttribute = storageGroupPolicyLimitsParam.toString();

                // This will identify the storage group with the lowest number of volumes and store the group name and
                // policy limit params
                // for that group name.
                if ((preferedChildGroupMap.get(policyLimitAttribute) == null) ||
                        ((preferedChildGroupMap.get(policyLimitAttribute) != null) &&
                        (numVolumes < preferedChildGroupMap.get(policyLimitAttribute)))) {
                    preferedChildGroupMap.put(policyLimitAttribute, numVolumes);
                    preferedPolicyLimitsParamToChildGroup.put(storageGroupPolicyLimitsParam, groupName);
                }
            }

            // Now place the volumes in the respective storage group.
            for (StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam : preferedPolicyLimitsParamToChildGroup.keySet()) {
                if (policyLimitsParamToVolumeGroup.containsKey(storageGroupPolicyLimitsParam)
                        && !storageGroupPolicyLimitsParamSet.contains(storageGroupPolicyLimitsParam)) {
                    volumeGroup.put(preferedPolicyLimitsParamToChildGroup.get(storageGroupPolicyLimitsParam),
                            policyLimitsParamToVolumeGroup.get(storageGroupPolicyLimitsParam));
                    storageGroupPolicyLimitsParamSet.add(storageGroupPolicyLimitsParam);
                }
            }
            _log.info("Storage Group exists already for given volume's fast Policies -->{}",
                    Joiner.on("\t").join(storageGroupPolicyLimitsParamSet));

            Set<String> existingGroupNames = getExistingStorageGroupsFromArray(storage);

            /**
             * At this point, volumes with expected fast policies might have been already grouped. A
             * new group needs to be created, for each remaining volumes, if non-fast. If fast
             * enabled, then run Storage Group Selection Process to find any existing Storage Groups
             * can be reused, if not, create a new storage group.
             */

            /**
             * At this point, we have the list of the volume groups (FP + bandwidth+IOPS), which has a Storage Group.
             * For the remaining volume groups, we need to create a new Storage Group.
             *
             * No changes needed
             */
            for (Entry<StorageGroupPolicyLimitsParam, Collection<VolumeURIHLU>> policyToVolumeEntry : policyLimitsParamToVolumeGroup
                    .asMap().entrySet()) {
                StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = policyToVolumeEntry.getKey();
                if (!storageGroupPolicyLimitsParamSet.contains(storageGroupPolicyLimitsParam)) {
                    _log.debug("Policy {} to which new Storage Group needs to be created", storageGroupPolicyLimitsParam);
                    ListMultimap<String, VolumeURIHLU> expectedVolumeHluMap = ControllerUtils
                            .getVolumeNativeGuids(policyToVolumeEntry.getValue(), _dbClient);
                    if (!StringUtils.equalsIgnoreCase(storageGroupPolicyLimitsParam.getAutoTierPolicyName(), Constants.NONE)) {
                        _log.info("Running Storage Group Selection Process to find out if any groups can be reused");

                        Map<String, Set<String>> existingReusableGroups = findAnyStorageGroupsCanBeReUsed(storage, expectedVolumeHluMap,
                                storageGroupPolicyLimitsParam);
                        // add existing group names, use later to add these
                        // groups to parent Cascaded Group.
                        _log.info("Existing Reusable Storage Groups Found {}", Joiner.on("\t").join(existingReusableGroups.keySet()));
                        for (String group : existingReusableGroups.keySet()) {
                            volumeGroup.put(group, null);
                        }
                        // find out remaining volumes which doesn't have any
                        // groups to fit into
                        Set<String> volumesInReusableStorageGroups = constructVolumeNativeGuids(existingReusableGroups
                                .values());
                        Set<String> volumesNotPartOfAnyGroup = Sets.difference(expectedVolumeHluMap
                                .asMap().keySet(), volumesInReusableStorageGroups);
                        _log.debug("Volumes not part of any Existing Storage Groups {}", Joiner.on("\t").join(volumesNotPartOfAnyGroup));
                        // create a new group for volumes, which doesn't have
                        // right storage groups
                        if (!volumesNotPartOfAnyGroup.isEmpty()) {
                            _log.info("Creating an new Volume Group for these Volumes");
                            VolumeURIHLU[] volumeURIHLU = ControllerUtils.constructVolumeUriHLUs(
                                    volumesNotPartOfAnyGroup, expectedVolumeHluMap);

                            sgDataSource.addProperty(CustomConfigConstants.AUTO_TIERING_POLICY_NAME,
                                    storageGroupPolicyLimitsParam.toString());

                            String storageGroupName = customConfigHandler.getComputedCustomConfigValue(sgCustomTemplateName,
                                    storage.getSystemType(), sgDataSource);

                            String generatedGroupName = generateGroupName(existingGroupNames, storageGroupName);
                            volumeGroup.put(
                                    generatedGroupName,
                                    Arrays.asList(volumeURIHLU));
                        }
                    } else {
                        // TODO check if this need to taken care off for VMAX3 as volumes will have policy name
                        _log.info("Creating a new Storage Group always, as non fast");
                        sgDataSource.addProperty(CustomConfigConstants.AUTO_TIERING_POLICY_NAME,
                                StorageGroupPolicyLimitsParam.NON_FAST_POLICY);
                        String storageGroupName = customConfigHandler.getComputedCustomConfigValue(sgCustomTemplateName,
                                storage.getSystemType(), sgDataSource);
                        String generatedGroupName = generateGroupName(existingGroupNames, storageGroupName);
                        volumeGroup.put(generatedGroupName, policyToVolumeEntry.getValue());
                    }
                }
            }
        } finally {
            closeCIMIterator(cimInstanceItr);
        }
        return volumeGroup;
    }

    /**
     * generate Group Name based on policy and host settings, which doesn't exist in array already.
     *
     * @param storageGroupPolicyLimitsParam
     * @param existingGroupNames
     * @param parentGroupName
     * @return
     */
    public String generateGroupName(Set<String> existingGroupNames, String storageGroupName) {
        // replace "+" in the group name with "-" to make sure that we do not hit limitation on vmax3 for group names with
        // "+" and do not return false positive here for such a mask. For example, mask with "+"s does not exist on array, but
        // when we replace "+"s bu "_"s we hit the existing mask on device. We will also convert them to lower case to avoid
        // hitting case sensitive issue during the search
        String storageGroupNameTemp = storageGroupName.replaceAll(Constants.SMIS_PLUS_REGEX, Constants.HYPHEN);
        _log.info("Converted storage group name from {} to {}", storageGroupName, storageGroupNameTemp);
        String result = storageGroupNameTemp;
        // Is 'storageGroupName' already in the list of existing names?
        if (existingGroupNames.contains(storageGroupNameTemp.toLowerCase())) {
            // Yes -- name is already in the existing group name list. We're going to have to generate a unique name by
            // using an appended
            // numeric index. The format will be storageGroupName_<[N]>, where N is a number between 1 and the size of
            // existingGroupNames.
            int size = existingGroupNames.size();
            for (int index = 1; index <= size; index++) {
                // Generate an indexed name ...
                result = String.format("%s_%d", storageGroupNameTemp, index);
                // If the indexed name does not exist, then exit the loop and return 'result'
                if (!existingGroupNames.contains(result.toLowerCase())) {
                    break;
                }
            }
        }
        _log.info(String.format("generateGroupName(existingGroupNames.size = %d, %s), returning %s", existingGroupNames.size(),
                storageGroupNameTemp, result));
        return result;
    }

    /**
     * TODO: VMAX3 Customized names
     * generate Group Names which doesn't exist in array already.
     *
     * @param count
     * @param existingGroupNames
     * @param parentGroupName
     * @return
     */
    public String generateGroupName(String policyName, Set<String> existingGroupNames, String parentGroupName) {
        int count = 0;
        String format = null;
        while (count <= existingGroupNames.size()) {
            if (0 == count) {
                format = String.format("SG_%s", policyName);
            } else {
                format = String.format("SG_%s_%d", policyName, count);
            }
            String generatedGroupName = generate(parentGroupName, format,
                    SmisConstants.MASK_NAME_DELIMITER,
                    SmisConstants.MAX_STORAGE_GROUP_NAME_LENGTH);
            if (!existingGroupNames.contains(generatedGroupName)) {
                return generatedGroupName;
            }
            count++;
        }
        // we will not hit this scenario any time in a real case.
        return generate(parentGroupName, String.format("SG1_%s", policyName),
                SmisConstants.MASK_NAME_DELIMITER,
                SmisConstants.MAX_STORAGE_GROUP_NAME_LENGTH);
    }

    /**
     * get SGs from Array. I hit a failure in this query off and on during my testing,
     * so I am using this method for a small retry method.
     *
     * @param storage
     *            storage system
     * @return set of storage group names
     */
    public Map<StorageGroupPolicyLimitsParam, Set<String>> getExistingSGNamesFromArray(StorageSystem storage) {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        Map<StorageGroupPolicyLimitsParam, Set<String>> storageGroups = new HashMap<StorageGroupPolicyLimitsParam, Set<String>>();
        final int RETRY_COUNT = 5;
        final int RETRY_SLEEP_MS = 5000;
        int retry = 0;
        while (retry < RETRY_COUNT) {
            try {
                CIMObjectPath deviceMaskingGroupPath = CimObjectPathCreator.createInstance(
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(),
                        ROOT_EMC_NAMESPACE);
                groupInstanceItr = getEnumerateInstances(storage, deviceMaskingGroupPath,
                        new String[] { CP_ELEMENT_NAME, EMC_MAX_BANDWIDTH, EMC_MAX_IO });
                while (groupInstanceItr.hasNext()) {
                    CIMInstance groupInstance = groupInstanceItr.next();
                    CIMObjectPath groupPath = groupInstance.getObjectPath();
                    // skipping device masking groups other than belong to expected
                    // storage system
                    if (!groupPath.toString().contains(storage.getSerialNumber())) {
                        continue;
                    }

                    String policyName = getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage, groupPath);

                    String groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);
                    String hostIOLimitBandwidth = CIMPropertyFactory.getPropertyValue(groupInstance, EMC_MAX_BANDWIDTH);
                    String hostIOLimitIOPs = CIMPropertyFactory.getPropertyValue(groupInstance, EMC_MAX_IO);

                    StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(policyName,
                            hostIOLimitBandwidth,
                            hostIOLimitIOPs, storage);
                    Set<String> storageGroupNames = storageGroups.get(storageGroupPolicyLimitsParam);
                    if (storageGroupNames == null) {
                        storageGroupNames = new HashSet<String>();
                        storageGroups.put(storageGroupPolicyLimitsParam, storageGroupNames);
                    }
                    storageGroupNames.add(groupName);

                }
                _log.debug("Existing Group Names on Array : {}", Joiner.on("\t").join(storageGroups.values()));
                return storageGroups;
            } catch (Exception e) {
                _log.warn("Get Existing SG Names failed", e);
                if (retry <= RETRY_COUNT) {
                    _log.warn(String.format("Going to retry (%d out of %d tries) SG name query in %s milliseconds",
                            retry + 1, RETRY_COUNT, RETRY_SLEEP_MS));
                    try {
                        Thread.sleep(RETRY_SLEEP_MS);
                    } catch (InterruptedException e1) {
                        // ignore
                    }
                }
            } finally {
                closeCIMIterator(groupInstanceItr);
            }
            retry++;
        }
        _log.debug("Existing Group Names on Array : {}", Joiner.on("\t").join(storageGroups.values()));
        return storageGroups;
    }

    /**
     * Get Existing Storage Group Names
     *
     * @param storage
     * @return
     */
    public Set<String> getExistingStorageGroupsFromArray(StorageSystem storage) {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        Set<String> storageGroupNames = new HashSet<String>();
        try {
            CIMObjectPath storageGroupPath = CimObjectPathCreator.createInstance(
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), ROOT_EMC_NAMESPACE);
            groupInstanceItr = getEnumerateInstances(storage, storageGroupPath, SmisConstants.PS_ELEMENT_NAME);
            while (groupInstanceItr.hasNext()) {
                CIMInstance groupInstance = groupInstanceItr.next();
                storageGroupNames.add((groupInstance.getPropertyValue(CP_ELEMENT_NAME).toString()).toLowerCase());
            }
        } catch (Exception e) {
            _log.warn("Get Existing port Group Names failed", e);
        } finally {
            closeCIMIterator(groupInstanceItr);
        }
        return storageGroupNames;

    }

    /**
     * Determines which of the passed volumes is in a storage group that is not a
     * parking storage group.
     * 
     * @param storage A reference to the storage system.
     * @param volumeURIs The URIs of the volumes to check.
     * 
     * @return The URIs of the volumes in non parking storage groups mapped by storage group name.
     */
    public Map<String, List<URI>> getVolumesInNonParkingStorageGroup(StorageSystem storage, List<URI> volumeURIs) {
        CloseableIterator<CIMInstance> storageGroupInstanceItr = null;
        Map<String, List<URI>> storageGroupMap = new HashMap<>();
        try {
            CIMObjectPath storageGroupPath = CimObjectPathCreator.createInstance(
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), ROOT_EMC_NAMESPACE);
            storageGroupInstanceItr = getEnumerateInstances(storage, storageGroupPath, SmisConstants.PS_ELEMENT_NAME);
            while (storageGroupInstanceItr.hasNext()) {
                CIMInstance storageGroupInstance = storageGroupInstanceItr.next();
                String storageGroupName = storageGroupInstance.getPropertyValue(CP_ELEMENT_NAME).toString();
                // Parking storage groups starts with "ViPR_".
                if (storageGroupName.startsWith(Constants.STORAGE_GROUP_PREFIX)) {
                    continue;
                }
                
                List<URI> volumesInStorageGroup = findVolumesInStorageGroup(storage, storageGroupName, volumeURIs);
                if (volumesInStorageGroup != null && !volumesInStorageGroup.isEmpty()) {
                    storageGroupMap.put(storageGroupName, volumesInStorageGroup);
                }
            }
        } catch (Exception e) {
            _log.warn("Get export mask volumes in non parking storage groups failed", e);
        } finally {
            closeCIMIterator(storageGroupInstanceItr);
        }
        
        return storageGroupMap;
    }

    /**
     * Get Existing Port Group Names
     *
     * @param storage
     * @return
     */
    public Set<String> getExistingPortGroupsFromArray(StorageSystem storage) {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        Set<String> portGroupNames = new HashSet<String>();
        try {
            CIMObjectPath portMaskingGroupPath = CimObjectPathCreator.createInstance(
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup.name(), ROOT_EMC_NAMESPACE);
            groupInstanceItr = getEnumerateInstances(storage, portMaskingGroupPath, SmisConstants.PS_ELEMENT_NAME);
            while (groupInstanceItr.hasNext()) {
                CIMInstance groupInstance = groupInstanceItr.next();
                portGroupNames.add((groupInstance.getPropertyValue(CP_ELEMENT_NAME).toString()).toLowerCase());
            }
        } catch (Exception e) {
            _log.warn("Get Existing port Group Names failed", e);
        } finally {
            closeCIMIterator(groupInstanceItr);
        }
        return portGroupNames;

    }

    /**
     * Get Existing Initiator Group Names from Array
     *
     * @param storage
     * @return
     */
    public Set<String> getExistingInitiatorGroupsFromArray(StorageSystem storage) {
        CloseableIterator<CIMInstance> groupInstanceItr = null;
        Set<String> initiatorGroupNames = new HashSet<String>();
        try {
            CIMObjectPath portMaskingGroupPath = CimObjectPathCreator.createInstance(
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup.name(), ROOT_EMC_NAMESPACE);
            groupInstanceItr = getEnumerateInstances(storage, portMaskingGroupPath, SmisConstants.PS_ELEMENT_NAME);
            while (groupInstanceItr.hasNext()) {
                CIMInstance groupInstance = groupInstanceItr.next();
                initiatorGroupNames.add((groupInstance.getPropertyValue(CP_ELEMENT_NAME).toString()).toLowerCase());
            }
        } catch (Exception e) {
            _log.warn("Get Existing initiator Group Names failed", e);
        } finally {
            closeCIMIterator(groupInstanceItr);
        }
        return initiatorGroupNames;

    }

    /**
     * Giver 2 strings: str1 and str2, this method concatenates them by using the delimeter
     * and restricting the size of the resulting string to maxLength.
     *
     * Did not want to use NameGenerator._generate as that takes three inputs and if the third input
     * is null it appends a random UUID - which we do not want in the users of this method.
     */
    public String generate(String str1, String str2,
            char delimiter, int maxLength) {
        return _nameGenerator.generate(str1, str2, null, delimiter, maxLength);
    }

    public String getVolumeNativeGuid(CIMObjectPath path) {
        String systemName = path.getKey(CP_SYSTEM_NAME).getValue().toString();
        systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        String id = path.getKey(CP_DEVICE_ID).getValue().toString();
        return NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                systemName.toUpperCase(), id);
    }

    public String getVolumeDeviceId(CIMObjectPath path) {
        String id = path.getKey(CP_DEVICE_ID).getValue().toString();
        return id;
    }

    public String getConsistencyGroupName(final BlockConsistencyGroup group,
            final StorageSystem storageSystem) {
        String groupName = null;

        if (group != null && storageSystem != null) {
            groupName = group.getCgNameOnStorageSystem(storageSystem.getId());
        }

        return groupName;
    }

    public boolean findReplicationGroupPartOfSRDFRelationShip(final CIMObjectPath replicationGroupPath,
            final StorageSystem forProvider, final StorageSystem system) {
        CloseableIterator<CIMObjectPath> names = null;
        try {
            names = getAssociatorNames(forProvider, replicationGroupPath, null, SE_GROUP_SYNCHRONIZED_RG_RG, null,
                    null);
            if (names.hasNext()) {
                return true;
            }
        } catch (WBEMException e) {
            _log.error("findReplicationGroupPartOfSRDFRelationShip -- WBEMException: ", e);
        } finally {
            closeCIMIterator(names);
        }
        return false;
    }

    /**
     * This is wrapper of the WBEM modifyIstance interface
     *
     * @param storage
     *            [required] - StorageSystem object representing the array
     * @param instance
     *            [required] - CIMInstance
     * @param properties
     *            [required] - Names of properties to update their values
     * @throws WBEMException
     */
    public void modifyInstance(StorageSystem storage, CIMInstance instance,
            String[] properties) throws WBEMException {
        getConnection(storage).getCimClient().modifyInstance(instance, properties);
    }

    public static String getPrefix(StorageSystem storage) {
        String prefix = "";
        if (storage.getSystemType().equals(StorageSystem.Type.vmax.name())) {
            prefix = VMAX_PREFIX;
        } else if (storage.getSystemType().equals(StorageSystem.Type.vnxblock.name())) {
            prefix = VNX_PREFIX;
        }
        return prefix;
    }

    private static int getArrayType(StorageSystem storage) {
        int type;
        if (storage.getSystemType().equals(StorageSystem.Type.vnxblock.name())) {
            type = 1;
        } else if (storage.getSystemType().equals(StorageSystem.Type.vmax.name())) {
            type = 2;
        } else {
            type = 0;
        }
        return type;
    }

    public static String getSmisStorageDeviceName(StorageSystem storage) {
        return getPrefix(storage) + "SmisDevice";
    }

    /**
     * Simple puts the thread to sleep for the passed duration.
     *
     * @param duration
     *            How long to pause in milliseconds.
     */
    static void pauseThread(long duration) {
        try {
            Thread.sleep(duration);
        } catch (Exception e) {
            _log.warn("Exception while trying to sleep", e);
        }
    }

    /**
     * FIXME
     * This method is a temporary alternative to the one below and
     * removes the initial waiting time (~3 minutes) before refreshing.
     *
     * The wait time is deemed unnecessary because the Provider will attempt
     * to consolidate its sync requests.
     *
     * Still, there appears to be a requirement for waiting an arbitrary amount of
     * time *after* the refresh call in order for subsequent associator calls to
     * get correct data.
     *
     * @param storage
     * @return
     * @throws WBEMException
     */
    public Object callRefreshSystem(StorageSystem storage) throws WBEMException {
        Object result = null;
        String lockKey = String.format("callRefreshSystem-%s",
                storage.getId().toString());

        try {
            if (_locker.acquireLock(lockKey, MAX_REFRESH_LOCK_WAIT_TIME)) {
                CIMObjectPath seSystemRegistrationSvc = getRegistrationService(storage);
                UnsignedInteger32[] syncType = new UnsignedInteger32[] {
                        new UnsignedInteger32(REPLICATION_DATA_SYNC_TYPE),
                        new UnsignedInteger32(DEVICES_SYNC_TYPE),
                        new UnsignedInteger32(MASKING_SYNC_TYPE)
                };
                CIMObjectPath[] systems = new CIMObjectPath[] {
                        _cimPath.getStorageSystem(storage)
                };
                CIMArgument[] refreshArgs = new CIMArgument[] {
                        _cimArgument.uint32Array(CP_SYNC_TYPE, syncType),
                        _cimArgument.referenceArray(CP_SYSTEMS, systems)
                };
                CIMArgument[] outArgs = new CIMArgument[5];
                result = invokeMethod(storage, seSystemRegistrationSvc,
                        EMC_REFRESH_SYSTEM, refreshArgs, outArgs);
                long currentMillis = Calendar.getInstance().getTimeInMillis();
                storage.setLastRefresh(currentMillis);
                _dbClient.updateObject(storage);

                _log.info(String.format("Did EMCRefresh against StorageSystem %s. " +
                        "Last refresh set to %d", storage.getNativeGuid(),
                        currentMillis));
            }
        } finally {
            _locker.releaseLock(lockKey);
        }

        return result;
    }

    public Object callRefreshSystem(StorageSystem storage,
            SimpleFunction toCallAfterRefresh)
            throws WBEMException {
        return callRefreshSystem(storage, toCallAfterRefresh, false);
    }

    public Object callRefreshSystem(StorageSystem storage,
            SimpleFunction toCallAfterRefresh, boolean force)
            throws WBEMException {
        Object result = null;
        String lockKey = String.format("callRefreshSystem-%s",
                storage.getId().toString());
        try {
            if (_locker.acquireLock(lockKey, MAX_REFRESH_LOCK_WAIT_TIME)) {
                storage = _dbClient.queryObject(StorageSystem.class, storage.getId());
                long currentMillis = Calendar.getInstance().getTimeInMillis();
                long deltaLastRefreshValue = currentMillis - storage.getLastRefresh();
                if (deltaLastRefreshValue < REFRESH_THRESHOLD && force) {
                    // In case of SRDF Active mode resume operation, its possible that second call
                    // to refreshSystem might be done where REFRESH_THRESHOLD value might not be met so we
                    // will pause thread for the remainder of the time as we need to make sure refresh
                    // system is executed to be able to get correct access state for the target volumes.
                    long sleepDuration = REFRESH_THRESHOLD - deltaLastRefreshValue;
                    _log.info(String.format("Sleep for %d msecs before calling refresh because last "
                            + "refresh was done %d msecs ago", sleepDuration, deltaLastRefreshValue));
                    pauseThread(sleepDuration);
                    currentMillis = Calendar.getInstance().getTimeInMillis();
                    deltaLastRefreshValue = currentMillis - storage.getLastRefresh();
                }

                // Do a basic calculation of how long we had to wait for the
                // acquireLock to finish. If were waiting for a long time, then
                // presumably the deltaLastRefreshValue would be non-zero and much
                // more than a few milliseconds. If it is beyond our threshold,
                // then we will assume that the refresh needs to be done. Otherwise,
                // there were multiple threads (possibly on different nodes) attempting
                // the refresh at the same time and hence it is not necessary to run it
                // again so soon.
                if (deltaLastRefreshValue >= REFRESH_THRESHOLD) {
                    _log.info(String.format("Difference between current time %d and " +
                            "lastRefresh %d is %d, greater than or equal to threshold %d - will " +
                            "attempt EMCRefresh", currentMillis,
                            storage.getLastRefresh(), deltaLastRefreshValue,
                            REFRESH_THRESHOLD));
                    CIMObjectPath seSystemRegistrationSvc = getRegistrationService(storage);
                    UnsignedInteger32[] syncType = new UnsignedInteger32[] {
                            new UnsignedInteger32(REPLICATION_DATA_SYNC_TYPE),
                            new UnsignedInteger32(DEVICES_SYNC_TYPE)
                    };
                    CIMObjectPath[] systems = new CIMObjectPath[] {
                            _cimPath.getStorageSystem(storage)
                    };
                    CIMArgument[] refreshArgs = new CIMArgument[] {
                            _cimArgument.uint32Array(CP_SYNC_TYPE, syncType),
                            _cimArgument.referenceArray(CP_SYSTEMS, systems)
                    };
                    CIMArgument[] outArgs = new CIMArgument[5];
                    result = invokeMethod(storage, seSystemRegistrationSvc,
                            EMC_REFRESH_SYSTEM, refreshArgs, outArgs);
                    currentMillis = Calendar.getInstance().getTimeInMillis();
                    storage.setLastRefresh(currentMillis);
                    _dbClient.persistObject(storage);
                    _log.info(String.format("Did EMCRefresh against StorageSystem %s. " +
                            "Last refresh set to %d", storage.getNativeGuid(),
                            currentMillis));
                    if (toCallAfterRefresh != null) {
                        toCallAfterRefresh.call();
                    }
                } else {
                    _log.info(String.format("Did not run EMCRefresh against " +
                            "StorageSystem %s because it was done %d msecs ago, " +
                            "which is within in the refresh threshold %d",
                            storage.getNativeGuid(), deltaLastRefreshValue,
                            REFRESH_THRESHOLD));
                }
            } else {
                _log.info(String.format("Could not get the EMCRefresh lock after %d seconds.",
                        MAX_REFRESH_LOCK_WAIT_TIME));
            }
        } finally {
            _locker.releaseLock(lockKey);
        }
        return result;
    }

    public CIMObjectPath getRegistrationService(StorageSystem storage) throws WBEMException {
        return _cimPath.getSeSystemRegistrationService(storage);
    }

    public CIMArgument[] getAddStorageCIMArguments(StorageSystem storage) throws WBEMException {
        String[] ips;
        UnsignedInteger16[] types;
        if (storage.getSecondaryIPs() == null) {
            ips = new String[] { storage.getIpAddress() };
            types = new UnsignedInteger16[] { new UnsignedInteger16(2) };
        } else {
            ips = new String[storage.getSecondaryIPs().size() + 1];
            types = new UnsignedInteger16[ips.length];
            ips[0] = storage.getIpAddress();
            types[0] = new UnsignedInteger16(2);
            int idx = 1;
            for (String ip : storage.getSecondaryIPs()) {
                ips[idx] = ip;
                types[idx] = new UnsignedInteger16(2);
                idx++;
            }
        }
        CIMArgument[] addSysArgs = new CIMArgument[] {
                _cimArgument.uint16("ArrayType", getArrayType(storage)),
                _cimArgument.stringArray("Addresses", ips),
                _cimArgument.uint16Array("Types", types),
                _cimArgument.string("User", storage.getUsername()),
                _cimArgument.string("Password", storage.getPassword())
        };
        return addSysArgs;
    }

    public CIMArgument[] getRemStorageCIMArguments(StorageSystem storage) throws WBEMException {
        return new CIMArgument[] {
                _cimArgument.reference("System", _cimPath.getStorageSystem(storage))
        };
    }

    public CIMArgument[] getCreateSynchronizationAspectForGroupInput(CIMObjectPath replicationGroup, boolean skipRefresh,
            String name, Integer mode) {
        return getCreateSynchronizationAspectInput(replicationGroup, skipRefresh, name, mode, CP_SOURCE_GROUP);
    }

    public CIMArgument[] getCreateSynchronizationAspectInput(CIMObjectPath sourcePath, boolean skipRefresh,
            String name, Integer mode) {
        return getCreateSynchronizationAspectInput(sourcePath, skipRefresh, name, mode, CP_SOURCE_ELEMENT);
    }

    /**
     * Get the SMI-S input arguments when creating a CIM_SynchronizedAspectForSource, i.e,
     * an array snapshot point-in-time copy, for the source with the passed path.
     *
     * @param sourcePath
     *            The CIM object path for the array snapshot source.
     * @param skipRefresh
     *            true if the skipRefresh argument should be included.
     * @param name
     *            The name for the array snapshot, or null for no name.
     * @param mode
     *            The update mode, or null.
     * @param sourceParameter
     *            Parameter specifying either SourceElement or SourceGroup
     *
     * @return An array of CIMArgument
     */
    public CIMArgument[] getCreateSynchronizationAspectInput(CIMObjectPath sourcePath, boolean skipRefresh, String name,
            Integer mode, String sourceParameter) {
        List<CIMArgument> argList = new ArrayList<CIMArgument>();
        argList.add(_cimArgument.uint16(CP_SYNC_TYPE, SNAPSHOT_VALUE));
        argList.add(_cimArgument.reference(sourceParameter, sourcePath));

        // If skip refresh, add argument.
        if (skipRefresh) {
            argList.add(_cimArgument.bool(CP_EMC_SKIP_REFRESH, true));
        }

        // Add argument if name is specified.
        if (name != null) {
            argList.add(_cimArgument.string(CP_NAME, name));
        }

        // Add argument if mode is specified.
        if (mode != null) {
            argList.add(_cimArgument.uint16(CP_MODE, mode.intValue()));
        }

        return argList.toArray(new CIMArgument[argList.size()]);
    }

    public CIMArgument[] getDeleteStorageHardwareIDArgs(StorageSystem storage, Initiator initiator)
            throws Exception {
        String[] initiatorNames = getInitiatorNames(Arrays.asList(initiator), storage);
        CIMObjectPath[] initiators = _cimPath.getInitiatorPaths(storage, initiatorNames);
        return new CIMArgument[] {
                _cimArgument.reference(CP_HARDWARE_ID, initiators[0])
        };
    }

    public CloseableIterator<CIMInstance> getSymmLunMaskingViews(StorageSystem storage) throws Exception {
        return getLunMaskingProtocolControllers(storage,
                SYMM_LUN_MASKING_VIEW);
    }

    public CIMInstance getSymmLunMaskingView(StorageSystem storage,
            ExportMask mask) throws Exception {
        return getInstance(storage,
                _cimPath.getMaskingViewPath(storage, mask.getMaskName()), false, true,
                null);
    }

    /**
     * Returns a CloseableIterator for ClarLunMaskingProtocolController CIMInstance objects. NOTE:
     * This will return all instances from all arrays that the SMISProvider upon which the 'storage'
     * system is managed.
     *
     * @param storage
     *            [in] - StorageSystem object. Used to look up SMIS connection.
     * @return CloseableIterator of CIMInstance objects
     * @throws Exception
     */
    public CloseableIterator<CIMInstance> getClarLunMaskingProtocolControllers(StorageSystem storage) throws Exception {

        return getLunMaskingProtocolControllers(storage,
                CLAR_LUN_MASKING_SCSI_PROTOCOL_CONTROLLER);
    }

    /**
     * Returns a CloseableIterator for protocol controller CIMInstance objects. NOTE: This will
     * return all instances from all arrays that the SMISProvider upon which the 'storage' system is
     * managed.
     *
     * @param storage
     *            [in] - StorageSystem object. Used to look up SMIS connection.
     * @param className
     *            [in] - Name of CIM class to enumerate
     * @return CloseableIterator of CIMInstance objects
     * @throws Exception
     */
    public CloseableIterator<CIMInstance> getLunMaskingProtocolControllers(StorageSystem storage, String className) throws Exception {
        CIMObjectPath controllerConfigSvcPath = _cimPath.getControllerConfigSvcPath(storage);

        return getAssociatorInstances(storage, controllerConfigSvcPath, null,
                className, null, null, PS_LUN_MASKING_CNTRL_NAME_AND_ROLE);
    }

    public CloseableIterator<CIMInstance> getClarPrivileges(StorageSystem storage) throws Exception {
        return getPrivileges(storage,
                EMC_CLAR_PRIVILEGE);
    }

    public CloseableIterator<CIMInstance> getPrivileges(StorageSystem storage, String className) throws Exception {
        CIMObjectPath privilegeMgmtSvcPath = _cimPath.getPrivilegeManagementService(storage);
        return getAssociatorInstances(storage, privilegeMgmtSvcPath, null,
                className, null, null, PS_EMC_CLAR_PRIVILEGE);
    }

    public CIMInstance getLunMaskingProtocolController(StorageSystem storage,
            ExportMask exportMask)
            throws Exception {
        return getInstance(storage,
                _cimPath.getLunMaskingProtocolControllerPath(storage, exportMask),
                false, true, null);
    }

    /**
     * Will return a map of the volume WWNs to their HLU values for an instance of LunMasking
     * container on the array.
     *
     * @param client
     *            [in] - WBEM client used to read data from SMI-S
     * @param instance
     *            [in] - Instance of CIM_LunMaskingSCSIProtocolController, holding a representation
     *            of an array masking container.
     * @return - Will return a map of the volume WWNs to their HLU values for an instance of
     *         LunMasking container on the array.
     * @throws WBEMException
     */
    public Map<String, Integer> getVolumesFromLunMaskingInstance(WBEMClient client,
            CIMInstance instance) throws WBEMException {
        Map<String, Integer> wwnToHLU = new HashMap<String, Integer>();
        CloseableIterator<CIMInstance> iterator = null;
        CloseableIterator<CIMInstance> protocolControllerForUnitIter = null;
        try {
            int retry = 0;
            boolean failures = true;
            Map<String, Integer> deviceIdToHLU = new HashMap<String, Integer>();
            while (failures) {
                failures = false;
                deviceIdToHLU.clear();
                protocolControllerForUnitIter = client.referenceInstances(instance.getObjectPath(),
                        CIM_PROTOCOL_CONTROLLER_FOR_UNIT, null, false,
                        PS_DEVICE_NUMBER);
                while (protocolControllerForUnitIter.hasNext()) {
                    CIMInstance pcu = protocolControllerForUnitIter.next();
                    CIMObjectPath pcuPath = pcu.getObjectPath();
                    CIMProperty<CIMObjectPath> dependentVolumePropery = (CIMProperty<CIMObjectPath>) pcuPath.getKey(CP_DEPENDENT);
                    CIMObjectPath dependentVolumePath = dependentVolumePropery.getValue();
                    String deviceId = dependentVolumePath.getKey(CP_DEVICE_ID).getValue()
                            .toString();
                    String deviceNumber = CIMPropertyFactory.getPropertyValue(pcu,
                            CP_DEVICE_NUMBER);
                    try {
                        Integer decimalHLU = (int) Long.parseLong(deviceNumber, 16);
                        deviceIdToHLU.put(deviceId, decimalHLU);
                    } catch (NumberFormatException nfe) {
                        // We've found in testing when users remove volumes from masks, the volume will still be in the
                        // provider, but the HLU will get returned as an empty string, causing the overall operation to
                        // fail. This retry loop will ensure that we wait until the provider is returning only volumes
                        // with valid HLUs, thus removing any volumes from the mask that are in-flight outside of the
                        // controller.
                        _log.error(String.format(
                                "deviceId: %s returned an invalid HLU.  It may be currently getting removed from the mask.", deviceId));
                        failures = true;
                        if (retry++ > GET_VOLUMES_BAD_HLU_RETRIES) {
                            // rethrow
                            throw nfe;
                        }
                        try {
                            _log.warn("Retrying query operation for volumes and HLU after 10 seconds");
                            Thread.sleep(GET_VOLUMES_BAD_HLU_RETRY_SLEEP_MSEC);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            _log.debug(String.format("getVolumesFromLunMaskingInstance(%s) - deviceIdToHLU map = %s",
                    instance.getObjectPath().toString(), Joiner.on(',').join(deviceIdToHLU.entrySet())));

            iterator = client.associatorInstances(instance.getObjectPath(), null,
                    CIM_STORAGE_VOLUME, null, null, false, PS_EMCWWN);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String deviceId = cimInstance.getObjectPath().getKey(CP_DEVICE_ID)
                        .getValue().toString();
                String wwn = CIMPropertyFactory.getPropertyValue(cimInstance,
                        CP_WWN_NAME);
                wwnToHLU.put(wwn.toUpperCase(), deviceIdToHLU.get(deviceId));
            }

            _log.debug(String.format("getVolumesFromLunMaskingInstance(%s) - wwnToHLU map = %s",
                    instance.getObjectPath().toString(), Joiner.on(',').join(wwnToHLU.entrySet())));
        } catch (WBEMException we) {
            _log.error("Caught an error will attempting to get volume list from " +
                    "masking instance", we);
            throw we;
        } finally {
            closeCIMIterator(iterator);
            closeCIMIterator(protocolControllerForUnitIter);
        }
        return wwnToHLU;

    }

    /**
     * Will return a list of port names for the LunMasking container 'instance'.
     *
     * @param client
     *            [in] - WBEM client used to read data from SMI-S
     * @param instance
     *            [in] - Instance of CIM_LunMaskingSCSIProtocolController, holding a representation
     *            of an array masking container.
     * @return - Will return a list of port names for the LunMasking container.
     * @throws WBEMException
     */
    public List<String> getInitiatorsFromLunMaskingInstance(WBEMClient client,
            CIMInstance instance) throws WBEMException {
        List<String> initiatorPorts = new ArrayList<String>();
        CloseableIterator<CIMInstance> iterator = null;
        try {
            iterator = client.associatorInstances(instance.getObjectPath(), null,
                    CP_SE_STORAGE_HARDWARE_ID, null, null, false, PS_STORAGE_ID);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String initiator = CIMPropertyFactory.getPropertyValue(cimInstance,
                        CP_STORAGE_ID);
                // initiator could be iSCSI or a WWN. We need to normalize if it is a
                // WWN, so that we can compare appropriately.
                String it = Initiator.normalizePort(initiator);
                initiatorPorts.add(it);
            }
        } catch (WBEMException we) {
            _log.error("Caught an error while attempting to get initiator list from " +
                    "masking instance", we);
            throw we;
        } finally {
            closeCIMIterator(iterator);
        }
        return initiatorPorts;
    }

    /**
     * Given a CIMInstance of a ClarLunMaskingProtocolController return a list of storage ports that
     * it references.
     *
     * @param client
     *            [in] - WBEMClient to be used to talk to SMIS provider
     * @param instance
     *            [in] - CIMInstance object pointing to a ClarLunMaskingProtocolController
     * @return List of port name String values. The WWNs will have colons separating the hex digits.
     */
    public List<String> getStoragePortsFromLunMaskingInstance(WBEMClient client,
            CIMInstance instance) {
        List<String> storagePorts = new ArrayList<String>();
        CloseableIterator<CIMInstance> iterator = null;
        try {
            iterator = client.associatorInstances(instance.getObjectPath(), null,
                    CIM_PROTOCOL_ENDPOINT, null, null, false, PS_NAME);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String portName = CIMPropertyFactory.getPropertyValue(cimInstance,
                        CP_NAME);
                String fixedName = Initiator.toPortNetworkId(portName);
                storagePorts.add(fixedName);
            }
        } catch (WBEMException we) {
            _log.error("Caught an error while attempting to get storage ports from " +
                    "masking instance", we);
        } finally {
            closeCIMIterator(iterator);
        }
        return storagePorts;
    }

    public void removeGroupsFromCascadedVolumeGroup(
            StorageSystem storage, String groupName, CIMObjectPath volumeGroupPath,
            SmisJob job, boolean forceFlag) throws Exception {
        _log.debug("{} RemoveGroupsFromCascadedVolumeGroup START...", storage.getSerialNumber());
        CIMObjectPath[] volumeGroupPaths = new CIMObjectPath[] { volumeGroupPath };
        CIMArgument[] inArgs = modifyCascadedStorageGroupInputArguments(
                storage, groupName, volumeGroupPaths, forceFlag);
        CIMArgument[] outArgs = new CIMArgument[5];
        invokeMethodSynchronously(storage,
                _cimPath.getControllerConfigSvcPath(storage), "RemoveMembers", inArgs,
                outArgs, job);
        _log.debug("{} RemoveGroupsFromCascadedVolumeGroup END...", storage.getSerialNumber());
    }

    public CIMObjectPath getInitiatorGroupPath(StorageSystem storageDevice,
            String initiatorGroupName) throws Exception {
        return _cimPath.getMaskingGroupPath(storageDevice, initiatorGroupName,
                MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
    }

    public CIMArgument[] getAddInitiatorGroupToMaskingGroupInputArguments(CIMObjectPath maskingGroupPath,
            CIMObjectPath initiatorGroupPath) throws Exception {
        CIMObjectPath[] members = {};
        ArrayList<CIMObjectPath> initiatorGroupPaths = new ArrayList<CIMObjectPath>();
        initiatorGroupPaths.add(initiatorGroupPath);
        members = initiatorGroupPaths.toArray(members);
        return new CIMArgument[] {
                _cimArgument.referenceArray(CP_MEMBERS, members),
                _cimArgument.reference(CP_MASKING_GROUP, maskingGroupPath)
        };
    }

    public CIMArgument[] getCloneInputArguments(String label, CIMObjectPath sourceVolumePath, CIMObjectPath volumeGroupPath,
            StorageSystem storageDevice, StoragePool pool, boolean createInactive,
            CIMInstance replicationSettingData) {
        int waitForCopyState = (createInactive) ? PREPARED_VALUE : ACTIVATE_VALUE;
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        args.add(_cimArgument.reference(CP_SOURCE_ELEMENT, sourceVolumePath));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, CLONE_VALUE));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState));
        if (pool != null) {
            addTargetPoolToArgs(storageDevice, pool, args);
        }

        if (storageDevice.checkIfVmax3() && volumeGroupPath != null) {
            args.add(_cimArgument.referenceArray(SmisConstants.CP_COLLECTIONS, new CIMObjectPath[] { volumeGroupPath }));
        }

        if (replicationSettingData != null) {
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, replicationSettingData));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    private void addTargetPoolToArgs(StorageSystem storageSystem, StoragePool pool, List<CIMArgument> args) {
        CIMProperty[] inPoolPropKeys = {
                _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageSystem, pool.getNativeId()))
        };
        CIMObjectPath inPoolPath = CimObjectPathCreator.createInstance(pool.getPoolClassName(),
                _cimConnection.getNamespace(storageSystem),
                inPoolPropKeys);
        args.add(_cimArgument.reference(CP_TARGET_POOL, inPoolPath));
    }

    /**
     * Retrieve the Clar_SettingsDefineState_RG_SAFS instance represented by the given
     * Clar_SynchronizationAspectForSourceGroup instance ID
     *
     * @param storageSystem
     *            - StorageArray reference, will be used to lookup SMI-S connection
     * @param synchAspectinstanceID
     *            - Clar_SynchronizationAspectForSourceGroup instance ID to look for
     * @return
     * @throws Exception
     *             - If it is not able to find the given id
     */
    public CIMObjectPath getSettingsDefineStateForSource(StorageSystem storageSystem, String synchAspectinstanceID) throws Exception {
        boolean isVmax = (storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vmax.name()));
        String className = (isVmax) ? SYMM_SETTINGS_DEFINE_STATE_SV_SAFS : CLAR_SETTINGS_DEFINE_STATE_SV_SAFS;
        CloseableIterator<CIMInstance> instances = getInstances(storageSystem,
                ROOT_EMC_NAMESPACE, className, true, false,
                false, new String[] { CP_INSTANCE_ID });
        try {
            while (instances.hasNext()) {
                CIMInstance instance = instances.next();
                CIMObjectPath settingsPath = instance.getObjectPath();
                CIMObjectPath syncPath = (CIMObjectPath) settingsPath.getKey(
                        CP_SETTING_DATA).getValue();
                String instanceID = (String) syncPath.getKey(CP_INSTANCE_ID).getValue();
                if (instanceID.equals(synchAspectinstanceID)) {
                    return settingsPath;
                }
            }
        } finally {
            closeCIMIterator(instances);
        }
        throw new ServiceCodeException(ServiceCode.CONTROLLER_ERROR,
                "Unable to find instance of Clar_SettingsDefineState_SV_SAFS with " +
                        "Clar_SynchronizationAspectForSource={0}",
                new Object[] { synchAspectinstanceID });
    }

    /**
     * Retrieve the Clar_SettingsDefineState_RG_SAFS instance represented by the given
     * Clar_SynchronizationAspectForSourceGroup instance ID
     *
     * @param storageSystem
     *            - StorageArray reference, will be used to lookup SMI-S connection
     * @param synchAspectinstanceID
     *            - Clar_SynchronizationAspectForSourceGroup instance ID to look for
     * @return
     * @throws Exception
     *             - If it is not able to find the given id
     */
    public CIMObjectPath getSettingsDefineStateForSourceGroup(StorageSystem storageSystem, String synchAspectinstanceID) throws Exception {
        CloseableIterator<CIMInstance> instances = getInstances(storageSystem, ROOT_EMC_NAMESPACE, CLAR_SETTINGS_DEFINE_STATE_RG_SAFS,
                true, false,
                false, new String[] { CP_INSTANCE_ID });
        try {
            while (instances.hasNext()) {
                CIMInstance instance = instances.next();
                CIMObjectPath settingsPath = instance.getObjectPath();
                CIMObjectPath syncPath = (CIMObjectPath) settingsPath.getKey(
                        CP_SETTING_DATA).getValue();
                String instanceID = (String) syncPath.getKey(CP_INSTANCE_ID)
                        .getValue();
                if (instanceID.equals(synchAspectinstanceID)) {
                    return settingsPath;
                }
            }
        } finally {
            closeCIMIterator(instances);
        }
        throw new ServiceCodeException(ServiceCode.CONTROLLER_ERROR,
                "Unable to find instance of Clar_SettingsDefineState_RG_SAFS with Clar_SynchronizationAspectForSourceGroup={0}",
                new Object[] { synchAspectinstanceID });
    }

    public Collection<? extends CIMObjectPath> constructMaskingGroupPathsFromNames(
            Set<String> groupNames, StorageSystem storage) throws Exception {
        List<CIMObjectPath> maskingGroupPaths = new ArrayList<CIMObjectPath>();
        for (String groupName : groupNames) {
            CIMObjectPath maskingGroupPath = _cimPath
                    .getMaskingGroupPath(storage, groupName,
                            SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
            maskingGroupPaths.add(maskingGroupPath);
        }
        return maskingGroupPaths;
    }

    public Set<String> constructVolumeNativeGuids(Collection<Set<String>> nativeGuidCollection) {
        Set<String> nativeGuids = new HashSet<String>();
        for (Set<String> nativeGuidSet : nativeGuidCollection) {
            nativeGuids.addAll(nativeGuidSet);
        }
        return nativeGuids;
    }

    public CIMArgument[] getActivateFullCopyArguments(StorageSystem storageSystem, Volume volume) {
        CIMObjectPath syncObject;
        try {
            syncObject = _cimPath.getSyncObject(storageSystem, volume);
        } catch (Exception e) {
            throw new IllegalArgumentException("Problem getting input arguments");
        }
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        argsList.add(_cimArgument.uint16(CP_OPERATION, ACTIVATE_VALUE));
        argsList.add(_cimArgument.reference(CP_SYNCHRONIZATION, syncObject));
        // For the case of VNX, we are going to set a flag that will
        // allow the database synchronization set to be skipped, so
        // that the activate runs faster, to meet our 10 second window.
        if (storageSystem.getSystemType().equals(StorageSystem.Type.vnxblock.name())) {
            argsList.add(_cimArgument.bool(CP_EMC_SKIP_REFRESH, true));
        }
        CIMArgument[] result = {};
        return argsList.toArray(result);
    }

    public CIMArgument[] getActivateGroupFullCopyInputArguments(StorageSystem storageSystem, CIMObjectPath groupSync) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>();
        argsList.add(_cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true));
        argsList.add(_cimArgument.uint16(CP_OPERATION, ACTIVATE_VALUE));
        argsList.add(_cimArgument.reference(CP_SYNCHRONIZATION, groupSync));
        // For the case of VNX, we are going to set a flag that will
        // allow the database synchronization set to be skipped, so
        // that the activate runs faster, to meet our 10 second window.
        if (storageSystem.getSystemType().equals(StorageSystem.Type.vnxblock.name())) {
            argsList.add(_cimArgument.bool(CP_EMC_SKIP_REFRESH, true));
        }
        CIMArgument[] result = {};
        return argsList.toArray(result);
    }

    public CIMArgument[] getCreateGroupReplicaForSRDFInputArguments(StorageSystem storage, String replicaName,
            CIMObjectPath srcCG, CIMObjectPath tgtCG, CIMObjectPath collection, int mode, Object repSettingInstance) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.reference(CP_CONNECTIVITY_COLLECTION, collection));
        // By default CG's are consistency enabled for 8.0.3 & 4.6.2.25 provider versions. Hence commenting the below
        // line
        // args.add(_cimArgument.uint16(CP_CONSISTENCY, NO_CONSISTENCY));
        args.add(_cimArgument.uint16(CP_MODE, mode));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, MIRROR_VALUE));
        args.add(_cimArgument.reference(CP_SOURCE_GROUP, srcCG));
        args.add(_cimArgument.reference(CP_TARGET_GROUP, tgtCG));

        int maxRelNameLength = storage.getUsingSmis80() ? MAX_SMI80_RELATIONSHIP_NAME : MAX_VMAX_RELATIONSHIP_NAME;
        final String relationshipName = (replicaName.length() > maxRelNameLength) ? replicaName.substring(0, maxRelNameLength)
                : replicaName;
        args.add(_cimArgument.string(RELATIONSHIP_NAME, relationshipName));
        // args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance));
        // WaitForCopyState only valid for Active mode.
        if (SRDFOperations.Mode.ACTIVE.getMode() == mode) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED));
        }
        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(
            CIMObjectPath srcCG, CIMObjectPath tgtCG, Collection<CIMObjectPath> elementSynchronizations) {
        return getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(srcCG, tgtCG,
                elementSynchronizations, null);
    }

    public CIMArgument[] getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(
            CIMObjectPath srcCG, CIMObjectPath tgtCG, Collection<CIMObjectPath> elementSynchronizations,
            String relationshipName) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.reference(CP_SOURCE_GROUP, srcCG));
        args.add(_cimArgument.reference(CP_TARGET_GROUP, tgtCG));
        args.add(_cimArgument.referenceArray(CP_ELEMENT_SYNCHRONIZATIONS,
                elementSynchronizations.toArray(new CIMObjectPath[] {})));
        if (relationshipName != null) {
            args.add(_cimArgument.string(CP_RELATIONSHIP_NAME, relationshipName));
        }
        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getCreateElementReplicaForSRDFInputArguments(CIMObjectPath srcVolume, CIMObjectPath tgtVolume,
            CIMObjectPath collection, int mode, Object repSettingInstance) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.reference(CP_CONNECTIVITY_COLLECTION, collection));
        args.add(_cimArgument.uint16(CP_MODE, mode));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, MIRROR_VALUE));
        args.add(_cimArgument.reference(CP_SOURCE_VOLUME, srcVolume));
        args.add(_cimArgument.reference(CP_TARGET_VOLUME, tgtVolume));
        args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingInstance));
        return args.toArray(new CIMArgument[] {});
    }

    public CIMArgument[] getDissolveSnapshotInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, DISSOLVE_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true)
        };
    }

    public CIMArgument[] getResyncSnapshotInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true)
        };
    }

    public CIMArgument[] getResyncReplicaInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath)
        };
    }

    public CIMArgument[] getSupportedOperationsForSnapshot() {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_REPLICATION_TYPE, SNAPSHOT_REPLICATION_TYPE)
        };
    }

    public CIMArgument[] getReplicationSettingDataInstance() {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_REPLICATION_TYPE, SNAPSHOT_REPLICATION_TYPE)
        };
    }

    public CIMArgument[] getReplicationSettingDataInstance(int replicationType) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_REPLICATION_TYPE, replicationType)
        };
    }

    public CIMArgument[] getSupportedFeatures() {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_REPLICATION_TYPE, EMULATION_VALUE)
        };
    }

    public StorageSystem getStorageSystemForProvider(final StorageSystem storageSystem,
            final Volume volume) {
        // wisely choose the right Storage System, so that right Provider is used in invoking calls
        if (LinkStatus.FAILED_OVER.toString().equalsIgnoreCase(volume.getLinkStatus())) {
            StringSet targets = volume.getSrdfTargets();
            if (null == targets) {
                return _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
            }
            for (String targetURi : targets) {
                Volume target = _dbClient.queryObject(Volume.class, URI.create(targetURi));
                if (null == target) {
                    return storageSystem;
                }
                return _dbClient.queryObject(StorageSystem.class, target.getStorageController());
            }
        }

        if (null == volume.getSrdfParent()) {
            return storageSystem;
        }
        Volume parent = _dbClient.queryObject(Volume.class, volume.getSrdfParent().getURI());
        if (null == parent) {
            return storageSystem;
        }
        return _dbClient.queryObject(StorageSystem.class,
                parent.getStorageController());

    }

    public boolean arraySupports(StorageSystem storage, int operation) {
        boolean result = false;
        CIMArgument[] out = new CIMArgument[5];
        try {
            invokeMethod(storage, _cimPath.getReplicationServiceCapabilitiesPath(storage),
                    GET_SUPPORTED_OPERATIONS, getSupportedOperationsForSnapshot(), out);
            Object value = _cimPath.getFromOutputArgs(out, SUPPORTED_OPERATIONS);
            if (value != null) {
                UnsignedInteger16[] supported = (UnsignedInteger16[]) value;
                for (UnsignedInteger16 it : supported) {
                    if (it.intValue() == operation) {
                        result = true;
                        break;
                    }
                }
            }
        } catch (WBEMException e) {
            _log.error("arraySupportsDissolve exception: ", e);
        }
        return result;
    }

    public boolean arraySupportsDissolve(StorageSystem storage) {
        return arraySupports(storage, DISSOLVE_VALUE);
    }

    public boolean arraySupportsResync(StorageSystem storage) {
        return arraySupports(storage, RESYNC_VALUE);
    }

    public boolean isThinlyProvisioned(StorageSystem storage, BlockObject to)
            throws Exception {
        CIMObjectPath path = _cimPath.getBlockObjectPath(storage, to);
        CIMInstance instance = getInstance(storage, path, false, false,
                PS_THINLY_PROVISIONED);
        String thinlyProvisionedString = CIMPropertyFactory.getPropertyValue(instance,
                CP_THINLY_PROVISIONED);
        return (thinlyProvisionedString != null) ? thinlyProvisionedString.equalsIgnoreCase(Boolean.TRUE.toString()) : false;
    }

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
     * Method to generate the input arguments for EMCManuallyRegisterHostInitiators
     *
     * @param storage
     *            [in] - StorageSystem object.
     * @param initiators
     *            [in] - List of Initiator objects to process for input generation
     * @param storagePortURI
     *            [in] - StoragePort URI reference
     * @return CIMArgument[] representing the generate arguments for the SMI-S method call.
     * @throws Exception
     */
    public CIMArgument[] getEMCManuallyRegisterHostInitiators(StorageSystem storage,
            Collection<Initiator> initiators,
            URI storagePortURI) throws Exception {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        int type = 2; // Fibre-Channel type
        String hostname = EMPTY_STRING;
        List<String> unRegisteredNodeIDs = new ArrayList<String>();
        String[] unRegisteredStorageIDs = new String[initiators.size()];
        CIMObjectPath[] targetEndpoints = _cimPath.getTargetPortPaths(storage, Collections.singletonList(storagePortURI));

        int it = 0;
        for (Initiator initiator : initiators) {
            if (WWNUtility.isValidWWN(initiator.getInitiatorPort())) {
                // This is an FC initiator, add the node part
                unRegisteredNodeIDs.add(Initiator.normalizePort(initiator.getInitiatorNode()));
            } else {
                type = 5; // iSCSI type
            }

            // The order matters to platforms like VNX. VNX requires to use the initiator hostname
            // field in order to keep consistent with peer initiators.
            if (initiator.getHostName() != null && !initiator.getHostName().equals(EMPTY_STRING)) {
                hostname = initiator.getHostName();
            } else if (hostname.equals(EMPTY_STRING) && initiator.getHost() != null) {
                Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                if (host != null) {
                    hostname = host.getHostName();
                }
            }

            // VNX does not like registering initiators with IP addresses
            if ((hostname != null) && (IPAddressUtil.isIPv4LiteralAddress(hostname) || IPAddressUtil.isIPv6LiteralAddress(hostname))) {
                hostname = "HOST-" + hostname;
            }

            // Regardless of the initiator type, we should always have the initiatorPort value for it
            unRegisteredStorageIDs[it++] = Initiator.normalizePort(initiator.getInitiatorPort());
        }

        args.add(_cimArgument.string(CP_HOSTNAME, hostname));
        args.add(_cimArgument.referenceArray(CP_TARGET_ENDPOINTS, targetEndpoints));
        args.add(_cimArgument.uint16(CP_UNREGISTERED_STORAGE_TYPE, type));
        args.add(_cimArgument.stringArray(CP_UNREGISTERED_STORAGE_IDS, unRegisteredStorageIDs));
        if (!unRegisteredNodeIDs.isEmpty()) {
            String[] nodeIDs = unRegisteredNodeIDs.toArray(new String[unRegisteredNodeIDs.size()]);
            args.add(_cimArgument.stringArray(CP_UNREGISTERED_NODE_IDS, nodeIDs));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    /**
     * This function will generate the input arguments for the EMCGetConnectedTargetEndpoints
     * SMI-S method call.
     *
     * @param initiator
     *            [in] - CIMObjectPath that references an initiator on the provider
     * @return CIMArgument array containing arguments for the method call
     */
    public CIMArgument[] getEMCGetConnectedTargetEndpoints(CIMObjectPath initiator) {
        return new CIMArgument[] {
                _cimArgument.reference(CP_HARDWARE_ID, initiator),
                _cimArgument.uint16(CP_FILTER, FILTER_CONNECTED_VALUE)
        };
    }

    /**
     * This function will prevent calls to obj.toString() from generating a
     * runtime Exception.
     *
     * Note: this function is here because there seem to be strange cases where
     * the result of an SMI-S invokeMethod returns a non-null Object, but calling
     * toString() on the Object results in a NPE deep within the toString() call.
     * This function is used as a countermeasure for this behavior.
     *
     * @param obj
     *            [in] - Object to call toString() against.
     * @return String (obj.toString() or "", if it could not be obtained)
     */
    private String protectedToString(Object obj) {
        String out = EMPTY_STRING;
        if (obj != null) {
            try {
                out = obj.toString();
            } catch (RuntimeException runtime) {
                String message = "Caught an exception while trying to call obj.toString()";
                if (_log.isDebugEnabled()) {
                    // If debugging is enabled, dump the stacktrace
                    _log.error(message, runtime);
                } else {
                    _log.info(message);
                }
            }
        }
        return out;
    }

    public CIMArgument[] getDefaultReplicationSettingDataInputArgumentsForSnapshot() {
        return new CIMArgument[] {
                _cimArgument.uint16(SmisConstants.CP_REPLICATION_TYPE, 6) // 6 -> Synchronous Snapshot Local
        };
    }

    public CIMArgument[] getDefaultReplicationSettingDataInputArgumentsForLocalMirror() {
        return new CIMArgument[] {
                _cimArgument.uint16(SmisConstants.CP_REPLICATION_TYPE, 2) // 2 -> Synchronous Mirror Local
        };
    }

    /*
     * find/create storage group for VMAX V3
     */
    public CIMObjectPath getVolumeGroupPath(StorageSystem forProvider, StorageSystem storageSystem, Volume volume, StoragePool storagePool) {
        CIMObjectPath volumeGrouptPath = null;
        URI policyURI = volume.getAutoTieringPolicyUri();
        // Don't create Parking SLO SG if there is no Fast policy associated
        if (storageSystem.checkIfVmax3() && !NullColumnValueGetter.isNullURI(policyURI)) {
            if (storagePool == null) {
                storagePool = _dbClient.queryObject(StoragePool.class, volume.getPool());
            }

            AutoTieringPolicy policy = _dbClient.queryObject(AutoTieringPolicy.class, policyURI);
            String slo = policy.getVmaxSLO();
            String workload = policy.getVmaxWorkload().toUpperCase();
            String srp = storagePool.getPoolName();

            // Try to find existing storage group.
            volumeGrouptPath = getVolumeGroupBasedOnSLO(forProvider, storageSystem, slo, workload, srp);
            if (volumeGrouptPath == null) {
                // Create new storage group.
                volumeGrouptPath = createVolumeGroupBasedOnSLO(forProvider, storageSystem, slo, workload, srp);
            }
        }
        return volumeGrouptPath;
    }

    /**
     * Removes the volumes from the associated parking SLO storage group.
     *
     * @param storage the storage system
     * @param nativeIds the volume native ids
     * @param forceFlag the force flag
     * @return the parking SLO storage group instances
     * @throws Exception the exception
     */
    public Set<CIMInstance> removeVolumeFromParkingSLOStorageGroup(StorageSystem storage, String[] nativeIds, boolean forceFlag)
            throws Exception {
        Map<String, List<CIMObjectPath>> parkingSGToVolumes = new HashMap<>();
        Set<CIMInstance> parkingSLOStorageGroups = new HashSet<>();
        CloseableIterator<CIMInstance> cimInstanceItr = null;
        for (String nativeId : nativeIds) {
            try {
                CIMObjectPath volumePath = _cimPath.getVolumePath(storage, nativeId);
                cimInstanceItr = getAssociatorInstances(storage, volumePath, null,
                        MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                        PS_ELEMENT_NAME);
                while (cimInstanceItr.hasNext()) {
                    CIMInstance groupInstance = cimInstanceItr.next();
                    String groupName = CIMPropertyFactory.getPropertyValue(groupInstance, CP_ELEMENT_NAME);
                    _log.debug("Found masking group {} associated for volume {}", groupName, nativeId);
                    if (groupName.startsWith(Constants.STORAGE_GROUP_PREFIX)) {
                        List<CIMObjectPath> volumes = parkingSGToVolumes.get(groupName);
                        if (volumes == null) {
                            volumes = new ArrayList<CIMObjectPath>();
                            parkingSGToVolumes.put(groupName, volumes);
                            // add this SG to the SG instances collection
                            parkingSLOStorageGroups.add(groupInstance);
                        }
                        volumes.add(volumePath);
                    }
                }
            } catch (WBEMException ex) {
                _log.error("Failed to find associated storage group for volume {}.", nativeId, ex);
                throw new DeviceControllerException(ex);
            } finally {
                if (cimInstanceItr != null) {
                    cimInstanceItr.close();
                }
            }
        }

        boolean foundSGwithMV = false;
        for (String parkingSGName : parkingSGToVolumes.keySet()) {

            _log.debug("Trying to find whether the storage group: {} is used in  a masking view...", parkingSGName);
            foundSGwithMV = isSGOrItsCSGPartOfMaskingView(storage, parkingSGName);
            if (foundSGwithMV) {
                _log.warn("The parking storage group is part of a masking view. So, don't remove volumes from it: {}", parkingSGName);
                continue;
            }
            List<CIMObjectPath> volumePaths = parkingSGToVolumes.get(parkingSGName);
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, parkingSGName,
                    MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
            CIMObjectPath[] memberPaths = volumePaths.toArray(new CIMObjectPath[] {});

            CIMArgument[] inArgs = getAddOrRemoveMaskingGroupMembersInputArguments(maskingGroupPath, memberPaths, forceFlag);
            CIMArgument[] outArgs = new CIMArgument[5];
            _log.info("Invoking remove volume(s) from parking SLO storage group {}", parkingSGName);
            invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                    SmisConstants.REMOVE_MEMBERS, inArgs, outArgs, null);
        }
        return parkingSLOStorageGroups;
    }

    /**
     * Finds the storage group based on SLO
     *
     * @param forProvider
     *            The storage provider where the query executes
     * @param storageSystem
     *            The reference to storage system
     * @param slo
     *            The slo name for the fast setting
     * @param workload
     *            The workload name for the fast setting
     * @param srp
     *            The srp name for the fast setting
     *
     * @return returns volumeGroupPath if found else null
     */
    public CIMObjectPath getVolumeGroupBasedOnSLO(StorageSystem forProvider, StorageSystem storageSystem, String slo, String workload,
            String srp) {
        CIMObjectPath volumeGroupObjectPath = null;
        StringBuffer fastSettingName = new StringBuffer();
        fastSettingName = fastSettingName.append(slo).append(Constants._plusDelimiter)
                .append(workload).append(Constants._plusDelimiter).append(srp);
        try {
            Map<CIMObjectPath, Set<String>> groupPaths = findAnySLOStorageGroupsCanBeReUsed(forProvider, storageSystem,
                    fastSettingName.toString(), false);
            for (CIMObjectPath groupPath : groupPaths.keySet()) {
                Set<String> groupVolumes = groupPaths.get(groupPath);
                if (groupVolumes == null || (groupVolumes != null && groupVolumes.size() < 4000)) {
                    volumeGroupObjectPath = groupPath;
                    break;
                }
            }
        } catch (WBEMException e) {
            _log.info(storageSystem.getSystemType() + " Problem when trying to look for existing storage group for SLO "
                    + fastSettingName.toString(), e);
            throw new DeviceControllerException(e);
        }
        return volumeGroupObjectPath;
    }

    /**
     * Creates storage group based on fast setting.
     *
     * @param forProvider
     *            The storage provider where the query executes
     * @param storageSystem
     *            The reference to the storage system
     * @param slo
     *            The slo name for the fast setting
     * @param workload
     *            The workload name for the fast setting
     * @param srp
     *            The srp name for the fast setting
     *
     * @return returns the storage group path for the storage group created
     */
    public CIMObjectPath createVolumeGroupBasedOnSLO(StorageSystem forProvider, StorageSystem storageSystem,
            String slo, String workload, String srp) {
        String groupName = generateGroupName(slo, workload, srp);
        String lockName = generateParkingSLOSGLockName(storageSystem, groupName);
        boolean gotLock = false;
        CIMObjectPath volumeGroupObjectPath = null;
        try {
            // The parking SLO StorageGroup's lifecycle will be maintained by ViPR.
            // It will create it and it will delete it. It is a shared resource, so
            // this call to create the SG needs distributed process protection
            if (_locker.acquireLock(lockName, PARKING_SLO_SG_LOCK_WAIT_SECS)) {
                gotLock = true;
                // Since we locked this operation, we need to make sure that some other system
                // did not already create the StorageGroup. So, check for it here.
                volumeGroupObjectPath = _cimPath.getStorageGroupObjectPath(groupName, storageSystem);
                CIMInstance instance = checkExists(forProvider, volumeGroupObjectPath, false, false);
                if (instance == null) {
                    // Nothing created yet and we have the lock, so let's create it ...
                    CIMArgument[] inArgs = getCreateVolumeGroupInputArguments(storageSystem, groupName, slo, srp, workload, null, false);
                    CIMArgument[] outArgs = new CIMArgument[5];
                    invokeMethod(forProvider, _cimPath.getControllerConfigSvcPath(storageSystem),
                            "CreateGroup", inArgs, outArgs);
                    volumeGroupObjectPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
                }
            } else {
                _log.warn(String.format("Could not get lock %s while trying to createVolumeGroupBasedOnSLO",
                        lockName));
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockName, "createVolumeGroupBasedOnSLO");
            }
        } catch (WBEMException we) {
            _log.info(storageSystem.getSystemType() + " Problem when trying to create volume group for SLO: "
                    + slo + " SRP: " + srp + " Workload:  " + workload, we);
            throw new DeviceControllerException(we);
        } catch (Exception e) {
            _log.error("An exception while processing createVolumeGroupBasedOnSLO", e);
        } finally {
            if (gotLock) {
                _locker.releaseLock(lockName);
            }
        }
        return volumeGroupObjectPath;
    }

    private String generateGroupName(String slo, String workload, String srp) {
        String groupName = null;
        StringBuffer groupNameSB = new StringBuffer(Constants.STORAGE_GROUP_PREFIX);
        groupNameSB = groupNameSB.append(slo).append(Constants.UNDERSCORE_DELIMITER)
                .append(workload).append(Constants.UNDERSCORE_DELIMITER)
                .append(srp).append(Constants.UNDERSCORE_DELIMITER)
                .append(UUID.randomUUID());
        groupName = groupNameSB.toString();
        if (groupName.length() > Constants.STOARGE_GROUP_MAX_LENGTH) {
            groupName = groupName.substring(0, Constants.STOARGE_GROUP_MAX_LENGTH);
        }
        return groupName;
    }

    /**
     * This method return volumes deviceId present in the passed in storage group.
     *
     * @param storage
     *            The reference to storage system
     * @param groupName
     *            The storage group name
     * @return set of deviceIds of volumes in the passed in storage group
     * @throws Exception
     */
    public Set<String> getVolumeDeviceIdsFromStorageGroup(StorageSystem storage, String groupName) throws Exception {
        CIMObjectPath groupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        return getVolumeDeviceIdsFromStorageGroup(storage, groupPath);
    }

    /**
     * This method returns volume device IDs present in the passed-in storage group.
     *
     * @param storage
     *            storage system
     * @param sgPath
     *            storage group object path
     * @return set of device IDs of volumes in the storage group
     * @throws Exception
     */
    public Set<String> getVolumeDeviceIdsFromStorageGroup(StorageSystem storage, CIMObjectPath sgPath) throws Exception {
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        try {
            Set<String> deviceIds = new HashSet<String>();
            // loop through all the volumes of this storage group
            _log.debug("Looping through all volumes in storage group {}", sgPath.getObjectName());
            volumePathItr = getAssociatorNames(storage, sgPath, null, CIM_STORAGE_VOLUME, null, null);
            while (volumePathItr.hasNext()) {
                deviceIds.add(getVolumeDeviceId(volumePathItr.next()));
            }
            return deviceIds;
        } finally {
            closeCIMIterator(volumePathItr);
        }

    }

    /**
     * This method is used to get fast setting value for the volume based on autoTierPolicyName.
     * fastSetting is combination of SLO+WORKLOAD+SRP.
     * For example: BRONZE+DSS+SRP_1 when autoTierPolicy is specified for a volume.
     * If no policy name is specified or block object is not associated with policy, then
     * fastSetting is NONE.
     *
     * @param blockObjectURI
     *            BlockObjectURI
     * @param autoTierPolicyName
     *            AutoTier Policy name
     *
     * @return VMAX3 fast setting
     */
    public String getVMAX3FastSettingForVolume(URI blockObjectURI, String autoTierPolicyName) {
        return getVMAX3FastPolicyNameForVolume(blockObjectURI, autoTierPolicyName, null);
    }
    
    
    /**
     * This method is used to get fast setting value for the volume based on autoTierPolicyName.
     * fastSetting is combination of SLO+WORKLOAD+SRP.
     * For example: BRONZE+DSS+SRP_1 when autoTierPolicy is specified for a volume.
     * If no policy name is specified or block object is not associated with policy, then
     * fastSetting is NONE.
     *
     * @param blockObjectURI
     *            BlockObjectURI
     * @param autoTierPolicyName
     *            AutoTier Policy name
     * @param compression
     *            Policy Name should be enabled for compression
     *
     * @return VMAX3 fast setting
     */
    public String getVMAX3FastSettingForVolume(URI blockObjectURI, String autoTierPolicyName, Boolean compression) {
        return getVMAX3FastPolicyNameForVolume(blockObjectURI, autoTierPolicyName, compression);
    }
    
    private String getVMAX3FastPolicyNameForVolume(URI blockObjectURI, String autoTierPolicyName, Boolean compression) {
        VirtualPool virtualPool = null;
        StringBuffer policyName = new StringBuffer();
        Volume volume = null;
        boolean compressionEnabled = false;
        if (URIUtil.isType(blockObjectURI, Volume.class)) {
            volume = _dbClient.queryObject(Volume.class, blockObjectURI);

            // If the there is a BlockSnapshot with the same native GUID as the volume, then
            // this is a backend volume representing the snapshot for the purpose of importing
            // the snapshot into VPLEX as a VPLEX volume. Therefore, treat it like a block snapshot
            // and use the parent volume.
            List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(_dbClient, volume.getNativeGuid());
            if (!snapshots.isEmpty()) {
                volume = _dbClient.queryObject(Volume.class, snapshots.get(0).getParent());
            }
        } else if (URIUtil.isType(blockObjectURI, BlockSnapshot.class)) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjectURI);
            volume = _dbClient.queryObject(Volume.class, snapshot.getParent());
        } else if (URIUtil.isType(blockObjectURI, BlockMirror.class)) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, blockObjectURI);
            virtualPool = _dbClient.queryObject(VirtualPool.class, mirror.getVirtualPool());
            compressionEnabled = virtualPool.getCompressionEnabled();
            if (compression != null) {
                compressionEnabled = compression;
            }
            policyName = getPolicyByBlockObject(mirror.getPool(), autoTierPolicyName, mirror.getAutoTieringPolicyUri(), mirror.getStorageController(), compressionEnabled);
        }

        if (volume != null) {
            virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            compressionEnabled = virtualPool.getCompressionEnabled();
            if (compression != null) {
                compressionEnabled = compression;
            }
            policyName = getPolicyByBlockObject(volume.getPool(), autoTierPolicyName, volume.getAutoTieringPolicyUri(), volume.getStorageController(),  compressionEnabled);
        }
        return policyName.toString();
    }

    /**
     * If policy name is simply NONE, then fastSetting to create is NONE+NONE+NONE for VMAX3.
     */
    public String getVMAX3FastSettingWithRightNoneString(StorageSystem storage, String policyName) {
        if (storage.checkIfVmax3() && Constants.NONE.equalsIgnoreCase(policyName)) {
            StringBuffer policyNameBuff = new StringBuffer();
            policyName = (policyNameBuff.append(Constants.NONE.toUpperCase()).append(Constants._plusDelimiter)
                    .append(Constants.NONE.toUpperCase()).append(Constants._plusDelimiter).append(Constants.NONE.toUpperCase())).toString();
        }
        return policyName;
    }

    /**
     * This method is will check if the Storage Pool associated with the Volume supports compression.
     * If it does support compression, it will check if the associated virtual Pool has compression enabled.
     * It will recommend the user to disable compression if the virtual pool has compression disabled and
     * the storage pool enables compression by default.
     *
     * @param blockObjectURI BlockObjectURI
     * @param storage - StorageSystem object
     * @return boolean to report if compression needs to be disabled.
     */
    public boolean disableVMAX3Compression(URI blockObjectURI, StorageSystem storageSystem) {
        VirtualPool virtualPool = null;
        StoragePool storagePool = null;
        Volume volume = null;
        if (URIUtil.isType(blockObjectURI, Volume.class)) {
            volume = _dbClient.queryObject(Volume.class, blockObjectURI);
            // Using the same logic that is in the above getVMAX3FastSettingForVolume method.
            // If the there is a BlockSnapshot with the same native GUID as the volume, then
            // this is a backend volume representing the snapshot for the purpose of importing
            // the snapshot into VPLEX as a VPLEX volume. Therefore, treat it like a block snapshot
            // and use the parent volume.
            List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(_dbClient, volume.getNativeGuid());
            if (!snapshots.isEmpty()) {
                volume = _dbClient.queryObject(Volume.class, snapshots.get(0).getParent());
            }
        } else if (URIUtil.isType(blockObjectURI, BlockSnapshot.class)) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjectURI);
            volume = _dbClient.queryObject(Volume.class, snapshot.getParent());
        } else if (URIUtil.isType(blockObjectURI, BlockMirror.class)) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, blockObjectURI);
            if (NullColumnValueGetter.isNullURI(mirror.getVirtualPool()) || NullColumnValueGetter.isNullURI(mirror.getPool())) {
                _log.info("The given BlockMirror {} does not have an associated Virtual pool or Storage Pool ",
                        blockObjectURI);
                return false; // This should never happen but we need this additional check.
            }
            virtualPool = _dbClient.queryObject(VirtualPool.class, mirror.getVirtualPool());
            storagePool = _dbClient.queryObject(StoragePool.class, mirror.getPool());
        }
        if (volume != null) {
            if (NullColumnValueGetter.isNullURI(volume.getVirtualPool()) || NullColumnValueGetter.isNullURI(volume.getPool())) {
                _log.info("The given Volume {} does not have an associated Virtual pool or Storage Pool ",
                        blockObjectURI);
                return false; // This should never happen but we need this additional check.
            }
            virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            storagePool = _dbClient.queryObject(StoragePool.class, volume.getPool());
        }
        return (checkIfProviderSupportsCompressionOperations(storageSystem) &&
                !virtualPool.getCompressionEnabled() && storagePool.getCompressionEnabled());
    }

    /**
     * This method is will check if the volume associated with virtual Pool has compression enabled.
     *
     * @param blockObjectURI BlockObjectURI
     * @return boolean to report if compression is enabled on the vpool.
     */
    public boolean isVMAX3VolumeCompressionEnabled(URI blockObjectURI) {
        VirtualPool virtualPool = null;
        Volume volume = null;
        if (URIUtil.isType(blockObjectURI, Volume.class)) {
            volume = _dbClient.queryObject(Volume.class, blockObjectURI);
        } else if (URIUtil.isType(blockObjectURI, BlockSnapshot.class)) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjectURI);
            volume = _dbClient.queryObject(Volume.class, snapshot.getParent());
        } else if (URIUtil.isType(blockObjectURI, BlockMirror.class)) {
            BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, blockObjectURI);
            virtualPool = _dbClient.queryObject(VirtualPool.class, mirror.getVirtualPool());
        }
        if (volume != null) {
            virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        }
        return ((virtualPool != null) && virtualPool.getCompressionEnabled());
    }

    /**
     * This method return true if the SMI-S provider supports compression operations.
     * If not, it will throw an exception
     *
     * @param storage - StorageSystem object
     * @param boolean to report if SMI-S provider supports compression
     */
    public Boolean checkIfProviderSupportsCompressionOperations(StorageSystem storageSystem) {
        if (storageSystem.checkIfVmax3() && storageSystem.getUsingSmis80()) {
            try {
                StorageProvider storageProvider = _dbClient.queryObject(StorageProvider.class, storageSystem.getActiveProviderURI());
                String providerVersion = storageProvider.getVersionString();
                if (VersionChecker.verifyVersionDetailsPostTrim(SMIS_PROVIDER_VERSION_8_3, providerVersion) < 0) {
                    String errMsg = String.format(
                            "SMI-S Provider associated with Storage System %s does not support compression operations",
                            storageSystem.getSerialNumber());
                    _log.error(errMsg);
                    return false;
                }
            } catch (Exception e) {
                _log.error("Exception get provider version for the storage system {} {}.", storageSystem.getLabel(),
                        storageSystem.getId());
                return false;
            }
        }

        return true;
    }

    /**
     * Get the policy by BlockObject autoTieringPolicy URI.
     *
     * @param pool
     * @param autoTierPolicyName
     * @param policyURI
     * @param compressionEnabled - If the compression is enabled at the Virtual pool level, we must honor it.
     * @return
     */
    private StringBuffer getPolicyByBlockObject(URI pool, String autoTierPolicyName, URI policyURI, 
            URI storageDevice, Boolean compressionEnabled) {
        StoragePool storagePool = _dbClient.queryObject(StoragePool.class, pool);
        StringBuffer policyName = new StringBuffer();
        if ((null != autoTierPolicyName && Constants.NONE.equalsIgnoreCase(autoTierPolicyName))
                || (NullColumnValueGetter.isNullURI(policyURI))) {
            if (compressionEnabled) {
                //If compression is enabled at the Virtual pool level, the Storage Group has to be fast managed when the SLO is none
                policyName = policyName.append(Constants.NONE.toUpperCase()).append(Constants._plusDelimiter)
                        .append(Constants.NONE.toUpperCase()).append(Constants._plusDelimiter).append(storagePool.getPoolName());
            } else {
                policyName = policyName.append(Constants.NONE);
            }
            
        } else {
            AutoTieringPolicy autoTierPolicy = _dbClient.queryObject(AutoTieringPolicy.class, policyURI);
            String workload = autoTierPolicy.getVmaxWorkload().toUpperCase();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageDevice);
            if(storageSystem != null && !storageSystem.getInactive()) {
                if(storageSystem.isV3ElmCodeOrMore()) {
                    workload = Constants.NONE.toUpperCase();
                }
            }
            
            policyName = policyName.append(autoTierPolicy.getVmaxSLO()).append(Constants._plusDelimiter)
                    .append(workload).append(Constants._plusDelimiter)
                    .append(storagePool.getPoolName());
        }
        return policyName;
    }

    /**
     * Convenient method to update storage group host IO limits attributes (bandwidth and iops, using setting from
     * volumes
     *
     * @param client
     * @param storageGroupPath
     * @param volumeURIHLUs
     * @throws WBEMException
     */
    public void setHostIOLimits(WBEMClient client, CIMObjectPath storageGroupPath, VolumeURIHLU[] volumeURIHLUs) throws WBEMException {
        if (volumeURIHLUs == null || volumeURIHLUs.length <= 0) {
            return;
        }

        if (volumeURIHLUs[0].isHostIOLimitIOPsSet()) {
            updateHostIOLimitIOPs(client, storageGroupPath, volumeURIHLUs[0].getHostIOLimitIOPs());
        }

        if (volumeURIHLUs[0].isHostIOLimitBandwidthSet()) {
            updateHostIOLimitBandwidth(client, storageGroupPath, volumeURIHLUs[0].getHostIOLimitBandwidth());
        }
    }

    /**
     * Reset storage group host IO limits to 0
     *
     * @param client
     * @param storage
     * @param storageGroupPath
     * @throws Exception
     */
    public void resetHostIOLimits(WBEMClient client, StorageSystem storage, CIMObjectPath storageGroupPath) throws Exception {
        CIMInstance storageGroupInstance = checkExists(storage, storageGroupPath, false, false);
        String hostIOLimitBandwidth = CIMPropertyFactory.getPropertyValue(storageGroupInstance, EMC_MAX_BANDWIDTH);
        String hostIOLimitIOPs = CIMPropertyFactory.getPropertyValue(storageGroupInstance, EMC_MAX_IO);

        // TODO : reset Bandwidth and IOPs for VMAX3 is not working, need to follow up with SMIS team
        boolean resetBandwidth = !StringUtils.isEmpty(hostIOLimitBandwidth) && Integer.parseInt(hostIOLimitBandwidth) > 0;
        if (resetBandwidth) {
            updateHostIOLimitBandwidth(client, storageGroupPath, 0);
        }
        boolean resetIOPs = !StringUtils.isEmpty(hostIOLimitIOPs) && Integer.parseInt(hostIOLimitIOPs) > 0;
        if (resetIOPs) {
            updateHostIOLimitIOPs(client, storageGroupPath, 0);
        }

    }

    /**
     * Set storage group host io limit bandwidth
     *
     * @param client
     * @param storageGroupPath
     * @param hostIOLimitBandwidth
     * @throws WBEMException
     */
    public void updateHostIOLimitBandwidth(WBEMClient client, CIMObjectPath storageGroupPath, Integer hostIOLimitBandwidth)
            throws WBEMException {
        if (hostIOLimitBandwidth == null) {
            // If there is no value provided, consider it as 0 which resets the limit.
            hostIOLimitBandwidth = 0;
        }

        _log.info("Attempting to update Host IO Limit Bandwidth for Storage Group : {} to {}", storageGroupPath.toString(),
                hostIOLimitBandwidth);
        CIMPropertyFactory factoryRef = (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory");

        CIMInstance toUpdate = new CIMInstance(storageGroupPath, new CIMProperty[] { factoryRef.uint32(EMC_MAX_BANDWIDTH,
                hostIOLimitBandwidth) });
        _log.debug("Params: " + toUpdate.toString());
        client.modifyInstance(toUpdate, new String[] { EMC_MAX_BANDWIDTH });
    }

    /**
     * Set storage group host io limit iops
     *
     * @param client
     * @param storageGroupPath
     * @param hostIOLimitIOPs
     * @throws WBEMException
     */
    public void updateHostIOLimitIOPs(WBEMClient client, CIMObjectPath storageGroupPath, Integer hostIOLimitIOPs) throws WBEMException {
        if (hostIOLimitIOPs == null) {
            // If there is no value provided, consider it as 0 which resets the limit.
            hostIOLimitIOPs = 0;
        }
        _log.info("Attempting to update Host IO Limit IOPs for Storage Group : {} to {}", storageGroupPath.toString(), hostIOLimitIOPs);
        CIMPropertyFactory factoryRef = (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory");

        CIMInstance toUpdate = new CIMInstance(storageGroupPath, new CIMProperty[] { factoryRef.uint32(EMC_MAX_IO, hostIOLimitIOPs) });
        _log.debug("Params: " + toUpdate.toString());
        client.modifyInstance(toUpdate, new String[] { EMC_MAX_IO });
    }

    /**
     * Set storage group host io limit iops
     *
     * @param client
     * @param storageGroupPath
     * @param hostIOLimitIOPs
     * @throws WBEMException
     */
    public void updateStorageGroupName(WBEMClient client, CIMObjectPath storageGroupPath, String storageGroupName) throws WBEMException {
        if (StringUtils.isEmpty(storageGroupName)) {
            return;
        }
        _log.info("Attempting to update storage group name to: " + storageGroupName);
        CIMPropertyFactory factoryRef = (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory");

        CIMInstance toUpdate = new CIMInstance(storageGroupPath,
                new CIMProperty[] { factoryRef.string(CP_ELEMENT_NAME, storageGroupName) });
        _log.debug("Params: " + toUpdate.toString());
        client.modifyInstance(toUpdate, PS_ELEMENT_NAME);
    }

    /**
     * Get policy and host limits information about the export mask that is specific to VMAX.
     *
     * @param storage
     *            storage system
     * @param exportMask
     *            export mask
     * @return export mask policy
     */
    public ExportMaskPolicy getExportMaskPolicy(StorageSystem storage, ExportMask exportMask) {
        ExportMaskPolicy policy = new ExportMaskPolicy();
        policy.simpleMask = false;
        CloseableIterator<CIMInstance> cimInstanceItr = null;

        String storageGroupName;
        try {
            CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, exportMask.getMaskName());
            CIMInstance maskingViewInstance = checkExists(storage, maskingViewPath, false, false);
            String maxUnitsControlled = CIMPropertyFactory.getPropertyValue(maskingViewInstance, CP_MAX_UNITS_CONTROLLED);
            if (!Strings.isNullOrEmpty(maxUnitsControlled)) {
                int maxVolumesAllowed = Integer.valueOf(maxUnitsControlled);
                policy.setMaxVolumesAllowed(maxVolumesAllowed);
            }

            storageGroupName = getStorageGroupForGivenMaskingView(maskingViewInstance,
                    exportMask.getMaskName(), storage);
            CIMObjectPath storageGroupPath = _cimPath.getStorageGroupObjectPath(storageGroupName, storage);
            CIMObjectPath igPath = getInitiatorGroupForGivenMaskingView(exportMask.getMaskName(), storage);
            if (isCascadedIG(storage, igPath)) {
                policy.setIgType(IG_TYPE.CASCADED.name());
            } else {
                policy.setIgType(IG_TYPE.SIMPLE.name());
            }
            CIMInstance storageGroupInstance = checkExists(storage, storageGroupPath, false, false);
            String hostIOLimitBandwidth = CIMPropertyFactory.getPropertyValue(storageGroupInstance, EMC_MAX_BANDWIDTH);
            String hostIOLimitIOPs = CIMPropertyFactory.getPropertyValue(storageGroupInstance, EMC_MAX_IO);

            policy.tierPolicies = findTierPoliciesForStorageGroup(storage, storageGroupName);
            policy.sgName = storageGroupName;
            policy.setHostIOLimitBandwidth(StringUtils.isEmpty(hostIOLimitBandwidth) ? null : Integer.parseInt(hostIOLimitBandwidth));
            policy.setHostIOLimitIOPs(StringUtils.isEmpty(hostIOLimitIOPs) ? null : Integer.parseInt(hostIOLimitIOPs));
            boolean childSG = !this.isCascadedSG(storage, storageGroupPath);

            // It is expected there would be only one fast policy, grab the first or set null
            policy.localTierPolicy = null;
            if (childSG && policy.tierPolicies != null && !policy.tierPolicies.isEmpty()) {
                for (String tier : policy.tierPolicies) {
                    policy.localTierPolicy = tier;
                }
            }

            Set<String> storageGroupNames = null;
            // If this is a non-cascaded, non-fast policy, check for additional phantom storage groups
            if (childSG && policy.tierPolicies.isEmpty()) {
                storageGroupNames = this.getPhantomStorageGroupsForGivenMaskingView(exportMask.getMaskName(), storage);
                StringSet policyNames = new StringSet();
                for (String sgName : storageGroupNames) {
                    policyNames.addAll(findTierPoliciesForStorageGroup(storage, sgName));
                }
                policy.tierPolicies.addAll(policyNames);
            }

            // If this is non-cascaded, and we found no phantoms either, it's a simple mask.
            if (childSG && (storageGroupNames == null || storageGroupNames.isEmpty())) {
                policy.simpleMask = true;
            }

            // if Non-cascaded SG and Phantom SGs found, then its Phantom Type
            if (childSG && (storageGroupNames != null && !storageGroupNames.isEmpty())) {
                policy.setExportType(ExportMaskPolicy.EXPORT_TYPE.PHANTOM.name());
            }

        } catch (Exception e) {
            String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
            _log.error(MessageFormat.format("Encountered an SMIS error when attempting to query existing exports policy: {0}", msg), e);

            throw SmisException.exceptions.queryExistingMasksFailure(msg, e);
        } finally {
            closeCIMIterator(cimInstanceItr);
        }

        return policy;
    }

    /**
     * Find any "phantom" storage groups that may be associated with a masking view. A phantom storage group is defined
     * as
     * a volume in the specified masking view that is contained in a storage group that is NOT in a masking view. This
     * is
     * a trick used by folks to be able to use a single non-cascaded storage group to contain volumes with different
     * fast-policies,
     * which is a policy that is attached to the storage group.
     *
     * In order to find a phantom storage group, starting from the masking view, we must:
     * 1. Find the volumes associated with the masking view
     * 2. Find the storage groups associated the volume that is not in a masking view
     * 3. Verify the storage group has a FAST policy and it is not child SG type (in case of CSG attached to MV)
     *
     * @param maskingViewName
     *            the name of the masking view
     * @param storage
     *            storage device
     * @return a set of phantom storage group names
     * @throws Exception
     */
    public Set<String> getPhantomStorageGroupsForGivenMaskingView(String maskingViewName,
            StorageSystem storage) throws Exception {

        Set<String> discoveredGroupNames = new HashSet<String>();
        CloseableIterator<CIMInstance> volumePathItr = null;
        CloseableIterator<CIMInstance> sgPaths = null;
        try {
            CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, maskingViewName);
            CIMInstance maskingViewInstance = checkExists(storage, maskingViewPath, false, false);
            _log.debug("Trying to find if any masking view by the same name {} exists", maskingViewName);
            if (null == maskingViewInstance) {
                _log.error(
                        "Masking View {} not available in Provider, either its deleted or provider might take some time to sync with Array.  Try again if group is available on Array",
                        maskingViewName);
            } else {
                _log.info("Running phantom SG check for Masking View {}", maskingViewName);
                volumePathItr = getAssociatorInstances(storage, maskingViewPath, null,
                        CIM_STORAGE_VOLUME, null, null, PS_EMCWWN);
                // COP-18172 - Use a cache for holding StorageGroup information. There could be many volumes in the
                // MaskingView, which
                // could be associated with the same StorageGroup(s). It could be more efficient to cache the
                // StorageGroup information, so
                // that we don't hit the provider over and over again for the same StorageGroup information.
                class StorageGroupInfo {
                    Set<String> policyNames;
                    String storageGroupName;
                    Boolean isPartOfExistingParentGroups;
                }
                Map<String, StorageGroupInfo> storageGroupInfoCache = new HashMap<>();
                while (volumePathItr.hasNext()) {
                    CIMInstance cimInstance = volumePathItr.next();
                    String deviceName = cimInstance.getObjectPath().getKey(CP_SYSTEM_NAME).getValue().toString() + ":" +
                            cimInstance.getObjectPath().getKey(CP_DEVICE_ID).getValue().toString();
                    _log.debug("phantom checker: looking at volume to see what storage groups it's part of: {}", deviceName);
                    // Get all of the storage groups associated with this volume
                    sgPaths = getAssociatorInstances(storage, cimInstance.getObjectPath(), null,
                            SmisConstants.SE_DEVICE_MASKING_GROUP, null, null, null);
                    while (sgPaths.hasNext()) {
                        CIMInstance sgPath = sgPaths.next();
                        String cacheKey = sgPath.toString();
                        StorageGroupInfo sgInfo = storageGroupInfoCache.get(cacheKey);
                        if (sgInfo == null) {
                            sgInfo = new StorageGroupInfo();
                            sgInfo.storageGroupName = CIMPropertyFactory.getPropertyValue(sgPath, SmisConstants.CP_ELEMENT_NAME);
                            sgInfo.policyNames = this.findTierPoliciesForStorageGroup(storage, sgInfo.storageGroupName);
                            sgInfo.isPartOfExistingParentGroups = checkStorageGroupAlreadyPartOfExistingParentGroups(storage,
                                    sgPath.getObjectPath());
                            storageGroupInfoCache.put(cacheKey, sgInfo);
                        }
                        if (sgInfo.policyNames != null && !sgInfo.policyNames.isEmpty() && !sgInfo.isPartOfExistingParentGroups) {
                            discoveredGroupNames.add(sgInfo.storageGroupName);
                        }
                    }
                }

                // remove storage groups that are directly attached to the masking view
                discoveredGroupNames.remove(getStorageGroupForGivenMaskingView(maskingViewName, storage));
            }
        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups under Masking View {}", maskingViewName, e);
            throw e;
        } finally {
            closeCIMIterator(sgPaths);
        }

        _log.info("Phantom SG check for Masking View {} found: {}", maskingViewName, discoveredGroupNames);
        return discoveredGroupNames;
    }

    /**
     * Finds the storage group attached to the masking view.
     *
     * @param maskingViewName
     *            name of masking view
     * @param storage
     *            storage system
     * @return name of storage group
     * @throws Exception
     */
    public String getStorageGroupForGivenMaskingView(String maskingViewName,
            StorageSystem storage) throws Exception {
        CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, maskingViewName);
        CIMInstance maskingViewInstance = checkExists(storage, maskingViewPath, false, false);
        return getStorageGroupForGivenMaskingView(maskingViewInstance, maskingViewName, storage);
    }

    /**
     * Finds the storage group attached to the masking view.
     *
     * @param maskingViewInstance
     *            CIMInstance that points to the Symm_LunMaskingView
     * @param maskingViewName
     *            name of masking view
     * @param storage
     *            storage system
     * @return name of storage group
     * @throws Exception
     */
    public String getStorageGroupForGivenMaskingView(CIMInstance maskingViewInstance, String maskingViewName,
            StorageSystem storage) throws Exception {

        String discoveredGroupName = null;
        CloseableIterator<CIMInstance> deviceMaskingGroupPathItr = null;
        try {
            if (null == maskingViewInstance) {
                _log.error(
                        "Masking View {} not available in Provider, either its deleted or provider might take some time to sync with Array.  Try again if group is available on Array",
                        maskingViewName);
            } else {
                _log.debug("Masking View {} found", maskingViewName);
                deviceMaskingGroupPathItr = getAssociatorInstances(storage, maskingViewInstance.getObjectPath(), null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.toString(), null, null, PS_ELEMENT_NAME);
                _log.info("Trying to find existing Storage Groups under Masking view {}", maskingViewName);
                while (deviceMaskingGroupPathItr.hasNext()) {
                    discoveredGroupName = CIMPropertyFactory.getPropertyValue(
                            deviceMaskingGroupPathItr.next(), SmisConstants.CP_ELEMENT_NAME);
                    _log.info("Storage Group Name {} found", discoveredGroupName);
                }
            }
        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups under Masking View {}", maskingViewName, e);
            throw e;
        } finally {
            closeCIMIterator(deviceMaskingGroupPathItr);
        }

        return discoveredGroupName;
    }

    /**
     * Get IG associated with masking view
     *
     * @param maskingViewName
     * @param storage
     * @return
     * @throws Exception
     */
    public CIMObjectPath getInitiatorGroupForGivenMaskingView(CIMObjectPath maskingViewPath, StorageSystem storage)
            throws Exception {
        CIMObjectPath iniGroup = null;
        CloseableIterator<CIMObjectPath> iniMaskingGroupPathItr = null;
        try {
            iniMaskingGroupPathItr = getAssociatorNames(storage, maskingViewPath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup.toString(), null, null);
            if (iniMaskingGroupPathItr.hasNext()) {
                iniGroup = iniMaskingGroupPathItr.next();
            }

        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups under Masking View {}", maskingViewPath, e);
            throw e;
        } finally {
            closeCIMIterator(iniMaskingGroupPathItr);
        }

        return iniGroup;
    }

    /**
     * Get IG associated with given masking view
     *
     * @param maskName
     * @param storage
     * @return
     * @throws Exception
     */
    public CIMObjectPath getInitiatorGroupForGivenMaskingView(String maskName, StorageSystem storage) throws Exception {
        CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, maskName);
        return getInitiatorGroupForGivenMaskingView(maskingViewPath, storage);
    }

    /**
     * Gets the initiator names for initiator group.
     *
     * @param storage the storage
     * @param igPath the ig path
     * @return the initiators for initiator group
     * @throws WBEMException the WBEM exception
     */
    public List<String> getInitiatorNamesForInitiatorGroup(StorageSystem storage, CIMObjectPath igPath) throws WBEMException {
        List<String> initiatorNames = new ArrayList<String>();
        CloseableIterator<CIMInstance> initiatorsForIg = getAssociatorInstances(storage, igPath, null,
                SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null,
                SmisConstants.PS_STORAGE_ID);
        if (initiatorsForIg != null) {
            while (initiatorsForIg.hasNext()) {
                CIMInstance initiatorInstance = initiatorsForIg.next();
                String initiatorPort = CIMPropertyFactory.getPropertyValue(initiatorInstance,
                        SmisConstants.CP_STORAGE_ID);
                initiatorNames.add(Initiator.normalizePort(initiatorPort));
            }
            initiatorsForIg.close();
        }
        return initiatorNames;
    }

    /**
     * Find a phantom (not belonging to any masking view) with matches the FAST (auto-tiering) policy name and limits
     * setting
     *
     * @param storage
     *            storage device
     * @param storageGroupPolicyLimitsParam
     *            policy name and limits setting
     * @return name of storage group that is a phantom SG with that policy, otherwise null
     * @throws Exception
     */
    public List<String> findPhantomStorageGroupAssociatedWithFastPolicy(StorageSystem storage,
            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam) throws Exception {
        CloseableIterator<CIMObjectPath> cimPathItr = null;
        List<String> sgNames = new ArrayList<String>();
        try {
            // Get all storage groups
            _log.info("findPhantomStorageGroupAssociatedWithFastPolicy START: " + storageGroupPolicyLimitsParam);
            Map<StorageGroupPolicyLimitsParam, Set<String>> allStorageGroups = getExistingSGNamesFromArray(storage);
            Set<String> storageGroupsOfPolicy = allStorageGroups.get(storageGroupPolicyLimitsParam);
            if (storageGroupsOfPolicy == null || storageGroupsOfPolicy.isEmpty()) {
                return null;
            }

            for (String storageGroupName : storageGroupsOfPolicy) {
                StringSet policyNames = findTierPoliciesForStorageGroup(storage, storageGroupName);
                CIMObjectPath storageGroupPath = _cimPath.getStorageGroupObjectPath(storageGroupName, storage);
                if (this.isCascadedSG(storage, storageGroupPath)) {
                    _log.info("findPhantomStorageGroupAssociatedWithFastPolicy avoiding storage group: " + storageGroupName
                            + " because it is a parent storage group.");
                    continue;
                }
                _log.debug("findPhantomStorageGroupAssociatedWithFastPolicy found policies: " + Joiner.on("\t").join(policyNames)
                        + " on storage group: " + storageGroupName);

                // See if this storage group is associated with the policy we sent in.
                if (policyNames != null && policyNames.contains(storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                    _log.info("findPhantomStorageGroupAssociatedWithFastPolicy found policy on storage group: " + storageGroupName);
                    // Now check to see if this storage group is associated with any masking views
                    if (!checkStorageGroupInAnyMaskingView(storage, storageGroupPath)) {
                        // Make sure this isn't a child storage group and its the parent(s) are not in masking views
                        // to qualify as phantom storage group
                        boolean inMaskView = false;

                        cimPathItr = getAssociatorNames(storage, storageGroupPath, null,
                                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null);
                        if (cimPathItr != null) {
                            while (cimPathItr.hasNext() && !inMaskView) {
                                CIMObjectPath parentObjectPath = cimPathItr.next();
                                if (checkStorageGroupInAnyMaskingView(storage, parentObjectPath)) {
                                    inMaskView = true;
                                }
                            }
                        }

                        if (!inMaskView) {
                            _log.info("findPhantomStorageGroupAssociatedWithFastPolicy found policy on storage group: " + storageGroupName
                                    + " and it's not associated with a masking view, so it's a container for FAST volumes");
                            _log.info("findPhantomStorageGroupAssociatedWithFastPolicy END: " + storageGroupPolicyLimitsParam);
                            sgNames.add(storageGroupName);
                        } else {
                            _log.info("findPhantomStorageGroupAssociatedWithFastPolicy found policy on storage group: " + storageGroupName
                                    + ", but it's associated indirectly with a masking view");
                        }
                    } else {
                        _log.info("findPhantomStorageGroupAssociatedWithFastPolicy found policy on storage group: " + storageGroupName
                                + ", but it's associated with a masking view");
                    }

                    closeCIMIterator(cimPathItr);
                }
            }
        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups with policy name", storageGroupPolicyLimitsParam, e);
            throw e;
        } finally {
            closeCIMIterator(cimPathItr);
        }
        _log.info("findPhantomStorageGroupAssociatedWithFastPolicy END: " + storageGroupPolicyLimitsParam);
        return sgNames;
    }

    /**
     * Determines which of the provided volumes URIs are already in the storage group
     * provided. Allows us to only remove the volumes that haven't been removed yet,
     * and assume success for the others.
     *
     * @param storage
     *            storage system
     * @param storageGroupName
     *            storage group name
     * @param volumeURIList
     *            list of volumes
     * @return list of volumes from the volumeURIList that are in the storage group
     * @throws Exception
     */
    public List<URI> findVolumesInStorageGroup(StorageSystem storage,
            String storageGroupName, List<URI> volumeURIList) throws Exception {
        List<URI> returnVolumes = new ArrayList<URI>();
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        try {
            List<BlockObject> bos = new ArrayList<>();
            for (URI boURI : volumeURIList) {
                bos.add(Volume.fetchExportMaskBlockObject(_dbClient, boURI));
            }
            volumePathItr = getAssociatorNames(storage,
                    _cimPath.getStorageGroupObjectPath(storageGroupName, storage), null,
                    SmisConstants.CIM_STORAGE_VOLUME, null, null);
            while (volumePathItr.hasNext()) {
                CIMObjectPath volumePath = volumePathItr.next();
                for (BlockObject bo : bos) {
                    String nativeGuid = getNativeGuid(bo);

                    if (nativeGuid != null && nativeGuid.equalsIgnoreCase(getVolumeNativeGuid(volumePath))) {
                        _log.info("Found object " + bo.getLabel() + " is in storage group " + storageGroupName);
                        returnVolumes.add(bo.getId());
                    }
                }
            }

            return returnVolumes;
        } catch (Exception e) {
            throw e;
        } finally {
            closeCIMIterator(volumePathItr);
        }
    }

    /**
     * Gets the native GUID corresponding to the block object. If the BlockObject is a RP BlockSnapshot, the native GUID will reference the
     * RecoverPoint protection system. Obtain the BlockSnapshot's associated volume to get the correct native GUID. The associated volume is
     * referenced using the BlockSnapshot's sourceNativeId field.
     *
     * @param bo the BlockObject
     * @return the native GUID
     */
    private String getNativeGuid(BlockObject bo) {
        String nativeGuid = "";

        if (bo != null) {
            // Set the native GUID to its default
            nativeGuid = bo.getNativeGuid();

            // If this is an RP BlockSnapshot, the native GUID will reference the RecoverPoint protection system. Obtain the
            // BlockSnapshot's associated volume to get the correct native GUID. The associated volume is referenced using
            // the BlockSnapshot's sourceNativeId field.
            if (URIUtil.isType(bo.getId(), BlockSnapshot.class) && BlockObject.checkForRP(_dbClient, bo.getId())) {
                URIQueryResultList result = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeNativeGuidConstraint(bo.getNativeId()), result);
                Iterator<URI> volumeIterator = result.iterator();
                while (volumeIterator.hasNext()) {
                    Volume vol = _dbClient.queryObject(Volume.class, volumeIterator.next());
                    // Make sure the storage system of the volume matches that of the RP BlockSnapshot
                    // to confirm a match.
                    if (vol.getStorageController().equals(bo.getStorageController())) {
                        nativeGuid = vol.getNativeGuid();
                        break;
                    }
                }
            }
        }

        return nativeGuid;
    }

    /**
     * Filter volumes that are already part of any FAST managed Storage Group.
     *
     * @param storage
     * @param deviceIds
     * @return filtered list
     * @throws WBEMException
     */
    public Set<String> filterVolumesPartOfAnyFASTStorageGroup(StorageSystem storage,
            Set<String> deviceIds) throws WBEMException {
        Set<String> volumesInFASTSG = new HashSet<String>();
        for (String deviceId : deviceIds) {
            if (checkVolumeAssociatedWithAnyFASTSG(deviceId, storage)) {
                volumesInFASTSG.add(deviceId);
            }
        }
        deviceIds.removeAll(volumesInFASTSG);
        return deviceIds;
    }

    /**
     * Filter volumes that are already part of the Replication Group.
     *
     * @param storage
     *            the storage
     * @param replicationGroupPath
     *            the replication group path
     * @param deviceIds
     *            the volumes
     * @return new list with volumes to add
     * @throws Exception
     *             the exception
     */
    public Set<String> filterVolumesAlreadyPartOfReplicationGroup(StorageSystem storage,
            CIMObjectPath replicationGroupPath, String[] deviceIds) throws Exception {
        Set<String> volumes = new HashSet<String>(Arrays.asList(deviceIds));
        Set<String> volumesInRG = getVolumeDeviceIdsFromStorageGroup(
                storage, replicationGroupPath);
        volumes.removeAll(volumesInRG);
        return volumes;
    }

    /**
     * Filter replicas that are already part of the Replication Group.
     *
     * @param storage
     *            the storage
     * @param replicationGroupPath
     *            the replication group path
     * @param deviceIds
     *            the volumes
     * @return new list with volumes to add
     * @throws Exception
     *             the exception
     */
    public List<URI> filterReplicasAlreadyPartOfReplicationGroup(StorageSystem storage,
            String replicationGroupName, List<URI> replicas) throws Exception {
        List<URI> replicasToAdd = new ArrayList<URI>();
        replicasToAdd.addAll(replicas);
        CIMObjectPath replicationGroupPath = _cimPath.getReplicationGroupPath(storage,
                ControllerUtils.extractGroupName(replicationGroupName));
        List<URI> volumesInRG = findVolumesInReplicationGroup(
                storage, replicationGroupPath, replicas);
        replicasToAdd.removeAll(volumesInRG);
        return replicasToAdd;
    }

    /**
     * Determines which of the provided volumes URIs are already in the replication group
     * provided.
     *
     * @param storage
     *            storage system
     * @param replicationGroupPath
     *            the replication group path
     * @param volumeURIList
     *            list of volumes
     * @return list of volumes from the volumeURIList that are in the storage group
     * @throws Exception
     *             the exception
     */
    public List<URI> findVolumesInReplicationGroup(StorageSystem storage,
            CIMObjectPath replicationGroupPath, List<URI> volumeURIList) throws Exception {
        List<URI> returnVolumes = new ArrayList<URI>();
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        try {
            List<BlockObject> bos = new ArrayList<>();
            for (URI boURI : volumeURIList) {
                bos.add(BlockObject.fetch(_dbClient, boURI));
            }
            volumePathItr = getAssociatorNames(storage, replicationGroupPath,
                    null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
            while (volumePathItr.hasNext()) {
                CIMObjectPath volumePath = volumePathItr.next();
                for (BlockObject bo : bos) {
                    if (bo.getNativeGuid().equalsIgnoreCase(getVolumeNativeGuid(volumePath))) {
                        returnVolumes.add(bo.getId());
                    }
                }
            }

            return returnVolumes;
        } finally {
            closeCIMIterator(volumePathItr);
        }
    }

    /**
     * Check to see if this volume is in a masking view other than the one that was sent in.
     * If it is, it is likely a volume that is in a view that requires the volume to remain in
     * its phantom storage group
     *
     * @param storage
     *            storage system
     * @param volumeId
     *            volume ID
     * @param knownStorageGroupName
     *            masking view name we already know about
     * @return true if the volume is in multiple masking views
     * @throws Exception
     */
    public boolean isPhantomVolumeInMultipleMaskingViews(StorageSystem storage, URI volumeId, String knownStorageGroupName)
            throws Exception {
        CloseableIterator<CIMInstance> sgPaths = null;
        try {
            Volume volume = _dbClient.queryObject(Volume.class, volumeId);
            CIMObjectPath volumePath = this.getVolumeMember(storage, volume);
            if (volumePath != null) {
                CIMInstance cimInstance = this.getInstance(storage, volumePath, true, false, PS_ELEMENT_NAME);
                _log.info("phantom checker: looking at volume to see what storage groups it's part of: " +
                        volume.getLabel());
                // Get all of the storage groups associated with this volume
                sgPaths = getAssociatorInstances(storage, cimInstance.getObjectPath(), null,
                        SmisConstants.SE_DEVICE_MASKING_GROUP, null, null, null);
                while (sgPaths.hasNext()) {
                    CIMInstance sgPath = sgPaths.next();
                    String storageGroupName = CIMPropertyFactory.getPropertyValue(sgPath, SmisConstants.CP_ELEMENT_NAME);
                    Set<String> policyNames = this.findTierPoliciesForStorageGroup(storage, storageGroupName);
                    if ((policyNames == null || policyNames.isEmpty() || policyNames.contains(Constants.NONE.toString())) &&
                            !storageGroupName.equalsIgnoreCase(knownStorageGroupName)) {
                        _log.info("Found that the volume is in storage group " + storageGroupName + " which has no FAST policy");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups for volume {}", volumeId, e);
            throw e;
        } finally {
            closeCIMIterator(sgPaths);
        }

        return false;
    }

    public void removeVolumeGroupFromPolicyAndLimitsAssociation(WBEMClient client,
            StorageSystem storage, CIMObjectPath volumeGroupPath)
            throws Exception {
        _log.debug("{} removeVolumeGroupFromPolicyAndLimitsAssociation START...",
                storage.getSerialNumber());
        if (!storage.checkIfVmax3()) {
            removeVolumeGroupFromAutoTieringPolicy(storage, volumeGroupPath);
        }
        resetHostIOLimits(client, storage, volumeGroupPath);
        _log.debug("{} removeVolumeGroupFromPolicyAndLimitsAssociation END...",
                storage.getSerialNumber());
    }

    public boolean isFastPolicy(String policyName) {
        return !StringUtils.isEmpty(policyName) && !StringUtils.equalsIgnoreCase(Constants.NONE, policyName);
    }

    /**
     * Construct a storage group policy limits object from a volumeURIHLU object.
     *
     * @param volumeUriHLUs
     * @param dbClient
     * @return
     */
    public StorageGroupPolicyLimitsParam createStorageGroupPolicyLimitsParam(
            Collection<VolumeURIHLU> volumeUriHLUs, StorageSystem storage, DbClient dbClient) {
        StorageGroupPolicyLimitsParam policyQuota = new StorageGroupPolicyLimitsParam(Constants.NONE);
        for (VolumeURIHLU volumeUriHLU : volumeUriHLUs) {
            String policyName = null;
            if (storage.checkIfVmax3()) {
                policyName = getVMAX3FastSettingForVolume(volumeUriHLU.getVolumeURI(), volumeUriHLU.getAutoTierPolicyName());
            } else {
                policyName = volumeUriHLU.getAutoTierPolicyName();
            }
            policyQuota = new StorageGroupPolicyLimitsParam(policyName,
                    volumeUriHLU.getHostIOLimitBandwidth(),
                    volumeUriHLU.getHostIOLimitIOPs(), storage);
            break;
        }
        return policyQuota;
    }

    /**
     * Construct a storage group policy limits based on info extracted from SMIS storage group object
     *
     * @param storage
     *            storage system
     * @param groupInstance
     *            SMIS storage group instance
     * @return
     */
    public StorageGroupPolicyLimitsParam createStorageGroupPolicyLimitsParam(StorageSystem storage, CIMInstance groupInstance)
            throws WBEMException {
        StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = null;

        String hostIOLimitBandwidth = CIMPropertyFactory.getPropertyValue(groupInstance, EMC_MAX_BANDWIDTH);
        String hostIOLimitIOPs = CIMPropertyFactory.getPropertyValue(groupInstance, EMC_MAX_IO);

        if (storage.checkIfVmax3()) {
            storageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(
                    getVMAX3FastSettingAssociatedWithVolumeGroup(groupInstance),
                    hostIOLimitBandwidth,
                    hostIOLimitIOPs, storage);
            storageGroupPolicyLimitsParam
                    .setCompression(SmisUtils.getEMCCompressionForStorageGroup(groupInstance));
        } else {
            storageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage,
                    groupInstance.getObjectPath()),
                    hostIOLimitBandwidth,
                    hostIOLimitIOPs, storage);
        }

        return storageGroupPolicyLimitsParam;
    }

    /**
     * Get Port Groups within the masking View.
     *
     * @param system
     * @param mvName
     * @return
     * @throws Exception
     */
    public CIMInstance getPortGroupInstance(StorageSystem system, String mvName) throws Exception {
        CIMObjectPath maskingViewPathPath = _cimPath.getMaskingViewPath(system, mvName);
        CloseableIterator<CIMInstance> cimPathItr = null;
        try {
            _log.info("Trying to find the port groups within masking view {}", mvName);
            cimPathItr = getAssociatorInstances(system, maskingViewPathPath, null, MASKING_GROUP_TYPE.SE_TargetMaskingGroup.name(),
                    null, null, PS_ELEMENT_NAME);
            while (cimPathItr.hasNext()) {
                return cimPathItr.next();
            }
        } finally {
            closeCIMIterator(cimPathItr);
        }
        return null;
    }

    /**
     * Check if volume is associated with any FAST SG, irrespective of MVs.
     *
     * @param volNativeId the volume native id
     * @param storage the storage system
     * @return true, if successful
     * @throws WBEMException
     */
    public boolean checkVolumeAssociatedWithAnyFASTSG(String volNativeId, StorageSystem storage) throws WBEMException {
        CloseableIterator<CIMInstance> sgInstanceIr = null;
        try {
            _log.info("Trying to find if volume {} is associated with any FAST managed SG", volNativeId);
            CIMObjectPath volumePath = _cimPath.getVolumePath(storage, volNativeId);
            sgInstanceIr = getAssociatorInstances(storage, volumePath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null,
                    SmisConstants.PS_V3_STORAGE_GROUP_PROPERTIES);
            while (sgInstanceIr.hasNext()) {
                CIMInstance sgInstance = sgInstanceIr.next();
                String gpNameFound = (String) sgInstance.getPropertyValue(SmisConstants.CP_ELEMENT_NAME);
                _log.info("Volume {} available in SG {}", volNativeId, gpNameFound);
                String fastSetting = getVMAX3FastSettingAssociatedWithVolumeGroup(sgInstance);
                if (!Constants.NONE.equalsIgnoreCase(fastSetting)) {
                    _log.info("Volume {} available in other FAST SG {}. EMCFastSetting {}",
                            volNativeId, gpNameFound, fastSetting);
                    return true;
                }
            }
        } catch (WBEMException we) {
            _log.error("Find volume associated with any FAST SG failed", we);
            throw we;
        } finally {
            if (null != sgInstanceIr) {
                sgInstanceIr.close();
            }
        }
        _log.info("No FAST SG found associated for volume {}", volNativeId);
        return false;
    }

    /**
     * Check volume is already added to any phantom sg with expected fast
     *
     * @param volNativeId
     * @param groupName
     * @param storage
     * @param policy
     * @return
     */
    public boolean checkVolumeAssociatedWithAnyPhantomSG(String volNativeId, String groupName, StorageSystem storage,
            String policy) {
        CloseableIterator<CIMInstance> sgInstanceIr = null;
        try {
            _log.info("Trying to find volume {} is associated with any phantom SG with expected FAST {}", volNativeId, policy);
            CIMObjectPath volumePath = _cimPath.getVolumePath(storage, volNativeId);
            sgInstanceIr = getAssociatorInstances(storage, volumePath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null, SmisConstants.PS_ELEMENT_NAME);
            while (sgInstanceIr.hasNext()) {
                CIMInstance sgInstance = sgInstanceIr.next();
                String gpNameFound = (String) sgInstance.getPropertyValue(SmisConstants.CP_ELEMENT_NAME);
                if (groupName.equalsIgnoreCase(gpNameFound)) {
                    continue;
                }
                _log.info("Volume {} available in other SG {}", volNativeId, gpNameFound);
                if (!checkStorageGroupInAnyMaskingView(storage, sgInstance.getObjectPath()) &&
                        checkVolumeGroupAssociatedWithPolicy(storage, sgInstance.getObjectPath(), policy)) {
                    return true;
                }
            }

        } catch (Exception e) {
            _log.warn("Find volume associated with any phantom SG with right policy failed", e);
        } finally {
            if (null != sgInstanceIr) {
                sgInstanceIr.close();
            }
        }
        _log.info("No Phantom SGs found for volume {}", volNativeId);
        return false;
    }

    /**
     * Check volume associated with any SG with fast policy applied, irrespective of MVs.
     *
     * @param volNativeId
     * @param storage
     * @param policy
     * @return
     */
    public boolean checkVolumeAssociatedWithAnySGWithPolicy(String volNativeId, StorageSystem storage,
            String policy) {
        CloseableIterator<CIMInstance> sgInstanceIr = null;
        try {
            _log.info("Trying to find if volume {} is associated with any phantom SG with expected FAST {}", volNativeId, policy);
            CIMObjectPath volumePath = _cimPath.getVolumePath(storage, volNativeId);
            sgInstanceIr = getAssociatorInstances(storage, volumePath, null,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name(), null, null, SmisConstants.PS_ELEMENT_NAME);
            while (sgInstanceIr.hasNext()) {
                CIMInstance sgInstance = sgInstanceIr.next();
                String gpNameFound = (String) sgInstance.getPropertyValue(SmisConstants.CP_ELEMENT_NAME);
                _log.info("Volume {} available in other SG {}", volNativeId, gpNameFound);
                if (storage.checkIfVmax3() && gpNameFound != null && gpNameFound.startsWith(Constants.STORAGE_GROUP_PREFIX)) {
                    continue;   // skip parking SLO SG
                }
                if (checkVolumeGroupAssociatedWithPolicy(storage, sgInstance.getObjectPath(), policy)) {
                    return true;
                }
            }
        } catch (Exception e) {
            _log.warn("Find volume associated with any phantom SG with right policy failed", e);
        } finally {
            if (null != sgInstanceIr) {
                sgInstanceIr.close();
            }
        }
        _log.info("No Phantom SGs found for volume {}", volNativeId);
        return false;
    }

    /**
     * Verify atleast 1 volume in SG is already associated with Fast through phantom sgs.
     *
     * @param sgPath
     * @param storage
     * @param policy
     * @return
     * @throws Exception
     */
    public boolean checkVolumeAssociatedWithPhantomSG(CIMObjectPath sgPath, StorageSystem storage, String policy) throws Exception {
        String groupName = null;
        try {
            groupName = (String) sgPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
            Set<String> volIds = getVolumeDeviceIdsFromStorageGroup(storage, sgPath);
            for (String id : volIds) {
                return checkVolumeAssociatedWithAnyPhantomSG(id, groupName, storage, policy);
            }
        } catch (Exception e) {
            // TODO till SMI-s OPT gets resolved, will have this fix.The impact
            // would be seen only in a single Node cluster with Phantom SG
            // adding a new Node will fail.
            _log.warn("Storage Group {} is not refreshed in DB yet", groupName, e);
        }
        return false;

    }

    public List<CIMObjectPath> getReplicationRelationships(StorageSystem sourceSystem,
            int localityValue, int syncType,
            int mode, int synchronizationType) throws WBEMException {
        CIMArgument[] inArgs = new CIMArgument[] {
                _cimArgument.uint16(CP_LOCALITY, localityValue),
                _cimArgument.uint16(CP_MODE, mode),
                _cimArgument.uint16(CP_SYNC_TYPE, syncType),
                _cimArgument.uint16(CP_TYPE, synchronizationType)
        };

        CIMArgument[] outArgs = new CIMArgument[5];
        CIMObjectPath repSvcPath = _cimPath.getControllerReplicationSvcPath(sourceSystem);
        invokeMethod(sourceSystem, repSvcPath, GET_REPLICATION_RELATIONSHIPS, inArgs, outArgs);

        for (CIMArgument arg : outArgs) {
            if (arg != null && arg.getName().equalsIgnoreCase(SYNCHRONIZATIONS)) {
                CIMObjectPath[] synchronizations = (CIMObjectPath[]) arg.getValue();
                return asList(synchronizations);
            }
        }
        return Collections.EMPTY_LIST;
    }

    public CIMArgument[] getSRDFRestoreInputArguments(CIMObjectPath syncPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncPath)
        };
    }

    public CIMArgument[] getSRDFRestoreInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMArgument[] getActiveSRDFRestoreInputArguments(CIMObjectPath syncPath) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncPath)
        };
    }

    public CIMArgument[] getActiveSRDFRestoreInputArguments(Collection<CIMObjectPath> syncPaths) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, RESTORE_FROM_REPLICA),
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED),
                _cimArgument.bool(CP_FORCE, true),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncPaths.toArray(new CIMObjectPath[] {}))
        };
    }

    public CIMObjectPath getDeviceGroup(final StorageSystem system,
            final StorageSystem forProvider, final BlockObject volume, final DbClient dbClient)
            throws Exception {
        URI cgUri = volume.getConsistencyGroup();
        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        String cgName = cgObj.getAlternateLabel();
        if (null == cgName) {
            cgName = cgObj.getLabel();
        }
        CIMObjectPath groupPath = checkDeviceGroupExists(cgName, forProvider, system);
        return groupPath;
    }

    public CIMObjectPath checkDeviceGroupExists(final String cgName,
            final StorageSystem forProvider, final StorageSystem system) {
        CloseableIterator<CIMObjectPath> names = null;
        try {
            CIMObjectPath path = _cimPath.getStorageSystem(system);
            names = getAssociatorNames(forProvider, path, null, SE_REPLICATION_GROUP, null,
                    null);
            while (names.hasNext()) {
                CIMObjectPath replicationGroupPath = names.next();
                String instanceId = replicationGroupPath.getKey(CP_INSTANCE_ID).getValue()
                        .toString();
                // Format
                // VMAX2: SE_ReplicationGroup.InstanceID="SynchCG+1+SYMMETRIX+000195701505"
                // VMAX3: SE_ReplicationGroup.InstanceID="000196700572+testRG"
                String repName = null;
                if (forProvider.getUsingSmis80()) {
                    repName = instanceId.split(Constants.PATH_DELIMITER_REGEX)[1];
                } else {
                    repName = instanceId.split(Constants.PATH_DELIMITER_REGEX)[0];
                }
                if (repName.equalsIgnoreCase(cgName)) {
                    return replicationGroupPath;
                }
            }
        } catch (WBEMException e) {
            _log.warn("Failed to get Device Group {}", cgName, e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return null;
    }

    public CIMInstance getReplicationSettingDataInstance(final StorageSystem sourceSystem) {
        CIMInstance modifiedInstance = null;
        try {
            CIMObjectPath replicationSettingCapabilities = _cimPath
                    .getReplicationServiceCapabilitiesPath(sourceSystem);
            CIMArgument[] inArgs = getReplicationSettingDataInstance();
            CIMArgument[] outArgs = new CIMArgument[5];
            invokeMethod(sourceSystem, replicationSettingCapabilities,
                    "GetDefaultReplicationSettingData", inArgs, outArgs);
            for (CIMArgument<?> outArg : outArgs) {
                if (null == outArg) {
                    continue;
                }
                if (outArg.getName().equalsIgnoreCase(DEFAULT_INSTANCE)) {
                    CIMInstance repInstance = (CIMInstance) outArg.getValue();
                    if (null != repInstance) {
                        CIMProperty<?> existingProp = repInstance.getProperty(EMC_CONSISTENCY_EXEMPT);
                        CIMProperty<?> prop = null;
                        if (existingProp == null) {
                            // ConsistencyExempt property is now part of the smi-s standard. Available in providers 8.0+
                            // (VMAX3 arrays)
                            // EMCConsistencyExempt property in ReplicationSettingData is removed
                            existingProp = repInstance.getProperty(CONSISTENCY_EXEMPT);
                            prop = new CIMProperty<Object>(CONSISTENCY_EXEMPT,
                                    existingProp.getDataType(), true);
                        } else {
                            prop = new CIMProperty<Object>(EMC_CONSISTENCY_EXEMPT,
                                    existingProp.getDataType(), true);
                        }
                        CIMProperty<?>[] propArray = new CIMProperty<?>[] { prop };
                        modifiedInstance = repInstance.deriveInstance(propArray);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Error retrieving Replication Setting Data Instance ", e);
        }
        return modifiedInstance;
    }

    /**
     * This is a distributed process protected operation that checks each CIMInstance in
     * the 'parkingSLOStorageGroups' set to see if it has no volumes associated it.
     * If so, it will be deleted.
     *
     * @param storage
     *            [in] - StorageSystem object against which the StorageGroups belong
     * @param _locker
     *            [in] - Locking service to protect access across
     * @param parkingSLOStorageGroups
     *            [in] - Set of SE_DeviceMaskingGroup CIMInstance objects
     * @throws Exception if there is a issue in deleting ParikingSLOStorageGroup
     */
    public void deleteParkingSLOStorageGroupsIfEmpty(StorageSystem storage, Set<CIMInstance> parkingSLOStorageGroups) throws Exception {
        String currentHeldLockName = null;
        CloseableIterator<CIMObjectPath> volumeIterator = null;
        try {
            for (CIMInstance seDeviceMaskingInstance : parkingSLOStorageGroups) {
                CIMProperty elementNameProperty = seDeviceMaskingInstance.getProperty(SmisConstants.CP_ELEMENT_NAME);
                String groupName = elementNameProperty.getValue().toString();
                String lockName = generateParkingSLOSGLockName(storage, groupName);
                // Get the lock for this StorageGroup and process it
                if (_locker.acquireLock(lockName, PARKING_SLO_SG_LOCK_WAIT_SECS)) {
                    currentHeldLockName = lockName;
                    volumeIterator = getAssociatorNames(storage, seDeviceMaskingInstance.getObjectPath(), null,
                            CIM_STORAGE_VOLUME, null, null);
                    if (volumeIterator != null && !volumeIterator.hasNext()) {
                        // There are no volume paths associated to this DeviceMaskingGroup, so we can delete it now.
                        deleteMaskingGroup(storage, groupName, MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                        volumeIterator.close();
                        volumeIterator = null;
                    }
                    _locker.releaseLock(lockName);
                    currentHeldLockName = null;
                } else {
                    currentHeldLockName = null;
                    _log.warn(String.format("Could not get lock %s while trying to deleteParkingSLOStorageGroupsIfEmpty",
                            lockName));
                    throw DeviceControllerException.exceptions.failedToAcquireLock(lockName, "deleteParkingSLOStorageGroupsIfEmpty");
                }
            }
            // Refresh the SMI-S provider to make sure that any other clients of SMI-S have the current
            // state of the changes on the array after we've processed all the StorageGroups.
            callRefreshSystem(storage, null);
        } catch (Exception e) {
            _log.error("An exception while processing deleteParkingSLOStorageGroupsIfEmpty", e);
            throw e;
        } finally {
            // Cleanup any iterator that may have been open but not yet closed
            if (volumeIterator != null) {
                volumeIterator.close();
            }
            // In case of some failure, release any lock that might have been acquired
            if (currentHeldLockName != null) {
                _locker.releaseLock(currentHeldLockName);
            }
        }
    }

    private String generateParkingSLOSGLockName(StorageSystem storageSystem, String groupName) {
        return String.format("%s-%s-lock", storageSystem.getSerialNumber(), groupName);
    }

    public CIMArgument[] getCreateListReplicaInputArguments(StorageSystem storageDevice, CIMObjectPath[] sourceVolumePath,
            CIMObjectPath[] targetVolumePath) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.referenceArray(CP_SOURCE_ELEMENTS, sourceVolumePath));
        args.add(_cimArgument.referenceArray(CP_TARGET_ELEMENTS, targetVolumePath));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, MIRROR_VALUE));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, ACTIVATE_VALUE));

        return args.toArray(new CIMArgument[args.size()]);
    }

    /**
     * Construct input arguments for calling CreateListReplica.
     *
     * @param storageDevice
     * @param sourceVolumePath
     * @param targetVolumePath
     * @param labels
     * @param syncType
     * @param replicaName
     * @param sessionName
     * @param createInactive
     * @return
     */
    public CIMArgument[] getCreateListReplicaInputArguments(StorageSystem storageDevice, CIMObjectPath[] sourceVolumePath,
            CIMObjectPath[] targetVolumePath, List<String> labels, int syncType, String replicaName, String sessionName,
            boolean createInactive, CIMObjectPath targetVPSnapPoolPath) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        int inactiveValue = (syncType == SmisConstants.CLONE_VALUE) ? PREPARED_VALUE : INACTIVE_VALUE;
        int waitForCopyState = (createInactive) ? inactiveValue : ACTIVATE_VALUE;
        args.add(_cimArgument.referenceArray(CP_SOURCE_ELEMENTS, sourceVolumePath));
        if (targetVolumePath != null && targetVolumePath.length > 0) {
            args.add(_cimArgument.referenceArray(CP_TARGET_ELEMENTS, targetVolumePath));
        } else if (labels != null) {
            args.add(_cimArgument.stringArray(CP_ELEMENT_NAMES, labels.toArray(new String[] {})));
        }

        args.add(_cimArgument.uint16(CP_SYNC_TYPE, syncType));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState));

        if (storageDevice.deviceIsType(Type.vmax)) {
            CIMInstance repSettingData = null;
            if (syncType == SmisConstants.CLONE_VALUE) {
                if (createInactive && storageDevice.getUsingSmis80()) {
                    repSettingData = getReplicationSettingDataInstanceForDesiredCopyMethod(storageDevice, replicaName,
                            COPY_BEFORE_ACTIVATE, true);
                } else if (storageDevice.checkIfVmax3() && ControllerUtils.isVmaxUsing81SMIS(storageDevice, _dbClient)) {
                    /**
                     * VMAX3 using SMI 8.1 provider needs to send DesiredCopyMethodology=32770
                     * to create TimeFinder differential clone.
                     */
                    repSettingData = getReplicationSettingDataInstanceForDesiredCopyMethod(storageDevice, replicaName,
                            SMIS810_TF_DIFFERENTIAL_CLONE_VALUE, true);
                } else {
                    repSettingData = getReplicationSettingDataInstanceForDesiredCopyMethod(storageDevice, replicaName,
                            DIFFERENTIAL_CLONE_VALUE, true);
                }
            } else if (syncType == SmisConstants.SNAPSHOT_VALUE) {
                // For VMAX2 arrays use the VPSNAPS during createListReplica.
                if (!storageDevice.checkIfVmax3()) {
                    repSettingData = getReplicationSettingDataInstanceForDesiredCopyMethod(storageDevice, replicaName, VP_SNAP_VALUE, true);
                    if (targetVPSnapPoolPath != null) {
                        // set the target pool path
                        args.add(_cimArgument.reference(CP_TARGET_POOL, targetVPSnapPoolPath));
                    }
                } else {
                    // For VMAX3, we always create snapvx snapshots
                    repSettingData = getReplicationSettingDataInstanceForDesiredCopyMethod(storageDevice, sessionName,
                            INSTRUMENTATION_DECIDES_VALUE, true);
                }
            }
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, repSettingData));
        }

        return args.toArray(new CIMArgument[args.size()]);
    }

    public CIMArgument[] getCreateListReplicaInputArguments(StorageSystem storageDevice,
            CIMObjectPath[] sourceVolumePath,
            CIMObjectPath[] targetVolumePath,
            int mode,
            CIMObjectPath repCollection,
            CIMInstance repSetting,
            boolean addWaitForCopyState) {
        List<CIMArgument> args = new ArrayList<>();
        args.add(_cimArgument.referenceArray(CP_SOURCE_ELEMENTS, sourceVolumePath));
        args.add(_cimArgument.referenceArray(CP_TARGET_ELEMENTS, targetVolumePath));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, MIRROR_VALUE));
        args.add(_cimArgument.uint16(CP_MODE, mode));
        args.add(_cimArgument.reference(CP_CONNECTIVITY_COLLECTION, repCollection));

        // WaitForCopyState only valid for Synchronous mode
        // Or Active Mode when adding pairs to an empty RDF group.
        if (SRDFOperations.Mode.SYNCHRONOUS.getMode() == mode || addWaitForCopyState) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED));
        }

        if (repSetting != null) {
            args.add(_cimArgument.object(CP_REPLICATION_SETTING_DATA, repSetting));
        }

        return args.toArray(new CIMArgument[args.size()]);
    }

    public CIMArgument[] getModifyListReplicaInputArguments(CIMObjectPath[] syncObjectPaths, int operationValue) {
        return new CIMArgument[] {
                _cimArgument.bool(CP_EMC_SYNCHRONOUS_ACTION, true),
                _cimArgument.uint16(CP_OPERATION, operationValue),
                _cimArgument.referenceArray(CP_SYNCHRONIZATION, syncObjectPaths)
        };
    }

    public CIMArgument[] getModifyListReplicaInputArguments(CIMObjectPath[] syncObjectPaths, int operation, int copyState) {
        CIMArgument[] baseArgs = getModifyListReplicaInputArguments(syncObjectPaths, operation);
        List<CIMArgument> args = new ArrayList<>(Arrays.asList(baseArgs));
        if (copyState != NON_COPY_STATE) {
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, copyState));
        }

        return args.toArray(new CIMArgument[] {});
    }

    public CIMObjectPath getPoolPath(StorageSystem storageSystem, StoragePool storagePool) {
        CIMProperty[] inPoolPropKeys = {
                _cimProperty.string(CP_INSTANCE_ID, _cimPath.getPoolName(storageSystem, storagePool.getNativeId()))
        };
        return CimObjectPathCreator.createInstance(storagePool.getPoolClassName(),
                _cimConnection.getNamespace(storageSystem),
                inPoolPropKeys);
    }

    public CIMArgument[] getCreateElementReplicaMirrorInputArgumentsWithReplicationSettingData(StorageSystem storageDevice,
            BlockObject volume,
            StoragePool pool, boolean createInactive, CIMInstance replicationSettingData,
            String label) {
        return getCreateElementReplicaInputArgumentsWithReplicationSettingData(storageDevice, volume, pool, createInactive,
                label, replicationSettingData, MIRROR_VALUE);
    }

    public CIMArgument[] getCreateElementReplicaInputArgumentsWithReplicationSettingData(
            StorageSystem storageDevice, BlockObject volume, StoragePool pool, boolean createInactive,
            String label, CIMInstance replicationSettingData, int syncType) {
        int waitForCopyState = (createInactive) ? INACTIVE_VALUE : ACTIVATE_VALUE;
        CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageDevice, volume);
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.string(CP_ELEMENT_NAME, label));
        args.add(_cimArgument.uint16(CP_SYNC_TYPE, syncType));
        args.add(_cimArgument.reference(CP_SOURCE_ELEMENT, volumePath));
        args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, waitForCopyState));
        if (pool != null) {
            addTargetPoolToArgs(storageDevice, pool, args);
        }
        if (replicationSettingData != null) {
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, replicationSettingData));
        }
        return args.toArray(new CIMArgument[] {});
    }

    /**
     * Get SettingsDefineState instances based on source or source group
     *
     * @param storage
     *            StorageSystem that holds the SettingsDefineState instances
     * @param blockObject
     *            BlockObject representing the source of replication
     * @param snapshot
     *            BlockSnapshot of the source blockObject
     * @return A List of CIMObjectPaths for SettingsDefineState instances
     * @throws WBEMException
     */
    public List<CIMObjectPath> getSettingsDefineStatePaths(
            StorageSystem storage, BlockObject blockObject,
            BlockSnapshot snapshot) throws WBEMException {

        if (blockObject.hasConsistencyGroup()
                && NullColumnValueGetter.isNotNullValue(blockObject.getReplicationGroupInstance())) {
            return getSettingsDefineStateFromSourceGroup(storage, blockObject);
        }
        return getSettingsDefineStateFromSource(storage, blockObject);
    }

    /**
     * Get Synchronization aspects associated with the source block object, then
     * constructs SettingsDefineState instances based on the source and aspects.
     *
     * @param storage
     *            StorageSystem that holds the SettingsDefineState instances
     * @param blockObject
     *            BlockObject representing the source of replication
     * @return A List of CIMObjectPaths for SettingsDefineState instances
     * @throws WBEMException
     */
    private List<CIMObjectPath> getSettingsDefineStateFromSource(
            StorageSystem storage, BlockObject blockObject)
            throws WBEMException {
        List<CIMObjectPath> settingsDefineStatePaths = new ArrayList<>();
        CIMObjectPath blockObjectPath = _cimPath.getBlockObjectPath(storage,
                blockObject);
        CloseableIterator<CIMInstance> aspectInstancesItr = null;

        try {
            aspectInstancesItr = getAssociatorInstances(storage,
                    blockObjectPath, null,
                    SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE, null, null,
                    new String[] { CP_SYNC_TYPE, CP_SYNC_STATE });
            while (aspectInstancesItr.hasNext()) {
                CIMInstance aspectInstance = aspectInstancesItr.next();
                CIMObjectPath aspectPath = aspectInstance.getObjectPath();
                String syncType = CIMPropertyFactory.getPropertyValue(
                        aspectInstance, CP_SYNC_TYPE);
                String syncState = CIMPropertyFactory.getPropertyValue(
                        aspectInstance, CP_SYNC_STATE);
                if (SNAPSHOT_SYNC_TYPE_STR.equals(syncType)
                        && RESTORED_SYNC_STATE_STR.equals(syncState)) {
                    CIMProperty[] settingsKeys = {
                            _cimProperty.reference(CP_MANAGED_ELEMENT,
                                    blockObjectPath),
                            _cimProperty.reference(CP_SETTING_DATA, aspectPath) };
                    settingsDefineStatePaths.add(CimObjectPathCreator
                            .createInstance(SYMM_SETTINGS_DEFINE_STATE_SV_SAFS,
                                    ROOT_EMC_NAMESPACE, settingsKeys));
                }
            }
        } finally {
            closeCIMIterator(aspectInstancesItr);
        }

        return settingsDefineStatePaths;
    }

    /**
     * Get SettingsDefineState instances related to the consistency group that
     * the source of the BlockSnapshot object belongs to.
     *
     * There is no association between source group and Synchronization aspects
     * (unlike source volume).
     * Steps -
     * 1. get consistency group from block snapshot
     * 2. get the CIMObjectPath of the consistency group
     * 3. query aspect instances with desired SyncType, SyncState, and array serial number in SourceElement
     * 4. check if SourceElement is the consistency group
     * 5. if yes, construct SettingsDefineState based on the source group and aspect
     *
     * @param storage
     *            StorageSystem that holds the SettingsDefineState instances
     * @param groupMember
     *            BlockObject that is part of a snapshot group
     * @return A List of CIMObjectPaths for SettingsDefineState instances
     * @throws WBEMException
     */
    public List<CIMObjectPath> getSettingsDefineStateFromSourceGroup(
            StorageSystem storage, BlockObject groupMember) throws WBEMException {
        List<CIMObjectPath> settingsDefineStatePaths = new ArrayList<>();
        String groupName = ConsistencyGroupUtils.getSourceConsistencyGroupName(groupMember, _dbClient);
        CIMObjectPath groupPath = _cimPath.getReplicationGroupPath(storage,
                groupName);

        /*
         * Query SourceElement name with groupPath string doesn't work as it is
         * actually an object path. Since there are special chars in the
         * InstanceID of groupPath (e.g, "+", "_"), avoid using the InstanceID
         * string in the LIKE operator.
         */
        String query = String.format(
                "SELECT %s FROM %s WHERE %s='%s' AND %s='%s' AND %s LIKE '%s'",
                CP_SOURCE_ELEMENT,
                SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP, CP_SYNC_TYPE,
                SNAPSHOT_SYNC_TYPE_STR, CP_SYNC_STATE, RESTORED_SYNC_STATE_STR,
                CP_SOURCE_ELEMENT, storage.getSerialNumber());
        List<CIMInstance> aspectList = executeQuery(storage, query, "wql");
        if (aspectList != null && !aspectList.isEmpty()) {
            for (CIMInstance aspectInstance : aspectList) {
                String sourceElement = CIMPropertyFactory.getPropertyValue(
                        aspectInstance, CP_SOURCE_ELEMENT);
                if (sourceElement.equalsIgnoreCase(groupPath.toString())) { // class name is lower case in SourceElement
                    CIMProperty[] settingsKeys = {
                            _cimProperty.reference(CP_MANAGED_ELEMENT,
                                    groupPath),
                            _cimProperty.reference(CP_SETTING_DATA,
                                    aspectInstance.getObjectPath()) };

                    settingsDefineStatePaths.add(CimObjectPathCreator
                            .createInstance(SYMM_SETTINGS_DEFINE_STATE_RG_SAFS,
                                    ROOT_EMC_NAMESPACE, settingsKeys));
                }
            }
        }

        return settingsDefineStatePaths;
    }

    public CIMArgument[] getEMCResumeInputArguments(
            CIMObjectPath settingsStatePath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, RESUME_FROM_SYNC_SETTINGS),
                _cimArgument.reference(CP_SETTINGS_STATE, settingsStatePath) };
    }

    /*
     * Create ReplicationSettingData for single volume/group snapshot
     */
    public CIMInstance getReplicationSettingData(StorageSystem storage, String snapSettingName, boolean isGroupConsistency)
            throws WBEMException {
        CIMInstance modifiedInstance = null;
        CloseableIterator<CIMInstance> repSvcCapIter = null;
        try {
            repSvcCapIter = getAssociatorInstances(storage,
                    _cimPath.getControllerReplicationSvcPath(storage), null,
                    _cimPath.prefixWithParamName(SmisConstants.REPLICATION_SERVICE_CAPABILTIES),
                    null, null, null);
            if (repSvcCapIter != null && repSvcCapIter.hasNext()) {
                CIMInstance instance = repSvcCapIter.next();
                CIMArgument[] in = getDefaultReplicationSettingDataInputArgumentsForSnapshot();
                CIMArgument[] out = new CIMArgument[5];
                invokeMethod(storage, instance.getObjectPath(), SmisConstants.GET_DEFAULT_REPLICATION_SETTING_DATA, in, out);
                CIMInstance defaultInstance = (CIMInstance) _cimPath.getFromOutputArgs(out, SmisConstants.DEFAULT_INSTANCE);

                // populate properties
                List<CIMProperty> propList = new ArrayList<CIMProperty>();
                propList.add(new CIMProperty<Object>(SmisConstants.CP_ELEMENT_NAME, CIMDataType.STRING_T, snapSettingName));
                for (CIMProperty prop : defaultInstance.getProperties()) {
                    if (isGroupConsistency && prop.getName().equals(SmisConstants.CP_CONSISTENT_POINT_IN_TIME)) {
                        propList.add(new CIMProperty<Object>(prop.getName(), prop.getDataType(), true));
                    } else if (!prop.getName().equals(SmisConstants.CP_ELEMENT_NAME)) {
                        propList.add(prop);
                    }
                }

                // ElementName is not in the defaultInstance, hence cannot be modified via
                // defaultInstance.deriveInstance
                // construct a new path, then create a new instance
                modifiedInstance = new CIMInstance(_cimPath.getReplicationSettingObjectPathFromDefault(defaultInstance),
                        propList.toArray(new CIMProperty[] {}));
            }
        } finally {
            if (repSvcCapIter != null) {
                repSvcCapIter.close();
            }
        }
        return modifiedInstance;
    }

    public boolean checkGroupEmpty(StorageSystem system, CIMObjectPath groupPath) throws WBEMException {
        CloseableIterator<CIMObjectPath> volumeIter = getAssociatorNames(system, groupPath, null, SmisConstants.CIM_STORAGE_VOLUME, null,
                null);
        boolean result = true;
        if (volumeIter != null && volumeIter.hasNext()) {
            result = false;
        }
        if (volumeIter != null) {
            volumeIter.close();
        }
        return result;
    }

    public CIMArgument[] getResyncSnapshotWithWaitInputArguments(CIMObjectPath syncObjectPath) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, ACTIVATE_VALUE),
                _cimArgument.uint16(CP_OPERATION, RESYNC_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObjectPath),
                _cimArgument.bool(CP_FORCE, true)
        };
    }

    /**
     * Verifies whether the group has replicas in split state.
     * When new replicas are added to the group, they can be in Sync state & old mirrors could be in SPLIT state.
     * Hence to make the group consistent, we should check for the syncState of all replicas in the group.
     * if there is any replica in split state, then resume the complete group.
     *
     * @param storage
     * @param replicaList
     * @param clazz
     * @return
     */
    public <T extends BlockObject> boolean groupHasReplicasInSplitState(StorageSystem storage, List<URI> replicaList, Class<T> clazz) {
        Iterator<T> replicaObjsItr = _dbClient.queryIterativeObjects(clazz, replicaList, true);
        while (replicaObjsItr.hasNext()) {
            T replicaObj = replicaObjsItr.next();
            CIMObjectPath syncObjCoP = _cimPath.getSyncObject(storage, replicaObj);
            _log.debug("Verifying replica {} sync state.", replicaObj.getId());
            try {
                CIMInstance instance = getInstance(storage, syncObjCoP, false, false,
                        new String[] { SmisConstants.CP_SYNC_STATE });
                if (null == instance) {
                    continue;
                }
                String syncState = CIMPropertyFactory.getPropertyValue(instance, SmisConstants.CP_SYNC_STATE);
                if (SynchronizationState.FRACTURED.toString().equals(syncState)) {
                    _log.info("Found a replica {} in Split state", replicaObj.getId());
                    return true;
                }
            } catch (Exception e) {
                String msg = String.format("Failed to acquire sync instance %s. continuing with next.. ",
                        syncObjCoP);
                _log.warn(msg, e);
            }
        }
        _log.info("All replicas in the group are in SYNC state. No resume required");
        return false;
    }

    /**
     * Creates an explicitly sized array of generic type T, containing the given value for all its elements.
     *
     * Example:
     * toMultiElementArray(2, true); => boolean[] array = new boolean[2] { true, true};
     *
     * @param count
     *            size of the array
     * @param value
     *            value for each element
     * @param <T>
     *            type of array
     *
     * @return Array of T, containing the same value for each element.
     */
    public static <T> T[] toMultiElementArray(int count, T value) {
        T[] array = (T[]) Array.newInstance(value.getClass(), count);

        for (int i = 0; i < count; i++) {
            array[i] = value;
        }
        return array;
    }

    public String createVolumesMethodName(StorageSystem storageSystem) {
        return storageSystem.getUsingSmis80() ? EMC_CREATE_MULTIPLE_TYPE_ELEMENTS_FROM_STORAGE_POOL
                : CREATE_OR_MODIFY_ELEMENT_FROM_STORAGE_POOL;
    }

    /**
     * Get source object for a replica.
     *
     * @param dbClient
     * @param replica
     *
     * @return source object
     */
    public BlockObject getSource(BlockObject replica) {
        URI sourceURI;
        if (replica instanceof BlockSnapshot) {
            sourceURI = ((BlockSnapshot) replica).getParent().getURI();
        } else if (replica instanceof BlockMirror) {
            sourceURI = ((BlockMirror) replica).getSource().getURI();
        } else {
            sourceURI = ((Volume) replica).getAssociatedSourceVolume();
        }

        return BlockObject.fetch(_dbClient, sourceURI);
    }

    /**
     * Add CIMArgument to CIMArgument[]
     *
     * @param args
     * @param element
     * @return new CIMArgument[]
     */
    public CIMArgument[] addElement(CIMArgument[] args, CIMArgument element) {
        List<CIMArgument> argsList = new ArrayList<CIMArgument>(Arrays.asList(args));
        argsList.add(element);
        CIMArgument[] argsNew = {};
        return argsList.toArray(argsNew);
    }

    public CIMInstance getReplicationSettingDataInstanceForDesiredCopyMethod(final StorageSystem storageSystem, int desiredValue,
            Boolean setTargetSupplier) {
        return this.getReplicationSettingDataInstanceForDesiredCopyMethod(storageSystem, null, desiredValue, setTargetSupplier);
    }

    /*
     * Get ReplicationSettingData instance.
     * 
     * @param storageSystem A reference to the storage system
     * 
     * @param elementName An optional name for the instance
     * 
     * @param desiredValue DesiredCopyMethodology value
     * 
     * @param steTargetSupplier Whether or not the TargetElementSupplier should also be specified.
     */
    @SuppressWarnings("rawtypes")
    public CIMInstance getReplicationSettingDataInstanceForDesiredCopyMethod(final StorageSystem storageSystem, String elementName,
            int desiredValue, Boolean setTargetSupplier) {
        CIMInstance modifiedInstance = null;
        // only for vmax, otherwise, return null
        if (!storageSystem.deviceIsType(Type.vmax)) {
            return modifiedInstance;
        }
        try {
            CIMObjectPath replicationSettingCapabilities = _cimPath
                    .getReplicationServiceCapabilitiesPath(storageSystem);
            CIMArgument[] inArgs = getReplicationSettingDataInstance();
            CIMArgument[] outArgs = new CIMArgument[5];

            invokeMethod(storageSystem, replicationSettingCapabilities,
                    GET_DEFAULT_REPLICATION_SETTING_DATA, inArgs, outArgs);
            for (CIMArgument<?> outArg : outArgs) {
                if (null == outArg) {
                    continue;
                }
                ArrayList<CIMProperty> list = new ArrayList<>();
                if (outArg.getName().equalsIgnoreCase(SmisConstants.DEFAULT_INSTANCE)) {
                    CIMInstance repInstance = (CIMInstance) outArg.getValue();
                    if (null != repInstance) {
                        CIMProperty<?> desiredMethod = new CIMProperty<Object>(SmisConstants.DESIRED_COPY_METHODOLOGY, UINT16_T,
                                new UnsignedInteger16(desiredValue));
                        list.add(desiredMethod);
                        if (setTargetSupplier) {
                            CIMProperty<?> targetElementSupplier = new CIMProperty<Object>(TARGET_ELEMENT_SUPPLIER,
                                    UINT16_T, new UnsignedInteger16(CREATE_NEW_TARGET_VALUE));
                            list.add(targetElementSupplier);
                        }

                        /**
                         * To create SNAPVX snapshot smis uses the ElementName attribute in replicationSettingsInstance.
                         * By default Element Name of replicationSettingsInstance is "Default ReplicationSettingData".
                         * Element Name should not be having white space as it accept only alphanumeric value.
                         * To avoid SNAPVX creation failure i just removed white space as a workaround.
                         */
                        if (null == elementName) {
                            elementName = SmisConstants.DEFAULT_REPLICATION_SETTING_DATA_ELEMENT_NAME;
                        }

                        CIMProperty<?> elementNameProp = new CIMProperty<Object>(SmisConstants.CP_ELEMENT_NAME, STRING_T,
                                elementName);
                        list.add(elementNameProp);

                        modifiedInstance = repInstance.deriveInstance(list.toArray(new CIMProperty[] {}));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Error retrieving Replication Setting Data Instance ", e);
        }
        return modifiedInstance;
    }

    /**
     * Return the CIMObjectPath representing the StoragePool to which 'volume belongs
     *
     * @param storage
     *            [IN] - StorageSystem object where 'volume' resides
     * @param volume
     *            [IN] - Volume object
     * @return CIMObjectPath of StoragePool where 'volume' belongs
     */
    public CIMObjectPath getVolumeStoragePoolPath(StorageSystem storage, Volume volume) {
        CIMObjectPath poolPath = null;
        if (volume != null && volume.getPool() != null) {
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, volume.getPool());
            if (storagePool != null) {
                poolPath = getPoolPath(storage, storagePool);
            }
        }
        return poolPath;
    }

    /**
     * Get the SMI-S input arguments when linking a target to an array snapshot.
     *
     * @param system
     *            A reference to the storage system.
     * @param settingsStatePath
     *            The CIM object path of the CIM_SettingsDefineState for the array snapshot.
     * @param targetDevicePath
     *            The CIM object path of the target volume.
     * @param copyMode
     *            Specifies if the array snapshot should simply be attached to the target
     *            (i.e., no data copy), or the array snapshot should actually be copied to the target.
     *
     * @return An array of CIMArgument
     */
    public CIMArgument[] getModifySettingsDefinedStateForLinkTargets(StorageSystem system, CIMObjectPath settingsStatePath,
            CIMObjectPath targetDevicePath, String copyMode) {
        return getModifySettingsDefinedStateForLinking(system, settingsStatePath, targetDevicePath, copyMode, CP_TARGET_ELEMENT);
    }

    public CIMArgument[] getModifySettingsDefinedStateForLinkTargetGroup(StorageSystem system, CIMObjectPath settingsStatePath,
            CIMObjectPath targetDevicePath, String copyMode) {
        return getModifySettingsDefinedStateForLinking(system, settingsStatePath, targetDevicePath, copyMode, CP_TARGET_GROUP);
    }

    public CIMArgument[] getModifySettingsDefinedStateForLinking(StorageSystem system, CIMObjectPath settingsStatePath,
            CIMObjectPath targetPath, String copyMode,
            String targetKey) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.reference(CP_SETTINGS_STATE, settingsStatePath));
        args.add(_cimArgument.reference(targetKey, targetPath));
        if (copyMode.equals(BlockSnapshotSession.CopyMode.copy.name())) {
            CIMInstance replicationsettingDataInstance = getReplicationSettingDataInstanceForDesiredCopyMethod(system,
                    COPY_METHODOLOGY_FULL_COPY, false);
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, replicationsettingDataInstance));
            args.add(_cimArgument.uint16(CP_OPERATION, COPY_TO_TARGET_VALUE));
            args.add(_cimArgument.uint16(CP_WAIT_FOR_COPY_STATE, SYNCHRONIZED));
        } else {
            args.add(_cimArgument.uint16(CP_OPERATION, ATTACH_TO_TARGET_VALUE));
        }

        return args.toArray(new CIMArgument[args.size()]);
    }

    /**
     * Get the SMI-S input arguments when re-linking a target to an array snapshot.
     *
     * @param system
     *            A reference to the storage system.
     * @param settingsStatePath
     *            The CIM object path of the CIM_SettingsDefineState for the target array snapshot.
     * @param targetDevicePath
     *            The CIM object path of the target volume.
     * @param isCopyMode
     *            true if the target is currently linked in "copy" mode, false otherwise.
     *
     * @return An array of CIMArgument
     */
    public CIMArgument[] getModifySettingsDefinedStateForRelinkTargets(StorageSystem system, CIMObjectPath settingsStatePath,
            CIMObjectPath targetDevicePath, boolean isCopyMode) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.uint16(CP_OPERATION, RELINK_TARGET_VALUE));
        args.add(_cimArgument.reference(CP_TARGET_ELEMENT, targetDevicePath));
        args.add(_cimArgument.reference(CP_SETTINGS_STATE, settingsStatePath));
        if (isCopyMode) {
            CIMInstance replicationsettingDataInstance = getReplicationSettingDataInstanceForDesiredCopyMethod(system,
                    COPY_METHODOLOGY_FULL_COPY, false);
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, replicationsettingDataInstance));
        }

        return args.toArray(new CIMArgument[args.size()]);
    }

    /**
     * Get the SMI-S input arguments when re-linking a target group to an array snapshot.
     *
     * @param system
     *            A reference to the storage system.
     * @param settingsStatePath
     *            The CIM object path of the CIM_SettingsDefineState for the target array snapshot.
     * @param targetDevicePath
     *            The CIM object path of the target volume.
     * @param isCopyMode
     *            true if the target group is currently linked in "copy" mode, false otherwise.
     *
     * @return An array of CIMArgument
     */
    public CIMArgument[] getModifySettingsDefinedStateForRelinkTargetGroups(StorageSystem system, CIMObjectPath settingsStatePath,
            CIMObjectPath replicationGroupPath, boolean isCopyMode) {
        List<CIMArgument> args = new ArrayList<CIMArgument>();
        args.add(_cimArgument.uint16(CP_OPERATION, RELINK_TARGET_VALUE));
        args.add(_cimArgument.reference(CP_TARGET_GROUP, replicationGroupPath));
        args.add(_cimArgument.reference(CP_SETTINGS_STATE, settingsStatePath));
        if (isCopyMode) {
            CIMInstance replicationsettingDataInstance = getReplicationSettingDataInstanceForDesiredCopyMethod(system,
                    COPY_METHODOLOGY_FULL_COPY, false);
            args.add(_cimArgument.object(CP_REPLICATIONSETTING_DATA, replicationsettingDataInstance));
        }
        return args.toArray(new CIMArgument[args.size()]);
    }

    /**
     * Get the SMI-S input arguments when unlinking a target from an array snapshot.
     *
     * @param syncObject
     *            The path for the CIM_StorageSynchronized instance for the linked target.
     *
     * @return An array of CIMArgument
     */
    public CIMArgument[] getUnlinkBlockSnapshotSessionTargetInputArguments(CIMObjectPath syncObject) {
        return new CIMArgument[] {
                _cimArgument.uint16(CP_OPERATION, DETACH_VALUE),
                _cimArgument.reference(CP_SYNCHRONIZATION, syncObject)
        };
    }

    public CIMArgument[] fabricateSourceGroupSynchronizationAspectInputArguments(StorageSystem system,
            String repGrpName, String sessionLabel) {
        List<String> addSFSEntries = new ArrayList<>();
        addSFSEntries.add(ADD_SFS_ENTRIES);
        addSFSEntries.add(formatSessionLabelForFabrication(system.getSerialNumber(), repGrpName, sessionLabel));
        return new CIMArgument[] {
                _cimArgument.stringArray(SFSENTRIES, addSFSEntries.toArray(new String[addSFSEntries.size()]))
        };
    }

    private String formatSessionLabelForFabrication(String systemSerial, String replicationGroupName, String sessionLabel) {
        return String.format("%s+%s##SSNAME+%s", systemSerial, replicationGroupName, sessionLabel);
    }

    private String formatSessionLabelForFabrication(String systemSerial, String replicationGroupName) {
        return String.format("%s+%s##SSNAME", systemSerial, replicationGroupName);
    }

    /**
     * Remove EMCSFSEntry containing the groupSynchronizedAspect information. It would find the entry using the snap
     * session
     * source replication group name, then remove it. This operation is necessary before deleting an attached snaphost
     * session replication
     * group.
     *
     * @param system
     * @param replicationSvc
     * @param sourceReplicationGroupName
     */
    public void removeSFSEntryForReplicaReplicationGroup(StorageSystem system,
            CIMObjectPath replicationSvc,
            String sourceReplicationGroupName) throws  WBEMException {
        List<String> sfsEntries = getEMCSFSEntries(system, replicationSvc);
        String groupSynchronizedAspectLabel = formatSessionLabelForFabrication(system.getSerialNumber(), sourceReplicationGroupName);
        List<String> removeEntryList = new ArrayList<String>();

        if (sfsEntries != null && !sfsEntries.isEmpty()) {
            for (String entry : sfsEntries) {
                if (entry.contains(groupSynchronizedAspectLabel)) {
                    removeEntryList.add(entry);
                }
            }
        }
        if (removeEntryList.isEmpty()) {
            _log.info(String.format("The expected SFS entry %s is not found for the source group %s",
                    groupSynchronizedAspectLabel, sourceReplicationGroupName));
            return;
        }
        try {
            String[] removeEntries = new String[removeEntryList.size()];
            removeEntries = removeEntryList.toArray(removeEntries);
            CIMArgument[] inArgs = new CIMArgument[] {
                    _cimArgument.stringArray("SFSEntries", removeEntries) };
            CIMArgument[] outArgs = new CIMArgument[5];
            invokeMethod(system, replicationSvc, SmisConstants.EMC_REMOVE_SFSENTRIES, inArgs, outArgs);
        } catch (WBEMException e) {
            _log.error("EMCRemoveSFSEntries -- WBEMException: ", e);
            throw e;
        }
    }

    /**
     * Get EMCSFSEntries
     *
     * @param storage
     * @param replicationSvc
     * @return the list of EMCSFSEntries
     */
    public List<String> getEMCSFSEntries(StorageSystem storage, CIMObjectPath replicationSvc) throws WBEMException {
        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            invokeMethod(storage, replicationSvc, SmisConstants.EMC_LIST_SFSENTRIES, null, outArgs);
            for (CIMArgument arg : outArgs) {
                if (arg != null && arg.getName().equalsIgnoreCase(SmisConstants.SFSENTRIES)) {
                    String[] entries = (String[]) arg.getValue();
                    return asList(entries);
                }
            }
        } catch (WBEMException e) {
            _log.error("get EMCSFSEntries -- WBEMException: ", e);
            throw e;
        }
        return null;
    }

    /**
     * Rename a volume on the SMIS storage device. Used by SRDF.
     * 
     * @param dbClient - database reference (volume deviceLabel is updated)
     * @param storageSystem - StorageSystem
     * @param volume - Volume
     * @param name - new name String
     */
    public void renameVolume(DbClient dbClient, StorageSystem storageSystem, Volume volume, String name) {
        try {
            CIMObjectPath volumePath = _cimPath.getBlockObjectPath(storageSystem, volume);
            _log.info(String.format("Attempting to modify volume %s to %s", volumePath.toString(), name));
            CIMInstance toUpdate = new CIMInstance(volumePath,
                    new CIMProperty[] {
                            new CIMPropertyFactory().string(SmisConstants.CP_ELEMENT_NAME, name)
                    }
                    );
            modifyInstance(storageSystem, toUpdate, SmisConstants.PS_ELEMENT_NAME);
            volume.setDeviceLabel(name);
            dbClient.updateAndReindexObject(volume);
            _log.info(String.format("Volume name has been modified to %s", name));
        } catch (WBEMException e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } catch (DatabaseException e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } catch (Exception e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        }
    }

    /**
     * Get the SMI-S input arguments for setting the Initiator Alias.
     * 
     * @param shidPath A reference to the HardwareID.
     * @param initiatorAlias The alias that needs to be set
     * @return An array of CIMArgument
     */
    public CIMArgument[] getEMCInitiatorAliasSetArgs(CIMObjectPath shidPath, String initiatorAlias)
            throws Exception {
        return new CIMArgument[] {
                _cimArgument.reference(CP_EXISTING_STORAGEID, shidPath),
                _cimArgument.string(CP_ALIAS_STORAGEID, initiatorAlias)
        };
    }

    /**
     * Get the SMI-S input arguments for getting the Initiator Alias.
     * 
     * @param shidPath A reference to the existing HardwareID.
     * @return An array of CIMArgument
     */
    public CIMArgument[] getEMCInitiatorAliasGetArgs(CIMObjectPath shidPath)
            throws Exception {
        return new CIMArgument[] {
                _cimArgument.reference(CP_EXISTING_STORAGEID, shidPath)
        };
    }
    
    
    /**
     * Check if Port Groups is shared with other masking view than the passed in masking view name.
     * If masking view name is null, then it is to check if the port group has any masking view associated
     *
     * @param system - Storage system
     * @param portGroupName - port group name
     * @param mvName - masking view name to check with
     * @return true or false
     * @throws Exception
     */
    public boolean checkPortGroupShared(StorageSystem system, String portGroupName, String mvName) throws Exception {
        CIMObjectPath portGroupPath = _cimPath.getMaskingGroupPath(system, portGroupName,
                SmisConstants.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
        return checkMaskingGroupShared(system, portGroupPath, mvName);
    }

    /**
     * Create port group
     * 
     * @param storage
     *            The storage system
     * @param portGroupName
     *            The port group name
     * @param targetURIList
     *            The list of storage ports
     * @return The created port group CIMObjectPath
     * @throws Exception
     */
    public CIMObjectPath createTargetPortGroup(StorageSystem storage,
            String portGroupName,
            List<URI> targetURIList) throws Exception {
        _log.debug("{} createTargetPortGroup {} START...", storage.getSerialNumber(), portGroupName);
        CIMObjectPath targetPortGroupPath = null;

        CIMArgument[] inArgs = getCreatePortGroupInputArguments(storage, portGroupName, targetURIList);
        CIMArgument[] outArgs = new CIMArgument[5];

        // Try to look up the port group. If it already exists, use it,
        // otherwise try to create it.
        targetPortGroupPath = _cimPath.getMaskingGroupPath(storage, portGroupName,
                SmisConstants.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
        CIMInstance instance = checkExists(storage, targetPortGroupPath, false, false);
        if (instance == null) {
            invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                    "CreateGroup", inArgs, outArgs);
            targetPortGroupPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
        } else {
            _log.info("The port group exists");
            // check if the ports members are the same
            boolean isSame = true;
            List<String> storagePorts = new ArrayList<String>();
            CloseableIterator<CIMInstance> iterator = getAssociatorInstances(storage, targetPortGroupPath, null,
                    Constants.CIM_PROTOCOL_ENDPOINT, null, null, Constants.PS_NAME);
            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String portName = CIMPropertyFactory.getPropertyValue(cimInstance,
                        Constants._Name);
                String fixedName = Initiator.toPortNetworkId(portName);
                storagePorts.add(fixedName);
            }
            if (!storagePorts.isEmpty()) {
                List<URI> storagePortURIs = new ArrayList<URI>();
                storagePortURIs.addAll(transform(ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                if (!storagePortURIs.containsAll(targetURIList) || !(storagePortURIs.size() == targetURIList.size())) {
                    isSame = false;
                }
            } else {
                isSame = false;
            }
            if (!isSame) {
                _log.error("The port group exists, but the port members are different");
                throw DeviceControllerException.exceptions.portGroupNameInvalid(portGroupName);
            }
        }
        _log.debug("{} createTargetPortGroup END...", storage.getSerialNumber());
        return targetPortGroupPath;
    }

    /**
     * create port group input arguments
     * 
     * @param storageDevice
     *            - Storage system
     * @param groupName
     *            - Port group name
     * @param targetURIList
     *            - Storage ports URIs
     * @return The input arguments for creating port group
     */
    public CIMArgument[] getCreatePortGroupInputArguments(StorageSystem storageDevice, String groupName, List<URI> targetURIList) {
        CIMObjectPath[] targetPortPaths;
        try {
            targetPortPaths = _cimPath.getTargetPortPaths(storageDevice, targetURIList);
        } catch (Exception e) {
            _log.error(String.format("Problem creating target port group: %s on array %s",
                    groupName, storageDevice.getSerialNumber()), e);
            throw new IllegalStateException("Problem creating target port group: " + groupName + "on array: "
                    + storageDevice.getSerialNumber());
        }
        return new CIMArgument[] {
                _cimArgument.string(CP_GROUP_NAME, groupName),
                _cimArgument.uint16(CP_TYPE, TARGET_PORT_GROUP_TYPE),
                _cimArgument.referenceArray(CP_MEMBERS, targetPortPaths),
                _cimArgument.bool(CP_DELETE_WHEN_BECOMES_UNASSOCIATED, Boolean.FALSE)
        };
    }

    /**
     * Finds the port group attached to the masking view.
     *
     * @param maskingViewName
     *            name of masking view
     * @param storage
     *            storage system
     * @return name of port group
     * @throws Exception
     */
    public String getPortGroupForGivenMaskingView(String maskingViewName,
            StorageSystem storage) throws Exception {
        CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, maskingViewName);
        CIMInstance maskingViewInstance = checkExists(storage, maskingViewPath, false, false);
        return getPortGroupForGivenMaskingView(maskingViewInstance, maskingViewName, storage);
    }

    /**
     * Finds the port group attached to the masking view.
     *
     * @param maskingViewInstance
     *            CIMInstance that points to the Symm_LunMaskingView
     * @param maskingViewName
     *            name of masking view
     * @param storage
     *            storage system
     * @return name of port group
     * @throws Exception
     */
    public String getPortGroupForGivenMaskingView(CIMInstance maskingViewInstance, String maskingViewName,
            StorageSystem storage) throws Exception {

        String discoveredGroupName = null;
        CloseableIterator<CIMInstance> targetGroupPathItr = null;
        try {
            if (null == maskingViewInstance) {
                _log.error(
                        "Masking View {} not available in Provider, either its deleted or provider might take some time to sync with Array.  Try again if group is available on Array",
                        maskingViewName);
            } else {
                _log.debug("Masking View {} found", maskingViewName);
                targetGroupPathItr = getAssociatorInstances(storage, maskingViewInstance.getObjectPath(), null,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup.toString(), null, null, PS_ELEMENT_NAME);
                _log.info("Trying to find existing Port Groups under Masking view {}", maskingViewName);
                while (targetGroupPathItr.hasNext()) {
                    discoveredGroupName = CIMPropertyFactory.getPropertyValue(
                            targetGroupPathItr.next(), SmisConstants.CP_ELEMENT_NAME);
                    _log.info("Port Group Name {} found", discoveredGroupName);
                }
            }
        } catch (Exception e) {
            _log.error("Failed trying to find existing Storage Groups under Masking View {}", maskingViewName, e);
            throw e;
        } finally {
            closeCIMIterator(targetGroupPathItr);
        }

        return discoveredGroupName;
    }

    /**
     * Get storage ports in the port group
     * 
     * @param storage
     *            The storage system
     * @param portGroupPath
     *            The port group CIMObjectPath
     * @return The list of storage ports URIs
     * @throws Exception
     */
    public List<URI> getPortGroupMembers(StorageSystem storage, CIMObjectPath portGroupPath) throws Exception {
        if (storage.getSystemType().equals(Type.vnxblock)) {
            return null;
        }
        CIMInstance instance = getInstance(storage, portGroupPath, false, false, null);
        WBEMClient client = getConnection(storage).getCimClient();
        List<String> storagePorts = getStoragePortsFromLunMaskingInstance(client,
                instance);
        _log.info("port group members : {}", Joiner.on(',').join(storagePorts));
        List<URI> storagePortURIs = new ArrayList<URI>();
        storagePortURIs.addAll(transform(ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts),
                CommonTransformerFunctions.FCTN_STRING_TO_URI));
        return storagePortURIs;
    }
    
    /**
     * Check if masking groups (port group or storage group) is shared with other masking view than the passed in masking view name.
     * If masking view name is null, then it is to check if the masking group has any masking view associated
     *
     * @param system - Storage system
     * @param maskingGroup - masking group CIMObjectPath
     * @param mvName - masking view name to check with
     * @return true or false
     * @throws Exception
     */
    public boolean checkMaskingGroupShared(StorageSystem system, CIMObjectPath maskingGroup, String mvName) throws Exception {
        CloseableIterator<CIMInstance> cimPathItr = null;
        boolean result = false;
        try {
            _log.info("Trying to find if any masking view associates with masking group {}", maskingGroup.toString());
            cimPathItr = getAssociatorInstances(system, maskingGroup, null, SYMM_LUNMASKINGVIEW,
                    null, null, PS_ELEMENT_NAME);
            
            while (cimPathItr.hasNext()) {
                if (mvName == null) {
                    // Just to check if there is any masking view associated to the storage group
                    result = true;
                    break;
                } else {
                    String maskingName = CIMPropertyFactory.getPropertyValue(cimPathItr.next(), SmisConstants.CP_ELEMENT_NAME);
                    _log.info("The masking group {} has lun masking view {} associated", maskingGroup.toString(), maskingName);
                    if (maskingName != null && !maskingName.equals(mvName)) {
                        result = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
           _log.error("Could not get associated masking view:", e);
        }  finally {
            if (cimPathItr != null) {
                cimPathItr.close();
            }
        }
        return result;
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.model.Volume.PersonalityTypes.SOURCE;
import static com.emc.storageos.db.client.model.Volume.PersonalityTypes.TARGET;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeGuid;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.LinkStatus;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkFailOverCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkStopCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFTaskCompleter;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderFactory;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisSRDFCreateMirrorJob;
import com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory;
import com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFOperationContext;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFOperationContextFactory40;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFOperationContextFactory80;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.BrokenSynchronizationsOnlyFilter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.ErrorOnEmptyFilter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.exceptions.NoSynchronizationsFoundException;
import com.emc.storageos.volumecontroller.impl.smis.srdf.exceptions.RemoteGroupAssociationNotFoundException;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class SRDFOperations implements SmisConstants {
    private static final Logger log = LoggerFactory.getLogger(SRDFOperations.class);
    private static final String FAILURE_MSG_FMT = "Failed to %s SRDF mirror for source:%s, target:%s";
    private static final String REPLICATION_GROUP_NAMES_NOT_FOUND = "Replication Group Names not found in RA Group %s";
    private static final String STORAGE_SYNCHRONIZATION_NOT_FOUND = "Storage Synchronization instance not found";
    private static final String REPLICATION_NOT_IN_RIGHT_STATE = "Storage replication not in expected state for failover-cancel";

    private static final int RESUME_AFTER_SWAP_MAX_ATTEMPTS = 15;
    private static final int RESUME_AFTER_SWAP_SLEEP = 30000; // 30 seconds
    private static final String RESUME_AFTER_SWAP_EXCEPTION_MSG = "Failed to resume link after swap, attempt %d/%d...";

    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;
    private SRDFUtils utils;

    public enum Mode {
        SYNCHRONOUS(2), ASYNCHRONOUS(3);
        int mode;

        Mode(final int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }

    }

    public void setDbClient(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCimObjectPathFactory(final CIMObjectPathFactory cimPath) {
        this.cimPath = cimPath;
    }

    public void setHelper(final SmisCommandHelper helper) {
        this.helper = helper;
    }

    public void setUtils(SRDFUtils utils) {
        this.utils = utils;
    }

    public void createSRDFMirror(final StorageSystem sourceSystem, final URI sourceURI,
                                 final URI targetURI, final TaskCompleter completer) {
        log.info("START createSRDFMirror");
        CIMObjectPath srcCGPath = null;
        CIMObjectPath tgtCGPath = null;
        SRDFMirrorCreateCompleter comp = null;
        try {
            Volume source = dbClient.queryObject(Volume.class, sourceURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, group.getRemoteStorageSystemUri());
            int modeValue = Mode.valueOf(target.getSrdfCopyMode()).getMode();
            CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(sourceSystem);

            srcCGPath = createDeviceGroup(sourceSystem, sourceSystem, source, dbClient);
            String sourceGroupName = (String) srcCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Source placed into replication group: {}", srcCGPath);

            // Note: We switch to the appropriate targetSystem but use sourceSystem for the provider call
            tgtCGPath = createDeviceGroup(targetSystem, sourceSystem, target, dbClient);
            String targetGroupName = (String) tgtCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Target placed into replication group: {}", tgtCGPath);

            CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(sourceSystem,
                    group);
            // look for existing volumes, if found then use AddSyncPair
            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(sourceSystem, modeValue);
            CIMArgument[] inArgs = helper.getCreateGroupReplicaForSRDFInputArguments(srcCGPath,
                    tgtCGPath, repCollectionPath, modeValue, replicationSettingDataInstance);
            CIMArgument[] outArgs = new CIMArgument[5];
            if (completer instanceof SRDFMirrorCreateCompleter) {
                comp = (SRDFMirrorCreateCompleter) completer;
                comp.setCGName(sourceGroupName, targetGroupName,
                        source.getConsistencyGroup());
            }
            helper.invokeMethodSynchronously(sourceSystem, srcRepSvcPath,
                    SmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs,
                    new SmisSRDFCreateMirrorJob(null, sourceSystem.getId(), completer));
        } catch (WBEMException wbeme) {
            log.error("SMI-S error creating mirror for {}", sourceURI, wbeme);
            // check whether synchronization really succeeds in Array
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        } catch (Exception e) {
            log.error("Error creating mirror for {}", sourceURI, e);
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                if (e.getMessage().contains("Replication Control Succeeded")) {
                    log.info(
                            "Replication Succeeded but save to DB failed exception leaves the SRDF relationship to get established properly after some time. Hence for now succeeding this operation. for {}",
                            sourceURI, e);
                    completer.ready(dbClient);
                    return;
                }
                ServiceError error = SmisException.errors.jobFailed(e.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        }
    }

    public void reSyncSRDFSyncVolumePair(final StorageSystem sourceSystem, final URI sourceURI,
                                         final URI targetURI, final TaskCompleter completer) {
        log.info("START ReSyncingSRDFMirror");
        CIMObjectPath tgtCGPath = null;
        CIMObjectPath srcCGPath = null;
        try {
            BlockObject sourceblockObj = BlockObject.fetch(dbClient, sourceURI);
            BlockObject targetblockObj = BlockObject.fetch(dbClient, targetURI);
            dbClient.queryObject(Volume.class, sourceURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                    target.getSrdfGroup());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                    group.getRemoteStorageSystemUri());
            int modeValue = Mode.valueOf(target.getSrdfCopyMode()).getMode();
            CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(sourceSystem);
            srcCGPath = getDeviceGroup(sourceSystem, sourceSystem, sourceblockObj, dbClient);
            if (null == srcCGPath) {
                log.info("Consistency Group missing in Array or might have deleted, hence no need to resync.");
                completer.ready(dbClient);
                return;
            }
            srcCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Source placed into replication group: {}", srcCGPath);
            // Note: We switch to the appropriate targetSystem but use sourceSystem for the provider
            // call
            tgtCGPath = getDeviceGroup(targetSystem, sourceSystem, targetblockObj, dbClient);
            if (null == tgtCGPath) {
                log.info("Consistency Group missing in Array or might have deleted, hence no need to resync.");
                completer.ready(dbClient);
                return;
            }
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                log.info("SRDF Group Link already established.");
                completer.ready(dbClient);
            }
            tgtCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Target placed into replication group: {}", tgtCGPath);
            CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(sourceSystem,
                    group);
            // look for existing volumes, if found then use AddSyncPair
            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(sourceSystem, modeValue);
            CIMArgument[] inArgs = helper.getCreateGroupReplicaForSRDFInputArguments(srcCGPath,
                    tgtCGPath, repCollectionPath, modeValue, replicationSettingDataInstance);
            CIMArgument[] outArgs = new CIMArgument[5];
            helper.invokeMethodSynchronously(sourceSystem, srcRepSvcPath,
                    SmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs,
                    new SmisSRDFCreateMirrorJob(null, sourceSystem.getId(), completer));
        } catch (WBEMException wbeme) {
            log.error("SMI-S error resynching mirror for {}", sourceURI, wbeme);
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        } catch (Exception e) {
            log.error("Error creating resynching mirror for {}", sourceURI, e);
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                if (e.getMessage().contains("Replication Control Succeeded")) {
                    log.info(
                            "Replication Succeeded but save to DB failed exception leaves the SRDF relationship to get established properly after some time. Hence for now succeeding this operation. for {}",
                            sourceURI, e);
                    completer.ready(dbClient);
                    return;
                }
                ServiceError error = SmisException.errors.jobFailed(e.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        }
    }

    private boolean verifyGroupSynchronizationCreatedinArray(final CIMObjectPath srcCGPath,
                                                             final CIMObjectPath tgtCGPath, final StorageSystem sourceSystem) {
        if (srcCGPath != null && tgtCGPath != null) {
            log.error("Trying to find whether SRDF Link established, even though Provider returned failure.");
            CIMObjectPath syncPath = cimPath.getGroupSynchronized(srcCGPath, tgtCGPath);
            CIMInstance syncInstance = getInstance(syncPath, sourceSystem);
            if (null != syncInstance) {
                log.info("Group Synchronization found, even Provider returned failure");
                return true;
            }
        }
        return false;
    }

    private CIMInstance getReplicationSettingDataInstance(final StorageSystem sourceSystem, int modeValue) {
        CIMInstance modifiedInstance = null;
        try {
            CIMObjectPath replicationSettingCapabilities = cimPath
                    .getReplicationServiceCapabilitiesPath(sourceSystem);
            CIMArgument[] inArgs = helper.getReplicationSettingDataInstance();
            CIMArgument[] outArgs = new CIMArgument[5];
            helper.invokeMethod(sourceSystem, replicationSettingCapabilities,
                    "GetDefaultReplicationSettingData", inArgs, outArgs);
            for (CIMArgument<?> outArg : outArgs) {
                if (null == outArg)
                    continue;
                if (outArg.getName().equalsIgnoreCase(DEFAULT_INSTANCE)) {
                    CIMInstance repInstance = (CIMInstance) outArg.getValue();
                    if (null != repInstance) {
                        if (Mode.ASYNCHRONOUS.getMode() == modeValue) {
                            CIMProperty<?> existingProp = repInstance.getProperty(EMC_CONSISTENCY_EXEMPT);
                            CIMProperty<?> prop = null;
                            if (existingProp == null) {
                                // ConsistencyExempt property is now part of the smi-s standard. Available in providers 8.0+ (VMAX3 arrays)
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
                        } else {
                            modifiedInstance = repInstance;
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving Replication Setting Data Instance ", e);
        }
        return modifiedInstance;
    }

    public void rollbackSRDFMirrors(final StorageSystem system, final List<URI> sourceURIs,
                                    final List<URI> targetURIs, final TaskCompleter completer) {
        log.info("START Rolling back SRDF mirror");
        List<Volume> sources = dbClient.queryObject(Volume.class, sourceURIs);

        try {
            for (Volume source : sources) {
                StringSet targets = source.getSrdfTargets();
                for (String targetStr : targets) {
                    URI targetURI = URI.create(targetStr);
                    if (!targetURIs.contains(targetURI)) {
                        continue;
                    }
                    Volume target = dbClient.queryObject(Volume.class, targetURI);
                    rollbackSRDFMirror(system, source,target,false);
                }
            }
        } finally {
            if (null != completer) {
                completer.ready(dbClient);
            }
        }
    }

    private void rollbackSRDFMirror(StorageSystem system, Volume source, Volume target, boolean rollback) {
        log.info("START Rolling back SRDF mirror");
        try {
            performDetach(system, target, true, new TaskCompleter() {
                @Override
                protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
                        throws DeviceControllerException {
                    // ignore
                }
            });

            if (target.hasConsistencyGroup()) {
                log.info("Removing Volume from device Group on roll back");
                removeDeviceGroups(system, source.getId(), target.getId(), null);
            }
        } catch (Exception e) {
            String msg = format(FAILURE_MSG_FMT, "rollback", source.getId(), target.getId());
            log.warn(msg, e);
        }
    }

    /**
     * Removes the source and target from their device groups, which should in turn remove the
     * group.
     *
     * @param system
     * @param sourceURI
     * @param targetURI
     * @param completer
     */
    public void removeDeviceGroups(final StorageSystem system, final URI sourceURI,
                                   final URI targetURI, TaskCompleter completer) {
        log.info("START removing device groups");
        RemoteDirectorGroup group = null;
        StorageSystem targetSystem = null;
        Volume source = null;
        Volume target = null;
        try {
            if (null == completer) {
                completer = new SRDFTaskCompleter(sourceURI, targetURI, "remove volume from device group");
            }
            source = dbClient.queryObject(Volume.class, sourceURI);
            target = dbClient.queryObject(Volume.class, targetURI);
            targetSystem = dbClient.queryObject(StorageSystem.class,
                    target.getStorageController());
            group = dbClient.queryObject(RemoteDirectorGroup.class,
                    target.getSrdfGroup());
            boolean cgSourceCleanUpRequired = removeFromDeviceGroups(system, system, source, group);
            boolean cgTargetCleanUpRequired = removeFromDeviceGroups(targetSystem, system, target, group);

            //after volumes are deleted .group gets removed
            if (cgSourceCleanUpRequired || cgTargetCleanUpRequired) {
                BlockConsistencyGroup targetCG = dbClient.queryObject(BlockConsistencyGroup.class,
                        target.getConsistencyGroup());
                if (null != targetCG) {
                    log.info("Set target {}-->{} as inactive",targetCG.getLabel(),targetCG.getId());
                    targetCG.setInactive(true);
                    dbClient.persistObject(targetCG);
                }

                BlockConsistencyGroup sourceCG = dbClient.queryObject(BlockConsistencyGroup.class, source.getConsistencyGroup());
                if (null != sourceCG) {
                    log.info("Clearing properties of source CG {}-->{}", sourceCG.getLabel(), sourceCG.getId());
                    // Clear the CG types and add the LOCAL types

                    if (null != sourceCG.getTypes()) {
                        sourceCG.getTypes().clear();
                    }
                    sourceCG.addConsistencyGroupTypes(Types.LOCAL.name());

                    // Remove the source storage system from the consistency group mappings
                    StringSetMap systemConsistencyGroups = sourceCG.getSystemConsistencyGroups();
                    if (systemConsistencyGroups != null) {
                        // CTRL-11467. For 8.0.3 provider (Add SRDF protection for local CG volume), there will be 2 RGs created.
                        StringSet systemCGNames = systemConsistencyGroups.get(system.getId().toString());
                        if (systemCGNames != null && systemCGNames.size() > 1) {
                            // remove the SRDF CG entry
                            systemCGNames.remove(sourceCG.getLabel());
                        } else {
                            systemConsistencyGroups.remove(system.getId().toString());
                        }
                    }
                    dbClient.persistObject(sourceCG);
                }
            }

        } catch (Exception e) {
            String msg = format(FAILURE_MSG_FMT, "remove srdf replication groups for ", sourceURI,
                    targetURI);
            log.warn(msg, e);
        } finally {
            // update DB objects
            // this step is actually a defensive check, hence even if it fails, remove the volumes, hence its already removed.
            if (group.getVolumes() != null) {
                group.getVolumes().remove(source.getNativeGuid());
                group.getVolumes().remove(target.getNativeGuid());
            }
            if (group.getVolumes() == null || group.getVolumes().isEmpty()) {
                // update below items only when we are removing last pair from Group
                if (NullColumnValueGetter.isNotNullValue(group.getSourceReplicationGroupName())) {
                    group.setSourceReplicationGroupName(NullColumnValueGetter.getNullStr());
                    group.setTargetReplicationGroupName(NullColumnValueGetter.getNullStr());
                    group.setSupportedCopyMode(SupportedCopyModes.ALL.toString());
                }

                if (targetSystem.getTargetCgs() != null && targetSystem.getTargetCgs().size() > 0) {
                    URI cgUri = source.getConsistencyGroup();
                    if (cgUri != null) {
                        targetSystem.getTargetCgs().remove(cgUri.toString());
                        dbClient.persistObject(targetSystem);
                    }
                }
            }
            dbClient.updateAndReindexObject(group);

            completer.ready(dbClient);
        }
    }

    /**
     * Build a list of SyncPair to pass along with the AddSyncPair method.
     *
     * @param system
     * @param sourceURIs
     * @param remoteDirectorGroupURI
     * @param completer
     */
    public void addVolumePairsToCg(StorageSystem system, List<URI> sourceURIs, URI remoteDirectorGroupURI,
                                   TaskCompleter completer) {

        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, remoteDirectorGroupURI);
        List<CIMObjectPath> syncPairs = newArrayList();
        List<Volume> sources = dbClient.queryObject(Volume.class, sourceURIs);

        for (Volume source : sources) {
            for (String targetStr : source.getSrdfTargets()) {
                URI targetURI = URI.create(targetStr);
                Volume target = dbClient.queryObject(Volume.class, targetURI);
                CIMObjectPath syncPair = utils.getStorageSynchronizedObject(system, source, target);
                syncPairs.add(syncPair);
            }
        }

        CIMObjectPath groupSynchronized = getGroupSyncObject(system, sources.get(0),
                group.getSourceReplicationGroupName(), group.getTargetReplicationGroupName());

        if (groupSynchronized == null || syncPairs.isEmpty()) {
            log.warn("Expected Group Synchronized not found");
            log.error("Expected Group Synchronized not found for volumes {}", sources);
            ServiceError error = SmisException.errors
                    .jobFailed("Expected Group Synchronized not found");
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
            return;
        }

        @SuppressWarnings("rawtypes")
        CIMArgument[] inArgs = helper.getAddSyncPairInputArguments(groupSynchronized,
                syncPairs.toArray(new CIMObjectPath[syncPairs.size()]));
        try {
            helper.callModifyReplica(system, inArgs);
            completer.ready(dbClient);
        } catch (WBEMException wbeme) {
            log.error("SMI-S error adding sync pairs for volumes {}", sources, wbeme);
            ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
        } catch (Exception e) {
            log.error("Error error adding sync pairs for volumes {}", sources, e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
        }

    }

    public void removeSRDFSyncPair(final StorageSystem system, final URI sourceURI,
                                   final URI targetURI, final boolean rollback, final TaskCompleter completer) {
        boolean setReadyState = false;
        ServiceError error = null;
        try {
            Volume source = dbClient.queryObject(Volume.class, sourceURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                    source.getStorageController());
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                    target.getSrdfGroup());
            CIMObjectPath syncPair = null;
            CIMObjectPath groupSynchronized = null;
            syncPair = utils.getStorageSynchronizedObject(sourceSystem, source, target);
            StorageSystem systemWithCg = findProviderWithGroup(sourceSystem, targetSystem,
                    source.getConsistencyGroup());
            groupSynchronized = getGroupSyncObjectForPairRemoval(systemWithCg, source,
                    group.getSourceReplicationGroupName(), group.getTargetReplicationGroupName());

            if (groupSynchronized != null && null != syncPair) {
                CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(systemWithCg,
                        Mode.valueOf(target.getSrdfCopyMode()).getMode());
                @SuppressWarnings("rawtypes")
                CIMArgument[] inArgs = helper.getRemoveSyncPairInputArguments(groupSynchronized,
                        syncPair, replicationSettingDataInstance);

                helper.callModifyReplica(systemWithCg, inArgs);

                if (group.getVolumes() != null) {
                    group.getVolumes().remove(source.getNativeGuid());
                    group.getVolumes().remove(target.getNativeGuid());
                }
                dbClient.persistObject(group);

            } else {
                log.warn("Expected Group Synchronized not found for volume {}, probably removed already.", sourceURI);
                //proceed with next step even if it fails.
            }
            setReadyState = true;
        } catch (WBEMException wbeme) {
            log.error("SMI-S error removing sync pair mirror for {}", sourceURI, wbeme);
            error = SmisException.errors.jobFailed(wbeme.getMessage());
        } catch (Exception e) {
            log.error("Error error removing sync pair mirror for {}", sourceURI, e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (rollback && !setReadyState) {
                completer.error(dbClient, error);
            } else {
                completer.ready(dbClient);
            }
        }

    }

    @SuppressWarnings("rawtypes")
    public void createSRDFVolumePair(final StorageSystem sourceSystem, final URI sourceURI,
                                     final URI targetURI, final TaskCompleter completer) {
        try {
            Volume source = dbClient.queryObject(Volume.class, sourceURI);
            Volume target = dbClient.queryObject(Volume.class, targetURI);
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
            int modeValue = Mode.valueOf(target.getSrdfCopyMode()).getMode();
            CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(sourceSystem);
            CIMObjectPath srcVolumePath = cimPath.getVolumePath(sourceSystem, source.getNativeId());
            CIMObjectPath tgtVolumePath = cimPath.getVolumePath(targetSystem, target.getNativeId());
            CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(sourceSystem,
                    group);
            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(sourceSystem, modeValue);
            CIMArgument[] inArgs = helper.getCreateElementReplicaForSRDFInputArguments(
                    srcVolumePath, tgtVolumePath, repCollectionPath, modeValue,
                    replicationSettingDataInstance);
            CIMArgument[] outArgs = new CIMArgument[5];
            helper.invokeMethodSynchronously(sourceSystem, srcRepSvcPath,
                    SmisConstants.CREATE_ELEMENT_REPLICA, inArgs, outArgs,
                    new SmisSRDFCreateMirrorJob(null, sourceSystem.getId(), completer));
        } catch (WBEMException wbeme) {
            log.error("SMI-S error creating mirror for {}", sourceURI, wbeme);
            ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
        } catch (Exception e) {
            log.error("Error creating mirror for {}", sourceURI, e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
        }
    }

    private CIMInstance getInstance(final CIMObjectPath path, final StorageSystem sourceSystem) {
        try {
            return helper.checkExists(sourceSystem, path, false, false);
        } catch (Exception e) {
        }
        return null;
    }

    public void performFailover(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performFailover");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;

        try {
            // FIXME Have to be more explicit here since failing over twice results in failback
            Volume sourceVolume = getSourceVolume(target);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
            Collection<CIMObjectPath> syncPaths = utils.getSynchronizations(sourceSystem, sourceVolume,
                    targetSystem, target, false);
            CIMInstance firstSync = getInstance(syncPaths.iterator().next(), sourceSystem);

            SRDFOperationContext failoverCtx = null;
            if (isFailedOver(firstSync)) {
                failoverCtx = getContextFactory(sourceSystem).build(SRDFOperation.FAIL_BACK, target);
                failoverCtx.perform();
                ((SRDFLinkFailOverCompleter)completer).setLinkStatus(Volume.LinkStatus.IN_SYNC);
            } else {
                failoverCtx = getContextFactory(sourceSystem).build(SRDFOperation.FAIL_OVER, target);
                failoverCtx.perform();
            }
        } catch (Exception e) {
            log.error("Failed to failover srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void failoverCancelSyncPair(final StorageSystem targetSystem, final Volume target,
                                       final TaskCompleter completer) {
        CIMObjectPath syncPath = null;
        NamedURI sourceVolumeNamedUri = target.getSrdfParent();
        if (NullColumnValueGetter.isNullNamedURI(sourceVolumeNamedUri)) {
            throw DeviceControllerException.exceptions.failbackVolumeOperationFailed(
                    target.getNativeGuid() + " doesn't have any parent", null);
        }
        URI sourceVolUri = sourceVolumeNamedUri.getURI();
        Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolUri);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());
        CIMInstance syncInstance = null;
        syncPath = cimPath.getStorageSynchronized(sourceSystem, sourceVolume, targetSystem,
                target);
        syncInstance = getInstance(syncPath, sourceSystem);
        if (null == syncInstance) {
            log.error(
                    "Failed to fail over source volume {}, as expected storage synchronized association  not found ",
                    target.getSrdfParent().getURI());
            ServiceError error = SmisException.errors.jobFailed(format(
                    STORAGE_SYNCHRONIZATION_NOT_FOUND, target.getSrdfGroup()));
            completer.error(dbClient, error);
            return;
        }
        if (null != syncInstance &&  isFailedOver(syncInstance)) {
            log.info("Source Volume {} already in failed over State, invoking failback", sourceVolUri);
            failBackSyncPair(targetSystem, target, completer);
            return;
        }
        ServiceError error = SmisException.errors.jobFailed(format(
                REPLICATION_NOT_IN_RIGHT_STATE, target.getSrdfGroup()));
        completer.error(dbClient, error);
    }

    private void changeSRDFVolumeBehaviors(Volume sourceVolume, Volume targetVolume, DbClient dbClient, String status) {
        List<Volume> volumes = new ArrayList<>();
        
        if (sourceVolume.hasConsistencyGroup()) {
            List<URI> srcVolumeUris =   dbClient.queryByConstraint(
                    getVolumesByConsistencyGroup(sourceVolume.getConsistencyGroup()));
            List<Volume> cgSrcVolumes = dbClient.queryObject(Volume.class, srcVolumeUris);
            volumes.addAll(cgSrcVolumes);
        } else {
            volumes.add(sourceVolume);
            /**
             * Swap operation will happen for all volumes under ra group for Async without CG.
             * Adding the missing source volumes to change the personalities of the missing volumes
             */
            if(Mode.ASYNCHRONOUS.name().equalsIgnoreCase(targetVolume.getSrdfCopyMode())){
        		volumes.addAll(utils.getRemainingSourceVolumesForAsyncRAGroup(sourceVolume, targetVolume));
        	}
        }
        log.debug("volumes size:{}", volumes.size());
        for (Volume sourceVol : volumes) {
            StringSet srdfTargets = new StringSet();
            String copyMode = null;
            srdfTargets.addAll(sourceVol.getSrdfTargets()) ;
            //CG cannot have different RA Groups and copyMode
            URI raGroupUri = null;
            
            for (String targetUri : srdfTargets) {
                Volume targetVol = dbClient.queryObject(Volume.class, URI.create(targetUri));
                raGroupUri = targetVol.getSrdfGroup();
                
                copyMode = targetVol.getSrdfCopyMode();
                targetVol.setPersonality(SOURCE.toString());
                targetVol.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                srdfTargets.add(sourceVol.getId().toString());
                srdfTargets.remove(targetVol.getId().toString());
                if (null == targetVol.getSrdfTargets()) {
                    targetVol.setSrdfTargets(new StringSet());
                }
                targetVol.getSrdfTargets().addAll(srdfTargets);
                targetVol.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                targetVol.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                targetVol.setSrdfGroup(NullColumnValueGetter.getNullURI());
                targetVol.setLinkStatus(status);
                // Set source fields
                sourceVol.setLinkStatus(status);
                sourceVol.setSrdfParent(new NamedURI(targetVol.getId(), targetVol.getLabel()));
                dbClient.persistObject(targetVol);
            }
            sourceVol.setPersonality(TARGET.toString());
            sourceVol.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
            sourceVol.setSrdfCopyMode(copyMode);
            sourceVol.setSrdfGroup(raGroupUri);
            sourceVol.getSrdfTargets().clear();
            dbClient.persistObject(sourceVol);
        }
    }
    

    private void changeRemoteDirectorGroup(URI remoteGroupUri) {
        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, remoteGroupUri);
        // Swap names
        String srcName = group.getSourceReplicationGroupName();
        group.setSourceReplicationGroupName(group.getTargetReplicationGroupName());
        group.setTargetReplicationGroupName(srcName);
        // TODO Should we swap anything else here?  Source/Remote system?

        dbClient.persistObject(group);
    }

    /**
     * Convenience method for creating a device group with a single volume.
     *
     * @param system
     * @param forProvider
     * @param volume
     * @param dbClient
     * @return
     * @throws Exception
     */
    private CIMObjectPath createDeviceGroup(final StorageSystem system,
                                            final StorageSystem forProvider, final BlockObject volume, final DbClient dbClient)
            throws Exception {
        return createDeviceGroup(system, forProvider, asList(volume), dbClient);
    }

    /**
     * Create a device group to contain the given list of volumes.
     *
     * @param system
     * @param forProvider
     * @param volumes
     * @param dbClient
     * @return
     * @throws Exception
     */
    private CIMObjectPath createDeviceGroup(final StorageSystem system,
                                            final StorageSystem forProvider, final List<? extends BlockObject> volumes,
                                            final DbClient dbClient)
            throws Exception {
        URI cgUri = volumes.get(0).getConsistencyGroup();
        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);

        String cgName = cgObj.getAlternateLabel();
        if (null == cgName) {
            cgName = cgObj.getLabel();
        }
        CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgName, forProvider, system);
        if (null != groupPath) {
            return groupPath;
        }

        Collection<String> nativeIds = transform(volumes, fctnBlockObjectToNativeID());
        CIMObjectPath repSvcPath = cimPath.getControllerReplicationSvcPath(system);
        CIMArgument[] cgInArgs = helper.getCreateReplicationGroupCreateInputArguments(system, cgName,
                cimPath.getVolumePaths(system, nativeIds.toArray(new String[nativeIds.size()])));
        CIMArgument[] cgOutArgs = new CIMArgument[5];
        helper.invokeMethod(forProvider, repSvcPath, CREATE_GROUP, cgInArgs, cgOutArgs);
        groupPath = cimPath.getCimObjectPathFromOutputArgs(cgOutArgs, CP_REPLICATION_GROUP);
        // update consistency Group
        cgObj.addSystemConsistencyGroup(system.getId().toString(), cgName);

        // Update CG requested types
        cgObj.getRequestedTypes().clear();
        cgObj.getRequestedTypes().add(Types.SRDF.toString());

        // Update CG types
        cgObj.getTypes().clear();
        cgObj.getTypes().add(Types.SRDF.toString());

        // volumes from same array will reside in one CG
        cgObj.setStorageController(system.getId());
        dbClient.persistObject(cgObj);
        return groupPath;
    }

    private CIMObjectPath getDeviceGroup(final StorageSystem system,
                                         final StorageSystem forProvider, final BlockObject volume, final DbClient dbClient)
            throws Exception {
        URI cgUri = volume.getConsistencyGroup();
        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        String cgName = cgObj.getAlternateLabel();
        if (null == cgName) {
            cgName = cgObj.getLabel();
        }
        CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgName, forProvider, system);
        return groupPath;
    }

    private StorageSystem findProviderWithGroup(final StorageSystem srcSystem,
                                                final StorageSystem targetSystem, URI cgUri) {
        if (NullColumnValueGetter.isNullURI(cgUri)) {
            return srcSystem;
        }

        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        String cgLabel = cgObj.getLabel();
        if (null != cgObj.getAlternateLabel()) {
            cgLabel = cgObj.getAlternateLabel();
        }
        CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgLabel, srcSystem, srcSystem);
        if (null == groupPath) {
            log.info("Replication Group {} not available in source Provider {}", cgLabel,
                    srcSystem.getActiveProviderURI());
            groupPath = helper.checkDeviceGroupExists(cgLabel, targetSystem, targetSystem);
            if (null == groupPath) {
                log.info("Replication Group {} not available in target Provider {}",
                        cgLabel, targetSystem.getActiveProviderURI());
                return null;
            } else {
                log.info("Replication Group {}  available in target Provider {}", cgLabel,
                        targetSystem.getActiveProviderURI());
                return targetSystem;
            }

        }
        log.info("Replication Group {} available in source Provider {}", cgLabel,
                srcSystem.getActiveProviderURI());
        return srcSystem;
    }


    private List<Volume> getVolumesPartOfReplicationGroup(final CIMObjectPath replicationGroupPath,
                                                          final StorageSystem forProvider, final StorageSystem system) {
        CloseableIterator<CIMObjectPath> volumePaths = null;
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            volumePaths = helper.getAssociatorNames(forProvider, replicationGroupPath, null, STORAGE_VOLUME_CLASS, null,
                    null);
            while (volumePaths.hasNext()) {
                String nativeGuid = helper.getVolumeNativeGuid(volumePaths.next());
                Volume volume =  SmisUtils.checkStorageVolumeExistsInDB(nativeGuid, dbClient);
                if (null != volume) {
                    volumes.add(volume);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get Volumes from Device Group ",  e);
        } finally {
            if (null != volumePaths) {
                volumePaths.close();
            }
        }
        return volumes;
    }

    @SuppressWarnings("rawtypes")
    private boolean removeFromDeviceGroups(final StorageSystem system,
                                           final StorageSystem forProvider, final Volume volume, RemoteDirectorGroup group) {
        log.info("removeFromDeviceGroups:");
        log.info("Volume: {} / {}", volume.getDeviceLabel(), volume.getNativeId());
        log.info("Array: {}", system.getSerialNumber());
        log.info("Provider: {}", forProvider.getSmisProviderIP());
        boolean removedFromAllGroups = true;
        try {
            Set<CIMObjectPath> deviceGroups = getDeviceGroup(system, forProvider, volume);
            CIMObjectPath volumePath = cimPath.getBlockObjectPath(system, volume);
            CIMObjectPath repSvcPath = cimPath.getControllerReplicationSvcPath(system);

            for (CIMObjectPath deviceGroupPath : deviceGroups) {
                // From 8.0 providers, DeviceMaskingGroup is also reported as RG and vice-versa.
                // check to skip the default ReplicationGroup (RG) created.
                if (!(deviceGroupPath.toString().contains(group.getSourceReplicationGroupName())
                        || deviceGroupPath.toString().contains(group.getTargetReplicationGroupName()))) {
                    continue;
                }

                log.info(format("Found Volume %s to be a member of group %s", volume.getNativeId(), deviceGroupPath));
                CIMArgument[] inArgs = helper.getRemoveMembersInputArguments(deviceGroupPath,
                        new CIMObjectPath[] { volumePath });
                CIMArgument[] outArgs = new CIMArgument[5];
                helper.invokeMethod(forProvider, repSvcPath, REMOVE_MEMBERS, inArgs, outArgs);

                if (getVolumesPartOfReplicationGroup(deviceGroupPath, forProvider, system).size() != 0) {
                    log.warn(format("Group %s still has Volumes part of it.", deviceGroupPath));
                    removedFromAllGroups = false;
                }

                // From 8.0 providers, DeleteOnEmptyElement property is not supported on Group creations.
                // hence, we need to check and delete the RG when it becomes empty.
                CIMInstance deviceGroupInstance = helper.checkExists(forProvider, deviceGroupPath, false, false);
                if (deviceGroupInstance != null) {
                    if (getVolumesPartOfRG(deviceGroupPath, forProvider, system).isEmpty()) {
                        // delete RG
                        log.info("No more volumes left on Group {}, Deleting it.", deviceGroupPath.toString());
                        inArgs = helper.getDeleteReplicationGroupInputArguments(system, 
                                deviceGroupInstance.getPropertyValue(CP_ELEMENT_NAME).toString());
                        helper.invokeMethod(forProvider, repSvcPath, SmisConstants.DELETE_GROUP, inArgs,
                                outArgs);
                    }
                }
            }

        } catch (WBEMException e) {
            log.debug("Failed to remove volume {} from its replication group, probably already removed", volume.getId(), e);
        } catch (Exception e) {
            log.debug("Failed to remove volume {} from its replication group, probabaly already removed", volume.getId(), e);
        }
        return removedFromAllGroups;
    }

    private Set<String> getVolumesPartOfRG(final CIMObjectPath deviceGroupPath,
            final StorageSystem forProvider, final StorageSystem system) {

        CloseableIterator<CIMObjectPath> volumePaths = null;
        Set<String> deviceIds = new HashSet<String>();
        try {
            volumePaths = helper.getAssociatorNames(forProvider, deviceGroupPath, null, STORAGE_VOLUME_CLASS, null,
                    null);
            while (volumePaths.hasNext()) {
                deviceIds.add(helper.getVolumeNativeGuid(volumePaths.next()));
            }
        } catch (Exception e) {
            log.debug("Failed to get Volumes from Device Group ",  e);
        } finally {
            if (null != volumePaths) {
                volumePaths.close();
            }
        }
        return deviceIds;
    }

    /**
     * Target volume needs to be passed in, on which fail over happened.
     *
     * @param targetSystem
     * @param targetVolume
     * @param completer
     */
    public void failBackSyncPair(final StorageSystem targetSystem, final Volume targetVolume,
                                 final TaskCompleter completer) {
        checkTargetHasParentOrFail(targetVolume);

        Volume sourceVolume = getSourceVolume(targetVolume);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
        Collection<CIMObjectPath> syncPaths = new ArrayList<>();

        try {
            if (isAsyncWithoutCG(targetVolume)) {
                log.info("Detected Async mode without CG.  Will operate on all volumes in this RDF group.");
                syncPaths.addAll(utils.getStorageSynchronizationsInRemoteGroup(sourceSystem, targetVolume));
            } else {
                // Handle Sync with/without CG + Async with CG.
                syncPaths.addAll(utils.getSynchronizations(sourceSystem, sourceVolume, targetSystem, targetVolume));
            }

            //this hack is needed, as currently triggering fail over twice invokes failback
            if (completer instanceof SRDFLinkFailOverCompleter) {
                ((SRDFLinkFailOverCompleter)completer).setLinkStatus(Volume.LinkStatus.IN_SYNC);
            }
            log.info("SRDF copy mode is {}", targetVolume.getSrdfCopyMode());

            StorageSystem systemToUse = findProviderWithGroup(sourceSystem, targetSystem, sourceVolume.getConsistencyGroup());
            if (systemToUse == null) {
                systemToUse = findProviderWithGroup(sourceSystem, targetSystem, targetVolume.getConsistencyGroup());
                if (systemToUse == null) {
                    log.error("Failed to failback source volume {}, as replication groups not found",
                            sourceVolume.getId());
                    ServiceError error = SmisException.errors.noConsistencyGroupWithGivenName();
                    completer.error(dbClient, error);
                    return;
                }
            }

            CIMArgument[] inArgs = helper.getASyncPairFailBackInputArguments(syncPaths);
            helper.callModifyListReplica(systemToUse, inArgs);
            completer.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to fail back source volume {}",
                    targetVolume.getSrdfParent().getURI(), e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            completer.error(dbClient, error);
        }
    }

    public void performSwap(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performSwap");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            Volume sourceVolume = getSourceVolume(target);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
            Collection<CIMObjectPath> syncPaths = utils.getSynchronizations(sourceSystem, sourceVolume,
                    targetSystem, target, false);
            CIMInstance firstSync = getInstance(syncPaths.iterator().next(), sourceSystem);
            AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(sourceSystem);
            SRDFOperationContext ctx = null;

            if (!isFailedOver(firstSync)) {
                log.info("Failing over link");
                ctx = ctxFactory.build(SRDFOperation.FAIL_OVER, target);
                ctx.perform();
            }

            ctx = ctxFactory.build(SRDFOperation.SWAP, target);
            ctx.perform();
            log.info(" Swapping Volume Pair {} succeeded ", sourceVolume.getId());

            log.info("Changing R1 and R2 characteristics after swap");
            changeSRDFVolumeBehaviors(sourceVolume, target, dbClient, LinkStatus.SWAPPED.toString());
            log.info("Updating RemoteDirectorGroup after swap");
            changeRemoteDirectorGroup(target.getSrdfGroup());

            // Refresh our view of the target, since it is now the source volume.
            target = dbClient.queryObject(Volume.class, sourceVolume.getId());

            boolean success = false;
            int attempts = 0;

            while (!success && attempts <= RESUME_AFTER_SWAP_MAX_ATTEMPTS) {
                try {
                    ctx = ctxFactory.build(SRDFOperation.ESTABLISH, target);
                    ctx.appendFilters(new ErrorOnEmptyFilter());
                    ctx.perform();

                    success = true;
                } catch (WBEMException | NoSynchronizationsFoundException e) {
                    attempts++;
                    log.warn(format(RESUME_AFTER_SWAP_EXCEPTION_MSG, attempts, RESUME_AFTER_SWAP_MAX_ATTEMPTS), e);
                    Thread.sleep(RESUME_AFTER_SWAP_SLEEP);
                }
            }

            if (!success) {
                URI sourceId = target.getSrdfParent().getURI();
                String msg = format("Failed to resume SRDF link after swap for source: %s", sourceId);
                log.error(msg);
                error = SmisException.errors.establishAfterSwapFailure(sourceId.toString(), target.getId().toString());
            }
        } catch (RemoteGroupAssociationNotFoundException e) {
            log.warn("No remote group association found for {}.  It may have already been removed.", target.getId());
        } catch (Exception e) {
            log.error("Failed to suspend srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void performSplit(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performSplit");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            SRDFOperationContext splitCtx = getContextFactory(system).build(SRDFOperation.SPLIT, target);
            splitCtx.perform();
        } catch (RemoteGroupAssociationNotFoundException e) {
            log.warn("No remote group association found for {}.  It may have already been removed.", target.getId());
        } catch (Exception e) {
            log.error("Failed to pause srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void performSuspend(StorageSystem system, Volume target, boolean consExempt, TaskCompleter completer) {
        log.info("START performSuspend (consExempt={})", consExempt);
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            SRDFOperation op = consExempt ? SRDFOperation.SUSPEND_CONS_EXEMPT : SRDFOperation.SUSPEND;
            SRDFOperationContext suspendCtx = getContextFactory(system).build(op, target);
            suspendCtx.perform();
        } catch (RemoteGroupAssociationNotFoundException e) {
            log.warn("No remote group association found for {}.  It may have already been removed.", target.getId());
        } catch (Exception e) {
            log.error("Failed to suspend srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void performEstablish(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performEstablish");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            SRDFOperationContext establishCtx = getContextFactory(system).build(SRDFOperation.ESTABLISH, target);
            establishCtx.appendFilters(new BrokenSynchronizationsOnlyFilter(utils));
            establishCtx.perform();
        } catch (Exception e) {
            log.error("Failed to establish srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void performRestore(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performRestore");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            SRDFOperationContext restoreCtx = getContextFactory(system).build(SRDFOperation.RESTORE, target);
            restoreCtx.appendFilters(new BrokenSynchronizationsOnlyFilter(utils));
            restoreCtx.perform();
        } catch (Exception e) {
            log.error("Failed to restore srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void performDetach(StorageSystem system, Volume target, boolean onGroup, TaskCompleter completer) {
        log.info("START performDetach");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(system);

            SRDFOperation suspendOp = null;
            SRDFOperation detachOp = null;

            if (onGroup) {
                suspendOp = SRDFOperation.SUSPEND;
                detachOp = SRDFOperation.DELETE_GROUP_PAIRS;
            } else {
                suspendOp = SRDFOperation.SUSPEND_CONS_EXEMPT;
                detachOp = SRDFOperation.DELETE_PAIR;
            }
            ctxFactory.build(suspendOp, target).perform();
            ctxFactory.build(detachOp, target).perform();

            utils.removeFromRemoteGroups(target);
        } catch (Exception e) {
            log.error("Failed to detach srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    public void startSRDFLink(final StorageSystem targetSystem, final Volume targetVolume,
                              final TaskCompleter completer) {
        try {
            NamedURI sourceVolumeNamedUri = targetVolume.getSrdfParent();
            if (NullColumnValueGetter.isNullNamedURI(sourceVolumeNamedUri)) {
                throw DeviceControllerException.exceptions.resumeVolumeOperationFailed(
                        targetVolume.getNativeGuid() + " doesn't have any parent", null);
            }
            URI sourceVolUri = sourceVolumeNamedUri.getURI();
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolUri);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                    sourceVolume.getStorageController());
            CIMObjectPath synchronizedPath = null;
            if (!sourceVolume.hasConsistencyGroup()) {
                // construct Storage synchronized Object
                synchronizedPath = cimPath.getStorageSynchronized(sourceSystem, sourceVolume,
                        targetSystem, targetVolume);
                if (null == synchronizedPath) {
                    log.error(
                            "Failed to fail back source volume {}, as expected storage synchronized association not found ",
                            targetVolume.getSrdfParent().getURI());
                    ServiceError error = SmisException.errors.jobFailed(format(
                            STORAGE_SYNCHRONIZATION_NOT_FOUND, targetVolume.getSrdfParent()
                                    .getURI()));
                    completer.error(dbClient, error);
                    return;
                }
                log.info("Establishing SRDF Link in Sync Mode");
            } else {
                // construct group synchronized object
                synchronizedPath = utils.getGroupSynchronized(targetVolume, sourceSystem);
                if (null == synchronizedPath) {
                    log.error(
                            "Failed to fail back source volume {}, as expected replication Group Names are not found in RA Group ",
                            targetVolume.getSrdfParent().getURI());
                    ServiceError error = SmisException.errors.jobFailed(format(
                            REPLICATION_GROUP_NAMES_NOT_FOUND, targetVolume.getSrdfGroup()));
                    completer.error(dbClient, error);
                    return;
                }
                log.info("Establishing SRDF Link in Async Mode");
            }
            StorageSystem systemWithCg = findProviderWithGroup(sourceSystem, targetSystem,
                    sourceVolume.getConsistencyGroup());

            CIMInstance syncInstance = getInstance(synchronizedPath, systemWithCg);
            if (null == syncInstance) {
                log.info("No valid synchronization found, hence restablishing link");
                createSRDFMirror(sourceSystem, sourceVolUri, targetVolume.getId(), completer);
            } else {
                log.info("Link already established..");
            }
            completer.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to start srdf link {}", targetVolume.getSrdfParent().getURI(), e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            completer.error(dbClient, error);
        }
    }

    public void performStop(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performStop");
        checkTargetHasParentOrFail(target);

        AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(system);
        ServiceError error = null;
        try {
            List<Volume> volumes = utils.getAssociatedVolumes(system, target);

            Collection<Volume> srcVolumes = newArrayList(filter(volumes, volumePersonalityPredicate(SOURCE)));
            Collection<Volume> tgtVolumes = newArrayList(filter(volumes, volumePersonalityPredicate(TARGET)));

            ((SRDFLinkStopCompleter) completer).setVolumes(srcVolumes, tgtVolumes);
            log.info("Sources: {}", Joiner.on(", ").join(transform(srcVolumes, fctnBlockObjectToNativeGuid())));
            log.info("Targets: {}", Joiner.on(", ").join(transform(tgtVolumes, fctnBlockObjectToNativeGuid())));

            ctxFactory.build(SRDFOperation.SUSPEND, target).perform();
            ctxFactory.build(SRDFOperation.DELETE_GROUP_PAIRS, target).perform();

            if (target.hasConsistencyGroup()) {
                FindProviderFactory providerFactory = new FindProviderFactory(dbClient, helper);
                StorageSystem provider = providerFactory.withGroup(target).find();
                cleanAllCgVolumesFromDeviceGroups(tgtVolumes, provider);
            }
        } catch (RemoteGroupAssociationNotFoundException e) {
            log.warn("No remote group association found for {}.  It may have already been removed.", target.getId());
        } catch (Exception e) {
            log.error("Failed to stop srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }
    }

    private void cleanAllCgVolumesFromDeviceGroups(Collection<Volume> tgtVolumes, StorageSystem sourceSystem) {
        for (Volume target : tgtVolumes) {
            removeDeviceGroups(sourceSystem, target.getSrdfParent().getURI(), target.getId(), null);
        }
    }

    private boolean isFailedOver(final CIMInstance syncInstance) {
        String copyState = syncInstance.getPropertyValue(CP_COPY_STATE).toString();
        if (String.valueOf(FAILOVER_SYNC_PAIR).equalsIgnoreCase(copyState)) {
            return true;
        }
        return false;
    }

    private Set<CIMObjectPath> getDeviceGroup(final StorageSystem system,
                                              final StorageSystem forProvider, final Volume volume) {
        CloseableIterator<CIMObjectPath> names = null;
        Set<CIMObjectPath> deviceGroups = new HashSet<>();
        try {
            CIMObjectPath path = cimPath.getBlockObjectPath(system, volume);
            names = helper.getAssociatorNames(forProvider, path, null, SE_REPLICATION_GROUP, null,
                    null);
            while (names.hasNext()) {
                deviceGroups.add(names.next());
            }
        } catch (WBEMException e) {
            log.warn("Failed to acquire replication groups associated with Volume {}", volume.getId(), e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return deviceGroups;
    }

    private CIMObjectPath getDeviceGroup(final StorageSystem system,
                                         final StorageSystem forProvider, final Volume volume, final String sourceGpName,
                                         final String tgtGpName) {
        if (null == sourceGpName && null == tgtGpName) {
            return null;
        }
        CloseableIterator<CIMObjectPath> names = null;
        try {
            CIMObjectPath path = cimPath.getStorageSystem(system);
            names = helper.getAssociatorNames(forProvider, path, null, SE_REPLICATION_GROUP, null,
                    null);
            while (names.hasNext()) {
                CIMObjectPath replicationGroupPath = names.next();
                if (replicationGroupPath.toString().contains(sourceGpName)
                        || replicationGroupPath.toString().contains(tgtGpName)) {
                    return replicationGroupPath;
                }
            }
        } catch (WBEMException e) {
            log.warn("Failed to remove volume {} from its replication group", volume.getId(), e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return null;
    }

    private CIMObjectPath getGroupSyncObject(final StorageSystem system, final Volume source,
                                             final String sourceGpName, final String tgtGpName) {
        CloseableIterator<CIMObjectPath> iterator = null;
        try {
            CIMObjectPath srcGroupPath = getDeviceGroup(system, system, source, sourceGpName,
                    tgtGpName);
            if (srcGroupPath == null) {
                throw new IllegalStateException("Expected to find a sync instance for source");
            }
            iterator = helper.getReference(system, srcGroupPath, SE_GROUP_SYNCHRONIZED_RG_RG, null);
            if (iterator.hasNext()) {
                return iterator.next();
            }
        } catch (Exception e) {
            log.debug("Failed to acquire group synchronization instance", e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return null;
    }

    private CIMObjectPath getGroupSyncObjectForPairRemoval(final StorageSystem system, final Volume source,
                                             final String sourceGpName, final String tgtGpName) {
        CloseableIterator<CIMObjectPath> iterator = null;
        try {
            // get ReplicationGroup for the volume
            Set<CIMObjectPath> deviceGroups = getDeviceGroup(system, system, source);
            for (CIMObjectPath deviceGroup : deviceGroups) {
                if (deviceGroup.toString().contains(sourceGpName) || deviceGroup.toString().contains(tgtGpName)) {
                    iterator = helper.getReference(system, deviceGroup, SE_GROUP_SYNCHRONIZED_RG_RG, null);
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to acquire group synchronization instance", e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return null;
    }

    public Set<String> findVolumesPartOfRDFGroups(StorageSystem system, RemoteDirectorGroup rdfGroup) {
        CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(system,
                rdfGroup);
        CloseableIterator<CIMObjectPath> names = null;
        Set<String> volumes = new HashSet<String>();
        try {
            CIMObjectPath path = cimPath.getStorageSystem(system);
            names = helper.getAssociatorNames(system, repCollectionPath, null, STORAGE_VOLUME_CLASS, null,
                    null);
            while (names.hasNext()) {
                CIMObjectPath volumeGroupPath = names.next();
                String volumeNativeGuid = getVolumeNativeGuid(volumeGroupPath);
                volumes.add(volumeNativeGuid);


            }
        } catch (WBEMException e) {
            log.warn("Failed to get Volumes part of RDF Group {} ", rdfGroup.getNativeGuid(), e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return volumes;

    }

    private String getVolumeNativeGuid(CIMObjectPath path) {
        String systemName = path.getKey(CP_SYSTEM_NAME).getValue().toString()
                .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);;
        String id = path.getKey(CP_DEVICE_ID).getValue().toString();
        return NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                systemName.toUpperCase(), id);
    }

    public void createSRDFCgPairs(final StorageSystem sourceSystem, List<URI> sourceURIs, List<URI> targetURIs,
                                  SRDFMirrorCreateCompleter completer) {

        List<Volume> sourceVolumes = dbClient.queryObject(Volume.class, sourceURIs);
        List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetURIs);

        Volume firstSource = sourceVolumes.get(0);
        Volume firstTarget = targetVolumes.get(0);
        int modeValue = Mode.valueOf(firstTarget.getSrdfCopyMode()).getMode();
        RemoteDirectorGroup raGroup = dbClient.queryObject(RemoteDirectorGroup.class, firstTarget.getSrdfGroup());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, raGroup.getRemoteStorageSystemUri());

        CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(sourceSystem);

        CIMObjectPath srcCGPath = null;
        CIMObjectPath tgtCGPath = null;

        try {
            log.info("Creating sources group with: {}", firstSource.getNativeId());
            srcCGPath = createDeviceGroup(sourceSystem, sourceSystem, sourceVolumes, dbClient);
            String sourceGroupName = (String) srcCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Source volumes placed into replication group: {}", srcCGPath);

            log.info("Creating targets group with: {}", targetVolumes.get(0).getNativeId());
            tgtCGPath = createDeviceGroup(targetSystem, sourceSystem, targetVolumes, dbClient);
            String targetGroupName = (String) tgtCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Target volumes placed into replication group: {}", tgtCGPath);

            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                log.info("SRDF Link already established.");
                completer.ready(dbClient);
            }
            CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(sourceSystem,
                    raGroup);
            // look for existing volumes, if found then use AddSyncPair
            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(sourceSystem, modeValue);

            CIMArgument[] inArgs = helper.getCreateGroupReplicaForSRDFInputArguments(srcCGPath,
                    tgtCGPath, repCollectionPath, modeValue, replicationSettingDataInstance);
            CIMArgument[] outArgs = new CIMArgument[5];
            completer.setCGName(sourceGroupName, targetGroupName,
                    firstSource.getConsistencyGroup());
            helper.invokeMethodSynchronously(sourceSystem, srcRepSvcPath,
                    SmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs,
                    new SmisSRDFCreateMirrorJob(null, sourceSystem.getId(), completer));
        } catch (WBEMException wbeme) {
            String msg = format("SMI-S error creating mirror for Sources:%s Targets:%s", sourceURIs, targetURIs);
            log.error(msg, wbeme);
            // check whether synchronization really succeeds in Array
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        } catch (Exception e) {
            String msg = format("Error creating mirror for Sources:%s Targets:%s", sourceURIs, targetURIs);
            log.error(msg, e);
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, sourceSystem)) {
                completer.ready(dbClient);
            } else {
                if (e.getMessage().contains("Replication Control Succeeded")) {
                    String dbMsg = format("Replication Succeeded but save to DB failed exception leaves the SRDF relationship to get established properly after some time. Hence for now succeeding this operation. for Sources:%s Targets:%s", sourceURIs, targetURIs);
                    log.info(dbMsg, e);
                    completer.ready(dbClient);
                    return;
                }
                ServiceError error = SmisException.errors.jobFailed(e.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        }
    }

    /**
     * Checks with the SMI-S provider to ensure that ViPR's source and target volumes are paired up
     * correctly and fixes any inconsistencies.
     *
     * @param sourceURIs The source volumes
     * @param targetURIs The target volumes
     */
    public void updateSourceAndTargetPairings(List<URI> sourceURIs, List<URI> targetURIs) {
        final String KEY = "%s:%s";
        List<Volume> sources = dbClient.queryObject(Volume.class, sourceURIs);
        List<Volume> targets = dbClient.queryObject(Volume.class, targetURIs);

        // Convenience maps
        Map<String, Volume> volumesMap = new HashMap<>();
        for (Volume v : Iterables.concat(sources, targets)) {
            String key = format(KEY, v.getStorageController(), v.getNativeId());
            volumesMap.put(key, v);
        }
        final Map<String, String> tgtURI2tgtDevId = new HashMap<>();
        for (Volume target : targets) {
            tgtURI2tgtDevId.put(target.getId().toString(), target.getNativeId());
        }

        Volume firstSource = sources.get(0);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, firstSource.getStorageController());
        Volume firstTarget = targets.get(0);
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, firstTarget.getStorageController());

        Collection<CIMObjectPath> syncPairs = null;
        try {
            // Note that we may have existing sync pairs in the consistency group.
            syncPairs = getConsistencyGroupSyncPairs(sourceSystem, firstSource, targetSystem, firstTarget);
        } catch (WBEMException e) {
            log.error("Failed to update SRDF pairings", e);
            return;
        }

        for (CIMObjectPath syncPair : syncPairs) {
            log.info("Checking {}", syncPair);
            // Get the deviceID for the system (source) element from SMI-S provider
            String srcEl = syncPair.getKeyValue(CP_SYSTEM_ELEMENT).toString();
            CIMObjectPath srcElPath = new CIMObjectPath(srcEl);
            String trustedSrcDevId = srcElPath.getKeyValue(CP_DEVICE_ID).toString();

            // Get the deviceID for the synced (target) element from SMI-S provider
            String tgtEl = syncPair.getKeyValue(CP_SYNCED_ELEMENT).toString();
            CIMObjectPath tgtElPath = new CIMObjectPath(tgtEl);
            String trustedTgtDevId = tgtElPath.getKeyValue(CP_DEVICE_ID).toString();

            // Get ViPR's side which requires validating...
            String srcKey = format(KEY, sourceSystem.getId(), trustedSrcDevId);
            Volume srcVolume = volumesMap.get(srcKey);

            if (srcVolume == null) {
                // Skip existing source volumes that were part of the consistency group.
                log.info("Skipping as {} is an existing consistency group member", srcKey);
                continue;
            }

            StringSet srdfTargets = srcVolume.getSrdfTargets();
            Collection<String> uncheckedTgtDevIds = transform(srdfTargets, new Function<String, String>() {
                @Override
                public String apply(String targetURI) {
                    return tgtURI2tgtDevId.get(targetURI);
                }
            });

            if (!uncheckedTgtDevIds.contains(trustedTgtDevId)) {
                log.info("Found pairing inconsistency!");
                String msg = format("Source %s is paired with Target %s", trustedSrcDevId, trustedTgtDevId);
                log.info(msg);
                // The target found in the sync pair will need updating in ViPR (has wrong SRDF parent)
                Volume invalidTgt = volumesMap.get(format(KEY, targetSystem.getId(), trustedTgtDevId));
                // This SRDF parent will need its SRDF target list updating (remove the target)
                Volume invalidSrc = dbClient.queryObject(Volume.class, invalidTgt.getSrdfParent().getURI());
                // The source found in the sync pair will need its SRDF target list updating (add the real target)
                Volume trustedSrc = volumesMap.get(format(KEY, sourceSystem.getId(), trustedSrcDevId));

                invalidTgt.setSrdfParent(new NamedURI(trustedSrc.getId(), trustedSrc.getLabel()));
                trustedSrc.getSrdfTargets().add(invalidTgt.getId().toString());
                invalidSrc.getSrdfTargets().remove(invalidTgt.getId().toString());

                dbClient.updateAndReindexObject(asList(invalidTgt, trustedSrc, invalidSrc));
            }
        }
    }

    private Collection<CIMObjectPath> getConsistencyGroupSyncPairs(StorageSystem sourceSystem, Volume source,
                                                                   StorageSystem targetSystem, Volume target) throws WBEMException {
        List<URI> srcVolumeUris =   dbClient.queryByConstraint(getVolumesByConsistencyGroup(source.getConsistencyGroup()));
        List<Volume> cgSrcVolumes = dbClient.queryObject(Volume.class, srcVolumeUris);
        Collection<String> srcDevIds = transform(cgSrcVolumes, fctnBlockObjectToNativeID());

        List<URI> tgtVolumeUris =   dbClient.queryByConstraint(getVolumesByConsistencyGroup(target.getConsistencyGroup()));
        List<Volume> cgTgtVolumes = dbClient.queryObject(Volume.class, tgtVolumeUris);
        Collection<String> tgtDevIds = transform(cgTgtVolumes, fctnBlockObjectToNativeID());

        // Get the storagesync instances for remote sync/async mirrors
        List<CIMObjectPath> repPaths = helper.getReplicationRelationships(sourceSystem,
                REMOTE_LOCALITY_VALUE, MIRROR_VALUE, Mode.valueOf(target.getSrdfCopyMode()).getMode(),
                STORAGE_SYNCHRONIZED_VALUE);

        log.info("Found {} relationships", repPaths.size());
        log.info("Looking for System elements on {} with IDs {}", sourceSystem.getNativeGuid(),
                Joiner.on(',').join(srcDevIds));
        log.info("Looking for Synced elements on {} with IDs {}", targetSystem.getNativeGuid(),
                Joiner.on(',').join(tgtDevIds));
        return filter(repPaths, and(
                cgSyncPairsPredicate(sourceSystem.getNativeGuid(), srcDevIds, CP_SYSTEM_ELEMENT),
                cgSyncPairsPredicate(targetSystem.getNativeGuid(), tgtDevIds, CP_SYNCED_ELEMENT)));
    }

    private Predicate<CIMObjectPath> cgSyncPairsPredicate(final String systemNativeGuid, final Collection<String> nativeIds,
                                                          final String propertyName) {
        return new Predicate<CIMObjectPath>() {
            @Override
            public boolean apply(CIMObjectPath path) {
                String el = path.getKeyValue(propertyName).toString();
                CIMObjectPath elPath = new CIMObjectPath(el);
                String elDevId   = elPath.getKeyValue(CP_DEVICE_ID).toString();
                String elSysName = elPath.getKeyValue(CP_SYSTEM_NAME).toString().
                        replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);

                return elSysName.equalsIgnoreCase(systemNativeGuid) && nativeIds.contains(elDevId);
            }
        };
    }

    private void checkTargetHasParentOrFail(Volume target) {
        if (NullColumnValueGetter.isNullNamedURI(target.getSrdfParent())) {
            throw DeviceControllerException.exceptions.failbackVolumeOperationFailed(
                    target.getNativeGuid() + " doesn't have any parent", null);
        }
    }

    private Volume getSourceVolume(Volume target) {
        NamedURI srdfParent = target.getSrdfParent();
        return dbClient.queryObject(Volume.class, srdfParent.getURI());
    }

    private boolean isAsyncWithoutCG(Volume target) {
        return Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(target.getSrdfCopyMode()) &&
                !target.hasConsistencyGroup();
    }

    /**
     * Returns the appropriate factory based on the Provider version.
     *
     * @param system    Local or remote system
     * @return          Concrete factory of AbstractSRDFOperationContextFactory
     */
    private AbstractSRDFOperationContextFactory getContextFactory(StorageSystem system) {
        AbstractSRDFOperationContextFactory factory = null;

        if (system.getUsingSmis80() != null && system.getUsingSmis80()) {
            factory = new SRDFOperationContextFactory80();
        } else {
            factory = new SRDFOperationContextFactory40();
        }

        factory.setDbClient(dbClient);
        factory.setHelper(helper);
        factory.setUtils(utils);

        return factory;
    }

    private Predicate<? super Volume> volumePersonalityPredicate(final PersonalityTypes personality) {
        return new Predicate<Volume>() {
            @Override
            public boolean apply(Volume input) {
                return personality.toString().equalsIgnoreCase(input.getPersonality());
            }
        };
    }
}

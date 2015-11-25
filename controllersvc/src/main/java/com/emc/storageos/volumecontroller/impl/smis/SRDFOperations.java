/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.model.Volume.PersonalityTypes.SOURCE;
import static com.emc.storageos.db.client.model.Volume.PersonalityTypes.TARGET;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeGuid;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefresh;
import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Arrays.asList;

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

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.LinkStatus;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFChangeCopyModeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkFailOverCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SRDFLinkStartCompleter;
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
import com.google.common.collect.Sets;

public class SRDFOperations implements SmisConstants {
    private static final Logger log = LoggerFactory.getLogger(SRDFOperations.class);
    private static final String FAILURE_MSG_FMT = "Failed to %s SRDF mirror for source:%s, target:%s";
    private static final String REPLICATION_GROUP_NAMES_NOT_FOUND = "Replication Group Names not found in RA Group %s";
    private static final String STORAGE_SYNCHRONIZATION_NOT_FOUND = "Storage Synchronization instance not found";
    private static final String REPLICATION_NOT_IN_RIGHT_STATE = "Storage replication not in expected state for failover-cancel";
    private static final String REPLICATION_GROUP_NOT_FOUND_ON_BOTH_PROVIDERS = "Replication group not found on both R1 and R2 providers";

    private static final int RESUME_AFTER_SWAP_MAX_ATTEMPTS = 15;
    private static final int RESUME_AFTER_SWAP_SLEEP = 30000; // 30 seconds
    private static final String RESUME_AFTER_SWAP_EXCEPTION_MSG = "Failed to resume link after swap, attempt %d/%d...";
    private static final int SLEEP_TIME = 30000; // 30 seconds

    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;
    private SRDFUtils utils;
    private FindProviderFactory findProviderFactory;

    public enum Mode {
        SYNCHRONOUS(2), ASYNCHRONOUS(3), ADAPTIVECOPY(32768);

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

    public void setFindProviderFactory(final FindProviderFactory findProviderFactory) {
        this.findProviderFactory = findProviderFactory;
    }

    public void createSRDFMirror(final StorageSystem systemWithCg, final List<Volume> srcVolumes,
            final List<Volume> targetVolumes, final boolean storSyncAvailable, final TaskCompleter completer) {
        log.info("START createSRDFMirror");
        CIMObjectPath srcCGPath = null;
        CIMObjectPath tgtCGPath = null;

        try {
            Volume firstSource = srcVolumes.iterator().next();
            Volume firstTarget = targetVolumes.iterator().next();
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, firstTarget.getSrdfGroup());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, firstTarget.getStorageController());
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, firstSource.getStorageController());
            int modeValue = Mode.valueOf(firstTarget.getSrdfCopyMode()).getMode();
            CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(systemWithCg);

            srcCGPath = createDeviceGroup(sourceSystem, systemWithCg, srcVolumes, dbClient);
            String sourceGroupName = (String) srcCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Source Volumes placed into replication group: {}", srcCGPath);

            // Note: We switch to the appropriate targetSystem but use sourceSystem for the provider call
            tgtCGPath = createDeviceGroup(targetSystem, systemWithCg, targetVolumes, dbClient);
            String targetGroupName = (String) tgtCGPath.getKey(CP_INSTANCE_ID).getValue();
            log.info("Target Volumes placed into replication group: {}", tgtCGPath);

            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(systemWithCg, modeValue);
            CIMArgument[] inArgs = null;
            CIMArgument[] outArgs = new CIMArgument[5];
            if (completer instanceof SRDFLinkStartCompleter) {
                ((SRDFLinkStartCompleter) completer).setCGName(sourceGroupName, targetGroupName,
                        firstSource.getConsistencyGroup());
            }
            if (storSyncAvailable) {
                log.info("Creating Group synchronization between source volume group and target volume group");
                // there are storage synchronizations available for these pairs
                Collection<CIMObjectPath> elementSynchronizations = utils
                        .getSynchronizations(systemWithCg, firstSource, firstTarget);
                inArgs = helper.getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(srcCGPath,
                        tgtCGPath, elementSynchronizations);
                helper.invokeMethod(systemWithCg, srcRepSvcPath,
                        SmisConstants.CREATE_GROUP_REPLICA_FROM_ELEMENT_SYNCHRONIZATIONS, inArgs, outArgs);
                // No Job returned
                completer.ready(dbClient);
            } else {
                CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(systemWithCg,
                        group);
                inArgs = helper.getCreateGroupReplicaForSRDFInputArguments(srcCGPath,
                        tgtCGPath, repCollectionPath, modeValue, replicationSettingDataInstance);
                helper.invokeMethodSynchronously(systemWithCg, srcRepSvcPath,
                        SmisConstants.CREATE_GROUP_REPLICA, inArgs, outArgs,
                        new SmisSRDFCreateMirrorJob(null, systemWithCg.getId(), completer));
            }

        } catch (WBEMException wbeme) {
            log.error("SMI-S error creating mirror group synchronization", wbeme);
            // check whether synchronization really succeeds in Array
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, systemWithCg)) {
                completer.ready(dbClient);
            } else {
                ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
            }
        } catch (Exception e) {
            log.error("Error creating mirror group synchronization", e);
            if (verifyGroupSynchronizationCreatedinArray(srcCGPath, tgtCGPath, systemWithCg)) {
                completer.ready(dbClient);
            } else {
                if (e.getMessage().contains("Replication Control Succeeded")) {
                    log.info(
                            "Replication Succeeded but save to DB failed exception leaves the SRDF relationship to get established properly after some time. Hence for now succeeding this operation.",
                            e);
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
                if (null == outArg) {
                    continue;
                }
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

    public void rollbackSRDFMirrors(final StorageSystem system,
            final List<URI> sourceURIs, final List<URI> targetURIs,
            final boolean isGrouprollback, final TaskCompleter completer) {
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
                    rollbackSRDFMirror(system, source, target, isGrouprollback);
                }
            }
        } finally {
            if (null != completer) {
                completer.ready(dbClient);
            }
        }
    }

    private void rollbackSRDFMirror(StorageSystem system, Volume source,
            Volume target, boolean isGrouprollback) {
        log.info("START Rolling back SRDF mirror");
        try {
            performDetach(system, target, isGrouprollback, new TaskCompleter() {
                @Override
                protected void complete(DbClient dbClient,
                        Operation.Status status, ServiceCoded coded)
                        throws DeviceControllerException {
                    // ignore
                }
            });

            if (target.hasConsistencyGroup()) {
                log.info("Removing Volume from device Group on roll back");
                removeDeviceGroups(system, source.getId(), target.getId(), null);
            }
        } catch (Exception e) {
            String msg = format(FAILURE_MSG_FMT, "rollback", source.getId(),
                    target.getId());
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

            BlockConsistencyGroup targetCG = dbClient.queryObject(
                    BlockConsistencyGroup.class, target.getConsistencyGroup());
            BlockConsistencyGroup sourceCG = dbClient.queryObject(
                    BlockConsistencyGroup.class, source.getConsistencyGroup());

            boolean cgSourceCleanUpRequired = removeFromDeviceGroups(system, system, source, sourceCG);
            boolean cgTargetCleanUpRequired = removeFromDeviceGroups(targetSystem, system, target, targetCG);

            // after volumes are deleted .group gets removed
            if (cgSourceCleanUpRequired || cgTargetCleanUpRequired) {
                if (null != targetCG) {
                    log.info("Set target {}-->{} as inactive", targetCG.getLabel(), targetCG.getId());
                    targetCG.setInactive(true);
                    dbClient.persistObject(targetCG);
                }

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

                if (targetSystem.getTargetCgs() != null && !targetSystem.getTargetCgs().isEmpty()) {
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
     * @param forceAdd
     * @param completer
     */
    public void addVolumePairsToCg(StorageSystem system, List<URI> sourceURIs, URI remoteDirectorGroupURI, boolean forceAdd,
            TaskCompleter completer) {

        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, remoteDirectorGroupURI);
        List<CIMObjectPath> syncPairs = newArrayList();
        List<Volume> sources = dbClient.queryObject(Volume.class, sourceURIs);
        List<Volume> targets = new ArrayList<>();

        try {
            // Build list of sources and targets
            for (Volume source : sources) {
                for (String targetStr : source.getSrdfTargets()) {
                    URI targetURI = URI.create(targetStr);
                    Volume target = dbClient.queryObject(Volume.class, targetURI);
                    targets.add(target);
                }
            }

            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sources.get(0).getStorageController());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targets.get(0).getStorageController());
            // Transform to list of their respective device ID's
            Collection<String> srcDevIds = transform(filter(sources, hasNativeID()), fctnBlockObjectToNativeID());
            Collection<String> tgtDevIds = transform(filter(targets, hasNativeID()), fctnBlockObjectToNativeID());

            int attempts = 0;
            final int MAX_ATTEMPTS = 12;
            final int DELAY_TIME_IN_MS = 5000;
            do {
                log.info("Attempt {}/{}...", attempts + 1, MAX_ATTEMPTS);
                // Get all remote mirror relationships from provider
                List<CIMObjectPath> repPaths = helper.getReplicationRelationships(system,
                        REMOTE_LOCALITY_VALUE, MIRROR_VALUE,
                        Mode.valueOf(targets.get(0).getSrdfCopyMode()).getMode(),
                        STORAGE_SYNCHRONIZED_VALUE);

                log.info("Found {} relationships", repPaths.size());
                log.info("Looking for System elements on {} with IDs {}", sourceSystem.getNativeGuid(),
                        Joiner.on(',').join(srcDevIds));
                log.info("Looking for Synced elements on {} with IDs {}", targetSystem.getNativeGuid(),
                        Joiner.on(',').join(tgtDevIds));

                // Filter the relationships on known source ID's that must match with some known target ID.
                Collection<CIMObjectPath> syncPaths = filter(repPaths, and(
                        cgSyncPairsPredicate(sourceSystem.getNativeGuid(), srcDevIds, CP_SYSTEM_ELEMENT),
                        cgSyncPairsPredicate(targetSystem.getNativeGuid(), tgtDevIds, CP_SYNCED_ELEMENT)));

                log.info("Need {} paths / Found {} paths", syncPaths.size(), sources.size());

                // We're done if the filtered list contains <sources-size> relationships.
                if (syncPaths.size() == sources.size()) {
                    // Add these pairs to the result list
                    syncPairs.addAll(syncPaths);
                } else {
                    try {
                        Thread.sleep(DELAY_TIME_IN_MS);
                    } catch (InterruptedException ie) {
                        log.warn("Error:", ie);
                    }
                }
            } while (syncPairs.isEmpty() && (attempts++) < MAX_ATTEMPTS);

            if (syncPairs.isEmpty()) {
                throw new IllegalStateException("Failed to find synchronization paths");
            }

            // Update targets with the existing target SRDF CG
            findOrCreateTargetBlockConsistencyGroup(targets);

            CIMObjectPath groupSynchronized = getGroupSyncObject(system, sources.get(0),
                    group.getSourceReplicationGroupName(), group.getTargetReplicationGroupName());

            if (groupSynchronized == null || syncPairs.isEmpty()) {
                log.warn("Expected Group Synchronized not found");
                log.error("Expected Group Synchronized not found for volumes {}", sources.get(0).getNativeId());
                ServiceError error = SmisException.errors
                        .jobFailed("Expected Group Synchronized not found");
                WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
                completer.error(dbClient, error);
                return;
            }

            Mode mode = Mode.valueOf(targets.get(0).getSrdfCopyMode());
            CIMInstance settingInstance = getReplicationSettingDataInstance(system, mode.getMode());

            @SuppressWarnings("rawtypes")
            CIMArgument[] inArgs = helper.getAddSyncPairInputArguments(groupSynchronized, forceAdd, settingInstance,
                    syncPairs.toArray(new CIMObjectPath[syncPairs.size()]));

            if (forceAdd) {
                log.info("There are replicas available for R1/R2, hence adding new volume pair(s) to CG with Force flag");
            }

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
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                    source.getStorageController());
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                    target.getSrdfGroup());
            CIMObjectPath syncPair = null;
            CIMObjectPath groupSynchronized = null;
            StorageSystem activeProviderSystem = findProviderWithGroup(target);

            syncPair = utils.getStorageSynchronizedObject(sourceSystem, source, target, activeProviderSystem);

            groupSynchronized = getGroupSyncObjectForPairRemoval(sourceSystem, activeProviderSystem, source,
                    group.getSourceReplicationGroupName(), group.getTargetReplicationGroupName());

            if (groupSynchronized != null && null != syncPair) {
                CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(activeProviderSystem,
                        Mode.valueOf(target.getSrdfCopyMode()).getMode());
                @SuppressWarnings("rawtypes")
                CIMArgument[] inArgs = helper.getRemoveSyncPairInputArguments(groupSynchronized,
                        syncPair, replicationSettingDataInstance);

                helper.callModifyReplica(activeProviderSystem, inArgs);

                if (group.getVolumes() != null) {
                    group.getVolumes().remove(source.getNativeGuid());
                    group.getVolumes().remove(target.getNativeGuid());
                }
                dbClient.persistObject(group);

            } else {
                log.warn("Expected Group Synchronized not found for volume {}, probably removed already.", sourceURI);
                // proceed with next step even if it fails.
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

    public void createListReplicas(StorageSystem system, List<URI> sources, List<URI> targets, TaskCompleter completer) {
        try {
            List<Volume> sourceVolumes = dbClient.queryObject(Volume.class, sources);
            List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targets);

            Volume firstTarget = targetVolumes.get(0);
            RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class, firstTarget.getSrdfGroup());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, firstTarget.getStorageController());
            int modeValue = Mode.valueOf(firstTarget.getSrdfCopyMode()).getMode();
            CIMObjectPath srcRepSvcPath = cimPath.getControllerReplicationSvcPath(system);
            CIMObjectPath repCollectionPath = cimPath.getRemoteReplicationCollection(system, group);
            CIMInstance replicationSettingDataInstance = getReplicationSettingDataInstance(system, modeValue);

            List<CIMObjectPath> sourcePaths = new ArrayList<>();
            List<CIMObjectPath> targetPaths = new ArrayList<>();
            for (Volume sourceVolume : sourceVolumes) {
                sourcePaths.add(cimPath.getVolumePath(system, sourceVolume.getNativeId()));
            }
            for (Volume targetVolume : targetVolumes) {
                targetPaths.add(cimPath.getVolumePath(targetSystem, targetVolume.getNativeId()));
            }

            CIMArgument[] inArgs = helper.getCreateListReplicaInputArguments(system,
                    sourcePaths.toArray(new CIMObjectPath[] {}),
                    targetPaths.toArray(new CIMObjectPath[] {}),
                    modeValue, repCollectionPath, replicationSettingDataInstance);
            CIMArgument[] outArgs = new CIMArgument[5];

            helper.invokeMethodSynchronously(system, srcRepSvcPath,
                    SmisConstants.CREATE_LIST_REPLICA, inArgs, outArgs,
                    new SmisSRDFCreateMirrorJob(null, system.getId(), completer));
        } catch (WBEMException wbeme) {
            log.error("SMI-S error creating mirrors for {}", Joiner.on(',').join(sources), wbeme);
            ServiceError error = SmisException.errors.jobFailed(wbeme.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);

        } catch (Exception e) {
            log.error("Error creating mirrors for {}", Joiner.on(',').join(sources), e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            WorkflowStepCompleter.stepFailed(completer.getOpId(), error);
            completer.error(dbClient, error);
        }
    }

    public void refreshStorageSystem(final URI storageSystemURI, List<URI> volumeURIs) {
        StorageSystem system = null;
        try {
            system = utils.getStorageSystem(storageSystemURI);
            long waitTime = 60000; // 60 sec
            if (null != volumeURIs && !volumeURIs.isEmpty()) {
                List<Volume> volumes = dbClient.queryObject(Volume.class, volumeURIs);
                if (null == volumes || volumes.isEmpty()) {
                    return;
                }
                Collection<String> nativeGuids = transform(volumes, fctnBlockObjectToNativeGuid());
                NamedURI sourceVolumeURI = volumes.get(0).getSrdfParent();
                if (NullColumnValueGetter.isNullURI(sourceVolumeURI.getURI())) {
                    return;
                }
                Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeURI);
                BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class,
                        sourceVolume.getConsistencyGroup());

                String cgName = cgObj.getAlternateLabel();
                if (null == cgName) {
                    cgName = cgObj.getLabel();
                }

                while (waitTime > 0) {
                    log.debug("Entering loop to check volume exists on replication group.");
                    CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgName, system, system);
                    if (null == groupPath) {
                        log.info("No group found with name {}", cgName);
                        break;
                    }
                    Set<String> commonElements = new HashSet<String>();
                    Set<String> deviceNativeGuids = getVolumesPartOfRG(groupPath, system, system);
                    log.info("Found volumes {} in RG {}", deviceNativeGuids, cgName);
                    if (null == deviceNativeGuids) {
                        log.info("No volumes found in the RG");
                        break;
                    }
                    Sets.intersection(new HashSet<String>(nativeGuids), deviceNativeGuids).copyInto(commonElements);
                    if (!commonElements.isEmpty()) {
                        log.info("Volumes {} still exists in RG {}.", Arrays.toString(commonElements.toArray()), cgName);
                        Thread.sleep(SLEEP_TIME);
                        waitTime = waitTime - SLEEP_TIME;
                    } else {
                        log.debug("Volumes not exist in RG {}", cgName);
                        break;
                    }
                }
            }
            callEMCRefresh(helper, system);
        } catch (Exception ex) {
            log.error("SMI-S error while refreshing target system {}", storageSystemURI, ex);
        }
    }

    private CIMInstance getInstance(final CIMObjectPath path, final StorageSystem sourceSystem) {
        try {
            return helper.checkExists(sourceSystem, path, false, false);
        } catch (Exception e) {
            log.error("Exception in getInstance", e);
        }
        return null;
    }

    public void performFailover(StorageSystem system, Volume target, TaskCompleter completer) {
        log.info("START performFailover");
        checkTargetHasParentOrFail(target);
        ServiceError error = null;

        try {
            Volume sourceVolume = getSourceVolume(target);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());

            // for 4.6.x CG, only failback and swap are at group level. Failover has to be called at ModifyListSync.
            StorageSystem activeSystem = findProviderWithGroup(target);
            AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(activeSystem);
            SRDFOperationContext ctx = null;

            if (!system.getUsingSmis80() && !isFailedOver(activeSystem, sourceVolume, target)) {
                log.info("Failing over link");
                ctx = ctxFactory.build(SRDFOperation.FAIL_OVER, target);
                ctx.perform();
            } else {
                invokeFailOverStrategy(sourceSystem, target);
            }

            if (completer instanceof SRDFLinkFailOverCompleter) {
                // Re-check the fail over status.
                LinkStatus status = null;
                if (isFailedOver(activeSystem, sourceVolume, target)) {
                    status = LinkStatus.FAILED_OVER;
                } else {
                    status = sourceVolume.hasConsistencyGroup() ? LinkStatus.CONSISTENT : LinkStatus.IN_SYNC;
                }
                ((SRDFLinkFailOverCompleter) completer).setLinkStatus(status);
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

    public void failoverCancelSyncPair(final StorageSystem targetSystem,
            final Volume target, final TaskCompleter completer) {
        CIMObjectPath syncPath = null;
        NamedURI sourceVolumeNamedUri = target.getSrdfParent();
        if (NullColumnValueGetter.isNullNamedURI(sourceVolumeNamedUri)) {
            throw DeviceControllerException.exceptions
                    .failbackVolumeOperationFailed(target.getNativeGuid()
                            + " doesn't have any parent", null);
        }
        URI sourceVolUri = sourceVolumeNamedUri.getURI();
        Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolUri);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());

        StorageSystem activeSystem = findProviderFactory.withGroup(target).find();
        if (activeSystem == null) {
            log.error(REPLICATION_GROUP_NOT_FOUND_ON_BOTH_PROVIDERS);
            ServiceError error = SmisException.errors.jobFailed(REPLICATION_GROUP_NOT_FOUND_ON_BOTH_PROVIDERS);
            completer.error(dbClient, error);
            return;
        }

        syncPath = cimPath.getStorageSynchronized(sourceSystem, sourceVolume, targetSystem,
                target);
        CIMInstance syncInstance = getInstance(syncPath, activeSystem);

        if (null == syncInstance) {
            log.error(
                    "Failed to fail over source volume {}, as expected storage synchronized association  not found ",
                    target.getSrdfParent().getURI());
            ServiceError error = SmisException.errors.jobFailed(format(
                    STORAGE_SYNCHRONIZATION_NOT_FOUND, target.getSrdfGroup()));
            completer.error(dbClient, error);
            return;
        }
        if (null != syncInstance && isFailedOver(syncInstance)) {
            log.info(
                    "Source Volume {} already in failed over State, invoking failback",
                    sourceVolUri);
            failBackSyncPair(targetSystem, target, activeSystem, completer);
            return;
        }
        ServiceError error = SmisException.errors.jobFailed(format(
                REPLICATION_NOT_IN_RIGHT_STATE, target.getSrdfGroup()));
        completer.error(dbClient, error);
    }

    private void changeSRDFVolumeBehaviors(Volume sourceVolume, Volume targetVolume, DbClient dbClient, String status) {
        List<Volume> volumes = new ArrayList<>();
        if (sourceVolume.hasConsistencyGroup()) {
            List<URI> srcVolumeUris = dbClient.queryByConstraint(
                    getVolumesByConsistencyGroup(sourceVolume.getConsistencyGroup()));
            List<Volume> cgSrcVolumes = dbClient.queryObject(Volume.class, srcVolumeUris);
            volumes.addAll(cgSrcVolumes);
        } else {
            volumes.add(sourceVolume);
            /**
             * Swap operation will happen for all volumes under ra group for Async without CG.
             * Adding the missing source volumes to change the personalities of the missing volumes
             */
            if (Mode.ASYNCHRONOUS.name().equalsIgnoreCase(targetVolume.getSrdfCopyMode())) {
                volumes.addAll(utils.getRemainingSourceVolumesForAsyncRAGroup(sourceVolume, targetVolume));
            }
        }
        log.debug("volumes size:{}", volumes.size());

        for (Volume sourceVol : volumes) {
            StringSet srdfTargets = new StringSet();
            String copyMode = null;
            if (sourceVol.getSrdfTargets() == null) {
                // skip the independent volume which is still associated with CG
                // This may happen if a target volume is not deleted properly
                continue;
            }
            srdfTargets.addAll(sourceVol.getSrdfTargets());
            // CG cannot have different RA Groups and copyMode
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
        // TODO Should we swap anything else here? Source/Remote system?

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
        if (cgUri == null) {
            findOrCreateTargetBlockConsistencyGroup(volumes);
            cgUri = volumes.get(0).getConsistencyGroup();
        }
        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);

        String cgName = cgObj.getAlternateLabel();
        if (null == cgName) {
            cgName = cgObj.getLabel();
        }
        Collection<String> nativeIds = transform(volumes, fctnBlockObjectToNativeID());
        CIMObjectPath repSvcPath = cimPath.getControllerReplicationSvcPath(system);
        CIMArgument[] cgOutArgs = new CIMArgument[5];

        CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgName, forProvider, system);
        if (null != groupPath) {
            /**
             * Stale RG exists with same name.
             * If empty group is found, add these volumes and return the group.
             * else
             * check if requested volumes are already part of this RG
             * (for expand volume where we try to re-add volumes)
             * else throw exception, because it has other volumes.
             */
            Set<String> deviceNativeGuids = getVolumesPartOfRG(groupPath, forProvider, system);
            if (deviceNativeGuids.isEmpty()) {
                log.info("Found empty group with same name, adding Volumes to it.");
                CIMArgument[] inArgs = helper.getAddMembersInputArguments(groupPath,
                        cimPath.getVolumePaths(system, nativeIds.toArray(new String[nativeIds.size()])));
                helper.invokeMethod(forProvider, repSvcPath, ADD_MEMBERS, inArgs, cgOutArgs);
            } else {
                Collection<String> nativeGuids = transform(volumes, fctnBlockObjectToNativeGuid());
                if (deviceNativeGuids.containsAll(nativeGuids)) {
                    log.info("Requested volumes {} are already part of the group {}",
                            Joiner.on(", ").join(nativeGuids), cgName);
                } else {
                    throw DeviceControllerException.exceptions.srdfConsistencyGroupAlreadyExistsWithVolume(cgName);
                }
            }
        } else {
            CIMArgument[] cgInArgs = helper.getCreateReplicationGroupCreateInputArguments(system, cgName,
                    cimPath.getVolumePaths(system, nativeIds.toArray(new String[nativeIds.size()])));
            helper.invokeMethod(forProvider, repSvcPath, CREATE_GROUP, cgInArgs, cgOutArgs);
            groupPath = cimPath.getCimObjectPathFromOutputArgs(cgOutArgs, CP_REPLICATION_GROUP);
        }

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

    private List<Volume> getVolumesPartOfReplicationGroup(final CIMObjectPath replicationGroupPath,
            final StorageSystem forProvider, final StorageSystem system) {
        CloseableIterator<CIMObjectPath> volumePaths = null;
        List<Volume> volumes = new ArrayList<Volume>();
        try {
            volumePaths = helper.getAssociatorNames(forProvider, replicationGroupPath, null, STORAGE_VOLUME_CLASS, null,
                    null);
            while (volumePaths.hasNext()) {
                String nativeGuid = helper.getVolumeNativeGuid(volumePaths.next());
                Volume volume = SmisUtils.checkStorageVolumeExistsInDB(nativeGuid, dbClient);
                if (null != volume) {
                    volumes.add(volume);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get Volumes from Device Group ", e);
        } finally {
            if (null != volumePaths) {
                volumePaths.close();
            }
        }
        return volumes;
    }

    @SuppressWarnings("rawtypes")
    private boolean removeFromDeviceGroups(final StorageSystem system,
            final StorageSystem forProvider, final Volume volume, final BlockConsistencyGroup cg) {
        log.info("removeFromDeviceGroups:");
        log.info("Volume: {} / {}", volume.getDeviceLabel(), volume.getNativeId());
        log.info("Array: {}", system.getSerialNumber());
        log.info("Provider: {}", forProvider.getSmisProviderIP());
        boolean removedFromAllGroups = true;
        try {
            String cgLabel = (cg.getAlternateLabel() != null) ? cg.getAlternateLabel() : cg.getLabel();
            log.info("Volume nativeId: {}, CG name: {}", volume.getNativeId(), cgLabel);
            CIMObjectPath deviceGroupPath = getDeviceGroup(system, forProvider, volume, cgLabel);
            CIMObjectPath volumePath = cimPath.getBlockObjectPath(system, volume);
            CIMObjectPath repSvcPath = cimPath.getControllerReplicationSvcPath(system);

            if (deviceGroupPath != null) {
                log.info(format("Found Volume %s to be a member of group %s", volume.getNativeId(), deviceGroupPath));
                CIMArgument[] inArgs = helper.getRemoveMembersInputArguments(deviceGroupPath,
                        new CIMObjectPath[] { volumePath });
                CIMArgument[] outArgs = new CIMArgument[5];
                helper.invokeMethod(forProvider, repSvcPath, REMOVE_MEMBERS, inArgs, outArgs);

                if (!getVolumesPartOfReplicationGroup(deviceGroupPath, forProvider, system).isEmpty()) {
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
            } else {
                // volume is not found to be part of SRDF RG. return false indicating this method didn't process anything.
                removedFromAllGroups = false;
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
            log.debug("Failed to get Volumes from Device Group ", e);
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
            StorageSystem activeProviderSystem, final TaskCompleter completer) {
        checkTargetHasParentOrFail(targetVolume);

        Volume sourceVolume = getSourceVolume(targetVolume);
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
        Collection<CIMObjectPath> syncPaths = new ArrayList<>();
        try {
            StorageSystem activeSystem = findProviderWithGroup(targetVolume);

            SRDFOperationContext failBackCtx = getContextFactory(activeSystem).build(SRDFOperation.FAIL_BACK, targetVolume);
            failBackCtx.perform();

            // this hack is needed, as currently triggering fail over twice invokes failback
            if (completer instanceof SRDFLinkFailOverCompleter) {
                LinkStatus status = sourceVolume.hasConsistencyGroup() ? LinkStatus.CONSISTENT : LinkStatus.IN_SYNC;
                ((SRDFLinkFailOverCompleter) completer).setLinkStatus(status);
            }
            completer.ready(dbClient);
        } catch (Exception e) {
            log.error("Failed to fail back source volume {}",
                    targetVolume.getSrdfParent().getURI(), e);
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            completer.error(dbClient, error);
        }
    }

    public void performSwap(StorageSystem targetSystem, Volume target, TaskCompleter completer) {
        log.info("START performSwap");
        checkTargetHasParentOrFail(target);

        ServiceError error = null;
        try {
            Volume sourceVolume = getSourceVolume(target);
            StorageSystem activeSystem = findProviderWithGroup(target);
            Collection<CIMObjectPath> syncPaths = utils.getSynchronizations(
                    activeSystem, sourceVolume, target, false);
            CIMInstance firstSync = getInstance(syncPaths.iterator().next(), activeSystem);

            AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(activeSystem);
            SRDFOperationContext ctx = null;

            if (!isFailedOver(firstSync)) {
                log.info("Failing over link");
                ctx = ctxFactory.build(SRDFOperation.FAIL_OVER, target);
                ctx.perform();
            }

            ctx = ctxFactory.build(SRDFOperation.SWAP, target);
            ctx.perform();
            log.info("Swapping Volume Pair {} succeeded ", sourceVolume.getId());

            log.info("Changing R1 and R2 characteristics after swap");
            changeSRDFVolumeBehaviors(sourceVolume, target, dbClient, LinkStatus.SWAPPED.toString());
            log.info("Updating RemoteDirectorGroup after swap");
            changeRemoteDirectorGroup(target.getSrdfGroup());

            StorageSystem sourceSystemAfterSwap = dbClient.queryObject(StorageSystem.class, target.getStorageController());
            // we run all SRDF operations using RDF group's source provider.
            // target provider needs to be refreshed to perform any snap/clone operations following swap.
            callEMCRefresh(helper, sourceSystemAfterSwap);

            // Refresh our view of the target, since it is now the source volume.
            target = dbClient.queryObject(Volume.class, sourceVolume.getId());

            boolean success = false;
            int attempts = 1;
            while (!success && attempts <= RESUME_AFTER_SWAP_MAX_ATTEMPTS) {
                try {
                    // Use new context to perform resume operation.
                    AbstractSRDFOperationContextFactory establishFactory = getContextFactory(activeSystem);
                    ctx = establishFactory.build(SRDFOperation.ESTABLISH, target);
                    ctx.appendFilters(new ErrorOnEmptyFilter());
                    ctx.perform();

                    success = true;
                } catch (WBEMException | NoSynchronizationsFoundException e) {
                    log.warn(format(RESUME_AFTER_SWAP_EXCEPTION_MSG, attempts, RESUME_AFTER_SWAP_MAX_ATTEMPTS), e);
                    attempts++;
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
            log.warn("No remote group association found for {}. It may have already been removed.", target.getId());
        } catch (Exception e) {
            log.error("Failed to swap srdf link {}", target.getSrdfParent().getURI(), e);
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
            // refresh RDF group source provider, it is required after R2 snap/clone restore performed on target provider
            RemoteDirectorGroup rdfGroup = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, rdfGroup.getSourceStorageSystemUri());
            callEMCRefresh(helper, sourceSystem);

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
            boolean isLinkEstablished = false;
            NamedURI sourceVolumeNamedUri = targetVolume.getSrdfParent();
            if (NullColumnValueGetter.isNullNamedURI(sourceVolumeNamedUri)) {
                throw DeviceControllerException.exceptions.resumeVolumeOperationFailed(
                        targetVolume.getNativeGuid() + " doesn't have any parent", null);
            }
            URI sourceVolUri = sourceVolumeNamedUri.getURI();
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolUri);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class,
                    sourceVolume.getStorageController());
            StorageSystem systemWithCg = findProviderWithGroup(targetVolume);
            if (!sourceVolume.hasConsistencyGroup()) {
                // construct Storage synchronized Object
                CIMObjectPath storSynchronizedPath = cimPath.getStorageSynchronized(sourceSystem, sourceVolume,
                        targetSystem, targetVolume);
                if (null == storSynchronizedPath) {
                    log.error(
                            "Failed to start SRDF link for source volume {}, as expected storage synchronized association not found",
                            sourceVolUri);
                    ServiceError error = SmisException.errors.jobFailed(format(
                            STORAGE_SYNCHRONIZATION_NOT_FOUND, targetVolume.getSrdfParent()
                                    .getURI()));
                    completer.error(dbClient, error);
                    return;
                }
                log.info("Establishing SRDF Link");

                CIMInstance syncInstance = getInstance(storSynchronizedPath, systemWithCg);
                if (null == syncInstance) {
                    log.info("No valid synchronization found, hence re-establishing link");
                    createSRDFVolumePair(systemWithCg, sourceVolUri, targetVolume.getId(), completer);
                } else {
                    isLinkEstablished = true;
                    log.info("Link already established..");
                }
            } else {
                CIMObjectPath groupSynchronizedPath = utils.getGroupSynchronized(targetVolume, systemWithCg);
                // groupSyncPath will be null as replication group names will be cleared from RA group during stop()
                if (null == groupSynchronizedPath) {
                    log.info(
                            "Expected replication Group Names are not found in RA Group for source volume {}",
                            sourceVolUri);
                }
                log.info("Establishing SRDF Link");

                CIMInstance syncInstance = getInstance(groupSynchronizedPath, systemWithCg);
                if (null == syncInstance) {
                    log.info("No valid synchronization found, hence re-establishing link");
                    // get source volumes part of this CG
                    List<Volume> srcVolumes = ControllerUtils.
                            getVolumesPartOfCG(sourceVolume.getConsistencyGroup(), dbClient);
                    // get target volumes part of this CG
                    List<Volume> targetVolumes = ControllerUtils.
                            getVolumesPartOfCG(targetVolume.getConsistencyGroup(), dbClient);

                    // if there is a storage sync available for the given pair, then
                    // we need to call new method CreateGroupReplicaFromElementSynchronizations
                    CIMObjectPath storSynchronizedPath = cimPath.getStorageSynchronized(sourceSystem, sourceVolume,
                            targetSystem, targetVolume);
                    boolean storSyncAvailable = false;
                    if (getInstance(storSynchronizedPath, systemWithCg) != null) {
                        storSyncAvailable = true;
                    }
                    createSRDFMirror(systemWithCg, srcVolumes, targetVolumes, storSyncAvailable, completer);
                } else {
                    isLinkEstablished = true;
                    log.info("Link already established..");
                }
            }
            if (isLinkEstablished) {
                completer.ready(dbClient);
            }
        } catch (Exception e) {
            log.error("Failed to start SRDF link {}", targetVolume.getSrdfParent().getURI(), e);
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
                StorageSystem provider = findProviderWithGroup(target);
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

    private boolean isFailedOver(StorageSystem system, Volume source, Volume target) throws WBEMException {
        Collection<CIMObjectPath> syncPaths = utils.getSynchronizations(system, source, target, false);
        CIMInstance firstSync = getInstance(syncPaths.iterator().next(), system);
        return isFailedOver(firstSync);
    }

    private boolean isFailedOver(final CIMInstance syncInstance) {
        String copyState = syncInstance.getPropertyValue(CP_COPY_STATE).toString();
        return String.valueOf(FAILOVER_SYNC_PAIR).equalsIgnoreCase(copyState);
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
            final StorageSystem forProvider, final Volume volume, final String grpName) {
        CloseableIterator<CIMObjectPath> names = null;
        try {
            CIMObjectPath path = cimPath.getBlockObjectPath(system, volume);
            names = helper.getAssociatorNames(forProvider, path, null, SE_REPLICATION_GROUP, null,
                    null);
            while (names.hasNext()) {
                CIMObjectPath replicationGroupPath = names.next();
                if (replicationGroupPath.toString().contains(grpName)) {
                    return replicationGroupPath;
                }
            }
        } catch (WBEMException e) {
            log.warn("Failed to acquire replication group associated with Volume {}", volume.getId(), e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return null;
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
            log.warn("Failed to get replication group for volume {}", volume.getId(), e);
        } finally {
            if (null != names) {
                names.close();
            }
        }
        return null;
    }

    private CIMObjectPath getGroupSyncObject(final StorageSystem system, final Volume source,
            final String sourceGpName, final String tgtGpName) {

        CIMObjectPath result = null;
        CloseableIterator<CIMObjectPath> iterator = null;

        try {
            CIMObjectPath srcGroupPath = getDeviceGroup(system, system, source, sourceGpName, tgtGpName);
            if (srcGroupPath != null) {
                iterator = helper.getReference(system, srcGroupPath, SE_GROUP_SYNCHRONIZED_RG_RG, null);
                if (iterator.hasNext()) {
                    result = iterator.next();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to acquire group synchronization instance", e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        if (result == null) {
            log.warn(String.format("Failed to get GroupSynchronized object for Src:%s, Tgt:%s from System:%s",
                    sourceGpName, tgtGpName, system.getId()));
        }
        return result;
    }

    private CIMObjectPath getGroupSyncObjectForPairRemoval(final StorageSystem system, final StorageSystem forProvider,
            final Volume source, final String sourceGpName, final String tgtGpName) {
        CloseableIterator<CIMObjectPath> iterator = null;
        try {
            // get ReplicationGroup for the volume
            Set<CIMObjectPath> deviceGroups = getDeviceGroup(system, forProvider, source);
            for (CIMObjectPath deviceGroup : deviceGroups) {
                if (deviceGroup.toString().contains(sourceGpName) || deviceGroup.toString().contains(tgtGpName)) {
                    iterator = helper.getReference(forProvider, deviceGroup, SE_GROUP_SYNCHRONIZED_RG_RG, null);
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
                .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        ;
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
                    String dbMsg = format(
                            "Replication Succeeded but save to DB failed exception leaves the SRDF relationship to get established properly after some time. Hence for now succeeding this operation. for Sources:%s Targets:%s",
                            sourceURIs, targetURIs);
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
        List<URI> srcVolumeUris = dbClient.queryByConstraint(getVolumesByConsistencyGroup(source.getConsistencyGroup()));
        List<Volume> cgSrcVolumes = dbClient.queryObject(Volume.class, srcVolumeUris);
        Collection<String> srcDevIds = transform(filter(cgSrcVolumes, hasNativeID()), fctnBlockObjectToNativeID());

        List<URI> tgtVolumeUris = dbClient.queryByConstraint(getVolumesByConsistencyGroup(target.getConsistencyGroup()));
        List<Volume> cgTgtVolumes = dbClient.queryObject(Volume.class, tgtVolumeUris);
        Collection<String> tgtDevIds = transform(filter(cgTgtVolumes, hasNativeID()), fctnBlockObjectToNativeID());

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
                String elDevId = elPath.getKeyValue(CP_DEVICE_ID).toString();
                String elSysName = elPath.getKeyValue(CP_SYSTEM_NAME).toString().
                        replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);

                return elSysName.equalsIgnoreCase(systemNativeGuid) && nativeIds.contains(elDevId);
            }
        };
    }

    private Predicate<Volume> hasNativeID() {
        return new Predicate<Volume>() {
            @Override
            public boolean apply(Volume input) {
                return input.getNativeId() != null;
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
     * @param system Local or remote system
     * @return Concrete factory of AbstractSRDFOperationContextFactory
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

    private void invokeFailOverStrategy(StorageSystem sourceSystem, Volume target) throws Exception {
        // Build a strategy to find whether source is failover or not.
        SRDFOperationContext isFailOverCtx = getContextFactory(sourceSystem).build(SRDFOperation.FAIL_MECHANISM, target);
        isFailOverCtx.perform();
    }

    private StorageSystem findProviderWithGroup(Volume target) {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, target.getStorageController());
        StorageSystem activeProviderSystem = null;
        /**
         * For 8.x, groups will be available on both providers.
         * Since we are using original source provider for all SRDF operations,
         * first check for RDF Group's source provider and return that.
         */
        if (system.getUsingSmis80()) {
            activeProviderSystem = findProviderFactory.anyReachable(target).find();
            if (activeProviderSystem == null) {
                log.error("Both source and target providers are not reachable. Target volume: {}",
                        target);
                throw DeviceControllerException.exceptions.srdfBothSourceAndTargetProvidersNotReachable();
            }
        } else {
            // continue on if it is 4.6.x
            activeProviderSystem = findProviderFactory.withGroup(target).find();
            if (activeProviderSystem == null) {
                log.error(REPLICATION_GROUP_NOT_FOUND_ON_BOTH_PROVIDERS);
                throw DeviceControllerException.exceptions.srdfConsistencyGroupNotFoundOnProviders();
            }
        }
        return activeProviderSystem;
    }

    public void performChangeCopyMode(StorageSystem system, Volume target,
            TaskCompleter completer) {

        log.info("START performChangeCopyMode");
        checkTargetHasParentOrFail(target);

        AbstractSRDFOperationContextFactory ctxFactory = getContextFactory(system);
        ServiceError error = null;
        try {
            if (target.hasConsistencyGroup()) {
                URIQueryResultList tgtVolumeUris = new URIQueryResultList();
                dbClient.queryByConstraint(
                        getVolumesByConsistencyGroup(target.getConsistencyGroup()), tgtVolumeUris);
                Iterator<URI> tgtVolIterator = tgtVolumeUris.iterator();
                List<Volume> tgtVolumes = new ArrayList<>();
                while (tgtVolIterator.hasNext()) {
                    tgtVolumes.add(dbClient.queryObject(Volume.class, tgtVolIterator.next()));
                }

                log.info("Targets: {}", Joiner.on(", ").join(transform(tgtVolumes, fctnBlockObjectToNativeGuid())));
                ((SRDFChangeCopyModeTaskCompleter) completer).setTgtVolumes(tgtVolumes);
            }

            String copyMode = ((SRDFChangeCopyModeTaskCompleter) completer).getNewCopyMode();
            if (RemoteDirectorGroup.SupportedCopyModes.ADAPTIVECOPY.name().equalsIgnoreCase(copyMode)) {
                ctxFactory.build(SRDFOperation.RESET_TO_ADAPTIVE, target).perform();
            } else if (RemoteDirectorGroup.SupportedCopyModes.SYNCHRONOUS.name().equalsIgnoreCase(copyMode)) {
                ctxFactory.build(SRDFOperation.RESET_TO_SYNC, target).perform();
            } else if (RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name().equalsIgnoreCase(copyMode)) {
                ctxFactory.build(SRDFOperation.RESET_TO_ASYNC, target).perform();
            }

        } catch (Exception e) {
            log.error("Failed to change copy mode for srdf link {}", target.getSrdfParent().getURI(), e);
            error = SmisException.errors.jobFailed(e.getMessage());
        } finally {
            if (error == null) {
                completer.ready(dbClient);
            } else {
                completer.error(dbClient, error);
            }
        }

    }

    /**
     * Given a list of target volumes, find a source volume and its associated BlockConsistencyGroup.
     *
     * Using this BlockConsistencyGroup, find or create a group for the targets and apply to it all
     * targets.
     *
     * @param targetVolumes List of target volumes
     */
    private void findOrCreateTargetBlockConsistencyGroup(List<? extends BlockObject> targetVolumes) {
        log.info("Find or create target BlockConsistencyGroup...");
        final String LABEL_SUFFIX_FOR_46X = "T";
        // Get a target
        Volume target = (Volume) targetVolumes.get(0);
        // Get its SRDF parent
        Volume source = dbClient.queryObject(Volume.class, target.getSrdfParent().getURI());
        // Get source system
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());
        // Get the SRDF parents' BlockConsistencyGroup
        BlockConsistencyGroup sourceGroup = dbClient.queryObject(BlockConsistencyGroup.class,
                source.getConsistencyGroup());
        // Get the VirtualArray associated with the target volume
        VirtualArray virtualArray = dbClient.queryObject(VirtualArray.class, target.getVirtualArray());

        // Get the Project
        Project project = dbClient.queryObject(Project.class, target.getProject().getURI());

        // Generate the target BlockConsistencyGroup name
        String CG_NAME_FORMAT = "%s-Target-%s";
        String cgName = String.format(CG_NAME_FORMAT, sourceGroup.getLabel(), virtualArray.getLabel());

        // Check for existing target group
        List<BlockConsistencyGroup> groups = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient,
                        BlockConsistencyGroup.class, PrefixConstraint.Factory
                                .getFullMatchConstraint(
                                        BlockConsistencyGroup.class, "label",
                                        cgName));

        BlockConsistencyGroup newConsistencyGroup = null;

        if (groups.isEmpty()) {
            log.info("Creating target group: {}", cgName);
            // create CG
            newConsistencyGroup = new BlockConsistencyGroup();
            newConsistencyGroup
                    .setId(URIUtil.createId(BlockConsistencyGroup.class));
            newConsistencyGroup.setLabel(cgName);
            newConsistencyGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
            newConsistencyGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(),
                    project.getTenantOrg().getName()));
            // ModifyReplica on GroupSync for swap operation will try to create CG with same name on target provider.
            // For 4.6.x, better to use a different name for target CG.
            StringBuffer label = new StringBuffer(sourceGroup.getLabel());
            if (!sourceSystem.getUsingSmis80()) {
                label.append(LABEL_SUFFIX_FOR_46X);
            }
            newConsistencyGroup.setAlternateLabel(label.toString());

            // Persist the new BCG
            dbClient.createObject(newConsistencyGroup);
        } else {
            newConsistencyGroup = groups.get(0);
            log.info("Using existing target group: {}", newConsistencyGroup.getLabel());
        }

        // Update and persist target volumes with BCG
        for (BlockObject targetObj : targetVolumes) {
            targetObj.setConsistencyGroup(newConsistencyGroup.getId());
        }
        dbClient.persistObject(targetVolumes);
    }

}

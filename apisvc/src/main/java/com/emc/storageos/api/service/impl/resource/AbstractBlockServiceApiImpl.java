/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.toVirtualPoolChangeRep;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VolumeTopology;
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologyRole;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.model.util.TagUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.storageos.model.block.BlockPerformancePolicyMap;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeCreatePerformancePolicies;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFCopyRecommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.workflow.WorkflowException;
import com.google.common.base.Joiner;

public abstract class AbstractBlockServiceApiImpl<T> implements BlockServiceApi {

    // A logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(AbstractBlockServiceApiImpl.class);

    @Autowired
    private PermissionsHelper _permissionsHelper;

    @Autowired
    protected DependencyChecker _dependencyChecker;

    protected T _scheduler;

    protected DbClient _dbClient;

    private CoordinatorClient _coordinator;

    // Permissions helper getter/setter

    public void setPermissionsHelper(PermissionsHelper permissionsHelper) {
        _permissionsHelper = permissionsHelper;
    }

    public PermissionsHelper getPermissionsHelper() {
        return _permissionsHelper;
    }

    // Dependency checker getter/setter

    public void setDependencyChecker(DependencyChecker dependencyChecker) {
        _dependencyChecker = dependencyChecker;
    }

    public DependencyChecker getDependencyChecker() {
        return _dependencyChecker;
    }

    // Coordinator getter/setter

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    // Db client getter/setter

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    // StorageScheduler getter/setter

    public void setBlockScheduler(T scheduler) {
        _scheduler = scheduler;
    }

    public T getBlockScheduler() {
        return _scheduler;
    }

    /**
     * Map of implementing class instances; used for iterating through them for
     * connectivity purposes.
     */
    static private Map<String, AbstractBlockServiceApiImpl> s_protectionImplementations = new HashMap<String, AbstractBlockServiceApiImpl>();

    /**
     * Constructor used to keep track of the various implementations of this class.
     * In particular, we are interested in "protection" implementations, that we need to
     * compute connectivity for.
     *
     * @param protectionType
     *            -- Should be null for regular Block implementation,
     *            or the DiscoveredDataObject.Type.name() value for "protection" implementations,
     *            so far RP and VPLEX.
     */
    public AbstractBlockServiceApiImpl(String protectionType) {
        if (protectionType != null) {
            s_protectionImplementations.put(protectionType, this);
        }
    }

    static protected Map<String, AbstractBlockServiceApiImpl> getProtectionImplementations() {
        return s_protectionImplementations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object, List<Class<? extends DataObject>> excludeTypes) throws InternalException {
        URI objectURI = object.getId();
        List<Class<? extends DataObject>> excludes = new ArrayList<Class<? extends DataObject>>();
        if (excludeTypes != null) {
            excludes.addAll(excludeTypes);
        }
        excludes.add(Task.class);
        String depMsg = getDependencyChecker().checkDependencies(objectURI, object.getClass(), true, excludes);
        if (depMsg != null) {
            return depMsg;
        }

        // The dependency checker does not pick up dependencies on
        // BlockSnapshotSession because the containment constraint
        // use the base class BlockObject as the parent i.e., source
        // for a BlockSnapshotSession could be a Volume or BlockSnapshot.
        if (object instanceof BlockObject) {
            List<BlockSnapshotSession> dependentSnapSessions = getDependentSnapshotSessions((BlockObject)object);
            if (!dependentSnapSessions.isEmpty()) {
                return BlockSnapshotSession.class.getSimpleName();
            }
        }

        return object.canBeDeleted();
    }
    
    /**
     * Get the snapshot sessions for the passed volume only, do not retrieve any of the related snap sessions.
     *
     * @param volume A reference to a volume.
     * 
     * @return The snapshot sessions for the passed volume.
     */
    public List<BlockSnapshotSession> getSnapshotSessionsForVolume(Volume volume) {
        List<BlockSnapshotSession> snapsSessions = new ArrayList<>();
        boolean vplex = VPlexUtil.isVplexVolume(volume, _dbClient);
        if (vplex) {
            Volume snapSessionSourceVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient, false);
            if (snapSessionSourceVolume != null) {
                snapsSessions.addAll(getDependentSnapshotSessions(snapSessionSourceVolume));
            }
        } else {
            snapsSessions.addAll(getDependentSnapshotSessions(volume));
        }
        return snapsSessions;
    }

    /**
     * Returns the active snapshot sessions associated with the passed block object.
     * 
     * @param bo A reference to the block object
     * 
     * @return The list of dependent snapshot sessions.
     */
    public List<BlockSnapshotSession> getDependentSnapshotSessions(BlockObject bo) {
        List<BlockSnapshotSession> dependantSnapSessions = new ArrayList<>();
        URI cgURI = bo.getConsistencyGroup();
        if (NullColumnValueGetter.isNullURI(cgURI)) {
            // If the Object is not in a CG, then we need to find all snapshots sessions
            // whose parent is the passed object.
            dependantSnapSessions.addAll(CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class, ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(bo.getId())));
        } else {
            // Otherwise, get all the consistency group snapshot sessions.
            List<BlockSnapshotSession> cgSnapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class, ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(cgURI));
            if (!cgSnapSessions.isEmpty()) {
                String boReplicationGroup = bo.getReplicationGroupInstance();
                for (BlockSnapshotSession session : cgSnapSessions) {
                    String sessionReplicationGroup = session.getReplicationGroupInstance();
                    if (NullColumnValueGetter.isNotNullValue(boReplicationGroup) && boReplicationGroup.equals(sessionReplicationGroup)) {
                        List<Volume> replicationGroupVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(
                                _dbClient, Volume.class, AlternateIdConstraint.Factory.getVolumeReplicationGroupInstanceConstraint(boReplicationGroup));
                        if (replicationGroupVolumes.size() == 1) {
                            // This is the only volume in the replication group, so
                            // this snapshot session is essentially dependent on this
                            // block object. If the volume being inventory deleted is 
                            // the last volume in the replication group, then don't allow
                            // the deletion and force the user to clean up the session first.
                            dependantSnapSessions.add(session);
                        }
                    }
                }
            }           
        }
        return dependantSnapSessions;
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup controller for
     * EXTERNALDEVICE.
     *
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        T controller;
        try {
            controller = _coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
        } catch (RetryableCoordinatorException rex) {
            controller = _coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, Constants.EXTERNALDEVICE, clazz.getSimpleName());
        }
        return controller;
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup default controller
     * for EXTERNALDEVICE tag.
     *
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param externalDevice
     *            hardware tag for external devices
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw, String externalDevice) {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, externalDevice, clazz.getSimpleName());
    }

    // Default unsupported operations

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, VirtualPool sourceVpool, VirtualPool mirrorVpool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            NativeContinuousCopyCreate param, String task) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList stopNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
            List<URI> mirrors,
            String taskId) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList pauseNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
            List<BlockMirror> blockMirrors, Boolean sync,
            String taskId) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList resumeNativeContinuousCopies(StorageSystem storageSystem, Volume sourceVolume,
            List<BlockMirror> blockMirrors, String taskId)
            throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep establishVolumeAndNativeContinuousCopyGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockMirror blockMirror, String taskId) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskResourceRep establishVolumeAndSnapshotGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockSnapshot snapshot, String taskId) throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     */
    @Override
    public TaskList deactivateMirror(StorageSystem storageSystem, URI mirrorURI, String task, String deleteType)
            throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep deleteConsistencyGroup(StorageSystem device, BlockConsistencyGroup consistencyGroup, String task)
            throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskResourceRep updateConsistencyGroup(StorageSystem cgStorageSystem,
            List<Volume> cgVolumes, BlockConsistencyGroup consistencyGroup,
            List<URI> addVolumesList, List<URI> removeVolumesList, String taskId)
            throws ControllerException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ControllerException
     * @throws InternalException
     */
    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray varray, VirtualPool vpool,
            VolumeTopology volumeTopology, Map<VpoolUse, List<Recommendation>> recommendationMap, TaskList taskList,
            String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws ControllerException,
            InternalException {

        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(URI systemURI, List<URI> volumeURIs, String deletionType, String task)
            throws InternalException {
        // Get volume descriptor for all volumes to be deleted.
        List<VolumeDescriptor> volumeDescriptors = getDescriptorsForVolumesToBeDeleted(
                systemURI, volumeURIs, deletionType);

        // Mark the volumes for deletion for a VIPR only delete, otherwise get
        // the controller and delete the volumes.
        if (VolumeDeleteTypeEnum.VIPR_ONLY.name().equals(deletionType)) {
            // Do any cleanup necessary for the ViPR only delete.
            cleanupForViPROnlyDelete(volumeDescriptors);

            // Mark them inactive. Note that some of the volumes may be mirrors,
            // which have a different database type.
            List<VolumeDescriptor> descriptorsForMirrors = VolumeDescriptor.getDescriptors(
                    volumeDescriptors, VolumeDescriptor.Type.BLOCK_MIRROR);
            _dbClient.markForDeletion(_dbClient.queryObject(BlockMirror.class,
                    VolumeDescriptor.getVolumeURIs(descriptorsForMirrors)));
            List<VolumeDescriptor> descriptorsForVolumes = VolumeDescriptor.filterByType(
                    volumeDescriptors, null, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR });
            _dbClient.markForDeletion(_dbClient.queryObject(Volume.class,
                    VolumeDescriptor.getVolumeURIs(descriptorsForVolumes)));
            
            // Delete the corresponding FCZoneReferences
            for (URI volumeURI : volumeURIs) {
                List<FCZoneReference> zoneReferences = CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, 
                        FCZoneReference.class, "volumeUri", volumeURI.toString());
                for (FCZoneReference zoneReference : zoneReferences) {
                    if (zoneReference != null) {
                        _dbClient.markForDeletion(zoneReference);
                    }
                }
            }

            // Update the task status for each volume
            for (URI volumeURI : volumeURIs) {
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                Operation op = volume.getOpStatus().get(task);
                op.ready("Volume succesfully deleted from ViPR");
                volume.getOpStatus().updateTaskStatus(task, op);
                _dbClient.updateObject(volume);
            }
        } else {
            BlockOrchestrationController controller = getController(
                    BlockOrchestrationController.class,
                    BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
            controller.deleteVolumes(volumeDescriptors, task);
        }
    }

    /**
     * Get the volume descriptors for all volumes to be deleted given the
     * passed volumes.
     *
     * @param systemURI
     *            The URI of the system on which the volumes reside.
     * @param volumeURIs
     *            The URIs of the volumes to be deleted.
     *
     * @return The list of volume descriptors.
     */
    @Override
    abstract public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(
            URI systemURI, List<URI> volumeURIs, String deletionType);

    /**
     * Perform any database clean up required as a result of removing the volumes
     * with the passed URIs from the ViPR database.
     *
     * @param volumeDescriptors
     *            The descriptors for all volumes involved in the ViPR only delete.
     */
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // Remove volumes from ExportGroup(s) and ExportMask(s).
        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        for (URI volumeURI : volumeURIs) {
            ExportUtils.cleanBlockObjectFromExports(volumeURI, true, _dbClient);
        }
    }

    /**
     * Perform any database clean up required as a result of removing the mirrors
     * with the passed URIs from the ViPR database.
     *
     * @param mirrorURIs
     *            The URIs of the mirrors involved in the ViPR only delete.
     */
    protected void cleanupForViPROnlyMirrorDelete(List<URI> mirrorURIs) {
        // NO-OP be default.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualPoolChangeList getVirtualPoolForVirtualPoolChange(Volume volume) {
        return getVirtualPoolChangeListForVolume(volume);
    }

    /**
     * Gets all potential vpools to to which the vpool for the passed volume can be
     * changed.
     *
     * @param volume
     *            A reference to the volume.
     *
     * @return A VirtualPoolChangeList specifying each vpool to which the volume's
     *         vpool could potentially be changed and whether or not the change would
     *         be allowed for that vpool.
     */
    protected VirtualPoolChangeList getVirtualPoolChangeListForVolume(Volume volume) {
        // Get all potential vpools for this volume based on system
        // connectivity of the volume's storage system. For each
        // vpool determine if a vpool change to that vpool would be
        // allowed for the volume.
        VirtualPoolChangeList vpoolChangeList = new VirtualPoolChangeList();
        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

        Collection<VirtualPool> allVpools = getVPoolsForVolumeBasedOnSystemConnectivity(volume);

        Iterator<VirtualPool> vpoolIter = allVpools.iterator();
        StringBuffer logMsg = new StringBuffer();
        logMsg.append("Analyzing vpools for change vpool operations:\n");
        while (vpoolIter.hasNext()) {
            StringBuffer notAllowedReason = new StringBuffer();
            VirtualPool targetVpool = vpoolIter.next();
            List<VirtualPoolChangeOperationEnum> allowedOperations = getVirtualPoolChangeAllowedOperationsForVolume(
                    volume, currentVpool, targetVpool, notAllowedReason);

            logMsg.append("\tVpool [" + targetVpool.getLabel() + "]");
            logMsg.append((notAllowedReason.length() > 0) ? " not allowed: " + notAllowedReason.toString() : " allowed but only for: ");
            logMsg.append((allowedOperations != null && !allowedOperations.isEmpty()) ? Joiner.on("\t").join(allowedOperations) : "");
            logMsg.append("\n");

            vpoolChangeList.getVirtualPools().add(
                    toVirtualPoolChangeRep(targetVpool, allowedOperations,
                            notAllowedReason.toString()));
        }
        s_logger.info(logMsg.toString());

        return vpoolChangeList;
    }

    /**
     * Get all potential vpools for the passed volume, based strictly on
     * connectivity of the volume's storage system.
     *
     * @param volume
     *            A reference to a Volume.
     *
     * @return A collection of vpools.
     */
    protected Collection<VirtualPool> getVPoolsForVolumeBasedOnSystemConnectivity(Volume volume) {

        Map<URI, VirtualPool> vpoolsMap = new HashMap<URI, VirtualPool>();

        // Get the volume's project.
        Project project = _permissionsHelper.getObjectById(volume.getProject(),
                Project.class);

        // Get the matching vpools for all storage pools on the volume's storage
        // system and all other storage systems to which this storage system
        // is connected.
        URI volumeSystemURI = volume.getStorageController();

        // If the volume storage system is a vplex, we want to find storage systems
        // associated by network connectivity with the vplex backend ports
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volumeSystemURI);
        StoragePort.PortType portType = getSystemConnectivityPortType();
        if (ConnectivityUtil.isAVPlex(storageSystem)) {
            s_logger
                    .info("Volume Storage System is a VPLEX, setting port type to backend for storage systems network association check.");
            portType = StoragePort.PortType.backend;
        }

        Set<URI> connectedSystemURIs = ConnectivityUtil
                .getStorageSystemAssociationsByNetwork(_dbClient, volumeSystemURI, portType);

        connectedSystemURIs.add(volumeSystemURI);
        Iterator<URI> systemURIsIter = connectedSystemURIs.iterator();
        while (systemURIsIter.hasNext()) {
            URIQueryResultList systemPools = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(systemURIsIter.next()),
                    systemPools);
            Iterator<URI> poolURIsIter = systemPools.iterator();
            while (poolURIsIter.hasNext()) {
                URIQueryResultList storagePoolMatchedVpoolURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getMatchedPoolVirtualPoolConstraint(poolURIsIter.next()),
                        storagePoolMatchedVpoolURIs);
                Iterator<URI> vpoolURIsItr = storagePoolMatchedVpoolURIs.iterator();
                while (vpoolURIsItr.hasNext()) {
                    URI storagePoolMatchedVpoolURI = vpoolURIsItr.next();
                    if (vpoolsMap.containsKey(storagePoolMatchedVpoolURI)) {
                        continue;
                    }

                    VirtualPool storagePoolMatchedVpool = _permissionsHelper.getObjectById(
                            storagePoolMatchedVpoolURI, VirtualPool.class);
                    String storagePoolMatchedVpoolType = storagePoolMatchedVpool.getType();
                    if ((VirtualPool.Type.block.name().equals(storagePoolMatchedVpoolType))
                            && (_permissionsHelper.tenantHasUsageACL(project.getTenantOrg()
                                    .getURI(), storagePoolMatchedVpool))) {
                        vpoolsMap.put(storagePoolMatchedVpoolURI, storagePoolMatchedVpool);
                    }
                }
            }
        }

        return vpoolsMap.values();
    }

    /**
     * When determining storage system connectivity we typically use the frontend
     * storage ports.
     *
     * @return The port type for determining storage system connectivity.
     */
    protected StoragePort.PortType getSystemConnectivityPortType() {
        return StoragePort.PortType.frontend;
    }

    /**
     * Determine virtual pool change operation which allows on the given volume.
     * If none, the disallowed reason should be referenced in <code>notSuppReasonBuff</code>
     *
     * @param volume
     *            A reference to the Volume.
     * @param currentVpool
     *            A reference to the current vpool for the volume.
     * @param newVpool
     *            A reference to the new vpool
     * @param notSuppReasonBuff
     *            - reason if no change operation was allowed.
     *
     * @return allowed volume virtual pool change operation
     */
    private List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperationsForVolume(
            Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            StringBuffer notSuppReasonBuff) {

        // The base class implementation just determines if the new
        // vpool is the current vpool or if this is a path param or auto-tiering policy change.
        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();

        if (!VirtualPoolChangeAnalyzer.isSameVirtualPool(currentVpool, newVpool, notSuppReasonBuff)) {
            allowedOperations.addAll(getVirtualPoolChangeAllowedOperations(volume,
                    currentVpool, newVpool, notSuppReasonBuff));

            // check if export path operation is allowable
            StringBuffer pathChangeReasonBuff = new StringBuffer();
            if (VirtualPoolChangeAnalyzer.isSupportedPathParamsChange(volume,
                    currentVpool, newVpool, _dbClient, pathChangeReasonBuff)) {
                allowedOperations.add(VirtualPoolChangeOperationEnum.EXPORT_PATH_PARAMS);
            }

            // check if replication mode change is supported
            StringBuffer replicationModeChangeReasonBuff = new StringBuffer();
            if (VirtualPoolChangeAnalyzer.isSupportedReplicationModeChange(
                    currentVpool, newVpool, replicationModeChangeReasonBuff)) {
                allowedOperations.add(VirtualPoolChangeOperationEnum.REPLICATION_MODE);
            }

            // check if Auto-tiering policy change operation is allowable
            StringBuffer autoTieringPolicyChangeReasonBuff = new StringBuffer();
            if (VirtualPoolChangeAnalyzer.isSupportedAutoTieringPolicyAndLimitsChange(volume,
                    currentVpool, newVpool, _dbClient, autoTieringPolicyChangeReasonBuff)) {
                allowedOperations.add(VirtualPoolChangeOperationEnum.AUTO_TIERING_POLICY);
            } else if (notSuppReasonBuff.length() == 0) {
                // Only override the not supported reason if no other
                // technology specific reason prevented the vpool change.
                notSuppReasonBuff.append(pathChangeReasonBuff.toString());
                notSuppReasonBuff.append(autoTieringPolicyChangeReasonBuff.toString());
            }

            // If a VPLEX vPool is eligible for both AUTO_TIERING_POLICY and VPLEX_DATA_MIGRATION operations,
            // remove the VPLEX_DATA_MIGRATION operation from the supported list of operations.
            // Reason: Current 'vPool change' design executes the first satisfying operation irrespective of what user
            // chooses in the UI.
            // Also when a Policy change can be performed by AUTO_TIERING_POLICY operation, why would the same needs
            // VPLEX_DATA_MIGRATION?
            if (allowedOperations.contains(VirtualPoolChangeOperationEnum.AUTO_TIERING_POLICY) &&
                    allowedOperations.contains(VirtualPoolChangeOperationEnum.VPLEX_DATA_MIGRATION)) {
                s_logger.info("Removing VPLEX_DATA_MIGRATION operation from supported operations list for vPool {} "
                        + "as the same can be accomplished via AUTO_TIERING_POLICY_IO_LIMITS change operation", newVpool.getLabel());
                allowedOperations.remove(VirtualPoolChangeOperationEnum.VPLEX_DATA_MIGRATION);
            }
        }

        // clear notSuppReasonBuff if there is an allowed vpool operation
        if (!allowedOperations.isEmpty()) {
            notSuppReasonBuff.setLength(0);
        }

        return allowedOperations;
    }

    /**
     * Determine virtual pool change operation which allows on volume. If none,
     * the disallowed reason should be referenced in <code>notSuppReasonBuff</code> This method should be implemented in
     * subclass get
     * operation based on specific
     * volume type.
     *
     * @param volume
     *            A reference to the Volume.
     * @param currentVpool
     *            A reference to the current vpool for the volume.
     * @param newVpool
     *            A reference to the new vpool.
     * @param notSuppReasonBuff
     *            - reason if no change operation was allowed.
     *
     * @return allowed volume virtual pool change operation
     */
    abstract protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(
            Volume volume, VirtualPool currentVpool, VirtualPool newVpool,
            StringBuffer notSuppReasonBuff);

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public TaskList changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        List<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return null;
        }
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public TaskList changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        /**
         * 'Auto-tiering policy change' operation supports multiple volume processing.
         * At present, other operations only support single volume processing.
         */
        TaskList taskList = createTasksForVolumes(vpool, volumes, taskId);
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return taskList;
        }
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(StorageSystem storageSystem) {
        StorageSystemConnectivityList list = new StorageSystemConnectivityList();
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeVirtualArrayForVolumes(List<Volume> volumes,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray varray,
            String taskId) throws InternalException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {
        throw APIException.badRequests.changesNotSupportedFor("VirtualArray",
                String.format("volume %s", volume.getId()));
    }

    /**
     * Given a list of VolumeDescriptors and a volume, adds the descriptors necessary
     * for any BlockMirrors on the volume.
     *
     * @param descriptors
     *            List<VolumeDescriptor>
     * @param volume
     */
    protected void addDescriptorsForMirrors(List<VolumeDescriptor> descriptors, Volume volume) {
        if (volume.getMirrors() != null && volume.getMirrors().isEmpty() == false) {
            for (String mirrorId : volume.getMirrors()) {
                BlockMirror mirror = _dbClient.queryObject(BlockMirror.class, URI.create(mirrorId));

                if (mirror != null && !mirror.getInactive()) {
                    VolumeDescriptor mirrorDesc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_MIRROR,
                            volume.getStorageController(), URI.create(mirrorId), null, null);
                    descriptors.add(mirrorDesc);
                }
            }
        }
    }

    /**
     * Determines the Virtual Storage Array Connectivity of the given Virtual Storage Array
     *
     * @param dbClient
     *            - Static method needs DbClient
     * @param varrayUID
     *            - UID of the varray to find the connectivity for
     * @return A Set of VirtualArrayConnectivityRestRep
     */
    public static Set<VirtualArrayConnectivityRestRep> getVirtualArrayConnectivity(DbClient dbClient, URI varrayUID) {
        Set<VirtualArrayConnectivityRestRep> varrayConnectivity = new HashSet<VirtualArrayConnectivityRestRep>();

        for (String key : getProtectionImplementations().keySet()) {
            Set<URI> varrays = getProtectionImplementations().get(key).getConnectedVarrays(varrayUID);

            Iterator<URI> it = varrays.iterator();

            while (it.hasNext()) {
                URI currentVirtualArrayUID = it.next();
                VirtualArray varray = dbClient.queryObject(VirtualArray.class, currentVirtualArrayUID);
                if (varray != null) {
                    VirtualArrayConnectivityRestRep connection = new VirtualArrayConnectivityRestRep();
                    connection.setVirtualArray(toNamedRelatedResource(ResourceTypeEnum.VARRAY, varray.getId(), varray.getLabel()));
                    StringSet connectivity = new StringSet();
                    connectivity.add(key);
                    connection.setConnectionType(connectivity);
                    varrayConnectivity.add(connection);
                }
            }
        }

        return varrayConnectivity;
    }

    protected Set<URI> getConnectedVarrays(URI varrayUID) {
        return new HashSet<URI>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVolumeExpansionRequest(Volume volume, long newSize) {
        // Verify the passed volume is not a meta volume w/mirrors.
        // Expansion is not supported in this case.
        if (isMetaVolumeWithMirrors(volume)) {
            throw APIException.badRequests.expansionNotSupportedForMetaVolumesWithMirrors();
        }

        // Throw exception as HDS Thick volume Expansion is not supported currently
        // @TODO remove this condition when we add full support for thick volume expansion.
        if (isHitachiVolume(volume) && !volume.getThinlyProvisioned()) {
            throw APIException.badRequests.expansionNotSupportedForHitachThickVolumes();
        }

        // Verify the passed volume is not exported.
        // Expansion is not supported in this case.
        if (isHitachiVolume(volume) && !isHitachiVolumeExported(volume)
                && !volume.getThinlyProvisioned()) {
            throw APIException.badRequests.expansionNotSupportedForHitachiVolumesNotExported();
        }

        // Regular VMAX/VNX volumes with mirrors where request results in
        // forming a meta volume is not supported.
        if (expansionResultsInMetaWithMirrors(volume)) {
            throw APIException.badRequests.cannotExpandMirrorsUsingMetaVolumes();
        }
        // Extension of volumes in VNX Unified storage pools can be done only as
        // regular volumes (meta extension is not supported)
        // For VNX Unified pool volumes, check that volume new size is within
        // max volume size limit of its storage pool.
        long maxVolumeSizeLimitKB = getMaxVolumeSizeLimit(volume);
        StoragePool storagePool = _permissionsHelper.getObjectById(volume.getPool(), StoragePool.class);
        if (StoragePool.PoolClassNames.Clar_UnifiedStoragePool.name().equalsIgnoreCase(
                storagePool.getPoolClassName())) {
            // COP-30564 : Check only expansion size against maxVolumeSizeLimit, not total volume size after expansion (this is
            // specific to VNX arrays implementation).
            Long expansionSize = newSize - volume.getCapacity() > 0 ? newSize - volume.getCapacity() : 0;
            Long expansionSizeKB = (expansionSize % 1024 == 0) ? expansionSize / 1024 : expansionSize / 1024 + 1;
            if (expansionSizeKB > maxVolumeSizeLimitKB) {
                s_logger.info("VNX volume can not be expanded --- expansion size request {} exceeds maximum volume size limit in the pool {} . ",
                        expansionSizeKB, maxVolumeSizeLimitKB);
                throw APIException.badRequests.invalidVolumeSize(newSize, maxVolumeSizeLimitKB);
            }
        }
    }

    /**
     * Determines whether Hitachi volume exported to a host or not.
     *
     * @param volume
     * @return
     */
    private boolean isHitachiVolumeExported(Volume volume) {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeExportGroupConstraint(
                volume.getId()), exportGroupURIs);
        while (exportGroupURIs.iterator().hasNext()) {
            URI exportGroupURI = exportGroupURIs.iterator().next();
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            if (!exportGroup.getInactive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expandVolume(Volume volume, long newSize, String taskId)
            throws InternalException {

        BlockOrchestrationController controller = getController(
                BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);

        long expandCapacity = newSize - volume.getCapacity();
        StorageScheduler.addVolumeExpansionSizeToReservedCapacityMap(_dbClient, volume, expandCapacity);

        VolumeDescriptor descriptor = new VolumeDescriptor(
                VolumeDescriptor.Type.BLOCK_DATA,
                volume.getStorageController(), volume.getId(), volume.getPool(), null, null, volume.getCapacity());
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>(Arrays.asList(descriptor));
        for (VolumeDescriptor volDesc : descriptors) {
            volDesc.setVolumeSize(newSize);
        }
        controller.expandVolume(descriptors, taskId);
    }

    /**
     * Determines if the passed volume is a meta volume and has attached
     * mirrors.
     *
     * @param volume
     *            A reference to a Volume.
     *
     * @return true if the volume is a meta volume and has attached mirrors,
     *         false otherwise.
     */
    protected boolean isMetaVolumeWithMirrors(Volume volume) {
        return ((isMeta(volume)) && (BlockServiceUtils.hasMirrors(volume)));
    }


    /**
     * Get the maximum volume size for the passed volume's storage pool.
     *
     * @param volume
     *            A reference to a volume.
     *
     * @return The maximum volume size for the passed volume's storage pool.
     */
    private Long getMaxVolumeSizeLimit(Volume volume) {
        StoragePool storagePool = _permissionsHelper.getObjectById(volume.getPool(),
                StoragePool.class);
        Long maxVolumeSizeLimit; // limit in kilobytes
        if (volume.getThinlyProvisioned()) {
            maxVolumeSizeLimit = storagePool.getMaximumThinVolumeSize();
        } else {
            maxVolumeSizeLimit = storagePool.getMaximumThickVolumeSize();
        }
        return maxVolumeSizeLimit;
    }

    /**
     * Determines if an expansion of the passed volume would result in a meta
     * volume with attached mirrors.
     *
     * @param volume
     *            A reference to a volume.
     *
     * @return true if an expansion of the passed volume would result in a meta
     *         volume with attached mirrors, false otherwise.
     */
    protected boolean expansionResultsInMetaWithMirrors(Volume volume) {
        StoragePool storagePool = _permissionsHelper.getObjectById(volume.getPool(),
                StoragePool.class);
        // Volume's not in Clariion unified storage pools & HDS Thin volumes result in meta
        // volume's when expanded.

        return ((!(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.name()
                .equalsIgnoreCase(storagePool.getPoolClassName()) || isHitachiThinVolume(volume))))
                && (BlockServiceUtils.hasMirrors(volume));
    }

    /**
     * Determines whether passed volume is HDS thin volume or not.
     *
     * @param volume
     *            A reference to a volume
     * @return true if the volume belongs to Hitachi else false.
     */
    protected boolean isHitachiThinVolume(Volume volume) {
        return volume.getThinlyProvisioned() && isHitachiVolume(volume);
    }

    /**
     * Determines whether passed volume is HDS or not.
     *
     * @param volume
     * @return
     */
    protected boolean isHitachiVolume(Volume volume) {
        StorageSystem system = _permissionsHelper.getObjectById(
                volume.getStorageController(), StorageSystem.class);
        return DiscoveredDataObject.Type.hds.name().equalsIgnoreCase(
                system.getSystemType());
    }

    /**
     * Determines if the passed volume has attached mirrors.
     *
     * @param volume
     *            A reference to a Volume.
     *
     * @return true if passed volume has attached mirrors, false otherwise.
     */
    protected boolean hasMirrors(Volume volume) {
        return volume.getMirrors() != null && !volume.getMirrors().isEmpty();
    }

    /**
     * Determines if the passed volume is a meta volume.
     *
     * @param volume
     *            A reference to a Volume.
     *
     * @return true if the passed volume is a meta volume, false otherwise.
     */
    private boolean isMeta(Volume volume) {
        return volume.getIsComposite() != null && volume.getIsComposite();
    }

    /**
     * Checks for Vpool updates that can be done on any device type.
     * For now, this is just the Export Path Params or Auto-tiering policy change.
     * If the update was processed, return true, else false.
     *
     * @param volumes
     * @param newVirtualPool
     * @param taskId
     * @return true if update processed
     * @throws InternalException
     */
    protected boolean checkCommonVpoolUpdates(List<Volume> volumes, VirtualPool newVirtualPool,
            String taskId) throws InternalException {
        VirtualPool volumeVirtualPool = _dbClient.queryObject(VirtualPool.class, volumes.get(0).getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();
        if (VirtualPoolChangeAnalyzer.isSupportedPathParamsChange(volumes.get(0),
                volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            BlockExportController exportController = getController(BlockExportController.class, BlockExportController.EXPORT);
            for (Volume volume : volumes) {
                exportController.updateVolumePathParams(volume.getId(), newVirtualPool.getId(), taskId);
            }
            return true;
        }

        if (VirtualPoolChangeAnalyzer
                .isSupportedAutoTieringPolicyAndLimitsChange(volumes.get(0), volumeVirtualPool,
                        newVirtualPool, _dbClient, notSuppReasonBuff)) {
            /**
             * If it is a Auto-tiering policy change, it is sufficient to check on one volume in the list.
             * Mixed type volumes case has already been taken care in BlockService API.
             */
            BlockExportController exportController = getController(BlockExportController.class, BlockExportController.EXPORT);
            List<URI> volumeURIs = new ArrayList<URI>();
            for (Volume volume : volumes) {
                volumeURIs.add(volume.getId());
            }

            exportController.updatePolicyAndLimits(volumeURIs, newVirtualPool.getId(), taskId);
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateCreateSnapshot(Volume reqVolume, List<Volume> volumesToSnap,
            String snapshotType, String snapshotName, Boolean readOnly, BlockFullCopyManager fcManager) {
        // Make sure a name was specified.
        ArgValidator.checkFieldNotEmpty(snapshotName, "name");

        // Validate there are volumes to snap.
        if (volumesToSnap == null || volumesToSnap.isEmpty()) {
            throw APIException.badRequests.noVolumesToSnap();
        }

        // Verify the vpools of the volumes to be snapped support
        // snapshots and the maximum snapshots has not been reached.
        // Also, check for a duplicate name.
        for (Volume volumeToSnap : volumesToSnap) {
            // check if the number of snapshots exceed the limit
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volumeToSnap.getVirtualPool());
            ArgValidator.checkFieldNotNull(vpool, "vpool");
            if (vpool.getMaxNativeSnapshots() == 0) {
                throw APIException.badRequests.maxNativeSnapshotsIsZero(vpool.getLabel());
            }
            if (getNumNativeSnapshots(volumeToSnap) >= vpool.getMaxNativeSnapshots().intValue()) {
                throw APIException.methodNotAllowed.maximumNumberSnapshotsReached();
            }

            // Check for duplicate name.
            checkForDuplicatSnapshotName(snapshotName, volumeToSnap);
        }
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, reqVolume.getStorageController());
        Boolean is8xProvider = system.getUsingSmis80();
        
        Boolean isXtremIO = StorageSystem.Type.xtremio.toString().equals(system.getSystemType());
        Boolean isVplexXtremIO = StorageSystem.Type.vplex.toString().equals(system.getSystemType()) 
        		&& VPlexUtil.isXtremIOBackend(reqVolume, _dbClient);
        if (readOnly && !(isXtremIO || isVplexXtremIO)) {
        	throw APIException.badRequests.cannotCreateReadOnlySnapshotForNonXIOVolumes();
        }
        // We should validate this for 4.x provider as it doesn't support snaps for SRDF meta volumes.
        if (!is8xProvider) {
            // Check that if the volume is a member of vmax consistency group all volumes in the group are regular
            // volumes, not meta
            // volumes.
            URI cgURI = reqVolume.getConsistencyGroup();
            if (!NullColumnValueGetter.isNullURI(cgURI)) {
                if (system.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
                    BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
                    for (Volume volumeToSnap : getActiveCGVolumes(cg)) {
                        if (volumeToSnap.getIsComposite()) {
                            throw APIException.methodNotAllowed.notSupportedWithReason(
                                    String.format("Volume %s is a member of vmax consistency group which has meta volumes.",
                                            reqVolume.getLabel()));
                        }
                    }
                }
            }
        }

        // Some systems (vmax3) don't support snapshots when there are active full
        // copies and vice versa.
        fcManager.validateSnapshotCreateRequest(reqVolume, volumesToSnap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Volume> getVolumesToSnap(Volume reqVolume, String snapshotType) {
        // By default, if the passed volume is in a consistency group
        // all volumes in the consistency group should be snapped.
        List<Volume> volumesToSnap = new ArrayList<Volume>();
        URI cgURI = reqVolume.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgURI)) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            volumesToSnap = getActiveCGVolumes(cg);
        } else {
            volumesToSnap.add(reqVolume);
        }
        return volumesToSnap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        _dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (!volume.getInactive()) {
                volumeList.add(volume);
            }
        }
        return volumeList;
    }

    /**
     * Counts and returns the number of snapshots on a volume
     *
     * @param volume
     *            A reference to a volume.
     *
     * @return The number of snapshots on a volume.
     */
    protected int getNumNativeSnapshots(Volume volume) {
        return BlockServiceUtils.getNumNativeSnapshots(volume.getId(), _dbClient);
    }

    /**
     * Check if a snapshot with the same name exists for the passed volume.
     * Note that we need to compare the passed name to the snapset label for
     * the volumes snapshots because the actually snapshot names can have
     * an appended suffix when the volumes is in a CG with multiple volumes.
     * Also, we need to run the name through the generator, which is done
     * prior to setting the snapset label for a snapshot.
     *
     * @param requestedName
     *            The name to verify.
     * @param volume
     *            The volume to check.
     */
    protected void checkForDuplicatSnapshotName(String requestedName, Volume volume) {
        BlockServiceUtils.checkForDuplicateArraySnapshotName(requestedName, volume.getId(), _dbClient);
    }

    /**
     * Prepares the snapshots for a snapshot request.
     *
     * @param volumes
     *            The volumes for which snapshots are to be created.
     * @param snapshotType
     *            The snapshot technology type.
     * @param snapshotName
     *            The snapshot name.
     * @param snapshotURIs
     *            [OUT] The URIs for the prepared snapshots.
     * @param taskId
     *            The unique task identifier
     *
     * @return The list of snapshots
     */
    @Override
    public List<BlockSnapshot> prepareSnapshots(List<Volume> volumes, String snapshotType,
            String snapshotName, List<URI> snapshotURIs, String taskId) {

        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        int count = 1;
        for (Volume volume : volumes) {
            // Attempt to create distinct labels here when creating >1 volumes (ScaleIO requirement)
            String rgName = volume.getReplicationGroupInstance();
            VolumeGroup application = volume.getApplication(_dbClient);
            if (volume.isVPlexVolume(_dbClient)) {
                Volume backendVol = VPlexUtil.getVPLEXBackendVolume(volumes.get(0), true, _dbClient);
                if (backendVol != null && !backendVol.getInactive()) {
                    rgName = backendVol.getReplicationGroupInstance();
                }
            }

            String label = snapshotName;
            if (NullColumnValueGetter.isNotNullValue(rgName) && application != null) {
                // There can be multiple RGs in a CG, in such cases generate unique name
                if (volumes.size() > 1) {
                    label = String.format("%s-%s-%s", snapshotName, rgName, count++);
                } else {
                    label = String.format("%s-%s", snapshotName, rgName);
                }
            } else if (volumes.size() > 1) {
                label = String.format("%s-%s", snapshotName, count++);
            }

            BlockSnapshot snapshot = prepareSnapshotFromVolume(volume, snapshotName, label);
            snapshot.setTechnologyType(snapshotType);
            snapshot.setOpStatus(new OpStatusMap());
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.CREATE_VOLUME_SNAPSHOT);
            snapshot.getOpStatus().createTaskStatus(taskId, op);
            snapshotURIs.add(snapshot.getId());
            snapshots.add(snapshot);
        }
        _dbClient.createObject(snapshots);

        return snapshots;
    }

    /**
     * Convenience method for when the snapshot label does not have to be unique.
     * In this case, we can use the label as the snapshot label.
     *
     * @param volume
     *            The volume for which the snapshot is being created.
     * @param label
     *            The label for the new snapshot
     * @return A reference to the new BlockSnapshot instance.
     */
    protected BlockSnapshot prepareSnapshotFromVolume(Volume volume, String label) {
        return prepareSnapshotFromVolume(volume, label, label);
    }

    /**
     * Creates and returns a new ViPR BlockSnapshot instance with the passed
     * name for the passed volume.
     *
     * @param volume
     *            The volume for which the snapshot is being created.
     * @param snapsetLabel
     *            The snapset label for grouping this snapshot
     * @param label
     *            The label for the new snapshot
     * @return A reference to the new BlockSnapshot instance.
     */
    protected BlockSnapshot prepareSnapshotFromVolume(Volume volume, String snapsetLabel, String label) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        URI cgUri = volume.getConsistencyGroup();
        if (cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }
        snapshot.setSourceNativeId(volume.getNativeId());
        snapshot.setParent(new NamedURI(volume.getId(), volume.getLabel()));
        snapshot.setLabel(label);
        snapshot.setStorageController(volume.getStorageController());
        snapshot.setSystemType(volume.getSystemType());
        snapshot.setVirtualArray(volume.getVirtualArray());
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(volume.getProtocol());
        snapshot.setProject(new NamedURI(volume.getProject().getURI(), volume.getProject().getName()));
        snapshot.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(
                snapsetLabel, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        return snapshot;
    }

    /**
     * Uses the appropriate controller to create the snapshots.
     *
     * @param reqVolume
     *            The volume from the snapshot request.
     * @param snapshotURIs
     *            The URIs of the prepared snapshots
     * @param snapshotType
     *            The snapshot technology type.
     * @param createInactive
     *            true if the snapshots should be created but not
     *            activated, false otherwise.
     * @param readOnly
     *            true if the snapshot should be read only, false otherwise
     * @param taskId
     *            The unique task identifier.
     */
    @Override
    public void createSnapshot(Volume reqVolume, List<URI> snapshotURIs,
            String snapshotType, Boolean createInactive, Boolean readOnly, String taskId) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, reqVolume.getStorageController());
        BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
        controller.createSnapshot(storageSystem.getId(), snapshotURIs, createInactive, readOnly, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshot(BlockSnapshot requestedSnapshot, List<BlockSnapshot> allSnapshots, String taskId, String deleteType) {
        if (VolumeDeleteTypeEnum.VIPR_ONLY.name().equals(deleteType)) {
            s_logger.info("Executing ViPR-only snapshot deletion");

            // Do any cleanup necessary for the ViPR only delete.
            cleanupForViPROnlySnapshotDelete(allSnapshots);

            // Mark them inactive.
            _dbClient.markForDeletion(allSnapshots);

            // Update the task status for each snapshot to successfully completed.
            // Note that we must go back to the database to get the latest snapshot status map.
            for (BlockSnapshot snapshot : allSnapshots) {
                BlockSnapshot updatedSnapshot = _dbClient.queryObject(BlockSnapshot.class, snapshot.getId());
                Operation op = updatedSnapshot.getOpStatus().get(taskId);
                op.ready("Snapshot succesfully deleted from ViPR");
                updatedSnapshot.getOpStatus().updateTaskStatus(taskId, op);
                _dbClient.updateObject(updatedSnapshot);
            }
        } else {
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, requestedSnapshot.getStorageController());
            BlockController controller = getController(BlockController.class, device.getSystemType());
            controller.deleteSnapshot(device.getId(), requestedSnapshot.getId(), taskId);
        }
    }

    /**
     * Perform any database clean up required as a result of removing the snapshots
     * with the passed URIs from the ViPR database.
     *
     * @param snapshots
     *            The snapshots to be cleaned up.
     */
    public void cleanupForViPROnlySnapshotDelete(List<BlockSnapshot> snapshots) {
        // Clean up the snapshot in the ViPR database.
        for (BlockSnapshot snapshot : snapshots) {
            URI snapshotURI = snapshot.getId();
            // If the BlockSnapshot instance represents a linked target, then
            // we need to remove the snapshot from the linked target list of the
            // session.
            URIQueryResultList snapSessionURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(snapshotURI),
                    snapSessionURIs);
            Iterator<URI> snapSessionURIsIter = snapSessionURIs.iterator();
            if (snapSessionURIsIter.hasNext()) {
                URI snapSessionURI = snapSessionURIsIter.next();
                s_logger.info("Snapshot {} is linked to snapshot session {}", snapshotURI, snapSessionURI);
                BlockSnapshotSession snapSession = _dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
                snapSession.getLinkedTargets().remove(snapshotURI.toString());
                _dbClient.updateObject(snapSession);
            }

            // Remove the snapshot from any export groups/masks.
            ExportUtils.cleanBlockObjectFromExports(snapshotURI, true, _dbClient);
        }
    }

    /**
     * Get the snapshots for the passed volume.
     *
     * @param volume
     *            A reference to a volume.
     *
     * @return The snapshots for the passed volume.
     */
    @Override
    public List<BlockSnapshot> getSnapshots(Volume volume) {
        URIQueryResultList snapshotURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                volume.getId()), snapshotURIs);
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
        return snapshots;
    }

    /**
     * Validates a restore snapshot request.
     *
     * @param snapshot
     *            The snapshot to restore.
     * @param parent
     *            The parent of the snapshot
     */
    @Override
    public void validateRestoreSnapshot(BlockSnapshot snapshot, Volume parent) {
        if (!snapshot.getIsSyncActive()) {
            throw APIException.badRequests.snapshotNotActivated(snapshot.getLabel());
        }

        List<URI> activeMirrorsForParent = getActiveMirrorsForVolume(parent);
        if (!activeMirrorsForParent.isEmpty()) {
            throw APIException.badRequests.snapshotParentHasActiveMirrors(parent.getLabel(), activeMirrorsForParent.size());
        }
        // Snap restore to V3 SRDF(Async) Target volume is not supported
        if (parent.isVmax3Volume(_dbClient) && Volume.isSRDFProtectedVolume(parent) && !parent.isSRDFSource()
                && RemoteDirectorGroup.SupportedCopyModes.ASYNCHRONOUS.name().equalsIgnoreCase(parent.getSrdfCopyMode())) {
            throw APIException.badRequests.snapshotRestoreNotSupported();
        }
    }

    /**
     * Validates a resynchronized snapshot request.
     *
     * @param snapshot
     *            The snapshot to be resynchronized.
     * @param parent
     *            The parent of the snapshot
     */
    @Override
    public void validateResynchronizeSnapshot(BlockSnapshot snapshot, Volume parent) {
        if (!snapshot.getIsSyncActive()) {
            throw APIException.badRequests.snapshotNotActivated(snapshot.getLabel());
        }

    }

    /**
     * Restore the passed parent volume from the passed snapshot of that parent volume.
     *
     * @param snapshot
     *            The snapshot to restore
     * @param parentVolume
     *            The volume to be restored.
     * @param taskId
     *            The unique task identifier.
     */
    @Override
    public void restoreSnapshot(BlockSnapshot snapshot, Volume parentVolume, String syncDirection, String taskId) {
        BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
        controller.restoreVolume(snapshot.getStorageController(), parentVolume.getPool(),
                parentVolume.getId(), snapshot.getId(), syncDirection, taskId);
    }

    /**
     * Resynchronize the passed snapshot from its parent volume.
     *
     * @param snapshot
     *            The snapshot to be resynchronized
     * @param parentVolume
     *            The volume to be resynchronized from.
     * @param taskId
     *            The unique task identifier.
     */
    @Override
    public void resynchronizeSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, snapshot.getStorageController());
        BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
        controller.resyncSnapshot(storageSystem.getId(), parentVolume.getId(), snapshot.getId(), Boolean.TRUE, taskId);
    }

    /**
     * validate the given volume label is not a duplicate within given project. If so, throw exception
     *
     * @param label
     *            - label to validate
     * @param project
     *            - project where label is being validate.
     */
    protected void validateVolumeLabel(String label, Project project) {
        List<Volume> volumeList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "project", project.getId(), label));
        if (!volumeList.isEmpty()) {
            throw APIException.badRequests.duplicateLabel(label);
        }
    }

    /**
     * Generate a unique volume label based on the given base name and index.
     *
     * @param baseVolumeLabel
     *            - prefix of volume name
     * @param volumeIndex
     *            - index to append to prefix for name (The first volume should send down zero)
     * @param volumeCount
     *            - number of volume to generate name for
     * @return generated volume name
     */
    public static String generateDefaultVolumeLabel(String baseVolumeLabel, int volumeIndex, int volumeCount) {
        StringBuilder volumeLabelBuilder = new StringBuilder(baseVolumeLabel);
        if (volumeCount > 1) {
            volumeLabelBuilder.append("-");
            volumeLabelBuilder.append(volumeIndex + 1);
        }
        return volumeLabelBuilder.toString();
    }

    /**
     * Convenient method to validate whether default generate volume names for RP and SRDF will result in duplicate.
     * If there is a duplicate, throw exception
     *
     * @param baseVolumeLabel
     *            - prefix of volume name
     * @param volumeCount
     *            - number of volume to generate name for
     * @param project
     *            - project containing the volumes
     */
    protected void validateDefaultVolumeLabels(String baseVolumeLabel, int volumeCount, Project project) {
        for (int i = 0; i < volumeCount; i++) {
            // Each volume has a unique label based off the passed value.
            // Note that the way the storage system creates the actual
            // volumes in a multi volume request, the names given the
            // Bourne volumes here likely will not match the names given
            // by the storage system. As such we will need to update the
            // actual volumes after they are created to match the names
            // given here.
            String newVolumeLabel = generateDefaultVolumeLabel(baseVolumeLabel, i, volumeCount);

            // to throw exception if duplicate label found
            validateVolumeLabel(newVolumeLabel, project);
        }
    }

    /**
     * Note: This method is also used for VPLEX volume validation during mirror creation.
     * Any Changes to this method should account for VPLEX as well.
     */
    protected void validateNotAConsistencyGroupVolume(Volume sourceVolume, VirtualPool vPool) {
        if (vPool != null && vPool.getMultivolumeConsistency() != null && vPool.getMultivolumeConsistency()) {
            if (sourceVolume != null && !NullColumnValueGetter.isNullURI(sourceVolume.getConsistencyGroup())) {
                throw APIException.badRequests.cannotCreateProtectionForConsistencyGroup();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxVolumesForConsistencyGroup(BlockConsistencyGroup consistencyGroup) {
        return Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateConsistencyGroupName(BlockConsistencyGroup consistencyGroup, Collection<String> requestedTypes) {
        // No-OP by default.
    }

    /**
     * Return a list of active BlockMirror URI's that are known to be active (in Synchronized state).
     *
     * @param volume
     *            Volume to check for mirrors against
     * @return List of active BlockMirror URI's
     */
    protected List<URI> getActiveMirrorsForVolume(Volume volume) {
        return BlockServiceUtils.getActiveMirrorsForVolume(volume, _dbClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyRemoveVolumeFromCG(Volume volume, List<Volume> cgVolumes) {
        // Make sure the volume is in the consistency group.
        List<URI> cgVolumeURIs = new ArrayList<URI>();
        for (Volume cgVolume : cgVolumes) {
            cgVolumeURIs.add(cgVolume.getId());
        }

        URI volumeURI = volume.getId();
        if (!cgVolumeURIs.contains(volumeURI)) {
            throw APIException.badRequests
                    .invalidParameterConsistencyGroupVolumeMismatch(volumeURI);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyAddVolumeToCG(Volume volume, BlockConsistencyGroup cg,
            List<Volume> cgVolumes, StorageSystem cgStorageSystem) {
        // Verify that the Virtual Pool for the volume specifies
        // consistency.
        URI volumeURI = volume.getId();
        VirtualPool vPool = _permissionsHelper.getObjectById(volume.getVirtualPool(),
                VirtualPool.class);
        if (vPool.getMultivolumeConsistency() == null
                || !vPool.getMultivolumeConsistency()) {
            throw APIException.badRequests
                    .invalidParameterConsistencyGroupVirtualPoolMustSpecifyMultiVolumeConsistency(volumeURI);
        }

        // Validate the volume is not in this CG or any other CG.
        /*
         * List<URI> cgVolumeURIs = new ArrayList<URI>();
         * for (Volume cgVolume : cgVolumes) {
         * cgVolumeURIs.add(cgVolume.getId());
         * }
         * if (cgVolumeURIs.contains(volumeURI)) {
         * throw APIException.badRequests
         * .invalidParameterConsistencyGroupAlreadyContainsVolume(volumeURI);
         * } else if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
         * }
         */
        // Validate the volume is not in any other CG.
        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())
                && !cg.getId().equals(volume.getConsistencyGroup())) {
            throw APIException.badRequests
                    .invalidParameterVolumeAlreadyInAConsistencyGroup(cg.getId(),
                            volume.getConsistencyGroup());
        }

        // Verify the project for the volumes to be added is the same
        // as the project for the consistency group.
        BlockConsistencyGroupUtils.verifyProjectForVolumeToBeAddedToCG(volume, cg,
                _dbClient);

        // Verify that the volume is on the storage system for
        // the consistency group.
        verifySystemForVolumeToBeAddedToCG(volume, cg, cgStorageSystem);

        // Don't allow partially ingested volume to be added to CG.
        BlockServiceUtils.validateNotAnInternalBlockObject(volume, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyReplicaCount(List<Volume> volumes, List<Volume> cgVolumes, boolean volsAlreadyInCG) {
        /*
         * For VMAX, volumes to be added can have replicas only if
         * 1. CG has no existing volumes, and
         * 2. SMI-S 8.x
         * For other arrays, or VMAX (CG has existing volumes, or non SMI-S 8.x), volumes to be added cannot have
         * replicas
         */
        boolean isReplicaAllowed = false;
        if ((volsAlreadyInCG || cgVolumes.isEmpty()) && ControllerUtils.isVmaxVolumeUsing803SMIS(volumes.get(0), _dbClient)
                || ControllerUtils.isVnxVolume(volumes.get(0), _dbClient)) {
            isReplicaAllowed = true;
        }

        int knownSnapCount = -1;
        int knownCloneCount = -1;
        int knownMirrorCount = -1;
        for (Volume volume : volumes) {
            // snapshot check
            URIQueryResultList list = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume.getId()),
                    list);
            Iterator<URI> it = list.iterator();
            int snapCount = 0;
            while (it.hasNext()) {
                it.next();
                snapCount++;
            }

            if (!isReplicaAllowed && snapCount > 0) {
                throw APIException.badRequests
                        .volumesWithReplicaCannotBeAdded(volume.getLabel(), "snapshots");
            }

            // Don't allow volume with multiple replicas
            // Currently we do not have a way to group replicas based on their time stamp
            // and put them into different groups on array.
            if (snapCount > 1) {
                throw APIException.badRequests
                        .volumesWithMultipleReplicasCannotBeAddedToConsistencyGroup(volume.getLabel(), "snapshots");
            }

            // Volume should have same number of replicas with other volumes
            if (knownSnapCount != -1 && snapCount != knownSnapCount) {
                throw APIException.badRequests
                        .volumeWithDifferentNumberOfReplicasCannotBeAdded(volume.getLabel(), "snapshots");
            }

            // clone check
            StringSet fullCopies = volume.getFullCopies();
            int cloneCount = fullCopies == null ? 0 : fullCopies.size();

            if (!isReplicaAllowed && cloneCount > 0) {
                throw APIException.badRequests
                        .volumesWithReplicaCannotBeAdded(volume.getLabel(), "full copies");
            }

            if (cloneCount > 1) {
                throw APIException.badRequests
                        .volumesWithMultipleReplicasCannotBeAddedToConsistencyGroup(volume.getLabel(), "full copies");
            }

            if (knownCloneCount != -1 && cloneCount != knownCloneCount) {
                throw APIException.badRequests
                        .volumeWithDifferentNumberOfReplicasCannotBeAdded(volume.getLabel(), "full copies");
            }

            // mirror check
            StringSet mirrors = volume.getMirrors();
            int mirrorCount = mirrors == null ? 0 : mirrors.size();

            if (!isReplicaAllowed && mirrorCount > 0) {
                throw APIException.badRequests
                        .volumesWithReplicaCannotBeAdded(volume.getLabel(), "mirrors");
            }

            if (mirrorCount > 1) {
                throw APIException.badRequests
                        .volumesWithMultipleReplicasCannotBeAddedToConsistencyGroup(volume.getLabel(), "mirrors");
            }

            if (knownMirrorCount != -1 && mirrorCount != knownMirrorCount) {
                throw APIException.badRequests
                        .volumeWithDifferentNumberOfReplicasCannotBeAdded(volume.getLabel(), "mirrors");
            }

            knownSnapCount = snapCount;
            knownCloneCount = cloneCount;
            knownMirrorCount = mirrorCount;
        }
    }

    /**
     * Verifies the system information for the volume to be added to the CG.
     *
     * @param volume
     *            A reference to the volume
     * @param cg
     *            A reference to the CG
     * @param cgStorageSystem
     *            A reference to the CG storage system
     */
    protected void verifySystemForVolumeToBeAddedToCG(Volume volume,
            BlockConsistencyGroup cg, StorageSystem cgStorageSystem) {
        // Verify that the volume is on the storage system for
        // the consistency group.
        URI volumeSystemURI = volume.getStorageController();
        if (!volumeSystemURI.equals(cgStorageSystem.getId())) {
            throw APIException.badRequests
                    .invalidParameterConsistencyGroupStorageySystemMismatch(volume.getId());
        }
    }

    /**
     * Verify if a volume belongs to a VMAX3 Storage array
     *
     * @param volume
     *            [in] - Volume object to check
     * @return true iff volume's StorageSystem is VMAX3
     */
    private boolean isVMAX3Volume(Volume volume) {
        boolean isVMAX3 = false;
        if (URIUtil.isType(volume.getStorageController(), StorageSystem.class)) {
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
            isVMAX3 = (storage != null && storage.checkIfVmax3());
        }
        return isVMAX3;
    }

    /**
     * Debug logging method to help assure that pre-created volumes during volume creation are honored/used
     * when the placement thread assembles recommendation objects and prepares volumes during APISVC volume
     * creation steps.
     *
     * @param volumesDescriptors
     *            volume descriptors
     * @param task
     *            task id
     */
    protected void logVolumeDescriptorPrecreateInfo(List<VolumeDescriptor> volumesDescriptors, String task) {
        // Look for high-order source device first, which would be RP_VPLEX_VIRT_SOURCE or RP_SOURCE
        List<VolumeDescriptor> volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_SOURCE,
                        VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE },
                new VolumeDescriptor.Type[] {});

        // If there are none of those, look for VPLEX_VIRT_VOLUME
        if (volumes.isEmpty()) {
            volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                    new VolumeDescriptor.Type[] {});
        }

        // Finally look for SRDF or regular block volumes
        if (volumes.isEmpty()) {
            volumes = VolumeDescriptor.filterByType(volumesDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA,
                            VolumeDescriptor.Type.SRDF_SOURCE },
                    new VolumeDescriptor.Type[] {});
        }

        // If no volumes to be created, just return.
        if (volumes.isEmpty()) {
            return;
        }

        for (VolumeDescriptor desc : volumes) {
            s_logger.info(String.format("Volume and Task Pre-creation Objects [Exec]--  Source Volume: %s, Op: %s",
                    desc.getVolumeURI(), task));
        }
    }

    @Override
    public List<VolumeDescriptor> createVolumesAndDescriptors(List<VolumeDescriptor> descriptors, String name,
            Long size, Project project, VirtualArray varray, VirtualPool vpool, URI performancePolicyURI, 
            Map<URI, Map<VolumeTopologyRole, URI>> copyPerformancePolicies, List<Recommendation> recommendations,
            TaskList taskList, String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) {
        BlockServiceApi api = null;
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (Recommendation recommendation : recommendations) {
            if (recommendation instanceof SRDFRecommendation
                    || recommendation instanceof SRDFCopyRecommendation) {
                api = BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());
            } else if (recommendation instanceof VolumeRecommendation) {
                api = BlockService.getBlockServiceImpl(BlockServiceApi.DEFAULT);
            } else {
                String message = String.format("No BlockServiceApiImpl to handle recommendation of class: ",
                        recommendation.getClass().getName());
                s_logger.error(message);
                throw WorkflowException.exceptions.workflowConstructionError(message);
            }
            volumeDescriptors.addAll(api.createVolumesAndDescriptors(
                    descriptors, name, size, project, varray, vpool, performancePolicyURI, copyPerformancePolicies,
                    recommendations, taskList, task, vpoolCapabilities));
        }
        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateVolumesInVolumeGroup(VolumeGroupVolumeList addVolumes,
            List<Volume> removeVolumes,
            URI applicationId, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * Determines if there are any associated resources that are indirectly affected by this volume operation. If
     * there are we should add them to the Task object.
     *
     * @param volume
     *            The volume the operation is being performed on
     * @param vpool
     *            The vpool needed for extra information on whether to add associated resources
     * @return A list of any associated resources, null otherwise
     */
    protected List<? extends DataObject> getTaskAssociatedResources(Volume volume, VirtualPool vpool) {
        List<? extends DataObject> associatedResources = null;

        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

        // If RP protection has been specified, we want to further determine what type of virtual pool
        // change has been made.
        if (VirtualPool.vPoolSpecifiesProtection(currentVpool) && VirtualPool.vPoolSpecifiesProtection(vpool)
                && !NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
            // Get all RP Source volumes in the CG
            List<Volume> allRPSourceVolumesInCG = RPHelper.getCgSourceVolumes(volume.getConsistencyGroup(), _dbClient);
            // Remove the volume in question from the associated list, don't want a double entry because the
            // volume passed in will already be counted as an associated resource for the Task.
            allRPSourceVolumesInCG.remove(volume.getId());

            if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                    && !VirtualPool.vPoolSpecifiesMetroPoint(currentVpool)
                    && VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
                // For an upgrade to MetroPoint (moving from RP+VPLEX vpool to MetroPoint vpool), even though the user
                // would have chosen 1 volume to update but we need to update ALL the RSets/volumes in the CG.
                // We can't just update one RSet/volume.
                s_logger.info("VirtualPool change for to upgrade to MetroPoint.");
                return allRPSourceVolumesInCG;
            }

            // Determine if the copy mode setting has changed. For this type of change, all source volumes
            // in the consistency group are affected.
            String currentCopyMode = NullColumnValueGetter.isNullValue(currentVpool.getRpCopyMode()) ?
                    "" : currentVpool.getRpCopyMode();
            String newCopyMode = NullColumnValueGetter.isNullValue(vpool.getRpCopyMode()) ?
                    "" : vpool.getRpCopyMode();

            if (!newCopyMode.equals(currentCopyMode)) {
                // The copy mode setting has changed.
                s_logger.info(String.format("VirtualPool change to update copy from %s to %s.", currentCopyMode, newCopyMode));
                return allRPSourceVolumesInCG;
            }
        }

        return associatedResources;
    }

    /**
     * Create a task list for the volumes sent in using the operation CHANGE_BLOCK_VOLUME_VPOOL.
     *
     * @param vPool
     *            virtual pool
     * @param volumes
     *            volumes
     * @param taskId
     *            task ID
     * @return a task list
     */
    protected TaskList createTasksForVolumes(VirtualPool vPool, List<Volume> volumes, String taskId) {

        TaskList taskList = new TaskList();
        if (volumes == null) {
            s_logger.info("No volumes were presented to create task objects.  This is a fatal error");
            if (vPool != null && vPool.getLabel() != null) {
                throw APIException.badRequests.noVolumesForTaskObjects(vPool.getLabel(), taskId);
            }
            throw APIException.badRequests.noVolumesForTaskObjects("None Specified", taskId);
        }

        for (Volume volume : volumes) {
            // Associated resources are any resources that are indirectly affected by this
            // volume's virtual pool change. The user should be notified if there are any.
            List<? extends DataObject> associatedResources = getTaskAssociatedResources(volume, vPool);
            List<URI> associatedResourcesURIs = new ArrayList<URI>();
            if (associatedResources != null
                    && !associatedResources.isEmpty()) {
                for (DataObject obj : associatedResources) {
                    associatedResourcesURIs.add(obj.getId());
                }
            }

            // New operation
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VPOOL);
            op.setDescription("Change vpool operation");
            if (!associatedResourcesURIs.isEmpty()) {
                op.setAssociatedResourcesField(Joiner.on(',').join(associatedResourcesURIs));
            }
            op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId, op);

            TaskResourceRep volumeTask = null;
            if (associatedResources != null) {
                // We need the task to reflect that there are associated resources affected by this operation.
                volumeTask = TaskMapper.toTask(volume, associatedResources, taskId, op);
            } else {
                volumeTask = TaskMapper.toTask(volume, taskId, op);
            }
            taskList.getTaskList().add(volumeTask);
        }

        return taskList;
    }

    /**
     * Create a task for the replication group and volumes sent in using operation CHANGE_BLOCK_VOLUME_VPOOL.
     * Since replication groups are not a primary resource, we cannot associate a Task to a replication
     * group. We get around this by associating the task to the first volume in the list, and the other
     * volumes are associated resources. This is not an ideal approach, so we will pursue a better approach
     * when we add support for retry/rollback as part of COP-22431 and its subtasks.
     *
     * Also see bugfix-feature-COP-22215-filter-tasks-with-same-workflow for an alternative approach.
     *
     * @param vPool
     *            virtual pool
     * @param volumes
     *            volumes
     * @param taskId
     *            task ID
     * @return a task list
     */
    protected TaskResourceRep createTaskForRG(VirtualPool vPool, List<Volume> volumes, String taskId) {
        if (volumes == null || volumes.isEmpty()) {
            s_logger.info("No volumes were presented to create task objects.  This is a fatal error");
            if (vPool != null && vPool.getLabel() != null) {
                throw APIException.badRequests.noVolumesForTaskObjects(vPool.getLabel(), taskId);
            }
            throw APIException.badRequests.noVolumesForTaskObjects("None Specified", taskId);
        }

        // Sort the based on label for deterministic primary resource
        // It's OK if there are duplicate labels.
        List<String> labelsToSort = new ArrayList<>();
        Map<String, Volume> labelToVolumeMap = new HashMap<String, Volume>();
        for (Volume volume : volumes) {
            labelsToSort.add(volume.getLabel());
            labelToVolumeMap.put(volume.getLabel(), volume);
        }
        Collections.sort(labelsToSort);

        // Grab the primary volume
        Volume primaryVolume = labelToVolumeMap.get(labelsToSort.get(0));

        // Do not include the primary volume in the associated resources
        volumes.remove(primaryVolume);
        List<? extends DataObject> associatedResources = new ArrayList<>();
        List<URI> associatedResourcesURIs = new ArrayList<URI>();
        for (Volume volume : volumes) {
            associatedResourcesURIs.add(volume.getId());
        }

        // New operation
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VPOOL);
        op.setDescription("Change vpool operation");
        if (!associatedResourcesURIs.isEmpty()) {
            op.setAssociatedResourcesField(Joiner.on(',').join(associatedResourcesURIs));
        }
        op = _dbClient.createTaskOpStatus(Volume.class, primaryVolume.getId(), taskId, op);

        TaskResourceRep cgTask = null;
        if (!associatedResources.isEmpty()) {
            // We need the task to reflect that there are associated resources affected by this operation.
            cgTask = TaskMapper.toTask(primaryVolume, associatedResources, taskId, op);
        } else {
            cgTask = TaskMapper.toTask(primaryVolume, taskId, op);
        }

        return cgTask;
    }

    /**
     * Transfer the contents of the source volume's mounted FS tags to the destination (target) 
     * volume so it can be protected against illegal operations.
     * 
     * @param srcVolume source volume
     * @param volume volume
     */
    protected static void transferMountedContentTags(Volume srcVolume, Volume volume) {
        if (volume == null) {
            return;
        }
        
        if (srcVolume == null || srcVolume.getTag() == null) {
            return;
        }
        
        if (volume.getTag() == null) {
            volume.setTag(new ScopedLabelSet());
        }
        
        for (ScopedLabel tag : srcVolume.getTag()) {
            if (TagUtils.isMountContentTag(tag)) {
                volume.getTag().add(tag);
            }
        }        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePerformancePoliciesForVolumeCreate(VirtualPool vpool, VolumeCreatePerformancePolicies performancePolicies) {
    }    

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePerformancePoliciesForMirrorCreate(VirtualPool sourceVolumeVpool, BlockPerformancePolicyMap performancePoliciesMap) {
    }
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.toVirtualPoolChangeRep;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.model.SynchronizationState.FRACTURED;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.transform;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
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
     * @param protectionType -- Should be null for regular Block implementation,
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
     * Check if a resource can be deactivated safely
     * 
     * @return detail type of the dependency if exist, null otherwise
     * @throws InternalException
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object) throws InternalException {
        String depMsg = getDependencyChecker().checkDependencies(object.getId(), object.getClass(), true);
        if (depMsg != null) {
            return depMsg;
        }
        return object.canBeDeleted();
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    // Default unsupported operations

    /**
     * {@inheritDoc}
     * 
     * @throws ControllerException
     */
    @Override
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, VirtualPool sourceVpool,
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
     * @throws ControllerException 
     */
    @Override
    public TaskList deactivateMirror(StorageSystem storageSystem, URI mirrorURI, String task) throws ControllerException {
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
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray varray,
            VirtualPool vpool, List<Recommendation> recommendations, TaskList taskList,
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
            for (URI volumeURI : volumeURIs) {
                BlockObject bo = BlockObject.fetch(_dbClient, volumeURI);
                if (bo instanceof Volume) {
                    Volume volume = (Volume) bo;
                    if (!volume.checkForRp() && volume.isVolumeExported(_dbClient)) {
                        // Check to see if the volume is exported to a host in the non-RP case.
                        throw APIException.badRequests.inventoryDeleteNotSupportedonExportedVolumes(volume.getNativeGuid());
                    } else if (volume.checkForRp() && volume.isVolumeExportedNonRP(_dbClient)) {
                        // Check to see if the volume is exported to anything other than RP.
                        throw APIException.badRequests.inventoryDeleteNotSupportedonExportedVolumes(volume.getNativeGuid());
                    }
                } else if (bo instanceof BlockSnapshot) {
                    throw APIException.badRequests.inventoryDeleteNotSupportedOnSnapshots(bo.getNativeGuid());
                }

            }
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
     * @param systemURI The URI of the system on which the volumes reside.
     * @param volumeURIs The URIs of the volumes to be deleted.
     * 
     * @return The list of volume descriptors.
     */
    abstract protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(
            URI systemURI, List<URI> volumeURIs, String deletionType);

    /**
     * Get the volume descriptors for all volumes to be deleted given the passed
     * volumes.
     * 
     * @param volumeDescriptors The descriptors for all volumes involved in the
     *            ViPR only delete
     */
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
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
     * @param volume A reference to the volume.
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
        while (vpoolIter.hasNext()) {
            StringBuffer notAllowedReason = new StringBuffer();
            VirtualPool targetVpool = vpoolIter.next();
            List<VirtualPoolChangeOperationEnum> allowedOperations = getVirtualPoolChangeAllowedOperationsForVolume(
                    volume, currentVpool, targetVpool, notAllowedReason);

            StringBuffer logMsg = new StringBuffer();
            logMsg.append("Vpool [" + targetVpool.getLabel() + "]");
            logMsg.append((notAllowedReason.length() > 0) ? " not allowed: " + notAllowedReason.toString() : " allowed but only for: ");
            logMsg.append((allowedOperations != null && !allowedOperations.isEmpty()) ? Joiner.on("\t").join(allowedOperations) : "");
            s_logger.info(logMsg.toString());

            vpoolChangeList.getVirtualPools().add(
                    toVirtualPoolChangeRep(targetVpool, allowedOperations,
                            notAllowedReason.toString()));
        }

        return vpoolChangeList;
    }

    /**
     * Get all potential vpools for the passed volume, based strictly on
     * connectivity of the volume's storage system.
     * 
     * @param volume A reference to a Volume.
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
        	s_logger.info("Volume Storage System is a VPLEX, setting port type to backend for storage systems network association check.");
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
     * @param volume A reference to the Volume.
     * @param currentVpool A reference to the current vpool for the volume.
     * @param newVpool A reference to the new vpool
     * @param notSuppReasonBuff - reason if no change operation was allowed.
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
            // Reason: Current 'vPool change' design executes the first satisfying operation irrespective of what user chooses in the UI.
            // Also when a Policy change can be performed by AUTO_TIERING_POLICY operation, why would the same needs VPLEX_DATA_MIGRATION?
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
     * the disallowed reason should be referenced in <code>notSuppReasonBuff</code> This method should be implemented in subclass get
     * operation based on specific
     * volume type.
     * 
     * @param volume A reference to the Volume.
     * @param currentVpool A reference to the current vpool for the volume.
     * @param newVpool A reference to the new vpool.
     * @param notSuppReasonBuff - reason if no change operation was allowed.
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
    public void changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        List<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void changeVolumeVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        /**
         * 'Auto-tiering policy change' operation supports multiple volume processing.
         * At present, other operations only support single volume processing.
         */
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
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
     * @param descriptors List<VolumeDescriptor>
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
     * @param dbClient - Static method needs DbClient
     * @param varrayUID - UID of the varray to find the connectivity for
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

        long maxVolumeSize = getMaxVolumeSizeLimit(volume);
        if (newSizeExceedsMaxVolumeSizeForPool(volume, newSize)) {
            throw APIException.badRequests.invalidVolumeSize(newSize, maxVolumeSize);
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
     * @param volume A reference to a Volume.
     * 
     * @return true if the volume is a meta volume and has attached mirrors,
     *         false otherwise.
     */
    protected boolean isMetaVolumeWithMirrors(Volume volume) {
        return ((isMeta(volume)) && (hasMirrors(volume)));
    }

    /**
     * Determines if the new size for a volume expansion exceeds the maximum volume
     * size for the volume's storage pool.
     * 
     * @param volume A reference to a Volume.
     * @param newSize The desired volume size.
     * 
     * @return true if the volume exceeds the max volume size, false otherwise.
     */
    protected boolean newSizeExceedsMaxVolumeSizeForPool(Volume volume, Long newSize) {
        StoragePool storagePool = _permissionsHelper.getObjectById(volume.getPool(),
                StoragePool.class);

        // Only applicable for Clariion unified storage pools.

        if (StoragePool.PoolClassNames.Clar_UnifiedStoragePool.name().equalsIgnoreCase(
                storagePool.getPoolClassName())) {
            Long maxVolumeSizeLimit = getMaxVolumeSizeLimit(volume);
            Long sizeInKB = (newSize % 1024 == 0) ? newSize / 1024 : newSize / 1024 + 1;
            return sizeInKB > maxVolumeSizeLimit;
        }

        return false;
    }

    /**
     * Get the maximum volume size for the passed volume's storage pool.
     * 
     * @param volume A reference to a volume.
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
     * @param volume A reference to a volume.
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
                && (hasMirrors(volume));
    }

    /**
     * Determines whether passed volume is HDS thin volume or not.
     * 
     * @param volume A reference to a volume
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
     * @param volume A reference to a Volume.
     * 
     * @return true if passed volume has attached mirrors, false otherwise.
     */
    protected boolean hasMirrors(Volume volume) {
        return volume.getMirrors() != null && !volume.getMirrors().isEmpty();
    }

    /**
     * Determines if the passed volume is a meta volume.
     * 
     * @param volume A reference to a Volume.
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
            String snapshotType, String snapshotName, BlockFullCopyManager fcManager) {
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
            if (getNumNativeSnapshots(volumeToSnap) >= vpool.getMaxNativeSnapshots()) {
                throw APIException.methodNotAllowed.maximumNumberSnapshotsReached();
            }

            // Check for duplicate name.
            checkForDuplicatSnapshotName(snapshotName, volumeToSnap);
        }
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, reqVolume.getStorageController());
        Boolean is8xProvider = system.getUsingSmis80();

        // We should validate this for 4.x provider as it doesn't support snaps for SRDF meta volumes.
        if (!is8xProvider) {
            // Check that if the volume is a member of vmax consistency group all volumes in the group are regular volumes, not meta
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
     * @param volume A reference to a volume.
     * 
     * @return The number of snapshots on a volume.
     */
    protected Integer getNumNativeSnapshots(Volume volume) {
        Integer numSnapshots = 0;
        URI volumeURI = volume.getId();
        URIQueryResultList snapshotURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                volumeURI), snapshotURIs);
        while (snapshotURIs.iterator().hasNext()) {
            URI snapshotURI = snapshotURIs.iterator().next();
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotURI);
            if (snapshot != null && !snapshot.getInactive()
                    && snapshot.getTechnologyType().equals(TechnologyType.NATIVE.toString())) {
                numSnapshots++;
            }
        }
        return numSnapshots;
    }

    /**
     * Check if a snapshot with the same name exists for the passed volume.
     * Note that we need to compare the passed name to the snapset label for
     * the volumes snapshots because the actually snapshot names can have
     * an appended suffix when the volumes is in a CG with multiple volumes.
     * Also, we need to run the name through the generator, which is done
     * prior to setting the snapset label for a snapshot.
     * 
     * @param requestedName The name to verify.
     * @param volume The volume to check.
     */
    protected void checkForDuplicatSnapshotName(String requestedName, Volume volume) {
        String snapsetLabel = ResourceOnlyNameGenerator.removeSpecialCharsForName(
                requestedName, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        List<BlockSnapshot> volumeSnapshots = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, BlockSnapshot.class,
                        ContainmentConstraint.Factory.getVolumeSnapshotConstraint(volume.getId()));
        for (BlockSnapshot snapshot : volumeSnapshots) {
            if (snapsetLabel.equals(snapshot.getSnapsetLabel())) {
                throw APIException.badRequests.duplicateLabel(requestedName);
            }
        }
    }

    /**
     * Prepares the snapshots for a snapshot request.
     * 
     * @param volumes The volumes for which snapshots are to be created.
     * @param snapshotType The snapshot technology type.
     * @param snapshotName The snapshot name.
     * @param snapshotURIs [OUT] The URIs for the prepared snapshots.
     * @param taskId The unique task identifier
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
            String label = snapshotName;
            if (volumes.size() > 1) {
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
     * @param volume The volume for which the snapshot is being created.
     * @param label The label for the new snapshot
     * @return A reference to the new BlockSnapshot instance.
     */
    protected BlockSnapshot prepareSnapshotFromVolume(Volume volume, String label) {
        return prepareSnapshotFromVolume(volume, label, label);
    }

    /**
     * Creates and returns a new ViPR BlockSnapshot instance with the passed
     * name for the passed volume.
     * 
     * @param volume The volume for which the snapshot is being created.
     * @param snapsetLabel The snapset label for grouping this snapshot
     * @param label The label for the new snapshot
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
     * @param reqVolume The volume from the snapshot request.
     * @param snapshotURIs The URIs of the prepared snapshots
     * @param snapshotType The snapshot technology type.
     * @param createInactive true if the snapshots should be created but not
     *            activated, false otherwise.
     * @param readOnly true if the snapshot should be read only, false otherwise
     * @param taskId The unique task identifier.
     */
    @Override
    public void createSnapshot(Volume reqVolume, List<URI> snapshotURIs,
            String snapshotType, Boolean createInactive, Boolean readOnly, String taskId) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, reqVolume.getStorageController());
        BlockController controller = getController(BlockController.class, storageSystem.getSystemType());
        controller.createSnapshot(storageSystem.getId(), snapshotURIs, createInactive, readOnly, taskId);
    }

    /**
     * Uses the appropriate controller to delete the snapshot.
     * 
     * @param snapshot The snapshot to delete
     * @param taskId The unique task identifier
     */
    @Override
    public void deleteSnapshot(BlockSnapshot snapshot, String taskId) {
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, snapshot.getStorageController());
        BlockController controller = getController(BlockController.class, device.getSystemType());
        controller.deleteSnapshot(device.getId(), snapshot.getId(), taskId);
    }

    /**
     * Get the snapshots for the passed volume.
     * 
     * @param volume A reference to a volume.
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
     * @param snapshot The snapshot to restore.
     * @param parent The parent of the snapshot
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
    }

    /**
     * Validates a resynchronized snapshot request.
     * 
     * @param snapshot The snapshot to be resynchronized.
     * @param parent The parent of the snapshot
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
     * @param snapshot The snapshot to restore
     * @param parentVolume The volume to be restored.
     * @param taskId The unique task identifier.
     */
    @Override
    public void restoreSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId) {
        BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
        controller.restoreVolume(snapshot.getStorageController(), parentVolume.getPool(),
                parentVolume.getId(), snapshot.getId(), taskId);
    }

    /**
     * Resynchronize the passed snapshot from its parent volume.
     * 
     * @param snapshot The snapshot to be resynchronized
     * @param parentVolume The volume to be resynchronized from.
     * @param taskId The unique task identifier.
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
     * @param label - label to validate
     * @param project - project where label is being validate.
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
     * @param baseVolumeLabel - prefix of volume name
     * @param volumeIndex - index to append to prefix for name (The first volume should send down zero)
     * @param volumeCount - number of volume to generate name for
     * @return generated volume name
     */
    public static String generateDefaultVolumeLabel(String baseVolumeLabel, int volumeIndex, int volumeCount) {
        StringBuilder volumeLabelBuilder = new StringBuilder(baseVolumeLabel);
        if (volumeCount > 1) {
            volumeLabelBuilder.append("-");
            volumeLabelBuilder.append(volumeIndex+1);
        }
        return volumeLabelBuilder.toString();
    }

    /**
     * Convenient method to validate whether default generate volume names for RP and SRDF will result in duplicate.
     * If there is a duplicate, throw exception
     * 
     * @param baseVolumeLabel - prefix of volume name
     * @param volumeCount - number of volume to generate name for
     * @param project - project containing the volumes
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
     * For ViPR-only delete volume operations, we use this method to remove the
     * volume from the export group and export masks associated with the volume.
     * 
     * @param volumeURI volume to remove from export masks
     * @param addToExistingVolumes When true, adds the volume to the existing volumes
     *            list from the mask.
     */
    protected void cleanVolumeFromExports(URI volumeURI, boolean addToExistingVolumes) {

        Map<URI, ExportGroup> exportGroupMap = new HashMap<URI, ExportGroup>();
        Map<URI, ExportGroup> updatedExportGroupMap = new HashMap<URI, ExportGroup>();
        Map<String, ExportMask> exportMaskMap = new HashMap<String, ExportMask>();
        Map<String, ExportMask> updatedExportMaskMap = new HashMap<String, ExportMask>();
        Volume assocVolume = _dbClient.queryObject(Volume.class, volumeURI);
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getBlockObjectExportGroupConstraint(assocVolume.getId()), exportGroupURIs);
        for (URI exportGroupURI : exportGroupURIs) {
            ExportGroup exportGroup = null;
            if (exportGroupMap.containsKey(exportGroupURI)) {
                exportGroup = exportGroupMap.get(exportGroupURI);
            } else {
                exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
                exportGroupMap.put(exportGroupURI, exportGroup);
            }

            if (exportGroup.hasBlockObject(volumeURI)) {
                exportGroup.removeVolume(volumeURI);
                if (!updatedExportGroupMap.containsKey(exportGroupURI)) {
                    updatedExportGroupMap.put(exportGroupURI, exportGroup);
                }
            }

            StringSet exportMaskIds = exportGroup.getExportMasks();
            for (String exportMaskId : exportMaskIds) {
                ExportMask exportMask = null;
                if (exportMaskMap.containsKey(exportMaskId)) {
                    exportMask = exportMaskMap.get(exportMaskId);
                } else {
                    exportMask = _dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
                    exportMaskMap.put(exportMaskId, exportMask);
                }
                if (exportMask.hasVolume(volumeURI)) {
                    StringMap exportMaskVolumeMap = exportMask.getVolumes();
                    String hluStr = exportMaskVolumeMap.get(volumeURI.toASCIIString());
                    exportMask.removeVolume(volumeURI);
                    exportMask.removeFromUserCreatedVolumes(assocVolume);
                    // Add this volume to the existing volumes map for the
                    // mask, so that if the last ViPR created volume goes
                    // away, the physical mask will not be deleted.
                    if (addToExistingVolumes) {
                        exportMask.addToExistingVolumesIfAbsent(assocVolume, hluStr);
                    }
                    if (!updatedExportMaskMap.containsKey(exportMaskId)) {
                        updatedExportMaskMap.put(exportMaskId, exportMask);
                    }
                }
            }
        }
        if (!updatedExportGroupMap.isEmpty()) {
            List<ExportGroup> updatedExportGroups = new ArrayList<ExportGroup>(
                    updatedExportGroupMap.values());
            _dbClient.persistObject(updatedExportGroups);
        }

        if (!updatedExportMaskMap.isEmpty()) {
            List<ExportMask> updatedExportMasks = new ArrayList<ExportMask>(
                    updatedExportMaskMap.values());
            _dbClient.persistObject(updatedExportMasks);
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
    public void validateConsistencyGroupName(BlockConsistencyGroup consistencyGroup) {
        // No-OP by default.
    }

    /**
     * Return a list of active BlockMirror URI's that are known to be active (in Synchronized state).
     * 
     * @param volume Volume to check for mirrors against
     * @return List of active BlockMirror URI's
     */
    protected List<URI> getActiveMirrorsForVolume(Volume volume) {
        List<URI> activeMirrorURIs = new ArrayList<>();
        if (hasMirrors(volume)) {
            Collection<URI> mirrorUris = transform(volume.getMirrors(), FCTN_STRING_TO_URI);
            List<BlockMirror> mirrors = _dbClient.queryObject(BlockMirror.class, mirrorUris);
            for (BlockMirror mirror : mirrors) {
                if (!FRACTURED.toString().equalsIgnoreCase(mirror.getSyncState())) {
                    activeMirrorURIs.add(mirror.getId());
                }
            }
        }
        return activeMirrorURIs;
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

        // No RP protected volumes can be added to a consistency group.
        if (volume.getProtectionController() != null) {
            throw APIException.badRequests
                    .invalidParameterConsistencyGroupCannotAddProtectedVolume(volumeURI);
        }

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
         *    1. CG has no existing volumes, and
         *    2. SMI-S 8.x
         * For other arrays, or VMAX (CG has existing volumes, or non SMI-S 8.x), volumes to be added cannot have replicas
         */
        boolean isReplicaAllowed = false;
        if ((volsAlreadyInCG || cgVolumes.isEmpty()) && ControllerUtils.isVmaxVolumeUsing803SMIS(volumes.get(0), _dbClient)) {
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
     * @param volume A reference to the volume
     * @param cg A reference to the CG
     * @param cgStorageSystem A reference to the CG storage system
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
     * @param volume [in] - Volume object to check
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
     * @param volumesDescriptors volume descriptors
     * @param task task id
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
}

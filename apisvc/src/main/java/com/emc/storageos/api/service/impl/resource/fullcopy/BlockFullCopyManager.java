/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.placement.PlacementManager.SchedulerType;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.ResourceService;
import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.InterNodeHMACAuthFilter;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.VPlexUtil;

/**
 * Class that manages all aspects of full copies, also known as clones, for
 * block volumes.
 */
public class BlockFullCopyManager {

    // Enumeration specifying the valid keys for the full copy implementations map.
    private enum FullCopyImpl {
        dflt, vmax, vmax3, vnx, vnxe, hds, openstack, scaleio, xtremio, xiv, rp, vplex
    }

    private static final int VMAX_MAX_FULLCOPY_COUNT = 8; // Applies for VMAX3 also
    private static final int VNX_MAX_FULLCOPY_COUNT = 8;
    private static final int SCALEIO_MAX_FULLCOPY_COUNT = 31;
    private static final int XIV_MAX_FULLCOPY_COUNT = Integer.MAX_VALUE; // No known limit
    private static final int OPENSTACK_MAX_FULLCOPY_COUNT = Integer.MAX_VALUE; // No known limit

    // Map of the values for maximum active full copy sessions for each block storage platform.
    public static Map<String, Integer> s_maxFullCopyMap = new HashMap<String, Integer>();
    static {
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.vmax.name(), VMAX_MAX_FULLCOPY_COUNT);
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.vnxblock.name(), VNX_MAX_FULLCOPY_COUNT);
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.vnxe.name(), 0); // not supported
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.hds.name(), HDSConstants.MAX_SHADOWIMAGE_PAIR_COUNT);
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.openstack.name(), OPENSTACK_MAX_FULLCOPY_COUNT);
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.scaleio.name(), SCALEIO_MAX_FULLCOPY_COUNT);
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.xtremio.name(), 0); // not supported
        s_maxFullCopyMap.put(DiscoveredDataObject.Type.ibmxiv.name(), XIV_MAX_FULLCOPY_COUNT);
    }

    // A reference to a database client.
    private DbClient _dbClient;

    // A reference to a permissions helper.
    private PermissionsHelper _permissionsHelper = null;

    // A reference to the audit log manager.
    private AuditLogManager _auditLogManager = null;

    // A reference to the placement manager
    private PlacementManager _placementManager = null;

    // A reference to the full copy request.
    protected HttpServletRequest _request;

    // A reference to the security context
    private SecurityContext _securityContext;

    // A reference to the URI information.
    private UriInfo _uriInfo;

    // The supported block full copy API implementations
    private Map<String, BlockFullCopyApi> _fullCopyImpls = new HashMap<String, BlockFullCopyApi>();

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockFullCopyManager.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param permissionsHelper A reference to a permission helper.
     * @param auditLogManager A reference to an audit log manager.
     * @param coordinator A reference to the coordinator.
     * @param placementManager A reference to the placement manager.
     * @param securityContext A reference to the security context.
     * @param uriInfo A reference to the URI info.
     * @param request A reference to the full copy request.
     * @param tenantsService A reference to the tenants service or null.
     */
    public BlockFullCopyManager(DbClient dbClient, PermissionsHelper permissionsHelper,
            AuditLogManager auditLogManager, CoordinatorClient coordinator,
            PlacementManager placementManager, SecurityContext securityContext,
            UriInfo uriInfo, HttpServletRequest request, TenantsService tenantsService) {
        _dbClient = dbClient;
        _permissionsHelper = permissionsHelper;
        _auditLogManager = auditLogManager;
        _placementManager = placementManager;
        _securityContext = securityContext;
        _uriInfo = uriInfo;
        _request = request;

        // Create full copy implementations.
        createPlatformSpecificFullCopyImpls(coordinator, tenantsService);
    }

    /**
     * Create all platform specific full copy implementations.
     * 
     * @param coordinator A reference to the coordinator.
     * @param tenantsService A reference to the tenants service or null.
     */
    private void createPlatformSpecificFullCopyImpls(CoordinatorClient coordinator,
            TenantsService tenantsService) {
        _fullCopyImpls.put(FullCopyImpl.dflt.name(),
                new DefaultBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.vmax.name(),
                new VMAXBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.vmax3.name(),
                new VMAX3BlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.vnx.name(),
                new VNXBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.vnxe.name(),
                new VNXEBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.hds.name(),
                new HDSBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls
                .put(FullCopyImpl.openstack.name(),
                        new OpenstackBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block
                                .name())));
        _fullCopyImpls.put(FullCopyImpl.scaleio.name(),
                new ScaleIOBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.xtremio.name(),
                new XtremIOBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.xiv.name(),
                new XIVBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.block.name())));
        _fullCopyImpls.put(FullCopyImpl.vplex.name(),
                new VPlexBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.vplex.name()),
                        tenantsService));
        _fullCopyImpls.put(FullCopyImpl.rp.name(),
                new RPBlockFullCopyApiImpl(_dbClient, coordinator, _placementManager.getStorageScheduler(SchedulerType.rp.name())));
    }

    /**
     * Manages a request to create a full copy from the block object with the
     * passed URI. The URI must reference a volume or snapshot. For supported
     * platforms, if the source is a volume and it is in a consistency group,
     * full copies will be created for all volumes in the consistency group.
     * 
     * @param sourceURI The URI of the source volume or snapshot.
     * @param param The request data specifying the parameters for the request.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList createFullCopy(URI sourceURI, VolumeFullCopyCreateParam param)
            throws InternalException {
        s_logger.info("START create full copy for source {}", sourceURI);

        // Get the volume/snapshot.
        BlockObject fcSourceObj = BlockFullCopyUtils.queryFullCopyResource(sourceURI,
                _uriInfo, true, _dbClient);

        // Get the requested full copy count.
        int count = param.getCount() == null ? 1 : param.getCount();

        // Get the full copy name.
        String name = param.getName();

        // Get whether or not the full copy should be created inactive.
        // Check if the request calls for activation of the full copy
        boolean createInactive = param.getCreateInactive() == null ? Boolean.FALSE : param.getCreateInactive();

        // Get the project for the full copy source object.
        Project project = BlockFullCopyUtils.queryFullCopySourceProject(fcSourceObj, _dbClient);

        // Get and verify the virtual array.
        VirtualArray varray = BlockServiceUtils.verifyVirtualArrayForRequest(project,
                fcSourceObj.getVirtualArray(), _uriInfo, _permissionsHelper, _dbClient);

        // Get the platform specific block full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fcSourceObj);

        // Get the list of all block objects for which we need to
        // create full copies. For example, when creating a full copy
        // for a volume in a consistency group, we may create full
        // copies for all volumes in the consistency group.
        List<BlockObject> fcSourceObjList = fullCopyApiImpl.getAllSourceObjectsForFullCopyRequest(fcSourceObj);

        // Validate the full copy request.
        validateFullCopyCreateRequest(fcSourceObjList, project, name, count, createInactive,
                fullCopyApiImpl);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        // Create the full copies
        TaskList taskList = fullCopyApiImpl.create(fcSourceObjList, varray, name,
                createInactive, count, taskId);
        s_logger.info("FINISH create full copy for source {}", sourceURI);
        return taskList;
    }

    /**
     * Validates the full copy creation request.
     * 
     * @param fcSourceObjList A list of full copy sources.
     * @param project A reference to the project.
     * @param name The desired name for the full copy volume.
     * @param count The number of full copies requested.
     * @param createInactive true if the full copy is to activated when created,
     *            false otherwise.
     * @param fullCopyApiImpl A reference to the platform specific full copy
     *            implementation.
     */
    private void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList,
            Project project, String name, int count, boolean createInactive,
            BlockFullCopyApi fullCopyApiImpl) {

        // Verify the requested full copy count.
        if (count <= 0) {
            throw APIException.badRequests.parameterMustBeGreaterThan("count", 0);
        }

        // Verify the requested name.
        ArgValidator.checkFieldNotEmpty(name, "name");

        // Validate the project tenant.
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        ArgValidator.checkEntity(tenant, project.getTenantOrg().getURI(), false);

        // Verify the user is authorized.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project,
                BlockServiceUtils.getUserFromContext(_securityContext), _permissionsHelper);

        // Verify the source objects.
        Map<URI, VirtualPool> vpoolMap = new HashMap<URI, VirtualPool>();
        Map<URI, List<BlockObject>> vpoolSourceObjMap = new HashMap<URI, List<BlockObject>>();
        for (BlockObject fcSourceObj : fcSourceObjList) {
            // The full copy source object id.
            URI fcSourceURI = fcSourceObj.getId();
            if (URIUtil.isType(fcSourceURI, Volume.class)) {
                // Verify the operation is supported for ingested volumes.
                VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume((Volume) fcSourceObj,
                        ResourceOperationTypeEnum.CREATE_VOLUME_FULL_COPY, _dbClient);
            }

            // Verify the source is not an internal object.
            BlockServiceUtils.validateNotAnInternalBlockObject(fcSourceObj, false);

            // Update virtual pool map.
            VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(fcSourceObj, _dbClient);
            URI vpoolURI = vpool.getId();
            if (!vpoolMap.containsKey(vpoolURI)) {
                vpoolMap.put(vpoolURI, vpool);
            }

            // Update the full copy source objects for this vpool.
            if (!vpoolSourceObjMap.containsKey(vpoolURI)) {
                List<BlockObject> vpoolSourceObjs = new ArrayList<BlockObject>();
                vpoolSourceObjs.add(fcSourceObj);
                vpoolSourceObjMap.put(vpoolURI, vpoolSourceObjs);
            } else {
                List<BlockObject> vpoolSourceObjs = vpoolSourceObjMap.get(vpoolURI);
                vpoolSourceObjs.add(fcSourceObj);
            }
        }

        // Verify capacity quotas.
        for (URI vpoolURI : vpoolSourceObjMap.keySet()) {
            long totalRequiredSize = 0;
            List<BlockObject> vpoolSourceObjs = vpoolSourceObjMap.get(vpoolURI);
            for (BlockObject vpoolSourcObj : vpoolSourceObjs) {
                totalRequiredSize += count
                        * BlockFullCopyUtils.getCapacityForFullCopySource(vpoolSourcObj, _dbClient);
            }
            CapacityUtils.validateQuotasForProvisioning(_dbClient,
                    vpoolMap.get(vpoolURI), project, tenant, totalRequiredSize, "volume");
        }

        // Platform specific checks
        fullCopyApiImpl.validateFullCopyCreateRequest(fcSourceObjList, count);
    }

    /**
     * Manages the activation of the full copy with the passed URI for the
     * source with the passed URI. For supported platforms, if the source is a
     * volume and the volume is part of a consistency group, this method will
     * also activate the corresponding full copies for all other volumes in the
     * consistency group.
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList activateFullCopy(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info("START activate full copy {}", fullCopyURI);

        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // Get the source and full copy volume.
        BlockObject fcSourceObj = resourceMap.get(sourceURI);
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        // If the full copy is detached, it cannot be activated.
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests.detachedFullCopyCannotBeActivated(fullCopyURI
                    .toString());
        }

        // Otherwise, check if the full copy is in the inactive state.
        // If it is in any other state, it was already activated.
        // In this case, the code simply returns a successful task.
        boolean alreadyActive = !BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient);

        // Now activate.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);
        TaskList taskList = fullCopyApiImpl.activate(fcSourceObj, fullCopyVolume);

        // Log an audit message if we actually need to active the full copy.
        if (!alreadyActive) {
            auditOp(OperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY, true,
                    AuditLogManager.AUDITOP_BEGIN, fullCopyURI);
        }

        s_logger.info("FINISH activate full copy {}", fullCopyURI);
        return taskList;
    }

    /**
     * Detaches the full copy with the passed URI from the source with the
     * passed URI. For supported platforms, if the source is a volume and the
     * volume is part of a consistency group, this method will also detach the
     * corresponding full copies for all other volumes in the consistency group.
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList detachFullCopy(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info("START detach full copy {}", fullCopyURI);

        // Check if full copy has been detached.
        // It will return success if it has been detached
        Volume fullCopy = (Volume) BlockFullCopyUtils.queryFullCopyResource(fullCopyURI, _uriInfo, false, _dbClient);
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopy, _dbClient)) {
            s_logger.info("The full copy {} has been detached", fullCopyURI);
            TaskList taskList = new TaskList();
            String taskId = UUID.randomUUID().toString();
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.DETACH_VOLUME_FULL_COPY);
            op.ready("Full copy is already detached");
            _dbClient.createTaskOpStatus(Volume.class, fullCopyURI, taskId, op);

            TaskResourceRep task = TaskMapper.toTask(fullCopy, taskId, op);
            taskList.addTask(task);
            return taskList;
        }
        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // Get the source and full copy volume.
        BlockObject fcSourceObj = resourceMap.get(sourceURI);
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        // Determine if the copy is activated. It is activated if it is
        // not detached and not in the inactive state.
        boolean wasActive = (!BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume,
                _dbClient) && !BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient));

        // Get the platform specific full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);

        // Now detach.
        TaskList taskList = fullCopyApiImpl.detach(fcSourceObj, fullCopyVolume);

        // Log an audit message if we actually need to active the full copy.
        if (wasActive) {
            auditOp(OperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY, true,
                    AuditLogManager.AUDITOP_BEGIN, fullCopyURI);
        }

        s_logger.info("FINISH detach full copy {}", fullCopyURI);
        return taskList;
    }

    /**
     * Restores the source with the passed URI from the full copy with the
     * passed URI. For supported platforms, if the source is a volume and the
     * volume is part of a consistency group, this method will also restore all
     * other volumes in the consistency group with their corresponding full
     * copies.
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList restoreFullCopy(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info("START restore source {} from full copy {}",
                sourceURI, fullCopyURI);

        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // We don't currently support restore when the source
        // is a snapshot.
        if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            throw APIException.badRequests.fullCopyRestoreNotSupportedForSnapshot();
        }

        // Get the source object and full copy volume
        Volume sourceVolume = (Volume) resourceMap.get(sourceURI);
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        // Check if the full copy is detached.
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .detachedFullCopyCannotBeRestored(fullCopyURI.toString());
        }

        // Check if the full copy was not activated.
        if (BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .inactiveFullCopyCannotBeRestored(fullCopyURI.toString());
        }

        // Verify that the full copy is restorable otherwise.
        if (!BlockFullCopyUtils.isFullCopyRestorable(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests.fullCopyCannotBeRestored(fullCopyURI
                    .toString(), fullCopyVolume.getReplicaState());
        }

        // Get the platform specific full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);

        // Now restore the source.
        TaskList taskList = fullCopyApiImpl.restoreSource(sourceVolume, fullCopyVolume);

        // Log an audit message
        auditOp(OperationTypeEnum.RESTORE_VOLUME_FULL_COPY, true,
                AuditLogManager.AUDITOP_BEGIN, fullCopyURI);

        s_logger.info("FINISH restore source {} from full copy {}",
                sourceURI, fullCopyURI);
        return taskList;
    }

    /**
     * Resynchronizes the full copy with the passed URI with the latest data on
     * the source volume with the passed URI. For supported platforms, if the
     * source is a volume and the volume is part of a consistency group, this
     * method will also resynchronize the corresponding full copies for all
     * other volumes in the consistency group.
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList resynchronizeFullCopy(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info("START resynchronize full copy {} from source {}",
                fullCopyURI, sourceURI);

        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // We don't currently support resynchronize4 when the source
        // is a snapshot.
        if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            throw APIException.badRequests.fullCopyResyncNotSupportedForSnapshot();
        }

        // Get the source and full copy volumes
        Volume sourceVolume = (Volume) resourceMap.get(sourceURI);
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        // Check if the full copy is detached.
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .detachedFullCopyCannotBeResynchronized(fullCopyURI.toString());
        }

        // Check if the full copy was not activated.
        if (BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .inactiveFullCopyCannotBeResynchronized(fullCopyURI.toString());
        }

        // Verify that the full copy is resynchronizable otherwise.
        if (!BlockFullCopyUtils.isFullCopyResynchronizable(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests.fullCopyCannotBeResynchronized(fullCopyURI
                    .toString(), fullCopyVolume.getReplicaState());
        }

        // Get the platform specific full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);

        // Now restore the source volume.
        TaskList taskList = fullCopyApiImpl.resynchronizeCopy(sourceVolume, fullCopyVolume);

        // Log an audit message
        auditOp(OperationTypeEnum.RESYNCHRONIZE_VOLUME_FULL_COPY, true,
                AuditLogManager.AUDITOP_BEGIN, fullCopyURI);

        s_logger.info("FINISH resynchronize full copy {} from source {}",
                fullCopyURI, sourceURI);
        return taskList;
    }

    /**
     * Generates a group synchronized between volume Replication group
     * and clone Replication group.
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList startFullCopy(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info(
                "START establish group relation between Volume group and Full copy group."
                        + " Source: {}, Full copy: {}", sourceURI, fullCopyURI);

        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // Get the source and full copy volumes
        Volume sourceVolume = (Volume) resourceMap.get(sourceURI);
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        if (!sourceVolume.hasConsistencyGroup() ||
                fullCopyVolume.getReplicationGroupInstance() == null) {
            // check if this is vplex
            if (!VPlexUtil.isBackendFullCopyInReplicationGroup(fullCopyVolume, _dbClient)) {
                throw APIException.badRequests.blockObjectHasNoConsistencyGroup();
            }
        }

        // Check if the full copy is detached.
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .cannotEstablishGroupRelationForDetachedFullCopy(fullCopyURI.toString());
        }

        // Check if the full copy was not activated.
        if (BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests
                    .cannotEstablishGroupRelationForInactiveFullCopy(fullCopyURI.toString());
        }

        // Get the platform specific full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);

        // Now restore the source volume.
        TaskList taskList = fullCopyApiImpl.establishVolumeAndFullCopyGroupRelation(sourceVolume, fullCopyVolume);

        // Log an audit message
        auditOp(OperationTypeEnum.ESTABLISH_VOLUME_FULL_COPY, true,
                AuditLogManager.AUDITOP_BEGIN, fullCopyURI);

        s_logger.info("FINISH establish group relation between Volume group and FullCopy group");
        return taskList;
    }

    /**
     * Checks the progress of the data copy from the source with the
     * passed URI to the full copy with the passed URI.
     * 
     * TBD Maybe vice versa for restore?
     * 
     * @param sourceURI The URI of the source.
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return VolumeRestRep
     * 
     * @throws InternalException
     */
    public VolumeRestRep checkFullCopyProgress(URI sourceURI, URI fullCopyURI)
            throws InternalException {
        s_logger.info("START full copy progress check for {}", fullCopyURI);

        // Verify passed URIs for the full copy request.
        Map<URI, BlockObject> resourceMap = BlockFullCopyUtils.verifySourceAndFullCopy(
                sourceURI, fullCopyURI, _uriInfo, _dbClient);

        // Get the full copy volume.
        Volume fullCopyVolume = (Volume) resourceMap.get(fullCopyURI);

        // Check if the full copy is detached.
        if (BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
            throw APIException.badRequests.cannotCheckProgressFullCopyDetached(fullCopyURI
                    .toString());
        }

        // Get the platform specific full copy implementation.
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(fullCopyVolume);

        // Now check the progress.
        s_logger.info("FINISH full copy progress check for {}", fullCopyURI);
        VolumeRestRep volumeRestRep = fullCopyApiImpl.checkProgress(sourceURI, fullCopyVolume);
        return volumeRestRep;
    }

    /**
     * Returns the full copies for the source volume with the passed URI.
     * 
     * @param sourceVolumeURI The URI of the full copy source volume.
     * 
     * @return NamedVolumesList
     */
    public NamedVolumesList getFullCopiesForSource(URI sourceVolumeURI) {
        NamedVolumesList fullCopyList = new NamedVolumesList();
        ArgValidator.checkFieldUriType(sourceVolumeURI, Volume.class, "id");
        Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
        StringSet fullCopyIds = sourceVolume.getFullCopies();
        if (fullCopyIds == null || fullCopyIds.isEmpty()) {
            return fullCopyList;
        }
        for (String fullCopyId : fullCopyIds) {
            Volume fullCopyVolume = _dbClient.queryObject(Volume.class,
                    URI.create(fullCopyId));
            if (fullCopyVolume == null || fullCopyVolume.getInactive()) {
                s_logger.warn("Stale full copy {} found for volume {}", fullCopyId,
                        sourceVolumeURI);
                continue;
            }
            fullCopyList.getVolumes().add(DbObjectMapper.toNamedRelatedResource(fullCopyVolume));
        }
        return fullCopyList;
    }

    /**
     * Returns the full copy with the passed URI.
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @return VolumeRestRep
     */
    public VolumeRestRep getFullCopy(URI fullCopyURI) {
        Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                fullCopyURI, _uriInfo, false, _dbClient);
        return BlockMapper.map(_dbClient, fullCopyVolume);
    }

    /**
     * For ViPR-Only delete, cleans up the full copy associations between
     * source and full copy volumes.
     * 
     * @param volumeDescriptors The descriptors for volumes being
     *            deleted from ViPR.
     * @param dbClient A reference to a database client.
     */
    public static void cleanUpFullCopyAssociations(
            List<VolumeDescriptor> volumeDescriptors, DbClient dbClient) {
        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        for (URI volumeURI : volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            URI sourceVolumeURI = volume.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(sourceVolumeURI)) {
                // The volume being removed is a full copy. Make sure the copies
                // list of the source no longer references this volume. Note
                // that it is possible that the source was already deleted but
                // we left the source URI set in the copy, so one could always
                // know the source of the copy. So, check for a null source
                // volume.
                Volume sourceVolume = dbClient.queryObject(Volume.class,
                        sourceVolumeURI);
                if (sourceVolume != null) {
                    StringSet fullCopyIds = sourceVolume.getFullCopies();
                    if (fullCopyIds.contains(volumeURI.toString())) {
                        fullCopyIds.remove(volumeURI.toString());
                        dbClient.persistObject(sourceVolume);
                    }
                }
            }
        }
    }

    /**
     * Verify that the passed volume can be deleted.
     * 
     * @param volume A reference to a volume.
     * 
     * @return true if the volume can be deleted, false otherwise.
     */
    public boolean volumeCanBeDeleted(Volume volume) {
        if ((BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) ||
                (BlockFullCopyUtils.isVolumeFullCopySource(volume, _dbClient))) {
            // Delegate to the platform specific full copy implementation
            // for the passed volume.
            BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(volume);
            return fullCopyApiImpl.volumeCanBeDeleted(volume);
        }

        return true;
    }

    /**
     * Verify that the passed volume can be expanded.
     * 
     * @param volume A reference to a volume.
     * 
     * @return true if the volume can be expanded, false otherwise.
     */
    public boolean volumeCanBeExpanded(Volume volume) {
        if ((BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) ||
                (BlockFullCopyUtils.isVolumeFullCopySource(volume, _dbClient))) {
            // Delegate to the platform specific full copy implementation
            // for the passed volume.
            BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(volume);
            return fullCopyApiImpl.volumeCanBeExpanded(volume);
        }

        return true;
    }

    /**
     * Verify that new volumes can be created in the passed consistency group.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param cgVolumes The volumes in the consistency group.
     */
    public void verifyNewVolumesCanBeCreatedInConsistencyGroup(
            BlockConsistencyGroup consistencyGroup, List<Volume> cgVolumes) {
        if (!canConsistencyGroupBeModified(consistencyGroup, cgVolumes)) {
            throw APIException.badRequests.cantCreateNewVolumesInCGActiveFullCopies(
                    consistencyGroup.getLabel());
        }
    }

    /**
     * Verify the passed consistency group can be updated.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param cgVolumes The volumes in the consistency group.
     */
    public void verifyConsistencyGroupCanBeUpdated(
            BlockConsistencyGroup consistencyGroup, List<Volume> cgVolumes) {
        if (!canConsistencyGroupBeModified(consistencyGroup, cgVolumes)) {
            throw APIException.badRequests.cantUpdateCGActiveFullCopies(
                    consistencyGroup.getLabel());
        }
    }

    /**
     * Determines if the passed consistency group can have new volumes added or
     * existing volumes removed.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * @param cgVolumes The volumes in the consistency group.
     * 
     * @return true if the group an be modified, false otherwise.
     */
    private boolean canConsistencyGroupBeModified(BlockConsistencyGroup consistencyGroup,
            List<Volume> cgVolumes) {
        Iterator<Volume> cgVolumesIter = cgVolumes.iterator();
        while (cgVolumesIter.hasNext()) {
            Volume cgVolume = cgVolumesIter.next();

            // Volumes that are full copies must be detached.
            if ((BlockFullCopyUtils.isVolumeFullCopy(cgVolume, _dbClient)) &&
                    (!BlockFullCopyUtils.isFullCopyDetached(cgVolume, _dbClient))) {
                return false;
            }

            // Volumes with full copies must be detached from
            // those copies.
            if ((BlockFullCopyUtils.isVolumeCGFullCopySource(cgVolume, _dbClient)) &&
                    (!BlockFullCopyUtils.volumeDetachedFromFullCopies(cgVolume, _dbClient))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Given the passed full copy volume, return all volumes in the full copy
     * set.
     * 
     * @param volume A Reference to a volume that is a full copy.
     * 
     * @return The full copy volumes in the full copy set.
     */
    public Collection<Volume> getFullCopySet(Volume fullCopyVolume) {
        if (!BlockFullCopyUtils.isVolumeFullCopy(fullCopyVolume, _dbClient)) {
            return new HashSet<Volume>();
        }

        BlockObject fcSourceObj = BlockObject.fetch(_dbClient,
                fullCopyVolume.getAssociatedSourceVolume());
        BlockFullCopyApi fullCopyApi = getPlatformSpecificFullCopyImpl(fullCopyVolume);
        return fullCopyApi.getFullCopySetMap(fcSourceObj, fullCopyVolume).values();
    }

    /**
     * Verifies that the volume's status with respect to full copies allows
     * snapshots to be created.
     * 
     * @param requestedVolume A reference to the volume for which a snapshot was requested.
     * @param volumesToSnap The list of volumes that would be snapped.
     */
    public void validateSnapshotCreateRequest(Volume requestedVolume, List<Volume> volumesToSnap) {
        BlockFullCopyApi fullCopyApiImpl = getPlatformSpecificFullCopyImpl(requestedVolume);
        fullCopyApiImpl.validateSnapshotCreateRequest(requestedVolume, volumesToSnap);
    }

    /**
     * Determines and returns the platform specific full copy implementation.
     * 
     * @param fcSourceObj A reference to the full copy source.
     * 
     * @return The platform specific full copy implementation
     */
    private BlockFullCopyApi getPlatformSpecificFullCopyImpl(BlockObject fcSourceObj) {

        BlockFullCopyApi fullCopyApi = null;
        if (BlockObject.checkForRP(_dbClient, fcSourceObj.getId())) {
            fullCopyApi = _fullCopyImpls.get(FullCopyImpl.rp.name());
        } else {
            VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(fcSourceObj, _dbClient);
            if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
                fullCopyApi = _fullCopyImpls.get(FullCopyImpl.vplex.name());
            } else {
                URI systemURI = fcSourceObj.getStorageController();
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
                String systemType = system.getSystemType();
                if (DiscoveredDataObject.Type.vmax.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.vmax.name());
                    if (system.checkIfVmax3()) {
                        fullCopyApi = _fullCopyImpls.get(FullCopyImpl.vmax3.name());
                    }
                } else if (DiscoveredDataObject.Type.vnxblock.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.vnx.name());
                } else if (DiscoveredDataObject.Type.vnxe.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.vnxe.name());
                } else if (DiscoveredDataObject.Type.hds.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.hds.name());
                } else if (DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.openstack.name());
                } else if (DiscoveredDataObject.Type.scaleio.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.scaleio.name());
                } else if (DiscoveredDataObject.Type.xtremio.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.xtremio.name());
                } else if (DiscoveredDataObject.Type.ibmxiv.name().equals(systemType)) {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.xiv.name());
                } else {
                    fullCopyApi = _fullCopyImpls.get(FullCopyImpl.dflt.name());
                }
            }
        }

        return fullCopyApi;
    }

    /**
     * Record audit log for services.
     * 
     * @param opType audit event type (e.g. CREATE_VPOOL|TENANT etc.)
     * @param operationalStatus Status of operation (true|false)
     * @param operationStage Stage of operation. For sync operation, it should
     *            be null; For async operation, it should be "BEGIN" or "END";
     * @param descparams Description parameters
     */
    private void auditOp(OperationTypeEnum opType, boolean operationalStatus,
            String operationStage, Object... descparams) {

        URI tenantId;
        URI username;
        if (!BlockServiceUtils.hasValidUserInContext(_securityContext)
                && InterNodeHMACAuthFilter.isInternalRequest(_request)) {
            // Use default values for internal datasvc requests that lack a user
            // context
            tenantId = _permissionsHelper.getRootTenant().getId();
            username = ResourceService.INTERNAL_DATASVC_USER;
        } else {
            StorageOSUser user = BlockServiceUtils.getUserFromContext(_securityContext);
            tenantId = URI.create(user.getTenantId());
            username = URI.create(user.getName());
        }
        _auditLogManager.recordAuditLog(tenantId, username,
                BlockService.EVENT_SERVICE_TYPE, opType, System.currentTimeMillis(),
                operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS
                        : AuditLogManager.AUDITLOG_FAILURE, operationStage, descparams);
    }

    /**
     * Return the maximum number of active full copies for storage
     * systems of the passed type.
     * 
     * @param systemType The system type.
     * 
     * @return The maximum number of active full copies.
     */
    public static int getMaxFullCopiesForSystemType(String systemType) {
        if (s_maxFullCopyMap.containsKey(systemType)) {
            return s_maxFullCopyMap.get(systemType);
        }

        return Integer.MAX_VALUE;
    }

}

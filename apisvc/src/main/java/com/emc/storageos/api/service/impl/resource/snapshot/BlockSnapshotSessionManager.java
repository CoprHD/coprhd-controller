/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToID;
import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;
import static com.google.common.collect.Collections2.transform;
import static java.lang.String.format;

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
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.ResourceService;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.BlockSnapshotSession.CopyMode;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.InterNodeHMACAuthFilter;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.collect.Lists;

/**
 * Class that implements all block snapshot session requests.
 */
public class BlockSnapshotSessionManager {

    // Enumeration specifying the valid keys for the snapshot session implementations map.
    public enum SnapshotSessionImpl {
        dflt, vmax, vmax3, vnx, vnxe, hds, openstack, scaleio, xtremio, xiv, rp, vplex
    }

    // A reference to a database client.
    private final DbClient _dbClient;

    // A reference to a permissions helper.
    private PermissionsHelper _permissionsHelper = null;

    // A reference to the audit log manager.
    private AuditLogManager _auditLogManager = null;

    // A reference to the security context
    private final SecurityContext _securityContext;

    // A reference to the snapshot session request.
    protected HttpServletRequest _request;

    // A reference to the URI information.
    private final UriInfo _uriInfo;

    // The supported block snapshot session API implementations
    private final Map<String, BlockSnapshotSessionApi> _snapshotSessionImpls = new HashMap<String, BlockSnapshotSessionApi>();

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionManager.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param permissionsHelper A reference to a permission helper.
     * @param auditLogManager A reference to an audit log manager.
     * @param coordinator A reference to the coordinator.
     * @param securityContext A reference to the security context.
     * @param uriInfo A reference to the URI info.
     * @param request A reference to the snapshot session request.
     */
    public BlockSnapshotSessionManager(DbClient dbClient, PermissionsHelper permissionsHelper,
            AuditLogManager auditLogManager, CoordinatorClient coordinator,
            SecurityContext securityContext, UriInfo uriInfo, HttpServletRequest request) {
        _dbClient = dbClient;
        _permissionsHelper = permissionsHelper;
        _auditLogManager = auditLogManager;
        _securityContext = securityContext;
        _uriInfo = uriInfo;
        _request = request;

        // Create snapshot session implementations.
        createPlatformSpecificImpls(coordinator);
    }

    /**
     * Create all platform specific snapshot session implementations.
     * 
     * @param coordinator A reference to the coordinator.
     */
    private void createPlatformSpecificImpls(CoordinatorClient coordinator) {
        _snapshotSessionImpls.put(SnapshotSessionImpl.dflt.name(), new DefaultBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax.name(), new VMAXBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vmax3.name(), new VMAX3BlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vnx.name(), new VNXBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vnxe.name(), new VNXEBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.hds.name(), new HDSBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.openstack.name(), new OpenstackBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.scaleio.name(), new ScaleIOBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.xtremio.name(), new XtremIOBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.xiv.name(), new XIVBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.vplex.name(), new VPlexBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
        _snapshotSessionImpls.put(SnapshotSessionImpl.rp.name(), new RPBlockSnapshotSessionApiImpl(_dbClient, coordinator,
                _permissionsHelper, _securityContext, this));
    }

    /**
     * Gets a specific platform implementation.
     * 
     * @param implType The specific implementation desired.
     * 
     * @return The platform specific snapshot session implementation.
     */
    public BlockSnapshotSessionApi getPlatformSpecificImplOfType(SnapshotSessionImpl implType) {
        return _snapshotSessionImpls.get(implType.name());
    }

    /**
     * Get the platform implementation for the passed system.
     * 
     * @param system A reference to the storage system.
     * 
     * @return A reference to the platform implementation for the passed system.
     */
    public BlockSnapshotSessionApi getPlatformSpecificImplForSystem(StorageSystem system) {
        BlockSnapshotSessionApi snapSessionApi = null;
        String systemType = system.getSystemType();
        if (DiscoveredDataObject.Type.vmax.name().equals(systemType)) {
            if (system.checkIfVmax3()) {
                snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vmax3.name());
            } else {
                snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vmax.name());
            }
        } else if (DiscoveredDataObject.Type.vnxblock.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vnx.name());
        } else if (DiscoveredDataObject.Type.vnxe.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vnxe.name());
        } else if (DiscoveredDataObject.Type.hds.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.hds.name());
        } else if (DiscoveredDataObject.Type.openstack.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.openstack.name());
        } else if (DiscoveredDataObject.Type.scaleio.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.scaleio.name());
        } else if (DiscoveredDataObject.Type.xtremio.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.xtremio.name());
        } else if (DiscoveredDataObject.Type.ibmxiv.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.xiv.name());
        } else if (DiscoveredDataObject.Type.vplex.name().equals(systemType)) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vplex.name());
        } else {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.dflt.name());
        }
        return snapSessionApi;
    }

    /**
     * Creates a snapshot session based on the given resource URI.  This method handles the following cases where
     * resourceURI is...
     *
     * 1) a non-CG Volume/BlockSnapshot
     * 2) a BlockConsistencyGroup
     * 3) a CG Volume/BlockSnapshot (recursively calls this method, passing in its BlockConsistencyGroup URI)
     *
     * @param resourceURI   Resource to create a snapshot session from
     * @param param         Snapshot session parameters
     * @param fcManager     Full copy manager
     * @return              TaskList
     */
    public TaskList createSnapshotSession(URI resourceURI, SnapshotSessionCreateParam param, BlockFullCopyManager fcManager) {
        if (URIUtil.isType(resourceURI, Volume.class) || URIUtil.isType(resourceURI, BlockSnapshot.class)) {
            BlockObject blockObject =
                    BlockSnapshotSessionUtils.querySnapshotSessionSource(resourceURI, _uriInfo, false, _dbClient);
            if (blockObject.hasConsistencyGroup()) {
                return createSnapshotSession(blockObject.getConsistencyGroup(), param, fcManager);
            } else {
                return createSnapshotSession(Lists.newArrayList(blockObject), param, fcManager);
            }
        } else if (URIUtil.isType(resourceURI, BlockConsistencyGroup.class)) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, resourceURI);
            return createSnapshotSession(cg, param, fcManager);
        }
        return null;
    }

    public TaskList createSnapshotSession(BlockConsistencyGroup cg, SnapshotSessionCreateParam param, BlockFullCopyManager fcManager) {
        List<BlockObject> sources = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
        return createSnapshotSession(sources, param, fcManager);
    }

    /**
     * Implements a request to create a new block snapshot session.
     * 
     * @param snapSessionSourceObjList The URI of the snapshot session source object.
     * @param param A reference to the create session information.
     * @param fcManager A reference to a full copy manager.
     * 
     * @return TaskList A TaskList
     */
    public TaskList createSnapshotSession(List<BlockObject> snapSessionSourceObjList,
                                          SnapshotSessionCreateParam param, BlockFullCopyManager fcManager) {
        Collection<URI> sourceURIs = transform(snapSessionSourceObjList, fctnDataObjectToID());
        s_logger.info("START create snapshot session for sources {}", Joiner.on(',').join(sourceURIs));

        // Get the snapshot session label.
        String snapSessionLabel = param.getName();

        // Get the target device information, if any.
        int newLinkedTargetsCount = 0;
        String newTargetsName = null;
        String newTargetsCopyMode = CopyMode.nocopy.name();
        SnapshotSessionNewTargetsParam linkedTargetsParam = param.getNewLinkedTargets();
        if (linkedTargetsParam != null) {
            newLinkedTargetsCount = linkedTargetsParam.getCount().intValue();
            newTargetsName = linkedTargetsParam.getTargetName();
            newTargetsCopyMode = linkedTargetsParam.getCopyMode();
        }

        BlockObject sourceObj = snapSessionSourceObjList.get(0);

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);

        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(sourceObj);

        // Validate the create snapshot session request.
        snapSessionApiImpl.validateSnapshotSessionCreateRequest(sourceObj, snapSessionSourceObjList, project, snapSessionLabel,
                newLinkedTargetsCount, newTargetsName, newTargetsCopyMode, false, fcManager);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        // Prepare the ViPR BlockSnapshotSession instances and BlockSnapshot
        // instances for any new targets to be created and linked to the
        // snapshot sessions.
        List<Map<URI, BlockSnapshot>> snapSessionSnapshots = new ArrayList<>();
        BlockSnapshotSession snapSession = snapSessionApiImpl.prepareSnapshotSession(snapSessionSourceObjList,
                snapSessionLabel, newLinkedTargetsCount, newTargetsName, snapSessionSnapshots, taskId);

        // Populate the preparedObjects list and create tasks for each snapshot session.
        TaskList response = new TaskList();

        response.getTaskList().add(toTask(snapSession, taskId));
        for (BlockObject sourceForTask : snapSessionSourceObjList) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, sourceForTask.getId(),
                    taskId, ResourceOperationTypeEnum.CREATE_SNAPSHOT_SESSION);
            response.getTaskList().add(toTask(sourceForTask, taskId, op));
        }
        addConsistencyGroupTasks(snapSessionSourceObjList, response, taskId,
                ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT_SESSION);

        List<DataObject> preparedObjects = new ArrayList<>();
        List<List<URI>> snapSessionSnapshotURIs = new ArrayList<>();

        for (Map<URI, BlockSnapshot> snapshotMap : snapSessionSnapshots) {
            preparedObjects.addAll(snapshotMap.values());
            Set<URI> uris = snapshotMap.keySet();
            snapSessionSnapshotURIs.add(Lists.newArrayList(uris));
        }

        preparedObjects.add(snapSession);

        // Create the snapshot sessions.
        try {
            snapSessionApiImpl.createSnapshotSession(sourceObj, snapSession.getId(),
                    snapSessionSnapshotURIs, newTargetsCopyMode, taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to create snapshot sessions for source %s: %s", sourceObj.getId(), e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(response.getTaskList(), preparedObjects, errorMsg, taskId, sc);
            throw e;
        }

        // Record a message in the audit log.
        auditOp(OperationTypeEnum.CREATE_SNAPSHOT_SESSION, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionLabel, sourceObj.getId().toString(), sourceObj.getStorageController().toString());

        s_logger.info("FINISH create snapshot session for source {}", sourceObj.getId());
        return response;
    }

    /**
     * Implements a request to create and link new target volumes to the
     * BlockSnapshotSession instance with the passed URI.
     * 
     * @param snapSessionURI The URI of a BlockSnapshotSession instance.
     * @param param The linked target information.
     * 
     * @return A TaskResourceRep.
     */
    public TaskList linkTargetVolumesToSnapshotSession(URI snapSessionURI, SnapshotSessionLinkTargetsParam param) {
        s_logger.info("START link new targets for snapshot session {}", snapSessionURI);

        // Get the snapshot session.
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, true);

        BlockObject snapSessionSourceObj = null;
        List<BlockObject> snapSessionSourceObjs = null;
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            snapSessionSourceObjs = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
            snapSessionSourceObj = snapSessionSourceObjs.get(0);
        } else {
            snapSessionSourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(snapSession.getParent().getURI(),
                    _uriInfo, true, _dbClient);
            snapSessionSourceObjs = Lists.newArrayList(snapSessionSourceObj);
        }

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Get the target information.
        int newLinkedTargetsCount = param.getNewLinkedTargets().getCount();
        String newTargetsName = param.getNewLinkedTargets().getTargetName();
        String newTargetsCopyMode = param.getNewLinkedTargets().getCopyMode();
        if (newTargetsCopyMode == null) {
            newTargetsCopyMode = CopyMode.nocopy.name();
        }

        // Validate that the requested new targets can be linked to the snapshot session.
        snapSessionApiImpl.validateLinkNewTargetsRequest(snapSessionSourceObj, project, newLinkedTargetsCount, newTargetsName,
                newTargetsCopyMode);

        // Prepare the BlockSnapshot instances to represent the new linked targets.
        List<Map<URI, BlockSnapshot>> snapshots = snapSessionApiImpl.prepareSnapshotsForSession(snapSessionSourceObjs, 0, newLinkedTargetsCount,
                newTargetsName);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        TaskList response = new TaskList();
        List<DataObject> preparedObjects = new ArrayList<>();

        // Create a task for the snapshot session.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.LINK_SNAPSHOT_SESSION_TARGETS);
        _dbClient.createTaskOpStatus(BlockSnapshotSession.class, snapSessionURI, taskId, op);
        snapSession.getOpStatus().put(taskId, op);
        response.getTaskList().add(toTask(snapSession, taskId));


        List<List<URI>> snapSessionSnapshotURIs = new ArrayList<>();
        for (Map<URI, BlockSnapshot> snapshotMap : snapshots) {
            preparedObjects.addAll(snapshotMap.values());
            Set<URI> uris = snapshotMap.keySet();
            snapSessionSnapshotURIs.add(Lists.newArrayList(uris));
        }

        // Create and link new targets to the snapshot session.
        try {
            snapSessionApiImpl.linkNewTargetVolumesToSnapshotSession(snapSessionSourceObj, snapSession,
                    snapSessionSnapshotURIs, newTargetsCopyMode, taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to link new targets for snapshot session %s: %s", snapSessionURI, e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(response.getTaskList(), preparedObjects, errorMsg, taskId, sc);
            throw e;
        }

        // Create the audit log entry.
        auditOp(OperationTypeEnum.LINK_SNAPSHOT_SESSION_TARGET, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionURI.toString(), snapSessionSourceObj.getId().toString(), snapSessionSourceObj.getStorageController().toString());

        s_logger.info("FINISH link new targets for snapshot session {}", snapSessionURI);
        return response;
    }

    /**
     * Implements a request to unlink the passed targets from the
     * BlockSnapshotSession instance with the passed URI.
     * 
     * @param snapSessionURI The URI of a BlockSnapshotSession instance.
     * @param param The linked target information.
     * 
     * @return A TaskResourceRep.
     */
    public TaskResourceRep relinkTargetVolumesToSnapshotSession(URI snapSessionURI, SnapshotSessionRelinkTargetsParam param) {
        s_logger.info("START relink targets to snapshot session {}", snapSessionURI);

        // Get the snapshot session.
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, true);

        BlockObject snapSessionSourceObj = null;
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            List<BlockObject> snapSessionSourceObjs = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
            snapSessionSourceObj = snapSessionSourceObjs.get(0);
        } else {
            snapSessionSourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(snapSession.getParent().getURI(),
                    _uriInfo, true, _dbClient);
        }

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Get the target information.
        List<URI> linkedTargetURIs = param.getLinkedTargetIds();

        // Validate that the requested targets can be re-linked to the snapshot session.
        snapSessionApiImpl.validateRelinkSnapshotSessionTargets(snapSessionSourceObj, snapSession, project, linkedTargetURIs, _uriInfo);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        // Create a task for the snapshot session.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.RELINK_SNAPSHOT_SESSION_TARGETS);
        _dbClient.createTaskOpStatus(BlockSnapshotSession.class, snapSessionURI, taskId, op);
        snapSession.getOpStatus().put(taskId, op);
        TaskResourceRep response = toTask(snapSession, taskId);

        // Re-link the targets to the snapshot session.
        try {
            snapSessionApiImpl.relinkTargetVolumesToSnapshotSession(snapSessionSourceObj, snapSession, linkedTargetURIs, taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to relink targets to snapshot session %s: %s", snapSessionURI, e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(Arrays.asList(response), new ArrayList<DataObject>(), errorMsg, taskId, sc);
            throw e;
        }

        // Create the audit log entry.
        auditOp(OperationTypeEnum.RELINK_SNAPSHOT_SESSION_TARGET, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionURI.toString(), snapSessionSourceObj.getId().toString(), snapSessionSourceObj.getStorageController().toString());

        s_logger.info("FINISH relink targets to snapshot session {}", snapSessionURI);
        return response;
    }

    /**
     * Implements a request to unlink the passed targets from the
     * BlockSnapshotSession instance with the passed URI.
     * 
     * @param snapSessionURI The URI of a BlockSnapshotSession instance.
     * @param param The linked target information.
     * 
     * @return A TaskResourceRep.
     */
    public TaskResourceRep unlinkTargetVolumesFromSnapshotSession(URI snapSessionURI, SnapshotSessionUnlinkTargetsParam param) {
        s_logger.info("START unlink targets from snapshot session {}", snapSessionURI);

        // Get the snapshot session.
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, true);

        BlockObject snapSessionSourceObj = null;
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            List<BlockObject> snapSessionSourceObjs = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
            snapSessionSourceObj = snapSessionSourceObjs.get(0);
        } else {
            snapSessionSourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(snapSession.getParent().getURI(),
                    _uriInfo, true, _dbClient);
        }

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Get the target information.
        Map<URI, Boolean> targetMap = new HashMap<>();
        for (SnapshotSessionUnlinkTargetParam targetInfo : param.getLinkedTargets()) {
            URI targetURI = targetInfo.getId();
            Boolean deleteTarget = targetInfo.getDeleteTarget();
            if (deleteTarget == null) {
                deleteTarget = Boolean.FALSE;
            }
            targetMap.put(targetURI, deleteTarget);
        }

        // Validate that the requested targets can be unlinked from the snapshot session.
        snapSessionApiImpl.validateUnlinkSnapshotSessionTargets(snapSession, snapSessionSourceObj, project, targetMap.keySet(), _uriInfo);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        // Create a task for the snapshot session.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.UNLINK_SNAPSHOT_SESSION_TARGETS);
        _dbClient.createTaskOpStatus(BlockSnapshotSession.class, snapSessionURI, taskId, op);
        snapSession.getOpStatus().put(taskId, op);
        TaskResourceRep response = toTask(snapSession, taskId);

        // Unlink the targets from the snapshot session.
        try {
            snapSessionApiImpl.unlinkTargetVolumesFromSnapshotSession(snapSessionSourceObj, snapSession, targetMap, taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to unlink targets from snapshot session %s: %s", snapSessionURI, e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(Arrays.asList(response), new ArrayList<DataObject>(), errorMsg, taskId, sc);
            throw e;
        }

        // Create the audit log entry.
        auditOp(OperationTypeEnum.UNLINK_SNAPSHOT_SESSION_TARGET, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionURI.toString(), snapSessionSourceObj.getId().toString(), snapSessionSourceObj.getStorageController().toString());

        s_logger.info("FINISH unlink targets from snapshot session {}", snapSessionURI);
        return response;
    }

    /**
     * Restores the data on the array snapshot point-in-time copy represented by the
     * BlockSnapshotSession instance with the passed URI, to the snapshot session source
     * object.
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance to be restored.
     * 
     * @return TaskResourceRep representing the snapshot session task.
     */
    public TaskList restoreSnapshotSession(URI snapSessionURI) {
        s_logger.info("START restore snapshot session {}", snapSessionURI);

        // Get the snapshot session.
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, true);

        BlockObject snapSessionSourceObj = null;
        List<BlockObject> snapSessionSourceObjs = null;
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            snapSessionSourceObjs = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
            snapSessionSourceObj = snapSessionSourceObjs.get(0);
        } else {
            snapSessionSourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(snapSession.getParent().getURI(),
                    _uriInfo, true, _dbClient);
            snapSessionSourceObjs = Lists.newArrayList(snapSessionSourceObj);
        }

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Validate that the snapshot session can be restored.
        snapSessionApiImpl.validateRestoreSnapshotSession(snapSessionSourceObjs, project);

        // Create the task identifier.
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();

        // Create the operation status entry in the status map for the snapshot.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.RESTORE_SNAPSHOT_SESSION);
        _dbClient.createTaskOpStatus(BlockSnapshotSession.class, snapSessionURI, taskId, op);
        snapSession.getOpStatus().put(taskId, op);
        taskList.addTask(toTask(snapSession, taskId));

        // Restore the snapshot session.
        try {
            snapSessionApiImpl.restoreSnapshotSession(snapSession, snapSessionSourceObjs.get(0), taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to restore snapshot session %s: %s", snapSessionURI, e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(taskList.getTaskList(), new ArrayList<DataObject>(), errorMsg, taskId, sc);
            throw e;
        }

        // Create the audit log entry.
        auditOp(OperationTypeEnum.RESTORE_SNAPSHOT_SESSION, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionURI.toString(), snapSessionSourceObjs.get(0).getId().toString(), snapSessionSourceObjs.get(0)
                        .getStorageController().toString());

        s_logger.info("FINISH restore snapshot session {}", snapSessionURI);
        return taskList;
    }

    /**
     * Get the details for the BlockSnapshotSession instance with the passed URI.
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * 
     * @return An instance of BlockSnapshotSessionRestRep with the details for the requested snapshot session.
     */
    public BlockSnapshotSessionRestRep getSnapshotSession(URI snapSessionURI) {
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, false);
        return map(_dbClient, snapSession);
    }

    /**
     * Get the BlockSnapshotSession instances for the source object with the passed URI.
     * 
     * @param sourceURI The URI of the snapshot session source object.
     * 
     * @return A BlockSnapshotSessionList of the snapshot sessions for the source.
     */
    public BlockSnapshotSessionList getSnapshotSessionsForSource(URI sourceURI) {
        // Get the snapshot session source object.
        BlockObject sourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(sourceURI, _uriInfo, true, _dbClient);

        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(sourceObj);

        // Get the BlockSnapshotSession instances for the source and prepare the result.
        List<BlockSnapshotSession> snapSessionsForSource = snapSessionApiImpl.getSnapshotSessionsForSource(sourceObj);
        BlockSnapshotSessionList result = new BlockSnapshotSessionList();
        for (BlockSnapshotSession snapSessionForSource : snapSessionsForSource) {
            result.getSnapSessionRelatedResourceList().add(toNamedRelatedResource(snapSessionForSource));
        }
        return result;
    }

    /**
     * @param group
     * @return
     */
    public BlockSnapshotSessionList getSnapshotSessionsForConsistencyGroup(BlockConsistencyGroup group) {
        BlockSnapshotSessionList result = new BlockSnapshotSessionList();
        List<Volume> volumes = ControllerUtils.getVolumesPartOfCG(group.getId(), _dbClient);

        if (volumes.isEmpty()) {
            return result;
        }

        Volume sourceVolume = volumes.get(0);

        // Get the platform specific block snapshot session implementation.
        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(sourceVolume);

        // Get the BlockSnapshotSession instances for the source and prepare the result.
        List<BlockSnapshotSession> snapSessions = snapSessionApiImpl.getSnapshotSessionsForConsistencyGroup(group);

        for (BlockSnapshotSession snapSession : snapSessions) {
            result.getSnapSessionRelatedResourceList().add(toNamedRelatedResource(snapSession));
        }

        return result;
    }

    /**
     * Delete the snapshot session with the passed URI.
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * 
     * @return TaskResourceRep representing the snapshot session task.
     */
    public TaskList deleteSnapshotSession(URI snapSessionURI) {
        s_logger.info("START delete snapshot session {}", snapSessionURI);

        // Get the snapshot session.
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapSessionURI, _uriInfo, _dbClient, true);

        BlockObject snapSessionSourceObj = null;
        if (snapSession.hasConsistencyGroup()) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
            List<BlockObject> snapSessionSourceObjs = BlockConsistencyGroupUtils.getAllSources(cg, _dbClient);
            snapSessionSourceObj = snapSessionSourceObjs.get(0);
        } else {
            snapSessionSourceObj = BlockSnapshotSessionUtils.querySnapshotSessionSource(snapSession.getParent().getURI(),
                    _uriInfo, true, _dbClient);
        }

        // Get the project for the snapshot session source object.
        Project project = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(snapSessionSourceObj, _dbClient);

        BlockSnapshotSessionApi snapSessionApiImpl = determinePlatformSpecificImplForSource(snapSessionSourceObj);

        // Validate that the snapshot session can be deleted.
        snapSessionApiImpl.validateDeleteSnapshotSession(snapSession, snapSessionSourceObj, project);

        // Create the task identifier.
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.DELETE_SNAPSHOT_SESSION);
        _dbClient.createTaskOpStatus(BlockSnapshotSession.class, snapSession.getId(), taskId, op);
        snapSession.getOpStatus().put(taskId, op);
        taskList.addTask(toTask(snapSession, taskId));

        // Delete the snapshot session.
        try {
            snapSessionApiImpl.deleteSnapshotSession(snapSession, snapSessionSourceObj, taskId);
        } catch (Exception e) {
            String errorMsg = format("Failed to delete snapshot session %s: %s", snapSessionURI, e.getMessage());
            ServiceCoded sc = null;
            if (e instanceof ServiceCoded) {
                sc = (ServiceCoded) e;
            } else {
                sc = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            }
            cleanupFailure(taskList.getTaskList(), new ArrayList<DataObject>(), errorMsg, taskId, sc);
            throw e;
        }

        // Create the audit log entry.
        auditOp(OperationTypeEnum.DELETE_SNAPSHOT_SESSION, true, AuditLogManager.AUDITOP_BEGIN,
                snapSessionURI.toString(), snapSessionSourceObj.getId().toString(), snapSessionSourceObj.getStorageController().toString());

        s_logger.info("FINISH delete snapshot session {}", snapSessionURI);

        return taskList;
    }

    /**
     * Determines and returns the platform specific snapshot session implementation.
     * 
     * @param sourceObj A reference to the snapshot session source.
     * 
     * @return The platform specific snapshot session implementation.
     */
    private BlockSnapshotSessionApi determinePlatformSpecificImplForSource(BlockObject sourceObj) {

        BlockSnapshotSessionApi snapSessionApi = null;
        if (BlockObject.checkForRP(_dbClient, sourceObj.getId())) {
            snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.rp.name());
        } else {
            VirtualPool vpool = BlockSnapshotSessionUtils.querySnapshotSessionSourceVPool(sourceObj, _dbClient);
            if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
                snapSessionApi = _snapshotSessionImpls.get(SnapshotSessionImpl.vplex.name());
            } else {
                URI systemURI = sourceObj.getStorageController();
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
                snapSessionApi = getPlatformSpecificImplForSystem(system);
            }
        }

        return snapSessionApi;
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
     * Cleans up after a failed request.
     * 
     * @param taskList A list of prepared task responses.
     * @param preparedObjects A collection of newly prepared ViPR database objects.
     * @param errorMsg An error message.
     * @param taskId The unique task identifier.
     * @param sc A reference to a ServoceCoded.
     */
    private <T extends DataObject> void cleanupFailure(List<TaskResourceRep> taskList, Collection<T> preparedObjects,
            String errorMsg, String taskId, ServiceCoded sc) {
        // Update the task responses to indicate an error occurred and also update
        // the operation status map for the associated resource.
        for (TaskResourceRep taskResourceRep : taskList) {
            taskResourceRep.setState(Operation.Status.error.name());
            taskResourceRep.setMessage(errorMsg);
            _dbClient.error(BlockSnapshotSession.class, taskResourceRep.getResource().getId(), taskId, sc);
        }

        // Mark any newly prepared database objects inactive.
        if (!preparedObjects.isEmpty()) {
            for (DataObject object : preparedObjects) {
                object.setInactive(true);
            }
            _dbClient.updateObject(preparedObjects);
        }
    }

    /**
     * Creates tasks against consistency groups associated with a request and adds them to the given task list.
     * 
     * @param objects
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     * @param <T>
     */
    protected <T extends BlockObject> void addConsistencyGroupTasks(List<T> objects, TaskList taskList, String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        Set<URI> consistencyGroups = new HashSet<>();
        for (T object : objects) {
            if (!isNullURI(object.getConsistencyGroup())) {
                consistencyGroups.add(object.getConsistencyGroup());
            }
        }

        if (consistencyGroups.isEmpty()) {
            return;
        }

        Iterator<BlockConsistencyGroup> groups = _dbClient.queryIterativeObjects(BlockConsistencyGroup.class, consistencyGroups);
        while (groups.hasNext()) {
            BlockConsistencyGroup group = groups.next();
            Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
        }
    }
}

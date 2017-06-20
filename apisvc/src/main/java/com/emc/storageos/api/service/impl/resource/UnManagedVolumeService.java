/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeGuid;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapUnmanagedVolume;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestVolumesExportedSchedulingThread;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestVolumesUnexportedSchedulingThread;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg.BlockCGIngestDecorator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg.BlockRPCGIngestDecorator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg.BlockVolumeCGIngestDecorator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg.BlockVplexCGIngestDecorator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BaseIngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.unmanaged.UnmanagedVolumeReportingUtils;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.ExceptionUtils;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.UnManagedVolumesBulkRep;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.emc.storageos.model.block.VolumeIngest;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.AuditBlockUtil;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.google.common.collect.Collections2;

@Path("/vdc/unmanaged/volumes")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class UnManagedVolumeService extends TaskResourceService {
    public static final String EVENT_SERVICE_TYPE = "block";
    public static final String EVENT_SERVICE_SOURCE = "UnManagedVolumeService";
    public static final String UNMATCHED_VARRAYS = "UnManaged Volume %s cannot be ingested as given VArray is not " +
            "matching with storage pool's connected VArrays. Skipping Ingestion";
    public static final String AUTO_TIERING_NOT_CONFIGURED = "UnManaged Volume %s Auto Tiering Policy %s does not match"
            + " with the Policy %s in given vPool %s. Skipping Ingestion";
    public static final String INGESTION_SUCCESSFUL_MSG = "Successfully ingested volume.";

    private static BlockCGIngestDecorator volumeCGDecorator = null;
    private static BlockCGIngestDecorator vplexCGDecorator = null;
    private static BlockCGIngestDecorator rpCGDecorator = null;

    static {
        rpCGDecorator = new BlockRPCGIngestDecorator();
        vplexCGDecorator = new BlockVplexCGIngestDecorator();
        volumeCGDecorator = new BlockVolumeCGIngestDecorator();

        rpCGDecorator.setNextDecorator(vplexCGDecorator);
        vplexCGDecorator.setNextDecorator(volumeCGDecorator);
    }

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(UnManagedVolumeService.class);

    private IngestStrategyFactory ingestStrategyFactory;

    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        UnManagedVolume unManagedVolume = _dbClient.queryObject(UnManagedVolume.class, id);
        ArgValidator.checkEntityNotNull(unManagedVolume, id, isIdEmbeddedInURL(id));
        return unManagedVolume;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     *
     * Show the details of unmanaged volume.
     *
     * @param id the URN of a ViPR unmanaged volume
     * @prereq none
     * @brief Show unmanaged volume
     * @return UnManagedVolumeRestRep unmanaged volume response
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public UnManagedVolumeRestRep getUnManagedVolumeInfo(@PathParam("id") URI id) {
        UnManagedVolume unManagedVolume = _dbClient.queryObject(UnManagedVolume.class, id);
        ArgValidator.checkEntityNotNull(unManagedVolume, id, isIdEmbeddedInURL(id));
        return map(unManagedVolume);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<UnManagedVolume> getResourceClass() {
        return UnManagedVolume.class;
    }

    /**
     *
     * List data of specified unmanaged volumes.
     *
     * @param param
     *            POST data containing the id list.
     * @prereq none
     * @brief List data of unmanaged volumes
     * @return list of representations.
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public UnManagedVolumesBulkRep getBulkResources(BulkIdParam param) {
        return (UnManagedVolumesBulkRep) super.getBulkResources(param);
    }

    @Override
    public UnManagedVolumesBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<UnManagedVolume> _dbIterator = _dbClient.queryIterativeObjects(
                UnManagedVolume.class, ids);
        return new UnManagedVolumesBulkRep(BulkList.wrapping(_dbIterator, MapUnmanagedVolume.getInstance()));
    }

    @Override
    public UnManagedVolumesBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    /**
     * UnManaged volumes are volumes, which are present within ViPR
     * storage systems, but have not been ingested by ViPR. Volume ingest is the process of
     * moving unmanaged volumes under ViPR management and provides the flexibility of
     * determining which volumes are ingested by ViPR A virtual pool, project, and virtual
     * array must be associated with an unmanaged volume before it can be ingested by ViPR
     * List of supported virtual pools for each unmanaged volume is exposed using
     * /vdc/unmanaged/volumes/bulk. Using unsupported virtual pool would result in an error.
     * Size of unmanaged volumes which can be ingested via a single API Call is limited to
     * 4000.
     *
     * @param param
     *            parameters required for unmanaged volume ingestion
     * @prereq none
     * @brief Ingest unmanaged volumes
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/ingest")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskList ingestVolumes(VolumeIngest param) throws InternalException {
        if (param.getUnManagedVolumes().size() > getMaxBulkSize()) {
            throw APIException.badRequests.exceedingLimit("unmanaged volumes", getMaxBulkSize());
        }
        TaskList taskList = new TaskList();
        Map<String, String> taskMap = new HashMap<String, String>();
        BaseIngestionRequestContext requestContext = null;
        try {
            // Get and validate the project.
            Project project = _permissionsHelper.getObjectById(param.getProject(),
                    Project.class);
            ArgValidator.checkEntity(project, param.getProject(), false);
            // Get and validate the varray
            VirtualArray varray = VolumeIngestionUtil.getVirtualArrayForVolumeCreateRequest(project,
                    param.getVarray(), _permissionsHelper, _dbClient);
            // Get and validate the vpool.
            VirtualPool vpool = VolumeIngestionUtil.getVirtualPoolForVolumeCreateRequest(project, param.getVpool(),
                    _permissionsHelper, _dbClient);
            // allow ingestion for VPool without Virtual Arrays
            if (null != vpool.getVirtualArrays() && !vpool.getVirtualArrays().isEmpty() &&
                    !vpool.getVirtualArrays().contains(param.getVarray().toString())) {
                throw APIException.internalServerErrors.virtualPoolNotMatchingVArray(param.getVarray());
            }
            // check for Quotas
            long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(_dbClient, param.getUnManagedVolumes());

            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
            CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, project, tenant, unManagedVolumesCapacity, "volume");
            _logger.info("UnManagedVolume provisioning quota validation successful for {}", unManagedVolumesCapacity);

            requestContext = new BaseIngestionRequestContext(
                    _dbClient, param.getUnManagedVolumes(), vpool,
                    varray, project, tenant, param.getVplexIngestionMethod());

            while (requestContext.hasNext()) {
                UnManagedVolume unManagedVolume = requestContext.next();
                if (null == unManagedVolume) {
                    _logger.info("No Unmanaged Volume with URI {} found in database. Continuing...",
                            requestContext.getCurrentUnManagedVolumeUri());
                    continue;
                }
                String taskId = UUID.randomUUID().toString();
                Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class,
                        unManagedVolume.getId(), taskId, ResourceOperationTypeEnum.INGEST_VOLUMES);

                TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
                taskList.getTaskList().add(task);
                taskMap.put(unManagedVolume.getId().toString(), taskId);
            }

            IngestVolumesUnexportedSchedulingThread.executeApiTask(
                    _asyncTaskService.getExecutorService(), requestContext, ingestStrategyFactory, this, _dbClient, taskMap, taskList);

        } catch (InternalException e) {
            throw e;
        } catch (Exception e) {
            _logger.debug("Unexpected ingestion exception:", e);
            throw APIException.internalServerErrors.genericApisvcError(ExceptionUtils.getExceptionMessage(e), e);
        }
        return taskList;
    }

    /**
     * Persist the ConsistencyGroups in DB.
     *
     * @param cgsToPersist
     */
    private void persistConsistencyGroups(Collection<BlockConsistencyGroup> cgsToPersist) {
        if (null != cgsToPersist && !cgsToPersist.isEmpty()) {
            List<BlockConsistencyGroup> cgsToCreate = new ArrayList<BlockConsistencyGroup>();
            List<BlockConsistencyGroup> cgsToUpdate = new ArrayList<BlockConsistencyGroup>();
            for (BlockConsistencyGroup cg : cgsToPersist) {
                if (null == cg.getCreationTime()) {
                    cgsToCreate.add(cg);
                } else {
                    cgsToUpdate.add(cg);
                }

            }
            if (!cgsToCreate.isEmpty()) {
                _dbClient.createObject(cgsToCreate);
            }
            if (!cgsToUpdate.isEmpty()) {
                _dbClient.updateObject(cgsToUpdate);
            }
        }

    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.UNMANAGED_VOLUMES;
    }

    /**
     * Ingest Exported Volumes
     * 
     * For each UnManaged Volume Find the list of masking views this volume
     * is exposed to.
     *
     * If only 1 masking view verify if all the initiators are available on
     * the existing MV. Verify the storage Ports are available in given
     * VArray Verify if this export mask is available already If not, then
     * create a new Export Mask with the storage Ports, initiators from
     * ViPr. Else, add volume to export mask.
     *
     * If more than 1 masking view verify if all the initiators are
     * available on all existing MVs. Verify the storage Ports within each
     * Masking view are available in given VArray. Verify if this export
     * mask is available already If not, then create a new Export Mask with
     * the storage Ports, initiators from ViPr. Else, add volume to export
     * mask.
     *
     * @param exportIngestParam
     * @brief Add volumes to new or existing export masks; create masks when needed
     * @return TaskList
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/ingest-exported")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskList ingestExportedVolumes(VolumeExportIngestParam exportIngestParam) throws InternalException {
        TaskList taskList = new TaskList();
        Map<String, TaskResourceRep> taskMap = new HashMap<String, TaskResourceRep>();

        BaseIngestionRequestContext requestContext = null;
        try {
            if (exportIngestParam.getUnManagedVolumes().size() > getMaxBulkSize()) {
                throw APIException.badRequests.exceedingLimit("unmanaged volumes", getMaxBulkSize());
            }

            Project project = _permissionsHelper.getObjectById(exportIngestParam.getProject(), Project.class);
            ArgValidator.checkEntity(project, exportIngestParam.getProject(), false);
            VirtualArray varray = VolumeIngestionUtil.getVirtualArrayForVolumeCreateRequest(project,
                    exportIngestParam.getVarray(), _permissionsHelper, _dbClient);
            VirtualPool vpool = VolumeIngestionUtil.getVirtualPoolForVolumeCreateRequest(project,
                    exportIngestParam.getVpool(), _permissionsHelper, _dbClient);
            // allow ingestion for VPool without Virtual Arrays
            if (null != vpool.getVirtualArrays() && !vpool.getVirtualArrays().isEmpty()
                    && !vpool.getVirtualArrays().contains(exportIngestParam.getVarray().toString())) {
                throw APIException.internalServerErrors.virtualPoolNotMatchingVArray(exportIngestParam.getVarray());
            }
            // check for Quotas
            long unManagedVolumesCapacity = VolumeIngestionUtil.getTotalUnManagedVolumeCapacity(_dbClient,
                    exportIngestParam.getUnManagedVolumes());
            _logger.info("UnManagedVolume provisioning quota validation successful");

            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
            CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, project, tenant,
                    unManagedVolumesCapacity, "volume");
            VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(exportIngestParam.getUnManagedVolumes(),
                    vpool, _dbClient);

            requestContext = new BaseIngestionRequestContext(
                    _dbClient, exportIngestParam.getUnManagedVolumes(), vpool,
                    varray, project, tenant, exportIngestParam.getVplexIngestionMethod());

            while (requestContext.hasNext()) {
                UnManagedVolume unManagedVolume = requestContext.next();

                if (null == unManagedVolume) {
                    _logger.warn("No Unmanaged Volume with URI {} found in database. Continuing...",
                            requestContext.getCurrentUnManagedVolumeUri());
                    continue;
                }

                String taskId = UUID.randomUUID().toString();
                Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class,
                        requestContext.getCurrentUnManagedVolumeUri(),
                        taskId, ResourceOperationTypeEnum.INGEST_EXPORTED_BLOCK_OBJECTS);

                TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
                taskMap.put(unManagedVolume.getId().toString(), task);
            }

            taskList.getTaskList().addAll(taskMap.values());

            // find or create ExportGroup for this set of volumes being ingested
            URI exportGroupResourceUri = null;
            String resourceType = ExportGroupType.Host.name();
            String computeResourcelabel = null;
            if (null != exportIngestParam.getCluster()) {
                resourceType = ExportGroupType.Cluster.name();
                Cluster cluster = _dbClient.queryObject(Cluster.class, exportIngestParam.getCluster());
                exportGroupResourceUri = cluster.getId();
                computeResourcelabel = cluster.getLabel();
                requestContext.setCluster(exportIngestParam.getCluster());
            } else {
                Host host = _dbClient.queryObject(Host.class, exportIngestParam.getHost());
                exportGroupResourceUri = host.getId();
                computeResourcelabel = host.getHostName();
                requestContext.setHost(exportIngestParam.getHost());
            }
            ExportGroup exportGroup = VolumeIngestionUtil.verifyExportGroupExists(requestContext, requestContext.getProject().getId(),
                    exportGroupResourceUri,
                    exportIngestParam.getVarray(), resourceType, _dbClient);
            if (null == exportGroup) {
                _logger.info("Creating Export Group with label {}", computeResourcelabel);
                ResourceAndUUIDNameGenerator nameGenerator = new ResourceAndUUIDNameGenerator();
                exportGroup = VolumeIngestionUtil.initializeExportGroup(requestContext.getProject(), resourceType, 
                        exportIngestParam.getVarray(), computeResourcelabel, _dbClient, 
                        nameGenerator, requestContext.getTenant());
                requestContext.setExportGroupCreated(true);
            }
            requestContext.setExportGroup(exportGroup);
            _logger.info("ExportGroup {} created ", exportGroup.forDisplay());

            IngestVolumesExportedSchedulingThread.executeApiTask(
                    _asyncTaskService.getExecutorService(), requestContext, ingestStrategyFactory, this, _dbClient, taskMap, taskList);

        } catch (InternalException e) {
            _logger.error("InternalException occurred due to: {}", e);
            throw e;
        } catch (Exception e) {
            _logger.error("Unexpected exception occurred due to: {}", e);
            throw APIException.internalServerErrors.genericApisvcError(ExceptionUtils.getExceptionMessage(e), e);
        }

        return taskList;
    }

    /**
     * Commit ingested consistency group
     *
     * @param requestContext request context
     * @param unManagedVolume unmanaged volume to ingest against this CG
     * @throws Exception
     */
    public void commitIngestedCG(IngestionRequestContext requestContext, UnManagedVolume unManagedVolume) throws Exception {

        VolumeIngestionContext volumeContext = requestContext.getVolumeContext();

        // Get the CG's created as part of the ingestion process
        // Iterate through each CG & decorate its objects.
        if (!volumeContext.getCGObjectsToCreateMap().isEmpty()) {
            for (Entry<String, BlockConsistencyGroup> cgEntry : volumeContext.getCGObjectsToCreateMap().entrySet()) {
                BlockConsistencyGroup cg = cgEntry.getValue();
                Collection<BlockObject> allCGBlockObjects = VolumeIngestionUtil.getAllBlockObjectsInCg(cg, requestContext);
                Collection<String> nativeGuids = transform(allCGBlockObjects, fctnBlockObjectToNativeGuid());
                _logger.info("Decorating CG {} with blockObjects {}", cgEntry.getKey(), nativeGuids);
                rpCGDecorator.setDbClient(_dbClient);
                rpCGDecorator.decorate(cg, unManagedVolume, allCGBlockObjects, requestContext);
            }
        }

        persistConsistencyGroups(volumeContext.getCGObjectsToCreateMap().values());

        // Update UnManagedConsistencyGroups.
        if (!volumeContext.getUmCGObjectsToUpdate().isEmpty()) {
            _logger.info("updating {} unmanagedConsistencyGroups in db.");
            _dbClient.updateObject(volumeContext.getUmCGObjectsToUpdate());
        }
    }

    /**
     * group initiators by Protocol
     *
     * @param iniStrList
     * @param dbClient
     * @return
     */
    public Map<String, Set<String>> groupInitiatorsByProtocol(Set<String> iniStrList, DbClient dbClient) {
        Map<String, Set<String>> iniByProtocol = new HashMap<String, Set<String>>();
        List<URI> iniList = new ArrayList<URI>(Collections2.transform(
                iniStrList, CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniList);
        for (Initiator ini : initiators) {
            if (null == ini.getProtocol()) {
                _logger.warn("Initiator {} with protocol set to Null", ini.getId());
                continue;
            }
            if (!iniByProtocol.containsKey(ini.getProtocol())) {
                iniByProtocol.put(ini.getProtocol(), new HashSet<String>());
            }
            iniByProtocol.get(ini.getProtocol()).add(ini.getId().toString());
        }
        return iniByProtocol;

    }

    /**
     * Record volume related event and audit
     *
     * @param dbClient
     *            db client
     * @param opType
     *            operation type
     * @param status
     *            operation status
     * @param evDesc
     *            event description
     * @param extParam
     *            parameters array from which we could generate detail audit message
     */
    public void recordVolumeOperation(DbClient dbClient, OperationTypeEnum opType,
            Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);
            URI uri = (URI) extParam[0];
            recordBourneVolumeEvent(dbClient, evType, status, evDesc, uri);
            String id = uri.toString();
            AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);
        } catch (Exception e) {
            _logger.error("Failed to record volume operation {}, err:", opType.toString(), e);
        }
    }

    /**
     * Generate and Record a Bourne volume specific event
     *
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBourneVolumeEvent(DbClient dbClient,
            String evtType, Operation.Status status, String desc, URI id)
                    throws Exception {
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);
        BlockObject blockObject = null;

        if (URIUtil.isType(id, Volume.class)) {
            blockObject = dbClient.queryObject(Volume.class, id);
        } else if (URIUtil.isType(id, BlockMirror.class)) {
            blockObject = dbClient.queryObject(BlockMirror.class, id);
        } else if (URIUtil.isType(id, BlockSnapshot.class)) {
            blockObject = dbClient.queryObject(BlockSnapshot.class, id);
        }
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(blockObject, evtType,
                        desc, "", dbClient,
                        EVENT_SERVICE_TYPE,
                        RecordType.Event.name(),
                        EVENT_SERVICE_SOURCE);
        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error:",
                    evtType, ex);
        }
    }

    /**
     * Return the OperationTypeEnum based on the volume type.
     *
     * @param blockObj
     * @return
     */
    public OperationTypeEnum getOpByBlockObjectType(BlockObject blockObj) {
        OperationTypeEnum operationType = OperationTypeEnum.CREATE_BLOCK_VOLUME;
        if (blockObj instanceof BlockSnapshot) {
            operationType = OperationTypeEnum.CREATE_VOLUME_SNAPSHOT;
        } else if (blockObj instanceof BlockMirror) {
            operationType = OperationTypeEnum.CREATE_VOLUME_MIRROR;
        }
        return operationType;
    }

    
    
    
    
    
    
    

    /**
     *
     * Show the dependency details of unmanaged volume.
     *
     * @param id the URN of a ViPR unmanaged volume
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN, MediaType.TEXT_HTML })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public String getUnManagedVolumeTree(@PathParam("id") URI id) {
        UnManagedVolume unmanagedVolume = _dbClient.queryObject(UnManagedVolume.class, id);
        ArgValidator.checkEntityNotNull(unmanagedVolume, id, isIdEmbeddedInURL(id));
        return UnmanagedVolumeReportingUtils.renderUnmanagedVolumeDependencyTree(_dbClient, _coordinator, unmanagedVolume);
    }

    /**
     *
     * Show all the unmanaged volumes in a tree format.
     *
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN, MediaType.TEXT_HTML })
    @Path("/tree")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public String getUnManagedVolumeTreeList() {
        return UnmanagedVolumeReportingUtils.renderUnmanagedVolumeDependencyTreeList(_dbClient, _coordinator, null);
    }
    

    /**
     *
     * Show the dependency details of unmanaged volume.
     *
     * @param searchString label filter
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN, MediaType.TEXT_HTML })
    @Path("/tree/{searchString}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public String getUnManagedVolumeTreeListSearch(@PathParam("searchString") String searchString) {
        return UnmanagedVolumeReportingUtils.renderUnmanagedVolumeDependencyTreeList(_dbClient, _coordinator, searchString);
    }
    
    
}

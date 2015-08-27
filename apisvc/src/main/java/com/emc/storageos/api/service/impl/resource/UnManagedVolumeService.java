/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestExportStrategy;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategy;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.ExceptionUtils;
import com.emc.storageos.db.client.util.ExportGroupNameGenerator;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedBulkRep;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
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

@Path("/vdc/unmanaged")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class UnManagedVolumeService extends TaskResourceService {
    public static final String EVENT_SERVICE_TYPE = "block";
    public static final String EVENT_SERVICE_SOURCE = "UnManagedVolumeService";
    public static final String UNMATCHED_VARRAYS = "UnManaged Volume %s cannot be ingested as given VArray is not " +
            "matching with storage pool's connected VArrays. Skipping Ingestion";
    public static final String AUTO_TIERING_NOT_CONFIGURED = "UnManaged Volume %s Auto Tiering Policy %s does not match"
            + " with the Policy %s in given vPool %s. Skipping Ingestion";
    public static final String INGESTION_SUCCESSFUL_MSG = "Successfully ingested volume.";
    public static final DataObject.Flag[] INTERNAL_VOLUME_FLAGS = new DataObject.Flag[] {
            Flag.INTERNAL_OBJECT, Flag.NO_PUBLIC_ACCESS, Flag.NO_METERING };

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
    @Path("/volumes/{id}")
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
    @Path("/volumes/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public UnManagedBulkRep getBulkResources(BulkIdParam param) {
        return (UnManagedBulkRep) super.getBulkResources(param);
    }

    @Override
    public UnManagedBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<UnManagedVolume> _dbIterator = _dbClient.queryIterativeObjects(
                UnManagedVolume.class, ids);
        return new UnManagedBulkRep(BulkList.wrapping(_dbIterator, MapUnmanagedVolume.getInstance()));
    }

    @Override
    public UnManagedBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
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
    @Path("/volumes/ingest")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskList ingestVolumes(VolumeIngest param) throws InternalException {
        if (param.getUnManagedVolumes().size() > getMaxBulkSize()) {
            throw APIException.badRequests.exceedingLimit("unmanaged volumes", getMaxBulkSize());
        }
        
        // TODO: temporary for UI testing
        _logger.info("VPLEX ingestion method is " + param.getVplexIngestionMethod());
        
        TaskList taskList = new TaskList();
        List<UnManagedVolume> unManagedVolumes = new ArrayList<UnManagedVolume>();
        Map<String, String> taskMap = new HashMap<String, String>();
        Map<String, StringBuffer> taskStatusMap = new HashMap<String, StringBuffer>();
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
            Map<String, BlockObject> createdObjectMap = new HashMap<String, BlockObject>();
            Map<String, List<DataObject>> updatedObjectMap = new HashMap<String, List<DataObject>>();
            Map<String, UnManagedVolume> processedUnManagedVolumeMap = new HashMap<String, UnManagedVolume>();
            List<URI> full_pools = new ArrayList<URI>();
            List<URI> full_systems = new ArrayList<URI>();

            Map<String, StorageSystem> systemCache = new HashMap<String, StorageSystem>();
            for (URI unManagedVolumeUri : param.getUnManagedVolumes()) {

                UnManagedVolume unManagedVolume = _dbClient.queryObject(UnManagedVolume.class, unManagedVolumeUri);
                if (null == unManagedVolume) {
                    _logger.info("No unManagedVolume {} found in db. continuing with others", unManagedVolumeUri);
                    continue;
                }
                String taskId = UUID.randomUUID().toString();
                Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class,
                        unManagedVolume.getId(), taskId, ResourceOperationTypeEnum.INGEST_VOLUMES);

                try {
                    _logger.info("Ingestion started for unmanagedvolume {}", unManagedVolume.getNativeGuid());
                    List<URI> volList = new ArrayList<URI>();
                    volList.add(unManagedVolumeUri);
                    VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(volList, vpool, _dbClient);

                    URI storageSystemUri = unManagedVolume.getStorageSystemUri();
                    StorageSystem system = systemCache.get(storageSystemUri.toString());
                    if (null == system) {
                        system = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                        systemCache.put(storageSystemUri.toString(), system);
                    }

                    IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume);
                    // TODO try to find put ways to reduce parameters.
                    @SuppressWarnings("unchecked")
                    BlockObject blockObject = ingestStrategy.ingestBlockObjects(full_systems, full_pools, system, unManagedVolume, vpool,
                            varray,
                            project, tenant, unManagedVolumes, createdObjectMap, updatedObjectMap, false,
                            getBlockObjectClass(unManagedVolume), taskStatusMap);
                    _logger.info("Ingestion ended for unmanagedvolume {}", unManagedVolume.getNativeGuid());
                    if (null == blockObject) {
                        throw IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), "check the logs for more details");
                    }

                    createdObjectMap.put(blockObject.getNativeGuid(), blockObject);
                    processedUnManagedVolumeMap.put(unManagedVolume.getNativeGuid(), unManagedVolume);

                } catch (APIException ex) {
                    _logger.debug("APIException occurred", ex);
                    _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
                } catch (Exception ex) {
                    _logger.debug("Exception occurred", ex);
                    _dbClient.error(UnManagedVolume.class, unManagedVolumeUri,
                            taskId, IngestionException.exceptions.generalVolumeException(
                                    unManagedVolume.getLabel(), ex.getLocalizedMessage()));
                }

                TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
                taskList.getTaskList().add(task);
                taskMap.put(unManagedVolume.getId().toString(), taskId);
            }

            // update the task status
            for (String unManagedVolumeGUID : processedUnManagedVolumeMap.keySet()) {
                UnManagedVolume unManagedVolume = processedUnManagedVolumeMap.get(unManagedVolumeGUID);
                String taskId = taskMap.get(unManagedVolume.getId().toString());
                String taskMessage = "";
                boolean ingestedSuccessfully = false;
                if (unManagedVolume.getInactive()) {
                    ingestedSuccessfully = true;
                    taskMessage = INGESTION_SUCCESSFUL_MSG;
                } else {
                    // check in the created objects for corresponding block object without any internal flags set
                    BlockObject createdObject = createdObjectMap.get(unManagedVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                            VolumeIngestionUtil.VOLUME));
                    if (!createdObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                        ingestedSuccessfully = true;
                        taskMessage = INGESTION_SUCCESSFUL_MSG;
                    } else {
                        ingestedSuccessfully = false;
                        StringBuffer taskStatus = taskStatusMap.get(unManagedVolume.getNativeGuid());
                        if (taskStatus == null) {
                            // No task status found. Put in a default message.
                            taskMessage = String.format("Not all the parent/replicas of unManagedVolume %s have been ingested",
                                    unManagedVolume.getLabel());
                        } else {
                            taskMessage = taskStatus.toString();
                        }
                    }

                }
                if (ingestedSuccessfully) {
                    _dbClient.ready(UnManagedVolume.class,
                            unManagedVolume.getId(), taskId, taskMessage);
                } else {
                    _dbClient.error(UnManagedVolume.class, unManagedVolume.getId(), taskId,
                            IngestionException.exceptions.unmanagedVolumeIsNotVisible(unManagedVolume.getLabel(), taskMessage));
                }
                // Update the related objects if any after ingestion
                List<DataObject> updatedObjects = updatedObjectMap.get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    _dbClient.updateAndReindexObject(updatedObjects);
                }
            }

            _dbClient.createObject(createdObjectMap.values());
            _dbClient.persistObject(unManagedVolumes);

            // record the events after they have been persisted
            for (BlockObject volume : createdObjectMap.values()) {
                recordVolumeOperation(_dbClient, getOpByBlockObjectType(volume),
                        Status.ready, volume.getId());
            }
        } catch (InternalException e) {
            throw e;
        } catch (Exception e) {
            _logger.debug("Unexpected ingestion exception:", e);
            throw APIException.internalServerErrors.genericApisvcError(ExceptionUtils.getExceptionMessage(e), e);
        }
        return taskList;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.UNMANAGED_VOLUMES;
    }

    /**
     * 
     * @param systemMap
     * @param systemCache
     * @param poolCache
     * @param unManagedVolumeUris
     * @param vPool
     * @param virtualArray
     * @param project
     * @param tenant
     * @param unManagedVolumesToBeDeleted
     * @param createdObjectMap
     * @param processedUnManagedVolumeMap
     * @param taskList
     */
    private void ingestBlockObjects(Map<String, StorageSystem> systemMap, List<URI> systemCache, List<URI> poolCache,
            List<URI> unManagedVolumeUris, VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted, Map<String, BlockObject> createdObjectMap,
            Map<String, List<DataObject>> updatedObjectMap,
            Map<String, UnManagedVolume> processedUnManagedVolumeMap, Map<String, TaskResourceRep> taskMap,
            Map<String, StringBuffer> taskStatusMap) {

        for (URI unManagedVolumeUri : unManagedVolumeUris) {
            UnManagedVolume unManagedVolume = _dbClient.queryObject(UnManagedVolume.class,
                    unManagedVolumeUri);
            _logger.info("Ingestion started for exported unmanagedvolume {}", unManagedVolume.getNativeGuid());
            String taskId = UUID.randomUUID().toString();
            Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class,
                    unManagedVolumeUri, taskId, ResourceOperationTypeEnum.INGEST_EXPORTED_BLOCK_OBJECTS);

            try {

                URI storageSystemUri = unManagedVolume.getStorageSystemUri();
                StorageSystem system = systemMap.get(storageSystemUri.toString());
                if (null == system) {
                    system = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                    systemMap.put(storageSystemUri.toString(), system);
                }
                // Build the Strategy , which contains reference to Block object & export orchestrators
                IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume);

                // TODO try to find ways to reduce parameters
                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool,
                        virtualArray,
                        project, tenant, unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap, true,
                        getBlockObjectClass(unManagedVolume), taskStatusMap);

                _logger.info("Ingestion ended for exported unmanagedvolume {}", unManagedVolume.getNativeGuid());
                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            unManagedVolume.getLabel(), "check the logs for more details");
                }

                // TODO come up with a common response object to hold snaps/mirrors/clones
                createdObjectMap.put(blockObject.getNativeGuid(), blockObject);
                processedUnManagedVolumeMap.put(unManagedVolume.getNativeGuid(), unManagedVolume);
            } catch (APIException ex) {
                _logger.warn("error: " + ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
            } catch (Exception ex) {
                _logger.warn("error: " + ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri,
                        taskId, IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), ex.getLocalizedMessage()));
            }

            TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
            taskMap.put(unManagedVolume.getId().toString(), task);
        }
    }

    /**
     * 
     * @param systemMap
     * @param exportIngestParam
     * @param exportGroup
     * @param unManagedVolumesToBeDeleted
     * @param exportGroupCreated
     * @param createdObjectMap
     * @param processedUnManagedVolumeMap
     * @param ingestedObjects
     * @param taskList
     */

    private void ingestBlockExportMasks(Map<String, StorageSystem> systemMap, VolumeExportIngestParam exportIngestParam,
            ExportGroup exportGroup,
            List<UnManagedVolume> unManagedVolumesToBeDeleted, boolean exportGroupCreated, Map<String, BlockObject> createdObjectMap,
            Map<String, List<DataObject>> updatedObjectMap,
            Map<String, UnManagedVolume> processedUnManagedVolumeMap, List<BlockObject> ingestedObjects,
            Map<String, TaskResourceRep> taskMap, Map<String, StringBuffer> taskStatusMap) {
        for (String unManagedVolumeGUID : processedUnManagedVolumeMap.keySet()) {
            String objectGUID = unManagedVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
            BlockObject processedBlockObject = createdObjectMap.get(objectGUID);
            UnManagedVolume processedUnManagedVolume = processedUnManagedVolumeMap.get(unManagedVolumeGUID);
            URI unManagedVolumeUri = processedUnManagedVolume.getId();
            String taskId = taskMap.get(processedUnManagedVolume.getId().toString()).getOpId();
            try {
                if (processedBlockObject == null) {
                    _logger.warn("The ingested block object is null. Skipping ingestion of export masks for unmanaged volume {}",
                            unManagedVolumeGUID);
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }

                URI storageSystemUri = processedUnManagedVolume.getStorageSystemUri();
                StorageSystem system = systemMap.get(storageSystemUri.toString());
                // Build the Strategy , which contains reference to Block object & export orchestrators
                IngestExportStrategy ingestStrategy = ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                BlockObject blockObject = ingestStrategy.ingestExportMasks(processedUnManagedVolume, exportIngestParam, exportGroup,
                        processedBlockObject, unManagedVolumesToBeDeleted, system, exportGroupCreated, null);
                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }
                ingestedObjects.add(blockObject);
                if (blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                    StringBuffer taskStatus = taskStatusMap.get(processedUnManagedVolume.getNativeGuid());
                    String taskMessage = "";
                    if (taskStatus == null) {
                        // No task status found. Put in a default message.
                        taskMessage = String.format("Not all the parent/replicas of unManagedVolume %s have been ingested",
                                processedUnManagedVolume.getLabel());
                    } else {
                        taskMessage = taskStatus.toString();
                    }
                    _dbClient.error(UnManagedVolume.class, processedUnManagedVolume.getId(), taskId,
                            IngestionException.exceptions.unmanagedVolumeIsNotVisible(processedUnManagedVolume.getLabel(), taskMessage));
                } else {
                    _dbClient.ready(UnManagedVolume.class,
                            processedUnManagedVolume.getId(), taskId, "Successfully ingested exported volume and its masks."); // TODO:
                                                                                                                               // convert to
                                                                                                                               // props
                                                                                                                               // message
                }
                // Update the related objects if any after successful export mask ingestion
                List<DataObject> updatedObjects = updatedObjectMap.get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    _dbClient.updateAndReindexObject(updatedObjects);
                }

            } catch (APIException ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
            } catch (Exception ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri,
                        taskId, IngestionException.exceptions.generalVolumeException(
                                processedUnManagedVolume.getLabel(), ex.getLocalizedMessage()));
            }
        }
    }

    /**
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
     * 
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/volumes/ingest-exported")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskList ingestExportedVolumes(VolumeExportIngestParam exportIngestParam) throws InternalException {
        
        // TODO: temporary for UI testing
        _logger.info("VPLEX ingestion method is " + exportIngestParam.getVplexIngestionMethod());
        
        TaskList taskList = new TaskList();
        Map<String, TaskResourceRep> taskMap = new HashMap<String, TaskResourceRep>();
        Map<String, StringBuffer> taskStatusMap = new HashMap<String, StringBuffer>();
        boolean exportGroupCreated = false;
        ExportGroup exportGroup = null;
        // List to hold the block objects which have been fully ingested
        List<BlockObject> ingestedObjects = new ArrayList<BlockObject>();

        try {
            ResourceAndUUIDNameGenerator nameGenerator = new ResourceAndUUIDNameGenerator();
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

            URI exportGroupResourceUri = null;
            String resourceType = ExportGroupType.Host.name();
            String computeResourcelabel = null;
            if (null != exportIngestParam.getCluster()) {
                resourceType = ExportGroupType.Cluster.name();
                Cluster cluster = _dbClient.queryObject(Cluster.class, exportIngestParam.getCluster());
                exportGroupResourceUri = cluster.getId();
                computeResourcelabel = cluster.getLabel();
            } else {
                Host host = _dbClient.queryObject(Host.class, exportIngestParam.getHost());
                exportGroupResourceUri = host.getId();
                computeResourcelabel = host.getHostName();
            }
            ExportGroupNameGenerator gen = new ExportGroupNameGenerator();
            String exportGroupLabel = gen.generate(null, computeResourcelabel, null, '_', 56);
            exportGroup = VolumeIngestionUtil.verifyExportGroupExists(project.getId(), exportGroupResourceUri,
                    varray.getId(), resourceType, _dbClient);
            if (null == exportGroup) {
                _logger.info("Creating Export Group with label {}", exportGroupLabel);
                exportGroup = VolumeIngestionUtil.initializeExportGroup(project, resourceType, varray.getId(),
                        exportGroupLabel, _dbClient, nameGenerator, tenant);
                exportGroupCreated = true;
            }

            // List to hold the unmanaged volumes which have been fully ingested and have been marked as inactive
            List<UnManagedVolume> unManagedVolumes = new ArrayList<UnManagedVolume>();
            // Map to hold the unmanaged volumes which have been processed while ingesting the block objects first
            Map<String, UnManagedVolume> processedUnManagedVolumeMap = new HashMap<String, UnManagedVolume>();
            // Map to hold the block objects which have been ingested. This will hold the ingested block objects with both NO_PUBLIC_ACCESS
            // is true and false.
            Map<String, BlockObject> createdObjectMap = new HashMap<String, BlockObject>();
            // Map to hold the block objects which were updated during ingestion. The data objects can be block objects, export masks,
            // export groups which were updated
            // while ingesting.
            Map<String, List<DataObject>> updatedObjectMap = new HashMap<String, List<DataObject>>();

            List<URI> full_pools = new ArrayList<URI>();
            List<URI> full_systems = new ArrayList<URI>();

            Map<String, StorageSystem> systemCache = new HashMap<String, StorageSystem>();
            _logger.info("Ingestion of unmanaged volumes started....");
            // First ingest the block objects
            ingestBlockObjects(systemCache, full_systems, full_pools, exportIngestParam.getUnManagedVolumes(), vpool, varray, project,
                    tenant,
                    unManagedVolumes, createdObjectMap, updatedObjectMap, processedUnManagedVolumeMap, taskMap, taskStatusMap);
            _logger.info("Ingestion of unmanaged volumes ended....");
            // next ingest the export masks for the unmanaged volumes which have been fully ingested
            _logger.info("Ingestion of unmanaged exportmasks started....");
            ingestBlockExportMasks(systemCache, exportIngestParam, exportGroup, unManagedVolumes, exportGroupCreated,
                    createdObjectMap, updatedObjectMap, processedUnManagedVolumeMap, ingestedObjects, taskMap, taskStatusMap);
            _logger.info("Ingestion of unmanaged exportmasks ended....");
            taskList.getTaskList().addAll(taskMap.values());

            _dbClient.createObject(ingestedObjects);
            _dbClient.persistObject(unManagedVolumes);
            // record the events after they have been persisted
            for (BlockObject volume : ingestedObjects) {
                recordVolumeOperation(_dbClient, getOpByBlockObjectType(volume),
                        Status.ready, volume.getId());
            }
        } catch (InternalException e) {
            _logger.debug("InternalException occurred due to: {}", e);
            throw e;
        } catch (Exception e) {
            _logger.debug("Unexpected exception occurred due to: {}", e);
            throw APIException.internalServerErrors.genericApisvcError(ExceptionUtils.getExceptionMessage(e), e);
        } finally {
            // if we created an ExportGroup, but no volumes were ingested into
            // it, then we should clean it up in the database (CTRL-8520)
            if (exportGroupCreated && ingestedObjects.isEmpty()) {
                _logger.info("an export group was created, but no volumes were ingested into it");
                if (exportGroup.getVolumes() == null || exportGroup.getVolumes().isEmpty()) {
                    _logger.info("since no volumes are present, marking {} for deletion",
                            exportGroup.getLabel());
                    _dbClient.markForDeletion(exportGroup);
                }
            }
        }

        return taskList;
    }

    @SuppressWarnings("rawtypes")
    private Class getBlockObjectClass(UnManagedVolume unManagedVolume) {
        Class blockObjectClass = Volume.class;
        if (VolumeIngestionUtil.isSnapshot(unManagedVolume)) {
            blockObjectClass = BlockSnapshot.class;
        } else if (VolumeIngestionUtil.isMirror(unManagedVolume)) {
            blockObjectClass = BlockMirror.class;
        }

        return blockObjectClass;
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
    private OperationTypeEnum getOpByBlockObjectType(BlockObject blockObj) {
        OperationTypeEnum operationType = OperationTypeEnum.CREATE_BLOCK_VOLUME;
        if (blockObj instanceof BlockSnapshot) {
            operationType = OperationTypeEnum.CREATE_VOLUME_SNAPSHOT;
        } else if (blockObj instanceof BlockMirror) {
            operationType = OperationTypeEnum.CREATE_VOLUME_MIRROR;
        }
        return operationType;
    }
}

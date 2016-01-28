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
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BaseIngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.ExceptionUtils;
import com.emc.storageos.db.client.util.ExportGroupNameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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
    
    private Map <String, DataObject> consistencyGroupObjectsToUpdate = new HashMap<String, DataObject>();
    private Set<BlockConsistencyGroup> blockCGsToCreate = new HashSet<BlockConsistencyGroup>();

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
    @Path("/volumes/ingest")
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

                try {
                    _logger.info("Ingestion started for unmanagedvolume {}", unManagedVolume.getNativeGuid());
                    List<URI> volList = new ArrayList<URI>();
                    volList.add(requestContext.getCurrentUnManagedVolumeUri());
                    VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(volList, vpool, _dbClient);

                    IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume, 
                            !IngestStrategyFactory.DISREGARD_PROTECTION);

                    @SuppressWarnings("unchecked")
                    BlockObject blockObject = ingestStrategy.ingestBlockObjects(requestContext, 
                            VolumeIngestionUtil.getBlockObjectClass(unManagedVolume));

                    _logger.info("Ingestion ended for unmanagedvolume {}", unManagedVolume.getNativeGuid());
                    if (null == blockObject) {
                        throw IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), "check the logs for more details");
                    }

                    requestContext.getObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
                    requestContext.getProcessedUnManagedVolumeMap().put(
                            unManagedVolume.getNativeGuid(), requestContext.getVolumeContext());
                    
                    // If the volume belongs to a consistency group perform consistency group processing 
                    if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
                    	ingestBlockConsistencyGroups(unManagedVolume, blockObject, requestContext);
                    }                                                                                                  

                } catch (APIException ex) {
                    _logger.error("APIException occurred", ex);
                    _dbClient.error(UnManagedVolume.class, requestContext.getCurrentUnManagedVolumeUri(), taskId, ex);
                    requestContext.getVolumeContext().rollback();
                } catch (Exception ex) {
                    _logger.error("Exception occurred", ex);
                    _dbClient.error(UnManagedVolume.class, requestContext.getCurrentUnManagedVolumeUri(),
                            taskId, IngestionException.exceptions.generalVolumeException(
                                    unManagedVolume.getLabel(), ex.getLocalizedMessage()));
                    requestContext.getVolumeContext().rollback();
                }

                TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
                taskList.getTaskList().add(task);
                taskMap.put(unManagedVolume.getId().toString(), taskId);

                requestContext.getVolumeContext().commit();
            }

            // update the task status & update related objects
            for (String unManagedVolumeGUID : requestContext.getProcessedUnManagedVolumeMap().keySet()) {
                UnManagedVolume unManagedVolume = 
                        requestContext.getProcessedUnManagedVolumeMap().get(unManagedVolumeGUID).getUnmanagedVolume();
                String taskId = taskMap.get(unManagedVolume.getId().toString());
                String taskMessage = "";
                boolean ingestedSuccessfully = false;
                if (unManagedVolume.getInactive()) {
                    ingestedSuccessfully = true;
                    taskMessage = INGESTION_SUCCESSFUL_MSG;
                } else {
                    // check in the created objects for corresponding block object without any internal flags set
                    BlockObject createdObject = requestContext.findCreatedBlockObject(unManagedVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                            VolumeIngestionUtil.VOLUME));
                    _logger.info("checking partial ingestion status of block object " + createdObject);
                    if ((null != createdObject) && (!createdObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS) || 
                        // If this is an ingested RP volume in an uningested protection set, the ingest is successful.
                        (createdObject instanceof Volume && ((Volume)createdObject).checkForRp() && ((Volume)createdObject).getProtectionSet() == null)) ||
                        // If this is a successfully processed VPLEX backend volume, it will have the INTERNAL_OBJECT Flag
                        (VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume) && createdObject.checkInternalFlags(Flag.INTERNAL_OBJECT))) {
                        _logger.info("successfully partially ingested block object {} ", createdObject.forDisplay());
                        ingestedSuccessfully = true;
                        taskMessage = INGESTION_SUCCESSFUL_MSG;
                    } else {
                        _logger.info("block object {} was not partially ingested successfully", createdObject.forDisplay());
                        ingestedSuccessfully = false;
                        StringBuffer taskStatus = requestContext.getTaskStatusMap().get(unManagedVolume.getNativeGuid());
                        if (taskStatus == null) {
                            // No task status found. Put in a default message.
                            taskMessage = String.format("Not all the parent/replicas of unmanaged volume %s have been ingested",
                                    unManagedVolume.getLabel());
                        } else {
                            taskMessage = taskStatus.toString();
                        }
                    }
                }

                // task id might be null in the case nested child ingestion contexts,
                // like for VPLEX backend volumes (the only task tracked is for the parent volume)
                if (taskId != null) {
                    if (ingestedSuccessfully) {
                        _dbClient.ready(UnManagedVolume.class,
                                unManagedVolume.getId(), taskId, taskMessage);
                    } else {
                        _dbClient.error(UnManagedVolume.class, unManagedVolume.getId(), taskId,
                                IngestionException.exceptions.unmanagedVolumeIsNotVisible(unManagedVolume.getLabel(), taskMessage));
                    }
                }

                // Update the related objects if any after ingestion
                List<DataObject> updatedObjects = requestContext.getObjectsToBeUpdatedMap().get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    _dbClient.updateObject(updatedObjects);
                }
            }

            _dbClient.createObject(requestContext.getObjectsToBeCreatedMap().values());
            _dbClient.updateObject(requestContext.getUnManagedVolumesToBeDeleted());
            
            // persist any consistency group changes
            if (!consistencyGroupObjectsToUpdate.isEmpty()) {
            	_dbClient.updateObject(consistencyGroupObjectsToUpdate.values());            	
            	// log remaining unmanaged volumes for unmanaged consistency groups
            	for (DataObject unManagedCG : consistencyGroupObjectsToUpdate.values()) {
            		if (unManagedCG instanceof UnManagedConsistencyGroup) {
            			_logger.info(((UnManagedConsistencyGroup) unManagedCG).logRemainingUnManagedVolumes().toString());
            		}
            	}
            }            
            if (!blockCGsToCreate.isEmpty()) {
            	_dbClient.createObject(blockCGsToCreate);
            }

            // record the events after they have been persisted
            for (BlockObject volume : requestContext.getObjectsToBeCreatedMap().values()) {
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
     * @param requestContext
     * @param taskMap
     */
    private void ingestBlockObjects(BaseIngestionRequestContext requestContext, Map<String, TaskResourceRep> taskMap) {

        while (requestContext.hasNext()) {
            UnManagedVolume unManagedVolume = requestContext.next();

            if (null == unManagedVolume) {
                _logger.info("No Unmanaged Volume with URI {} found in database. Continuing...", 
                        requestContext.getCurrentUnManagedVolumeUri());
                continue;
            }

            _logger.info("Ingestion started for exported unmanagedvolume {}", unManagedVolume.getNativeGuid());
            String taskId = UUID.randomUUID().toString();
            Operation operation = _dbClient.createTaskOpStatus(UnManagedVolume.class,
                    requestContext.getCurrentUnManagedVolumeUri(), 
                    taskId, ResourceOperationTypeEnum.INGEST_EXPORTED_BLOCK_OBJECTS);

            try {

                URI storageSystemUri = unManagedVolume.getStorageSystemUri();
                StorageSystem system = requestContext.getStorageSystemCache().get(storageSystemUri.toString());
                if (null == system) {
                    system = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                    requestContext.getStorageSystemCache().put(storageSystemUri.toString(), system);
                }
                // Build the Strategy , which contains reference to Block object & export orchestrators
                IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume, 
                        !IngestStrategyFactory.DISREGARD_PROTECTION);

                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(requestContext, 
                        VolumeIngestionUtil.getBlockObjectClass(unManagedVolume));

                _logger.info("Ingestion ended for exported unmanagedvolume {}", unManagedVolume.getNativeGuid());
                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            unManagedVolume.getLabel(), "check the logs for more details");
                }

                // TODO come up with a common response object to hold snaps/mirrors/clones
                requestContext.getObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
                requestContext.getProcessedUnManagedVolumeMap().put(
                        unManagedVolume.getNativeGuid(), requestContext.getVolumeContext());
                
                // If the volume belongs to a consistency group perform consistency group processing 
                if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
                	ingestBlockConsistencyGroups(unManagedVolume, blockObject, requestContext);
                } 

            } catch (APIException ex) {
                _logger.warn("error: " + ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, 
                        requestContext.getCurrentUnManagedVolumeUri(), taskId, ex);
                requestContext.getVolumeContext().rollback();
            } catch (Exception ex) {
                _logger.warn("error: " + ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, 
                        requestContext.getCurrentUnManagedVolumeUri(),
                        taskId, IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), ex.getLocalizedMessage()));
                requestContext.getVolumeContext().rollback();
            }

            TaskResourceRep task = toTask(unManagedVolume, taskId, operation);
            taskMap.put(unManagedVolume.getId().toString(), task);

            requestContext.getVolumeContext().commit();
        }
    }

    /**
     * 
     * @param requestContext
     * @param taskMap
     * @param exportIngestParam
     */
    private void ingestBlockExportMasks(IngestionRequestContext requestContext, Map<String, TaskResourceRep> taskMap) {
        for (String unManagedVolumeGUID : requestContext.getProcessedUnManagedVolumeMap().keySet()) {
            BlockObject processedBlockObject = requestContext.getProcessedBlockObject(unManagedVolumeGUID);
            VolumeIngestionContext volumeContext = requestContext.getVolumeContext(unManagedVolumeGUID);
            UnManagedVolume processedUnManagedVolume = volumeContext.getUnmanagedVolume();
            URI unManagedVolumeUri = processedUnManagedVolume.getId();
            TaskResourceRep resourceRep = taskMap.get(processedUnManagedVolume.getId().toString());
            String taskId = resourceRep != null ? resourceRep.getOpId() : null;
            try {
                if (processedBlockObject == null) {
                    _logger.warn("The ingested block object is null. Skipping ingestion of export masks for unmanaged volume {}",
                            unManagedVolumeGUID);
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }

                URI storageSystemUri = processedUnManagedVolume.getStorageSystemUri();
                StorageSystem system = requestContext.getStorageSystemCache().get(storageSystemUri.toString());
                // Build the Strategy , which contains reference to Block object & export orchestrators
                IngestExportStrategy ingestStrategy = ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);
                // TODO: get rid of exportIngestParam, deviceInitiators, others in requestContext
                //       when reducing params in the orchestrator interfaces
                BlockObject blockObject = ingestStrategy.ingestExportMasks(processedUnManagedVolume, 
                        processedBlockObject, requestContext);
                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }
                requestContext.getObjectsIngestedByExportProcessing().add(blockObject);

                // task id might be null in the case nested child ingestion contexts,
                // like for VPLEX backend volumes (the only task tracked is for the parent volume)
                if (taskId != null) {
                    // If the ingested object is internal, flag an error.  If it's an RP volume, it's exempt from this check.
                    if (blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS) && 
                            !(blockObject instanceof Volume && ((Volume)blockObject).getRpCopyName() != null)) {
                        StringBuffer taskStatus = requestContext.getTaskStatusMap().get(processedUnManagedVolume.getNativeGuid());
                        String taskMessage = "";
                        if (taskStatus == null) {
                            // No task status found. Put in a default message.
                            taskMessage = String.format("Not all the parent/replicas of unmanaged volume %s have been ingested",
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
                }

                // Update the related objects if any after successful export mask ingestion
                List<DataObject> updatedObjects = requestContext.getObjectsToBeUpdatedMap().get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    _dbClient.updateObject(updatedObjects);
                }
                volumeContext.commit();

            } catch (APIException ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
                volumeContext.rollback();
            } catch (Exception ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri,
                        taskId, IngestionException.exceptions.generalVolumeException(
                                processedUnManagedVolume.getLabel(), ex.getLocalizedMessage()));
                volumeContext.rollback();
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
        TaskList taskList = new TaskList();
        Map<String, TaskResourceRep> taskMap = new HashMap<String, TaskResourceRep>();

        BaseIngestionRequestContext requestContext = null;
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

            requestContext = new BaseIngestionRequestContext(
                    _dbClient, exportIngestParam.getUnManagedVolumes(), vpool, 
                    varray, project, tenant, exportIngestParam.getVplexIngestionMethod());

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

            ExportGroupNameGenerator gen = new ExportGroupNameGenerator();
            String exportGroupLabel = gen.generate(null, computeResourcelabel, null, '_', 56);
            ExportGroup exportGroup = VolumeIngestionUtil.verifyExportGroupExists(project.getId(), exportGroupResourceUri,
                    varray.getId(), resourceType, _dbClient);
            if (null == exportGroup) {
                _logger.info("Creating Export Group with label {}", exportGroupLabel);
                exportGroup = VolumeIngestionUtil.initializeExportGroup(project, resourceType, varray.getId(),
                        exportGroupLabel, _dbClient, nameGenerator, tenant);
                requestContext.setExportGroupCreated(true);
            }
            
            requestContext.setExportGroup(exportGroup);

            _logger.info("Ingestion of exported unmanaged volumes started....");
            
            // First ingest the block objects
            ingestBlockObjects(requestContext, taskMap);
            _logger.info("Ingestion of unmanaged volumes ended....");
            
            // next ingest the export masks for the unmanaged volumes which have been fully ingested
            _logger.info("Ingestion of unmanaged exportmasks started....");
            ingestBlockExportMasks(requestContext, taskMap);
            
            _logger.info("Ingestion of unmanaged exportmasks ended....");
            taskList.getTaskList().addAll(taskMap.values());

            _dbClient.createObject(requestContext.getObjectsIngestedByExportProcessing());
            _dbClient.updateObject(requestContext.getUnManagedVolumesToBeDeleted());
            
            // persist any consistency group changes
            if (!consistencyGroupObjectsToUpdate.isEmpty()) {
            	_dbClient.updateObject(consistencyGroupObjectsToUpdate.values());
            	// log remaining unmanaged volumes for unmanaged consistency groups
            	for (DataObject unManagedCG : consistencyGroupObjectsToUpdate.values()) {
            		if (unManagedCG instanceof UnManagedConsistencyGroup) {
            			_logger.info(((UnManagedConsistencyGroup) unManagedCG).logRemainingUnManagedVolumes().toString());
            		}
            	}
            }            
            if (!blockCGsToCreate.isEmpty()) {
            	_dbClient.createObject(blockCGsToCreate);
            }
            
            // record the events after they have been persisted
            for (BlockObject volume : requestContext.getObjectsIngestedByExportProcessing()) {
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
            if ((null != requestContext) 
                    && requestContext.isExportGroupCreated() 
                    && requestContext.getObjectsIngestedByExportProcessing().isEmpty()) {
                _logger.info("an export group was created, but no volumes were ingested into it");
                if (requestContext.getExportGroup().getVolumes() == null || 
                        requestContext.getExportGroup().getVolumes().isEmpty()) {
                    _logger.info("since no volumes are present, marking {} for deletion",
                            requestContext.getExportGroup().getLabel());
                    _dbClient.markForDeletion(requestContext.getExportGroup());
                }
            }
        }

        return taskList;
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
     
    /**
     * Determines if all the unmanaged volumes belonging to the unmanaged consistency group have been ingested.
     * If they have it creates a block consistency group and marks the unmanaged consistency group inactive.  
     * If all the unmanaged volumes of an unmanaged consistency group have not been ingested it moves the 
     * unmanaged volume passed in from the map of unmanaged volumes to the map of managed volumes. 
     * 
     * @param unManagedVolume - current unmanaged volume being ingested
     * @param blockObject - current volume representing the unmanaged volume being ingested
     * @param requestContext - context containing ingestion information
     * @throws IngestionException
     */
    private void ingestBlockConsistencyGroups(UnManagedVolume unManagedVolume, BlockObject blockObject, BaseIngestionRequestContext requestContext) 
    		throws IngestionException {
    	UnManagedConsistencyGroup unManagedCG = VolumeIngestionUtil.getUnManagedConsistencyGroup(unManagedVolume, consistencyGroupObjectsToUpdate, _dbClient);
    	if (unManagedCG == null) {    			
    		throw IngestionException.exceptions.generalVolumeException(
    				unManagedVolume.getLabel(), "Unable to determine the UnManagedConsistencyGroup for this volume.");
    	}    		
    	_logger.info("Attempting to move volume {} from unmanaged to managed in the unmanaged consistency group {}", unManagedVolume.getLabel(), unManagedCG.getLabel());
    	VolumeIngestionUtil.updateVolumeInUnManagedConsistencyGroup(unManagedCG, unManagedVolume, blockObject);
    	if (VolumeIngestionUtil.allVolumesInUnamangedCGIngested(unManagedCG)) {
    		// all unmanaged volumes have been ingested            			
    		// create block consistency group and remove UnManagedBlockConsistency Group
    		BlockConsistencyGroup consistencyGroup = VolumeIngestionUtil.createCGFromUnManagedCG(unManagedCG, requestContext.getProject(), requestContext.getTenant(), _dbClient);
    		_logger.info("Ingesting the final volume of unmanaged consistency group {}.  Block consistency group {} has been created.", 
    				unManagedCG.getLabel(), consistencyGroup.getLabel());                    			
    		for (String volumeNativeGuid : unManagedCG.getManagedVolumesMap().keySet()) {                    				
    			BlockObject volume  = requestContext.getObjectsToBeCreatedMap().get(volumeNativeGuid);                    				
    			if (volume == null) {                    					
    				// check if the volume has already been ingested
    				String ingestedVolumeURI = unManagedCG.getManagedVolumesMap().get(volumeNativeGuid);
    				volume = _dbClient.queryObject(Volume.class, URI.create(ingestedVolumeURI));
    				if (volume == null) {
    					throw IngestionException.exceptions.generalVolumeException(
    							unManagedVolume.getLabel(), "Unable to locate volume which is part of a consistency group ingestion operation");
    				}
    				_logger.info("Volume {} was ingested as part of previous ingestion operation.", volume.getLabel());

                    // TEMPORARY HACK WJEIV
                    // This change is here to ensure that RP consistency groups are not overwritten by lower-level CGs.
                    // Once Ganga checks in his changes for combined CGs, this code will be removed.
                    if (temporaryOverwriteCG(volume)) {
    				    volume.setConsistencyGroup(consistencyGroup.getId());
    				
    				    // add the volume to the list of cg objects to be 
    				    // updated because it already exists in the database
    				    consistencyGroupObjectsToUpdate.put(volume.getId().toString(), volume);
    				}
    			} else {
                    // TEMPORARY HACK WJEIV
                    // This change is here to ensure that RP consistency groups are not overwritten by lower-level CGs.
                    // Once Ganga checks in his changes for combined CGs, this code will be removed.
    			    if (temporaryOverwriteCG(volume)) {
    			        _logger.info("Adding ingested volume {} to consistency group {}", volume.getLabel(), consistencyGroup.getLabel());
    			        volume.setConsistencyGroup(consistencyGroup.getId());
    			    }
    			}
    		}
    		// All unmanaged volumes have been ingested, remove unmanaged consistency group
    		_logger.info("Removing unmanaged consistency group {}", unManagedCG.getLabel());
    		unManagedCG.setInactive(true);
    		blockCGsToCreate.add(consistencyGroup);    			
    	} else {                    			
    		_logger.info("Updating unmanaged consistency group {}", unManagedCG.getLabel());
    	}
    	consistencyGroupObjectsToUpdate.put(unManagedCG.getId().toString(), unManagedCG);
    }

    // TEMPORARY HACK WJEIV
    // This change is here to ensure that RP consistency groups are not overwritten by lower-level CGs.
    // Once Ganga checks in his changes for combined CGs, this code will be removed.
    private boolean temporaryOverwriteCG(BlockObject volume) {
        boolean overwriteCG = true;
        if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
            BlockConsistencyGroup bcg = _dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
            if (bcg == null || bcg.checkForType(BlockConsistencyGroup.Types.RP)) {
                // This is a CG that is brand new and not saved yet, or it's RP
                overwriteCG = false;
            } 
        }
        return overwriteCG;
    }
    
}

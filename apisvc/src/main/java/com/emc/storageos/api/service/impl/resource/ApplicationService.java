/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.Application;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.ApplicationCreateParam;
import com.emc.storageos.model.application.ApplicationList;
import com.emc.storageos.model.application.ApplicationRestRep;
import com.emc.storageos.model.application.ApplicationUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * APIs to view, create, modify and remove applications
 */

@Path("/applications/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class ApplicationService extends TaskResourceService {
    private static final String APPLICATION_NAME = "name";
    private static final String APPLICATION_ROLES = "roles";
    private static final String EVENT_SERVICE_TYPE = "application";
    private static final Set<String> ALLOWED_SYSTEM_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vplex.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.rp.name(),
            DiscoveredDataObject.Type.srdf.name(),
            DiscoveredDataObject.Type.ibmxiv.name()));
    private static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.ibmxiv.name()));
    private static final String BLOCK = "block";

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        Application application = _permissionsHelper.getObjectById(id, Application.class);
        ArgValidator.checkEntityNotNull(application, id, isIdEmbeddedInURL(id));
        return application;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.APPLICATION;
    }

    @Override
    protected URI getTenantOwner(final URI id) {
        return null;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Create an application
     * 
     * @param param Parameters for creating an application
     * @return created application
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApplicationRestRep createApplication(ApplicationCreateParam param) {
        ArgValidator.checkFieldNotEmpty(param.getName(), APPLICATION_NAME);
        checkDuplicateLabel(Application.class, param.getName(), "Application");
        Set<String> roles = param.getRoles();
        ArgValidator.checkFieldNotEmpty(roles, APPLICATION_ROLES);
        for (String role : roles) {
            ArgValidator.checkFieldValueFromEnum(role, APPLICATION_ROLES, Application.ApplicationRole.class);
        }
        Application application = new Application();
        application.setId(URIUtil.createId(Application.class));
        application.setLabel(param.getName());
        application.setDescription(param.getDescription());
        application.addRoles(param.getRoles());
        _dbClient.createObject(application);
        auditOp(OperationTypeEnum.CREATE_APPLICATION, true, null, application.getId().toString(),
                application.getLabel());
        return DbObjectMapper.map(application);
    }

    /**
     * List an application
     * 
     * @param id Application Id
     * @return ApplicationRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public ApplicationRestRep getApplication(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        return DbObjectMapper.map(application);
    }

    /**
     * List applications.
     * 
     * @return A reference to applicationList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApplicationList getApplications() {
        ApplicationList applicationList = new ApplicationList();

        List<URI> ids = _dbClient.queryByType(Application.class, true);
        Iterator<Application> iter = _dbClient.queryIterativeObjects(Application.class, ids);
        while (iter.hasNext()) {
            applicationList.getApplications().add(toNamedRelatedResource(iter.next()));
        }
        return applicationList;
    }

    /**
     * Delete the application.
     * When an application is deleted it will move to a "marked for deletion" state.
     *
     * @param id the URN of the application
     * @brief Deactivate application
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public Response deactivateApplication(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        ArgValidator.checkReference(Application.class, id, checkForDelete(application));

        if (!getApplicationVolumes(application).isEmpty()) {
            // application could not be deleted if it has volumes
            throw APIException.badRequests.applicationWithVolumesCantBeDeleted(application.getLabel());
        }

        _dbClient.markForDeletion(application);

        auditOp(OperationTypeEnum.DELETE_CONFIG, true, null, id.toString(),
                application.getLabel());
        return Response.ok().build();
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList updateApplication(@PathParam("id") final URI id,
            final ApplicationUpdateParam param) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        if (application.getInactive()) {
            throw APIException.badRequests.applicationCantBeUpdated(application.getLabel(), "The application has been deleted");
        }
        boolean isChanged = false;
        String apname = param.getName();
        if (apname != null && !apname.isEmpty()) {
            checkDuplicateLabel(Application.class, apname, "Application");
            application.setLabel(apname);
            isChanged = true;
        }
        String description = param.getDescription();
        if (description != null && !description.isEmpty()) {
            application.setDescription(description);
            isChanged = true;
        }
        if (isChanged) {
            _dbClient.updateObject(application);
        }
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = null;
        if (!param.hasEitherAddOrRemoveVolumes()) {
            op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_APPLICATION);
            op.ready();
            application.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.updateObject(application);
            TaskResourceRep task = toTask(application, taskId, op);
            taskList.getTaskList().add(task);
            return taskList;
        }
        List<Volume> removeVols = null;
        List<Volume> addVols = null;
        Set<URI> removeVolumeCGs = null;
        Volume firstVol = null;

        if (param.hasVolumesToAdd()) {
            addVols = validateAddVolumes(param, application);
            if (firstVol == null) {
                firstVol = addVols.get(0);
            }
        }
        if (param.hasVolumesToRemove()) {
            removeVolumeCGs = new HashSet<URI>();
            List<URI> removeVolList = param.getRemoveVolumesList().getVolumes();
            removeVols = validateRemoveVolumes(removeVolList, application, removeVolumeCGs);
            if (!removeVols.isEmpty() && firstVol == null) {
                firstVol = removeVols.get(0);
            }
        }

        BlockServiceApi serviceAPI = BlockService.getBlockServiceImpl(firstVol, _dbClient);
        op = _dbClient.createTaskOpStatus(Application.class, application.getId(),
                taskId, ResourceOperationTypeEnum.UPDATE_APPLICATION);
        taskList.getTaskList().add(toTask(application, taskId, op));
        addTasksForVolumesAndCGs(addVols, removeVols, removeVolumeCGs, taskId, taskList);
        try {
            serviceAPI.updateVolumesInApplication(param.getAddVolumesList(), removeVols, id, taskId);
        } catch (InternalException e) {
            op = application.getOpStatus().get(taskId);
            op.error(e);
            application.getOpStatus().updateTaskStatus(taskId, op);
            if (param.hasVolumesToAdd()) {
                List<URI> addURIs = param.getAddVolumesList().getVolumes();
                updateFailedVolumeTasks(addURIs, taskId, e);
            }
            if (param.hasVolumesToRemove()) {
                List<URI> removeURIs = param.getRemoveVolumesList().getVolumes();
                updateFailedVolumeTasks(removeURIs, taskId, e);
                updateFailedCGTasks(removeVolumeCGs, taskId, e);
            }
            throw e;
        }
        auditOp(OperationTypeEnum.UPDATE_APPLICATION, true, null, application.getId().toString(),
                application.getLabel());
        return taskList;
    }

    /**
     * Validate the volumes to be added to the application.
     * All volumes should be the same type (block, or RP, or VPLEX, or SRDF),
     * If the volumes are not in a consistency group, it should specify a CG that the volumes to be add to
     * 
     * @param volumes
     * @return The validated volumes
     */
    private List<Volume> validateAddVolumes(ApplicationUpdateParam param, Application application) {
        String addedVolType = null;
        String firstVolLabel = null;
        List<URI> addVolList = param.getAddVolumesList().getVolumes();
        // URI paramCG = param.getAddVolumesList().getConsistencyGroup();
        List<Volume> volumes = new ArrayList<Volume>();
        for (URI volUri : addVolList) {
            ArgValidator.checkFieldUriType(volUri, Volume.class, "id");
            Volume volume = _dbClient.queryObject(Volume.class, volUri);
            if (volume.getInactive()) {
                throw APIException.badRequests.volumeCantBeAddedToApplication(volume.getLabel(), "The volume has been deleted");
            }
            URI cgUri = volume.getConsistencyGroup();
            if (NullColumnValueGetter.isNullURI(cgUri)) {
                // Do not support not in CG volumes yet.
                throw APIException.badRequests.volumeCantBeAddedToApplication(volume.getLabel(),
                        "The volume is not in CG. Not supported yet");
                /*
                 * if (paramCG == null) {
                 * throw APIException.badRequests.volumeCantBeAddedToApplication(volume.getLabel(),
                 * "Conssitency group is not specified for the volumes not in a consistency group");
                 * }
                 * ArgValidator.checkFieldUriType(paramCG, BlockConsistencyGroup.class, "consistency_group");
                 */

            }
            URI systemUri = volume.getStorageController();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemUri);
            String type = system.getSystemType();
            if (!ALLOWED_SYSTEM_TYPES.contains(type)) {
                throw APIException.badRequests.volumeCantBeAddedToApplication(volume.getLabel(),
                        "The storage system type that the volume created in is not allowed ");
            }
            String volType = getVolumeType(type);
            if (addedVolType == null) {
                addedVolType = volType;
                firstVolLabel = volume.getLabel();
            }
            if (!volType.equals(addedVolType)) {
                throw APIException.badRequests.volumeCantBeAddedToApplication(volume.getLabel(),
                        "The volume type is not same as others");
            }
            volumes.add(volume);
        }
        // Check if the to-add volumes are the same volume type as existing volumes in the application
        List<Volume> existingVols = getApplicationVolumes(application);
        if (!existingVols.isEmpty()) {
            Volume firstVolume = existingVols.get(0);
            URI systemUri = firstVolume.getStorageController();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemUri);
            String type = system.getSystemType();
            String existingType = getVolumeType(type);
            if (!existingType.equals(addedVolType)) {
                throw APIException.badRequests.volumeCantBeAddedToApplication(firstVolLabel,
                        "The volume type is not same as existing volumes in the application");
            }
        }
        return volumes;
    }

    /**
     * Valid the volumes to be removed from the application. Called by updateApplication()
     * 
     * @param volumes the volumes to be removed from application
     * @param application The application
     * @return The validated volumes
     */
    private List<Volume> validateRemoveVolumes(List<URI> volumes, Application application, Set<URI> removeVolumeCGs) {
        List<Volume> removeVolumes = new ArrayList<Volume>();
        for (URI voluri : volumes) {
            ArgValidator.checkFieldUriType(voluri, Volume.class, "id");
            Volume vol = _dbClient.queryObject(Volume.class, voluri);
            if (vol == null) {
                throw APIException.badRequests.applicationCantBeUpdated(voluri.toString(),
                        "The volume does not exist");
            }
            StringSet applications = vol.getApplicationIds();
            if (!applications.contains(application.getId().toString())) {
                throw APIException.badRequests.applicationCantBeUpdated(application.getLabel(),
                        "The volume is not assigned to the application");
            }
            if (vol.getInactive()) {
                continue;
            }
            removeVolumeCGs.add(vol.getConsistencyGroup());

            removeVolumes.add(vol);
        }
        return removeVolumes;
    }

    /**
     * Get Volume type, either block, rp, vplex or srdf
     * 
     * @param type The system type
     * @return
     */
    private String getVolumeType(String type) {
        if (BLOCK_TYPES.contains(type)) {
            return BLOCK;
        } else {
            return type;
        }
    }

    /**
     * Get application volumes
     * 
     * @param application
     * @return The list of volumes in application
     */
    private List<Volume> getApplicationVolumes(Application application) {
        List<Volume> result = new ArrayList<Volume>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByApplicationId(application.getId().toString()));
        for (Volume vol : volumes) {
            if (!vol.getInactive()) {
                result.add(vol);
            }
        }
        return result;
    }

    /**
     * Creates tasks against consistency group associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    private void addConsistencyGroupTask(URI groupUri, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        BlockConsistencyGroup group = _dbClient.queryObject(BlockConsistencyGroup.class, groupUri);
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
    }

    /**
     * Creates tasks against volume associated with a request and adds them to the given task list.
     *
     * @param volume
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    private void addVolumeTask(Volume volume, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
    }

    /**
     * Add task for volumes and consistency groups
     * 
     * @param addVols
     * @param removeVols
     * @param removeVolumeCGs
     * @param taskId
     * @param taskList
     */
    private void addTasksForVolumesAndCGs(List<Volume> addVols, List<Volume> removeVols, Set<URI> removeVolumeCGs,
            String taskId, TaskList taskList) {
        if (addVols != null && !addVols.isEmpty()) {
            for (Volume vol : addVols) {
                addVolumeTask(vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_APPLICATION);
            }
        }
        if (removeVols != null && !removeVols.isEmpty()) {
            for (Volume vol : removeVols) {
                addVolumeTask(vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_APPLICATION);
            }
        }

        if (removeVolumeCGs != null && !removeVolumeCGs.isEmpty()) {
            for (URI cg : removeVolumeCGs) {
                addConsistencyGroupTask(cg, taskList, taskId, ResourceOperationTypeEnum.UPDATE_APPLICATION);
            }
        }
    }

    private void updateFailedVolumeTasks(List<URI> uriList, String taskId, InternalException e) {
        for (URI uri : uriList) {
            Volume vol = _dbClient.queryObject(Volume.class, uri);
            Operation op = vol.getOpStatus().get(taskId);
            if (op != null) {
                op.error(e);
                vol.getOpStatus().updateTaskStatus(taskId, op);
            }
        }
    }

    private void updateFailedCGTasks(Set<URI> uriList, String taskId, InternalException e) {
        for (URI uri : uriList) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, uri);
            Operation op = cg.getOpStatus().get(taskId);
            if (op != null) {
                op.error(e);
                cg.getOpStatus().updateTaskStatus(taskId, op);
            }
        }
    }

}

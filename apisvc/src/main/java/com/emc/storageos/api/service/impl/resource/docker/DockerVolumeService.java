/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.docker;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.service.impl.resource.docker.AbstractDockerVolumeService.getBlockServiceImpl;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.docker.DockerVolumeCreateParams;
import com.emc.storageos.model.docker.PluginActivationResponse;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/docker")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN,
        ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DockerVolumeService extends AbstractDockerVolumeService {

    private static final Logger _log = LoggerFactory.getLogger(DockerVolumeService.class);

    private PlacementManager _placementManager;

    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    @Override
    protected DataObject queryResource(URI id) {
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return null;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/test/{volume_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolume(@PathParam("volume_id") String volumeId) {

        _log.info("Got request for " + volumeId);
        return null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/VolumeDriver.Create")
    public Response createVolume(DockerVolumeCreateParams params) {

        _log.info("Got request for create volume! Options are :");
        VirtualArray vArray = getVarray(params.getOpts().get("varray"));
        VirtualPool requestedVpool = getVPool(params.getOpts().get("vpool"));
        Project project = getProject(params.getOpts().get("project"), getUserFromContext());
        VolumeCreate createVolumeParams = new VolumeCreate(params.getName(), params.getOpts().get("size"),
                Integer.parseInt(params.getOpts().get("count")), requestedVpool.getId(), vArray.getId(),
                project.getId(), null);
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, Integer.parseInt(params.getOpts().get("count")));
        long size = 1073741824;
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, size);
        List recommendations = _placementManager.getRecommendationsForVolumeCreateRequest(vArray, project,
                requestedVpool, capabilities);
        Map<VpoolUse, List<Recommendation>> recommendationsMap = new HashMap<VpoolUse, List<Recommendation>>();
        recommendationsMap.put(VpoolUse.ROOT, recommendations);
        BlockServiceApi api = getBlockServiceImpl(requestedVpool, _dbClient);
        String task = UUID.randomUUID().toString();
        api.createVolumes(createVolumeParams, project, vArray, requestedVpool, recommendationsMap,
                createTaskList(1, project, vArray, requestedVpool, params.getName(), task, 1), task, capabilities);
        return null;
    }

    private TaskList createTaskList(long size, Project project, VirtualArray varray, VirtualPool vpool, String label,
            String task, Integer volumeCount) {
        TaskList taskList = new TaskList();

        // For each volume requested, pre-create a volume object/task object
        // long lsize = SizeUtil.translateSize(size);
        for (int i = 0; i < volumeCount; i++) {
            Volume volume = StorageScheduler.prepareEmptyVolume(_dbClient, size, project, varray, vpool, label, i,
                    volumeCount);
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), task,
                    ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
            volume.getOpStatus().put(task, op);
            TaskResourceRep volumeTask = toTask(volume, task, op);
            taskList.getTaskList().add(volumeTask);
            _log.info(
                    String.format("Volume and Task Pre-creation Objects [Init]--  Source Volume: %s, Task: %s, Op: %s",
                            volume.getId(), volumeTask.getId(), task));
        }

        return taskList;
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/Plugin.Activate")
    public PluginActivationResponse activatePlugin() {
        PluginActivationResponse activationResponse = new PluginActivationResponse("VolumeDriver");
        return activationResponse;
    }

}

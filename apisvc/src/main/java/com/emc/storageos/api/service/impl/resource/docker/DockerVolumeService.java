package com.emc.storageos.api.service.impl.resource.docker;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.cinder.model.VolumeCreateRequestGen;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
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
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/docker")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DockerVolumeService extends TaskResourceService{
	
	private static final Logger _log = LoggerFactory.getLogger(DockerVolumeService.class);
	
	private PlacementManager _placementManager;
	
	public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

	@Override
	protected DataObject queryResource(URI id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected URI getTenantOwner(URI id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ResourceTypeEnum getResourceType() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_id}")
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
		URI vpoolUri;
		URI vArrayUri;
		URI projectUri;
		
		try {
			VolumeCreate createVolumeParams = new VolumeCreate();
			vpoolUri = new URI(params.getOpts().get("vpool"));
			VirtualPool requestedVpool = _dbClient.queryObject(
	                VirtualPool.class, vpoolUri);
			BlockServiceApi api = getBlockServiceImpl(requestedVpool, _dbClient);
			
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
     * Returns the bean responsible for servicing the request
     * 
     * @param vpool Virtual Pool
     * @return block service implementation object
     */
    private static BlockServiceApi getBlockServiceImpl(VirtualPool vpool, DbClient dbClient) {
        // Mutually exclusive logic that selects an implementation of the block service
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.rp.name());
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.vplex.name());
        } else if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());
        } else if (VirtualPool.vPoolSpecifiesMirrors(vpool, dbClient)) {
            return BlockService.getBlockServiceImpl("mirror");
        } else if (vpool.getMultivolumeConsistency() != null && vpool.getMultivolumeConsistency()) {
            return BlockService.getBlockServiceImpl("group");
        }

        return BlockService.getBlockServiceImpl("default");
    }
	
	
	
	protected TaskList newVolume(VolumeCreate volumeCreate, Project project, BlockServiceApi api,
            VirtualPoolCapabilityValuesWrapper capabilities,
            VirtualArray varray, String task, VirtualPool vpool,
            VolumeCreateRequestGen param, int volumeCount, long requestedSize, String name)
    {
        List recommendations = _placementManager.getRecommendationsForVolumeCreateRequest(
                varray, project, vpool, capabilities);
        Map<VpoolUse, List<Recommendation>> recommendationsMap = new HashMap<VpoolUse, List<Recommendation>>();
        recommendationsMap.put(VpoolUse.ROOT, recommendations);

        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getLabel(), varray.getLabel());
        }

        String volname = null;
        if (param.volume.name != null)
            volname = param.volume.name;
        else
            volname = param.volume.display_name;

        auditOp(OperationTypeEnum.CREATE_BLOCK_VOLUME, true, AuditLogManager.AUDITOP_BEGIN,
                volname, volumeCount, varray.getId().toString(), project.getId().toString());

        _log.debug("Block Service API call for : Create New Volume ");
        TaskList passedTaskist = createTaskList(requestedSize, project, varray, vpool, name, task, volumeCount);
        return api.createVolumes(volumeCreate, project, varray, vpool, recommendationsMap, passedTaskist, task,
                capabilities);
    }
	
	private TaskList createTaskList(long size, Project project, VirtualArray varray, VirtualPool vpool, String label, String task,
            Integer volumeCount) {
        TaskList taskList = new TaskList();

        // For each volume requested, pre-create a volume object/task object
        // long lsize = SizeUtil.translateSize(size);
        for (int i = 0; i < volumeCount; i++) {
            Volume volume = StorageScheduler.prepareEmptyVolume(_dbClient, size, project, varray, vpool, label, i, volumeCount);
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(),
                    task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
            volume.getOpStatus().put(task, op);
            TaskResourceRep volumeTask = toTask(volume, task, op);
            taskList.getTaskList().add(volumeTask);
            _log.info(String.format("Volume and Task Pre-creation Objects [Init]--  Source Volume: %s, Task: %s, Op: %s",
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

package com.emc.storageosplugin.model.vce;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;














import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;














import static com.emc.storageosplugin.model.vce.CommonUtils.uri;
import static com.emc.storageosplugin.model.vce.CommonUtils.uris;





public class VCEProvisionHelper {

	private static final String LOGIN_ERROR_MSG = "The EMC ViPR session is expierd, please start new vipr session ";

	
	public TenantObjInfo[] getUserTenants(){
		List<ViPRObjectInfo> tenantInfos = new ArrayList<ViPRObjectInfo>();
		List<TenantOrgRestRep> tenants = ViPRClientUtils.getUserTenants();
		
		for (TenantOrgRestRep tenant : tenants){
			tenantInfos.add(new TenantObjInfo(tenant.getName(),tenant.getId().toString()));
		}
		return tenantInfos.toArray(new TenantObjInfo[tenantInfos.size()]);
	}
	
	public TenantObjInfo getTenantById(String tenantId){
		TenantOrgRestRep tenant = ViPRClientUtils.getTenantById(uri(tenantId));
		return new TenantObjInfo(tenant.getName(),tenant.getId().toString());

	}
	
	
	public ProjectObjInfo[] getUserProjects(String tenantId){
		List<ProjectObjInfo> projectsInfos = new ArrayList<ProjectObjInfo>();
		 List<ProjectRestRep> projects = ViPRClientUtils.getProjects(tenantId);
		
		for (ProjectRestRep project : projects){
			projectsInfos.add(new ProjectObjInfo(project.getName(),project.getId().toString()));
		}
		return projectsInfos.toArray(new ProjectObjInfo[projectsInfos.size()]);
	}
	
	
	public ProjectObjInfo getProjectById(String projectId){
		 ProjectRestRep project = ViPRClientUtils.getProject(projectId);

		return new ProjectObjInfo(project.getName(),project.getId().toString());
	}

	
	

	public VArrayObjInfo[] getVArrays(String tenantId){

		List<VArrayObjInfo> varrayInfos = new ArrayList<VArrayObjInfo>();
		List<VirtualArrayRestRep> varrays = ViPRClientUtils.getVirtualArraysForTenant(tenantId);
		
		for (VirtualArrayRestRep varray : varrays){
			varrayInfos.add(new VArrayObjInfo(varray.getName(),varray.getId().toString()));
		}
		return varrayInfos.toArray(new VArrayObjInfo[varrayInfos.size()]);
	}

	public VArrayObjInfo getVArrayById(String varrayId){
		VirtualArrayRestRep varray = ViPRClientUtils.getVirtualArray(varrayId);
		return new VArrayObjInfo(varray.getName(),varray.getId().toString());
	}
	

	
	public BVPoolObjInfo[] getBlockVPoolsByVirtualArray(String varrayId){

		List<BVPoolObjInfo> blockVpoolInfos = new ArrayList<BVPoolObjInfo>();
		 List<BlockVirtualPoolRestRep> blocVpools = ViPRClientUtils.getBlockVirtualPoolsByVirtualArray(varrayId);
		
		for (BlockVirtualPoolRestRep blocVpool : blocVpools){
			blockVpoolInfos.add(new BVPoolObjInfo(blocVpool.getName(),blocVpool.getId().toString()));
		}
		return blockVpoolInfos.toArray(new BVPoolObjInfo[blockVpoolInfos.size()]);
	}
	
	
	public BVPoolObjInfo getBlockVPool(String vpoolId){

		 BlockVirtualPoolRestRep blocVpool = ViPRClientUtils.getBlockVirtualPoolById(vpoolId);
		 
		 return (new BVPoolObjInfo(blocVpool.getName(),blocVpool.getId().toString()));
	}
	
	
	public VolumeObjectInfo getVolumeById(String volumeId) {
		VolumeRestRep volume = ViPRClientUtils.getVolumeById(volumeId);
		return  (new VolumeObjectInfo(volume.getName(), volume.getId().toString()) );
	}
	

	public VolumeObjectInfo[] getVolumesByProject(String projectId) {
		ArrayList<VolumeObjectInfo> volumeInfos = new ArrayList<VolumeObjectInfo>();
		List<VolumeRestRep> volumes = ViPRClientUtils.getVolumeByProjectId(projectId);

		for (VolumeRestRep volume : volumes){
			volumeInfos.add(new VolumeObjectInfo(volume.getName(), volume.getId().toString()));
			
		}
		return  volumeInfos.toArray(new VolumeObjectInfo[volumeInfos.size()] );
	}
	
	
	public HostOrClusterObjectInfo getHostOrClusterBy(String isCluster, String hostOrclusterId) {
		
		boolean isTypeCluster = Boolean.parseBoolean(isCluster);
		
		if (isTypeCluster){
			return getClusterById(hostOrclusterId);
		} else {
			return getHostById(hostOrclusterId);
			
		}
	}
	
	public HostOrClusterObjectInfo[] getHostOrClustersByTenant(String isCluster, String tenantId) {
		
		boolean isTypeCluster = Boolean.parseBoolean(isCluster);
		
		if (isTypeCluster){
			return getClustersByTenantId(tenantId);
		} else {
			return getHostsByTenantId(tenantId);
		}
	}
	
	public ClusterObjectInfo getClusterById(String clusterId) {
		 ClusterRestRep cluster = ViPRClientUtils.getClusterById(clusterId);
		return  (new ClusterObjectInfo(cluster.getName(), cluster.getId().toString()) );
	}
	
	public ClusterObjectInfo[] getClustersByTenantId(String tenantId) {
		ArrayList<ClusterObjectInfo> clusterInfos = new ArrayList<ClusterObjectInfo>();
		 List<ClusterRestRep> clusters = ViPRClientUtils.getClustersByTenant(tenantId);
			for (ClusterRestRep cluster : clusters){
				clusterInfos.add(new ClusterObjectInfo(cluster.getName(), cluster.getId().toString()));
			}
		return  clusterInfos.toArray(new ClusterObjectInfo[clusterInfos.size()] );
	}
	
	
	public HostObjectInfo getHostById(String hostId) {
		HostRestRep host = ViPRClientUtils.getHostById(hostId);
		return  (new HostObjectInfo(host.getName(), host.getId().toString()) );
	}
	
	public HostObjectInfo[] getHostsByTenantId(String tenantId) {
		ArrayList<HostObjectInfo> hostInfos = new ArrayList<HostObjectInfo>();
		 List<HostRestRep> hosts = ViPRClientUtils.getHostsByTenant(tenantId);
			for (HostRestRep host : hosts){
				hostInfos.add(new HostObjectInfo(host.getName(), host.getId().toString()));
			}
		return  hostInfos.toArray(new HostObjectInfo[hostInfos.size()] );
	}
	
	
	
	public ExportGroupObjectInfo getExportGroupById(String exportgroupId) {
		ExportGroupRestRep exportGroup = ViPRClientUtils.getExportGroupById(exportgroupId);
		return  (new ExportGroupObjectInfo(exportGroup.getName(), exportGroup.getId().toString()) );
	}

	public ClusterInfo createCluster(URI tenantURI, String clusterName) {

		ClusterRestRep clusterRestRep = ViPRClientUtils.createCluster(tenantURI, clusterName);
		return new ClusterInfo(clusterRestRep);

	}
	
	
	public VolumeCreationResult[] 	createVolumes(String varrayId, String projectId, String vpoolId, String volName, String volSize, String count, String consistencyGroupId ){
		VolumesCreationInfo volumesCreationResult = new VolumesCreationInfo();
		
    	boolean isActive = AuthenticationSession.isSessionActive();
    	
    	if (!isActive){
    		volumesCreationResult.setTaskFailed(LOGIN_ERROR_MSG);

			return volumesCreationResult.getResults();
    	}
    	
    	if ( (varrayId == null  || varrayId.isEmpty()) || (projectId == null || projectId.isEmpty()) || (vpoolId == null || vpoolId.isEmpty()) ) {
			String ErrorMsg = " Invalid Varray / Project ? Vpool URI  is required. ";
    		volumesCreationResult.setTaskFailed(ErrorMsg);

			return volumesCreationResult.getResults();
		}
    	
    	

		try {
			
	
			Tasks<VolumeRestRep> volumeTasks = ViPRClientUtils.createVolumes(uri(varrayId), uri(projectId), uri(vpoolId), volName, volSize, Integer.parseInt(count), uri(consistencyGroupId) );
			
			volumesCreationResult.processVolumeRestRep(volumeTasks);
			
			
			return volumesCreationResult.getResults();

			
		} catch (NumberFormatException e) {
    		volumesCreationResult.setTaskFailed(e.getMessage());

			return volumesCreationResult.getResults();
		} 
	 }


	

	public   VolumeExportResult[] createHostOrClusterExport(String varrayId, String projectId, String[] volumeIds, String hlu, String isCluster, String hostOrClusterId) {

		VolumesExportInfo volumeExportResult = new VolumesExportInfo();
    	boolean isActive = AuthenticationSession.isSessionActive();
    	
    	if (!isActive){
			volumeExportResult.setMsg(LOGIN_ERROR_MSG);
			volumeExportResult.setSuccess(false);
			return volumeExportResult.getResults();
    	}
    	
    	if ( (varrayId == null  || varrayId.isEmpty()) || (projectId == null || projectId.isEmpty())  ) {
			String msg = " Invalid Varray / Project ? Vpool URI  is required. ";
			volumeExportResult.setMsg(msg);
			volumeExportResult.setSuccess(false);
			return volumeExportResult.getResults();
		}
    	

    	
    	try {
        	Task<ExportGroupRestRep> exportGrouptask = ViPRClientUtils.createHostOrClusterExport(uri(varrayId), uri(projectId), uris(Arrays.asList(volumeIds)), Integer.parseInt(hlu), Boolean.valueOf(isCluster), uri(hostOrClusterId) );
			
    		volumeExportResult.processVolumeRestRep(exportGrouptask);
    			
    		return volumeExportResult.getResults();
    		
    	} 	catch (NumberFormatException e) {
    		volumeExportResult.setTaskFailed(e.getMessage());

			return volumeExportResult.getResults();
		} 
 	}

	
	
	

}

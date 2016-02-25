package com.emc.storageosplugin.model.vce;


import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.block.export.VolumeUpdateParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.user.UserInfo;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
//import com.emc.storageosplugin.model.ViPRClientFactory;



import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

import static com.emc.storageosplugin.model.vce.ViPRClientFactory.getViprClient;


public class ViPRClientUtils {
	

	
	private static Logger log = Logger.getLogger(ViPRClientUtils.class);
	
	public static final String SYSTEM_AUDITOR = "SYSTEM_AUDITOR";
	public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
	public static final String SYSTEM_MONITOR = "SYSTEM_MONITOR";
	public static final String TENANT_ADMIN = "TENANT_ADMIN";
	public static final String TENANT_APPROVER = "TENANT_APPROVER";
	public static final String SECURITY_ADMIN = "SECURITY_ADMIN";
	public static final String PROJECT_ADMIN = "PROJECT_ADMIN";
	public static final String RESTRICTED_SYSTEM_ADMIN = "RESTRICTED_SYSTEM_ADMIN";
	public static final String RESTRICTED_SECURITY_ADMIN = "RESTRICTED_SECURITY_ADMIN";
	
	//NOT GETTING USER ROLES
	public static boolean isAdminOrSecurity() {
		UserInfo userInfo = getViprClient().getUserInfo();
		List<String> roles = userInfo.getVdcRoles();
        List<String> vdcRoles = userInfo.getVdcRoles();
        
        List<String> homeTenantRoles = userInfo.getHomeTenantRoles();
        roles.addAll(vdcRoles);
        roles.addAll(homeTenantRoles);



		boolean previllaged = false;

		for (String role : roles) {

			log.info("**********USER ROLES ARE " + role);
			if (role.equals(SYSTEM_ADMIN) || role.equals(SECURITY_ADMIN) || role.equals(SYSTEM_ADMIN)) {
				previllaged = true;

			}
		}
		return previllaged;
	}
    

	  public  static TenantOrgRestRep getUserTenant() {
	        URI userTenantId = getViprClient().getUserTenantId();
	        TenantOrgRestRep userBaseTenant = getViprClient().tenants().get(userTenantId);
	        return userBaseTenant;
	    }
 
    public  static List<TenantOrgRestRep> getUserTenants() {
        List<TenantOrgRestRep> tenants = new ArrayList<TenantOrgRestRep>();
        URI userTenantId = getViprClient().getUserTenantId();

        TenantOrgRestRep userBaseTenant = getViprClient().tenants().get(userTenantId);
        tenants.add(userBaseTenant);
        if (isAdminOrSecurity ()){
            tenants.addAll(getSubTenants(userTenantId));
               	
        }
        return tenants;
    }

    public static  List<TenantOrgRestRep> getSubTenants(URI parentTenantId) {
        return getViprClient().tenants().getAllSubtenants(parentTenantId);
    }
    
    public  static TenantOrgRestRep getTenantById(URI tenantId) {
        TenantOrgRestRep tenant = getViprClient().tenants().get(tenantId);
        return tenant;
    }
    

    

    
    public static List<ProjectRestRep> getAllProjects() {
        return  getViprClient().projects().getByUserTenant();
    }
    
    public static List<ProjectRestRep> getProjects(String tenantId) {
        return  getViprClient().projects().getByTenant(uri(tenantId));
    }


    
	public static List<ProjectRestRep> getProjectsByUserTenant() {
	   return  getViprClient().projects().getByUserTenant();
	       		
	}
    
    public static ProjectRestRep getProject(String projectId) {
    	return getViprClient().projects().get(uri(projectId));
    }
    




	public static List<VirtualArrayRestRep> getVirtualArraysForTenant(String tenantId) {
		return getViprClient().varrays().getAll();
	}
	
    public static VirtualArrayRestRep getVirtualArray(String varrayId) {
    	return getViprClient().varrays().get(uri(varrayId));
    }


    
    public static List<BlockVirtualPoolRestRep> getBlockVirtualPoolsByVirtualArray(String varrayId) {
        return getViprClient().blockVpools().getByVirtualArray(uri(varrayId));
    }

    public static BlockVirtualPoolRestRep getBlockVirtualPoolById(String blockVpoolByID) {
        return getViprClient().blockVpools().get(uri(blockVpoolByID));
    }



    public static List<FileVirtualPoolRestRep> getFileVirtualPools() {
        return getViprClient().fileVpools().getAll();
    }

    public static FileVirtualPoolRestRep getFileVirtualPool(String fileVpoolByID) {
        return getViprClient().fileVpools().get(uri(fileVpoolByID));
    }
    
    
    public static List<VolumeRestRep> getVolumeByProjectId(String projectId) {
		 List<VolumeRestRep> volumes = getViprClient().blockVolumes().findByProject(uri(projectId));
		return volumes;
    	
    }
    
    
    public static VolumeRestRep getVolumeById(String volumeId) {
		VolumeRestRep volume = getViprClient().blockVolumes().get(uri(volumeId));
		
		 //getViprClient().blockVolumes().findByProject(projectId);
		
		return volume;
    	
    }
    	


	public static ExportGroupRestRep getExportGroupById(String exportgroupId) {
		ExportGroupRestRep exportGroup = getViprClient().blockExports().get(uri(exportgroupId));
		return exportGroup;
	}
	
	public static ExportGroupRestRep getExportGroupByRef(String ref) {

		ExportGroupRestRep exportGroup = getViprClient().blockExports().get(uri(ref));
		return exportGroup;
	}
	
    public static ClusterRestRep getClusterById(String clusterId) {

        return (clusterId != null) ? getViprClient().clusters().get(uri(clusterId)) : null;
        
    }
    
    public static List<ClusterRestRep> getClustersByTenant(String tenantId) {
    	return getViprClient().clusters().getByTenant(uri(tenantId));
    	
    }
	
	
    public static HostRestRep getHostById(String hostIdId) {

        return (hostIdId != null) ? getViprClient().hosts().get(uri(hostIdId)) : null;
        
    }
    
    public static List<HostRestRep> getHostsByTenant(String tenantId) {
    	return getViprClient().hosts().getByTenant(uri(tenantId));
    	
    }
	
    
    public static ClusterRestRep createCluster(URI tenantURI,String clusterName){
    	
    	//URI tenantURI = ViPRClientUtils.uri(tenantName);
       	ClusterCreateParam clusterCreateParam = new ClusterCreateParam(clusterName);
		return getViprClient().clusters().create(tenantURI, clusterCreateParam);

    }
	


    
    
    public static URI uri(String value)  {
        try {
            return (value != null && value.length() > 0) ? URI.create(value) : null;
        } catch(IllegalArgumentException invalid) {
            return null;
        }
    }
    
    
    public static Tasks<VolumeRestRep> createVolumes(URI varrayId, URI projectId, URI vpoolId, String volName,String volSize,Integer count, URI consistencyGroupId ) {
    	

        
        VolumeCreate create = new VolumeCreate();

		create.setVpool(vpoolId);

		create.setVarray(varrayId);
		create.setProject(projectId);

		create.setName(volName);


		create.setSize(volSize);
        int numberOfVolumes = 1;

        if ((count != null) && (count > 1)) {
            numberOfVolumes = count;
        }
        create.setCount(numberOfVolumes);

        create.setConsistencyGroup(consistencyGroupId);

         Tasks<VolumeRestRep> tasks = getViprClient().blockVolumes().create(create).waitFor();
         
         return tasks;

    }
    
   
 


    public static List<ExportGroupRestRep>  exportVolumes(URI varrayId, URI projectId, URI vpoolId, List<URI> volumeIds, URI hostOrClusterId, boolean isCluster,   Integer hlu ) throws Exception{
    	
        
        // we will need to keep track of the current HLU number
        Integer currentHlu = hlu;
        
    	// the list of exports to return
        List<ExportGroupRestRep> exports = new ArrayList<ExportGroupRestRep>();

        List<URI> newVolumes = new ArrayList<URI>();
        Map<URI, Set<URI>> existingVolumeExports = new HashMap<URI, Set<URI>>();
        
        List<BlockObjectRestRep> blockResources = new ArrayList<BlockObjectRestRep>();
        
		for (URI volumeId: volumeIds){
        	VolumeRestRep volumeRestRep = getViprClient().blockVolumes().get(volumeId);
        	blockResources.add(volumeRestRep);
        }
		
        for (BlockObjectRestRep blockResource : blockResources) {
        	URI volVirualArrayId = ((VolumeRestRep)blockResource).getVirtualArray().getId();
            
            // see if we can find an export that uses this block resource
        	List<ExportGroupRestRep> exportgrps = new ArrayList<ExportGroupRestRep>();
        	
            // see if we can find an export that uses this block resource
       	
        	if (isCluster ){
            	exportgrps = getViprClient().blockExports().findByCluster(hostOrClusterId, projectId, volVirualArrayId);
        		
        	} else {
            	exportgrps = getViprClient().blockExports().findContainingHost(hostOrClusterId, projectId, varrayId);

        	}
        	
        	

        	ExportGroupRestRep macthedExportGrp=null;
            if (blockResource.getId() != null) {
                for (ExportGroupRestRep exportgrp : exportgrps) {
                    if (isVolumeInExportGroup(exportgrp, blockResource.getId() )) {
                    	macthedExportGrp= exportgrp;
                    	break;
                    }
                }
                macthedExportGrp=exportgrps.size() > 0 ? exportgrps.get(0) : null;
            }


            // If the export does not exist for this volume
            if (macthedExportGrp == null) {
                newVolumes.add(blockResource.getId());
            } else { 					// Export exists, check if volume belongs to it
                if (isVolumeInExportGroup(macthedExportGrp, blockResource.getId())) {
                	log.info("export.block.volume.contains.volume "+macthedExportGrp.getId()+" "+blockResource.getId());
                }
                else {

                	updateExportVolumes(macthedExportGrp, blockResource, existingVolumeExports);
                }
                exports.add(macthedExportGrp);
            }
 
        }

            // Bulk update multiple volumes to single export // Entry Set of existingExportGroupId and  new VolumeIDs
            for (Map.Entry<URI, Set<URI>> existingVolumeExport : existingVolumeExports.entrySet()) {

            	Task<ExportGroupRestRep> updatedExportGroups = updateVolumesToExistingExport( existingVolumeExport.getKey(),existingVolumeExport.getValue(), currentHlu);
                log.info("export.block.volume.add.existing "+ updatedExportGroups.getMessage()+ "  "+existingVolumeExport.getValue()+" "+existingVolumeExport.getKey());
                if ((currentHlu != null) && (currentHlu > -1)) {
                    currentHlu += existingVolumeExport.getValue().size();
                }
            }

            // Create new export with multiple volumes that don't belong to an export
            if (!newVolumes.isEmpty()) {
                URI newExportId = null;
                if (hostOrClusterId != null) {
                	
                    Task<ExportGroupRestRep> taskExportGroup = createHostOrClusterExport( varrayId, projectId, newVolumes, currentHlu, isCluster, hostOrClusterId);
                    newExportId = taskExportGroup.getResourceId();
                } 
                
                ExportGroupRestRep export =getViprClient().blockExports().get(newExportId);


                // add this export to the list of exports we will return to the caller
                exports.add(export);
            }            
        
        return exports;    	
    }
    


	public static  Task<ExportGroupRestRep> createHostOrClusterExport(URI varrayId, URI projectId, List<URI> volumeIds, Integer hlu, boolean isCluster, URI hostOrClusterId) {

		
        ExportCreateParam export = new ExportCreateParam();

        export.setVarray(varrayId);
        export.setProject(projectId);
        Integer currentHlu = hlu;

        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                volume.setLun(currentHlu);
            }
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu++;
            }
            export.getVolumes().add(volume);
        }

        if (isCluster) {
        	export.setName(getViprClient().clusters().get(hostOrClusterId).getName());
            export.addCluster(hostOrClusterId);
            export.setType("Cluster");
        }
        else {
        	export.setName(getViprClient().hosts().get(hostOrClusterId).getName());
        	export.addHost(hostOrClusterId);
            export.setType("Host");
        }

        return getViprClient().blockExports().create(export);

	}


	private static Task<ExportGroupRestRep> updateVolumesToExistingExport(URI exportId, Collection<URI> volumeIds, Integer hlu) {
        ExportUpdateParam export = new ExportUpdateParam();
        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        Integer currentHlu = hlu;
        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                volume.setLun(currentHlu);
            }
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu++;
            }
            volumes.add(volume);
        }
        export.setVolumes(new VolumeUpdateParam(volumes, new ArrayList<URI>()));
        return getViprClient().blockExports().update(exportId, export);
		
	}


	public static boolean isVolumeInExportGroup(ExportGroupRestRep exportGroupRep, URI volumeId) {
        if (volumeId == null) {
            return false;
        }

        for (ExportBlockParam param : exportGroupRep.getVolumes()) {
            if (param.getId().equals(volumeId)) {
                return true;
            }
        }

        return false;
    }
    
    
    private static void updateExportVolumes(ExportGroupRestRep export, BlockObjectRestRep volume, Map<URI, Set<URI>> addVolumeExports) {
        // Store mapping of export to volumes that will be bulk updated
        Set<URI> value = addVolumeExports.get(export.getId());
        if (value == null) {
            value = new HashSet<URI>();
            value.add(volume.getId());
        } else {
            value.add(volume.getId());
        }
        addVolumeExports.put(export.getId(), value);
    }


    
}

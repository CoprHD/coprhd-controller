package comet.vipr;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeMigrate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;

public class ViperCaller {

    private ViPRCoreClient client;
    public ViperCaller(){
        client = MigrateClient.getViprClient();
    }
    public List<URI>  getVolumes(){
        
       return client.blockVolumes().listBulkIds();
    }
    
    public List<NamedRelatedVirtualPoolRep> getVpools(){
    	return client.blockVpools().list();
        
    }
    
    public List<String> getHosts(){
    	List<URI> hostURILists = client.hosts().listBulkIds();
    	List<String> hostNameList = null;
    	for( URI hostURI:hostURILists ) {
    		hostNameList.add(hostURI.getHost());
    		
    	}
    	return hostNameList;
    }
    
    public Tasks<VolumeRestRep> createVolume(URI sourceVolumeURI, URI targetVirtualPoolId){
    	
    	
    	URI project = client.blockVolumes().get(sourceVolumeURI).getProject().getId();
    	String sourceVolumeName = client.blockVolumes().get(sourceVolumeURI).getName();
    	String size = client.blockVolumes().get(sourceVolumeURI).getCapacity();
    	List<RelatedResourceRep> resourceList = client.blockVpools().get(targetVirtualPoolId).getVirtualArrays();
    	RelatedResourceRep vArray=resourceList.get(0);
    	VolumeCreate create = new VolumeCreate(sourceVolumeName+"Target_2", size, 1, targetVirtualPoolId, vArray.getId(), project);
    	
    	return client.blockVolumes().create(create);
    	
    	
    }
    
  /*  public boolean exportVolumeURI (URI hostURI, URI targetVolumeURI){
        HostRestRep host = client.hosts().get(hostURI);
        VolumeRestRep volume = client.blockVolumes().get(targetVolumeURI);
        RelatedResourceRep project = volume.getProject();
    	URI projectUri=project.getId();
    	URI vArray = client.blockVolumes().get(targetVolumeURI).getVirtualArray().getId();
    	        
    	List<ExportGroupRestRep> hostExportGroupList = client.blockExports().findByHost(hostURI,projectUri ,vArray);
    	if(hostExportGroupList != null) {
    		
    		for(ExportGroupRestRep export:hostExportGroupList ) {
    			List<ExportBlockParam> volumes = client.blockExports().get(export.getId()).getVolumes();
    			volumes.add(new ExportBlockParam(targetVolumeURI, null));

    			client.blockExports().get(export.getId()).setVolumes(volumes);
    			export.setVolumes(volumes);
    			return true;
    		}
    	}
    		
    	/*List<ExportGroupRestRep> hostExportGroupList = client.blockExports().findContainingHost(hostURI);	
    	if(hostExportGroupList != NULL) {
    		
    		List<ExportBlockParam> volumes = new ExportBlockParam(targetVolumeURI);
    		for(ExportGroupRestRep export:hostExportGroupList ) {
    			client.blockExports().get(export)export.setVirtualArray(client.blockVolumes().get(targetVolumeURI).getVirtualArray().getId());;
    			client.blockExports().get(export)export.setVolumes(volumes);
    			return true;
    		}
    	}
    	return false;
    	
    }
    */
    
    public boolean exportVolumeURI (URI hostURI, URI targetVolumeURI){
        
        URI project = client.blockVolumes().get(targetVolumeURI).getProject().getId();
        URI vArray = client.blockVolumes().get(targetVolumeURI).getVirtualArray().getId();
        
        List<URI> hosts = new ArrayList<URI>();
        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        volumes.add(new VolumeParam(targetVolumeURI));
        hosts.add(hostURI);
        client.blockExports().create(new ExportCreateParam(project, vArray, "sampleExport", "Host", volumes ,null, hosts, null)); 
        return true;
        
    } 
    
    public boolean doExport(URI sourceVolumeURI, URI hostURI, URI targetVirtualPoolId){
        
        
        boolean result =false;
        Tasks<VolumeRestRep> volumeTask = createVolume(sourceVolumeURI, targetVirtualPoolId);
        
        List<VolumeRestRep> volumeList= volumeTask.get();
        
        URI targetVolumeURI = volumeList.get(0).getId();
        
        result = exportVolumeURI (hostURI, targetVolumeURI);
        
        return result;
    }
    
    public Tasks<VolumeRestRep> migrate(URI hostURI, URI sourceVolumeURI, URI targetVolumeURI){
    	
    	return client.blockVolumes().ppmigrate(sourceVolumeURI, new VolumeMigrate(hostURI, sourceVolumeURI, targetVolumeURI));
    }

}

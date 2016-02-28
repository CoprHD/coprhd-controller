package comet.vipr;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeMigrate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
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
    
    public List<URI> getHosts(){
        return client.hosts().listBulkIds();
    }
    
    public Tasks<VolumeRestRep> createVolume(URI sourceVolumeURI, URI targetVirtualPoolId){
    	
    	
    	URI project = client.blockVolumes().get(sourceVolumeURI).getProject().getId();
    	String sourceVolumeName = client.blockVolumes().get(sourceVolumeURI).getName();
    	String size = client.blockVolumes().get(sourceVolumeURI).getCapacity();
    	
    	RelatedResourceRep vArray = ((RelatedResourceRep[]) client.blockVpools().get(targetVirtualPoolId).getVirtualArrays().toArray())[0];
    	
    	VolumeCreate create = new VolumeCreate(sourceVolumeName+"Target", size, 1, targetVirtualPoolId, vArray.getId(), project);
    	
    	return client.blockVolumes().create(create);
    	
    	
    }
    
    public boolean exportVolumeURI (URI hostURI, URI targetVolumeURI){
    	
    	List<ExportGroupRestRep> hostExportGroupList = client.blockExports().findByHost(hostURI, client.hosts().get(hostURI).getProject().getId(), 
    															client.blockVolumes().get(targetVolumeURI).getVirtualArray().getId());
    	if(hostExportGroupList != null) {
    		
    		
    		for(ExportGroupRestRep export:hostExportGroupList ) {
    			List<ExportBlockParam> volumes = client.blockExports().get(export.getId()).getVolumes();
    			volumes.add(new ExportBlockParam(targetVolumeURI, null));
    			client.blockExports().get(export.getId());
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
    	}*/
    	return false;
    	
    }
    
    public Tasks<VolumeRestRep> migrate(URI hostURI, URI sourceVolumeURI, URI targetVolumeURI){
    	
    	return client.blockVolumes().ppmigrate(sourceVolumeURI, new VolumeMigrate(hostURI, sourceVolumeURI, targetVolumeURI));
    }

}

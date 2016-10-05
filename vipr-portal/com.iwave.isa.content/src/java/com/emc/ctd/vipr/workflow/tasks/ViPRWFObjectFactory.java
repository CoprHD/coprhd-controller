package com.emc.ctd.vipr.workflow.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.ctd.workflow.vipr.core.GenericWFInputParams;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.google.gson.Gson;



/*
 * {
	"workflowInputParams": 
	{
		"virtualPool": "urn:storageos:VirtualPool:dbda8252-375b-4129-8e83-71bedd75cdf4:vdc1",
		"virtualArray": "urn:storageos:VirtualArray:265be179-1ed0-42c3-b9a2-5dc1d6fda4eb:vdc1",
		"project": "urn:storageos:Project:3701e6cc-c60f-49c7-906d-831eb8f90b0f:global",
		"volumeName": "MANOJ-WF-VOL",
		"volumeSize": "2GB",
		"count": "1",
		"hostId": "urn:storageos:Host:de6cdbb2-74d1-4903-bc5e-6ceae5429d1f:vdc1",
		"hlu": -1
	}
}
 */
public class ViPRWFObjectFactory {
	
	public static VolumeCreate getVolumeParams(Map<String, Object> wfInputobjectMap){
		Gson gson = new Gson();

		VolumeCreate volumeCreate = gson.fromJson( gson.toJson(wfInputobjectMap), VolumeCreate.class);
		
		return volumeCreate;
		

	}


	public static ExportCreateParam getExportParams(Map<String, Object> wfInputobjectMap, List<URI> volumeIds ) {
		Gson gson = new Gson();
		
		List<VolumeParam> volumeParams =new ArrayList<VolumeParam>();
		
		for (URI volumeId : volumeIds){
			VolumeParam volumeParam = new VolumeParam();
			volumeParam.setId(volumeId);
			volumeParams.add(volumeParam);
		}
		
		wfInputobjectMap.put("volumes", volumeParams);
		ExportCreateParam exportCreateParam =gson.fromJson( gson.toJson(wfInputobjectMap), ExportCreateParam.class);
		
		return exportCreateParam;


        
 
//        
//        ExportParams(URI hostId, URI virtualArray, URI project,
//    			List<String> volumeIds, String volumeId, List<String> snapshotIds,
//    			Integer hlu, Integer minPaths, Integer maxPaths,
//    			Integer pathsPerInitiator)
//        return new ExportParams(CommonUtils.uri(hostId), CommonUtils.uri(varrayID), CommonUtils.uri(projectID),
//        		volumeIds, null, null,Integer.parseInt(hlu), null, null,null);
	}
	
	

}

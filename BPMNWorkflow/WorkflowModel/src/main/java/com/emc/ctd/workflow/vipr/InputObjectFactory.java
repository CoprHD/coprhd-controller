package com.emc.ctd.workflow.vipr;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.ctd.vipr.api.CommonUtils;
import com.emc.ctd.vipr.api.VolumeCreationResult;


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
public class InputObjectFactory {
	
	public static VolumeParams getVolumeParams(Map<String, Object> objectHashMap){
		
        String projectID = (String)objectHashMap.get("project");
        String varrayID = (String)objectHashMap.get("virtualArray");
        String vpoolID = (String)objectHashMap.get("virtualPool");
        String volumeName = (String)objectHashMap.get("volumeName");

        String volumeSize = (String)objectHashMap.get("volumeSize");
        String count = (String)objectHashMap.get("count");
        String consistencyGroupId = (String)objectHashMap.get("consistencyGroup");
        
        return new VolumeParams(CommonUtils.uri(varrayID), CommonUtils.uri(projectID), CommonUtils.uri(vpoolID),
              volumeName, volumeSize, Integer.parseInt(count), CommonUtils.uri(consistencyGroupId));
	}

	public static ExportParams getExportParams(Map<String, Object> objectHashMap, VolumeCreationResult[]  volumeCreationResults) {
		
        String hostId = (String)objectHashMap.get("hostId");
        String varrayID = (String)objectHashMap.get("virtualArray");
        String projectID = (String)objectHashMap.get("project");
        List<String> volumeIds = new ArrayList<String>();
        for (VolumeCreationResult volumeCreationResult : volumeCreationResults){
        	volumeIds.add(volumeCreationResult.getVolId());
        }

        
        String hlu = (String)objectHashMap.get("hlu");

        
 
//        
//        ExportParams(URI hostId, URI virtualArray, URI project,
//    			List<String> volumeIds, String volumeId, List<String> snapshotIds,
//    			Integer hlu, Integer minPaths, Integer maxPaths,
//    			Integer pathsPerInitiator)
    			
        return new ExportParams(CommonUtils.uri(hostId), CommonUtils.uri(varrayID), CommonUtils.uri(projectID),
        		volumeIds, null, null,Integer.parseInt(hlu), null, null,null);
	}
	
	

}

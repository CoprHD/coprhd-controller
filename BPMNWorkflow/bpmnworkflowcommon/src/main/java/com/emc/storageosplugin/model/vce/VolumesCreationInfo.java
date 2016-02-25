package com.emc.storageosplugin.model.vce;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.block.VolumeRestRep;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

public class VolumesCreationInfo extends OperationResult {

	List<VolumeCreationResult> results = new ArrayList<VolumeCreationResult>();

	public void processVolumeRestRep(Tasks<VolumeRestRep> volumeTasks) {
		
		
		
		VolumeCreationResult result = new VolumeCreationResult();

		if(TaskUtils.isAllTasksCompleted(volumeTasks)){
	        for (Task<VolumeRestRep> volumeTask : volumeTasks.getTasks()) {
	        	
	        	VolumeRestRep volume=volumeTask.get();
	        	result.setSuccess(true);
				result.setMsg("Volume created successfully");
				
				result.setVolName(volume.getName());
				result.setNeighborhoodId(volume.getVirtualArray().getId().toString());
				result.setProjectId(volume.getProject().getId().toString());
				result.setVolId(volume.getId().toString());
				result.setVolWWN(volume.getWwn());
				
				results.add(result);
	        }
		}

	}

	public void setTaskFailed(String ErrorMsg) {
		results.clear();
		setSuccess(false);
		setMsg(ErrorMsg);
	}

	public VolumeCreationResult[] getResults() {
		
		return results.toArray(new VolumeCreationResult[results.size()]);
		//return results;
	}
//
//	public void setResults(List<VolumeCreationResult> results) {
//		this.results = results;
//	}
//	

	
	
	
}

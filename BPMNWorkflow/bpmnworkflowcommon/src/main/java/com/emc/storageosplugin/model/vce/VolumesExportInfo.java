package com.emc.storageosplugin.model.vce;


import java.util.ArrayList;
import java.util.List;






import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;



import com.emc.vipr.client.Task;


public class VolumesExportInfo extends OperationResult {

	List<VolumeExportResult> results = new ArrayList<VolumeExportResult>();

	public void processVolumeRestRep(Task<ExportGroupRestRep> exportGrouptask) {

		VolumeExportResult result = new VolumeExportResult();

		if (TaskUtils.isTasksCompleted(exportGrouptask)) {

				ExportGroupRestRep exportRep = exportGrouptask.get();

				if (exportRep != null) {
					
					exportRep.getType();
					exportRep.getClusters();
					exportRep.getHosts();
					exportRep.getInitiators();
					exportRep.getId();
					exportRep.getGeneratedName();
					exportRep.getName();
					exportRep.getVolumes();
				
					List<ExportBlockParam> volumeParams = exportRep.getVolumes();
					for (ExportBlockParam volumeParam : volumeParams) {


							result.setExportGroupID(exportRep.getId().toString());
							result.setExportGroupGeneratedName(exportRep.getGeneratedName());
							result.setExportGroupName(exportRep.getName());
							//t<String> initIds = new HashSet<String>();
							
							


							
							result.setLunID(String.valueOf(volumeParam.getLun()));
							VolumeRestRep exportedVolume = ViPRClientUtils.getVolumeById(volumeParam.getId().toString());
							result.setWwn(exportedVolume.getWwn());
								
							//t<String> iqns = new HashSet<String>();
							
							result.setSuccess(true);
							result.setMsg("Volume exported to "	+ exportRep.getType()+". Please check respective hosts for the correct Lun Id");
						}
					}



				results.add(result);

		}

	}

	public void setTaskFailed(String ErrorMsg) {
		results.clear();
		setSuccess(false);
		setMsg(ErrorMsg);
	}

	public VolumeExportResult[] getResults() {
		return results.toArray(new VolumeExportResult[results.size()]);
		//return results;
	}

	public void setResults(List<VolumeExportResult> results) {
		this.results = results;
	}

	
	
	
}

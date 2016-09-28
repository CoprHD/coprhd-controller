/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.ctd.vipr.workflow.tasks;


import static com.emc.ctd.vipr.api.CommonUtils.uris;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;








import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.ctd.vipr.api.ViPRClientFactory;
import com.emc.ctd.vipr.api.ViPRClientUtils;
import com.emc.ctd.vipr.api.VolumeCreationResult;
import com.emc.ctd.vipr.api.VolumeExportResult;
import com.emc.ctd.vipr.api.VolumesCreationInfo;
import com.emc.ctd.vipr.api.VolumesExportInfo;
import com.emc.ctd.workflow.vipr.ExportParams;
import com.emc.ctd.workflow.vipr.VolumeParams;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;


public class CustomVolumeSampleTest implements FunctionProvider {
	String host = "localhost";
    Integer port = 4443;
    String user = "root";
    String password = "ChangeMe1!";
    
    public CustomVolumeSampleTest(){
    	try {
			ViPRClientFactory.getViprClient("lglbv240.lss.emc.com", 4443, "root", "Dangerous@123");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }


	@Override
	public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

		String params=(String)args.get("InputParams");
		Boolean taskInputWired = new Boolean((String)args.get("TaskInputWired")); 
		String className = (String) args.get("InputParamsClass");
		
		Object obj =null;
		String taskName = (String)args.get("TaskName");
		if ( taskInputWired){
			obj = ps.getObject("StepInputObject");

		} else {
			System.out.println("Input param "+params);
		}
		
		try {
			if (taskName.equals("CreateVolume")) {

				System.out.println("Task executing... " + taskName);
				VolumeCreationResult[] volumeCreateresult = createVolume((VolumeParams) obj);
				if (volumeCreateresult.length > 0) {
					transientVars.put("CurrentStepProcessResult",volumeCreateresult);
					transientVars.put("STATUSCODE", "200");
					List<String> rollback = (List<String>)ps.getObject("WFRollback");
					rollback.add(volumeCreateresult[0].getVolId());
					ps.setObject("WFRollback",rollback);
					
				} else {
					transientVars.put("CurrentStepProcessResult",	volumeCreateresult);
					transientVars.put("STATUSCODE", "-1");
				}

			} else if (taskName.equals("ExportVolume")) {
				System.out.println("Task executing... " + taskName);
				VolumeExportResult[] volumeExportResult = exportVolume((ExportParams) obj);
				if (volumeExportResult.length > 0) {
					transientVars.put("CurrentStepProcessResult",volumeExportResult);
					transientVars.put("STATUSCODE", "-200");
					List<String> rollback = (List<String>)ps.getObject("WFRollback");
					rollback.add(volumeExportResult[0].getExportGroupID());
					ps.setObject("WFRollback",rollback);
				} else {
					transientVars.put("CurrentStepProcessResult",volumeExportResult);
					transientVars.put("STATUSCODE", "-1");
				}

			} else if (taskName.equals("Rollback")) {
				List<String> rollbackObj = (List<String>)ps.getObject("WFRollback");
				rollback(rollbackObj);
			} else {
				System.out.println("Task not found " + taskName);
			}
		} catch (Exception e) {
			e.printStackTrace();
			List<String> rollbackObj = (List<String>)ps.getObject("WFRollback");
			rollback(rollbackObj);

			throw new WorkflowException(e.getMessage());
		}
		

		
	}
	


    private void rollback(List<String> rollbackObjs) {
    	
    	Collections.reverse(rollbackObjs);
    	for (String rollbackObj : rollbackObjs){
    		if (rollbackObj.contains("Volume")){
    			ViPRClientUtils.deactiveVolume(rollbackObj);
    		} else if (rollbackObj.contains("Export")){
    			ViPRClientUtils.deactiveBlockExport(rollbackObj);
    		}
    	}

		
	}


	public VolumeCreationResult[] createVolume(VolumeParams volumeParams)  {
    	VolumesCreationInfo volumesCreationResult = new VolumesCreationInfo();
        Tasks<VolumeRestRep> response = ViPRClientUtils.createVolumes(volumeParams.getVirtualArray(), volumeParams.getProject(), volumeParams.getVirtualPool(),
        		volumeParams.getVolumeName(), volumeParams.getVolumeSize(), volumeParams.getCount(), volumeParams.getConsistencyGroup());
        
        if (volumesCreationResult.isSuccess()){
            volumesCreationResult.processVolumeRestRep(response);
            return volumesCreationResult.getResults();
        } else {
            volumesCreationResult.processVolumeRestRep(response);
            return volumesCreationResult.getResults();
        }
        


    }
    
    

	 public VolumeExportResult[] exportVolume(ExportParams exportParams) throws Exception {
		 
		 VolumesExportInfo volumeExportResult = new VolumesExportInfo();
		 
	        String isCluster = "false";
	        Task<ExportGroupRestRep> response = ViPRClientUtils.createHostOrClusterExport(exportParams.getVirtualArray(), exportParams.getProject(), uris(exportParams.getVolumeIds()), exportParams.getHlu(), Boolean.valueOf(isCluster), exportParams.getHostId());

	        
	        if (volumeExportResult.isSuccess()){
	        	 System.out.println("Volume exported to host ");
	        	volumeExportResult.processVolumeRestRep(response);
	            return volumeExportResult.getResults();
	        } else {
	        	volumeExportResult.processVolumeRestRep(response);
	            return volumeExportResult.getResults();
	        }
	        

	    }
	


	
	
	public static void main(String[] args) throws Exception {
		CustomVolumeSampleTest customSample = new CustomVolumeSampleTest();
		String extenalTaskParam=null;
		customSample.createVolume(null);
	}




}

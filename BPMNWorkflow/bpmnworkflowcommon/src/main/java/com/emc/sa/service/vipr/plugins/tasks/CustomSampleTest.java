/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.plugins.tasks;


import static com.emc.storageosplugin.model.vce.CommonUtils.uri;
import static com.emc.storageosplugin.model.vce.CommonUtils.uris;

import java.util.Arrays;

import com.emc.sa.engine.extension.ExternalTaskApdapterInterface;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.vasa.async.TaskInfo;
import com.emc.storageosplugin.model.vce.CommonUtils;
import com.emc.storageosplugin.model.vce.ViPRClientFactory;
import com.emc.storageosplugin.model.vce.ViPRClientUtils;
import com.emc.storageosplugin.model.vce.VolumeCreationResult;
import com.emc.storageosplugin.model.vce.VolumeExportResult;
import com.emc.storageosplugin.model.vce.VolumesCreationInfo;
import com.emc.storageosplugin.model.vce.VolumesExportInfo;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;


public class CustomSampleTest implements ExternalTaskApdapterInterface {
	String host = "localhost";
    Integer port = 4443;
    String user = "root";
    String password = "ChangeMe1!";
    
    public CustomSampleTest(){
    	
    }

 	@Override
	public void init() throws Exception {
		System.out.println("Docker mount service init");
		
	}
	@Override
	public void precheck() throws Exception {
		System.out.println("Docker mount service Task precheck");
		
	}

	@Override
	public void preLaunch(String externalTaskParam) throws Exception {
		System.out.println("Docker mount service Task preLaunch "+ externalTaskParam);
		
	}	
	@Override
	public TaskInfo executeExternal(String extenalTaskParam) throws Exception {


        System.out.println("Docker mount service Task executeExternal " + extenalTaskParam);
        TaskInfo taskInfo = new TaskInfo();

        System.out.println("Volume Creation begin..");
        VolumeCreationResult[] volumeCreateresult = createVolume(extenalTaskParam);
        System.out.println("Volume Export begin...");
        VolumeExportResult[] exportResults = exportVolume(volumeCreateresult, extenalTaskParam);
       
        System.out.println("Docker volume attach begin...");
        dockerVolumeAttach(extenalTaskParam);
        System.out.println("Docker volume attach end...");

        taskInfo.setProgress(100);
        taskInfo.setTaskState("SUCCESS");
        taskInfo.setResult("Extrenal Custom Task Completed");
        //endSession();
        return taskInfo;
	}
    public VolumeCreationResult[] createVolume(String externalTaskParam) throws Exception {
    	VolumesCreationInfo volumesCreationResult = new VolumesCreationInfo();
    	ViPRClientFactory.getViprClient("10.247.142.203", 4443, "root", "ChangeMe1!");
        String projectID = "urn:storageos:Project:d1ebe4ef-b7a0-4ae2-a923-11819acd7476:global";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Project_ID", String.class);
        String varrayID = "urn:storageos:VirtualArray:f0938660-5007-4294-8b29-763e4ff800e1:vdc1";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Virtual_Array_ID", String.class);
        String vpoolID = "urn:storageos:VirtualPool:1aca7df3-546a-48fd-8ae0-e0b2d8431e55:vdc1";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Block_Virtual_Pool_ID", String.class);
        String volumeName = "MANOJ-2";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Volume_Name", String.class);
        String volumeSize = "2";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Volume_Size", String.class);
        String count = "1";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Count", String.class);       

        String consistencyGroupId = null;

        Tasks<VolumeRestRep> response = ViPRClientUtils.createVolumes(CommonUtils.uri(varrayID), CommonUtils.uri(projectID), CommonUtils.uri(vpoolID),
                volumeName, volumeSize, Integer.parseInt(count), CommonUtils.uri(consistencyGroupId));
       
        volumesCreationResult.processVolumeRestRep(response);
        return volumesCreationResult.getResults();
        /*new VCEProvisionHelper().createVolumes(varrayID, projectID, vpoolID,
                volumeName, volumeSize, count, consistencyGroupId);*/

       // return volResults;

    }
	
	 public VolumeExportResult[] exportVolume(VolumeCreationResult[] volResults, String externalTaskParam) throws Exception {

		 VolumesExportInfo volumeExportResult = new VolumesExportInfo();
	        String hostOrClusterId = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Docker_Host_or_Cluster_ID", String.class);
	        String isCluster = "false";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "Is_Cluster", String.class);
	        String hlu = "-1";//TaskParamParser.getJsonXpathPropert(externalTaskParam, "hlu", String.class);
	        String projectID = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Project_ID", String.class);
	        String varrayID = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Virtual_Array_ID", String.class);

	        String[] volumeIds = new String[volResults.length];

	        for (int i = 0; i < volResults.length; i++) {
	            volumeIds[i] = volResults[i].getVolId();
	            System.out.println("Created Volume ID " + volumeIds[i]);
	        }

	        Task<ExportGroupRestRep> response = ViPRClientUtils.createHostOrClusterExport(uri(varrayID), uri(projectID), uris(Arrays.asList(volumeIds)), Integer.parseInt(hlu), Boolean.valueOf(isCluster), uri(hostOrClusterId));
	        volumeExportResult.processVolumeRestRep(response);
	        /*VolumeExportResult[] export = new VCEProvisionHelper().createHostOrClusterExport(varrayID, projectID, volumeIds,
	                hlu, isCluster, hostOrClusterId);
*/
	        System.out.println("Volume exported to host ");

	       

	        return volumeExportResult.getResults();
	    }
	
	    public void dockerVolumeAttach(String externalTaskParam) throws Exception {

	        String scriptFileName = "/root/lookUpAndMountVolume.sh";
	        
	        String dockerHost = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Docker_Host_IP", String.class);
	        String dockerUser = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Username", String.class);
	        String dockerPassword = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Password", String.class);
	        String dockerContainer = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Container_Name", String.class);
	        String exportedMountPoint = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Exported_Mount_Point", String.class);
	        String containerMountPoint = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Container_Mount_Point", String.class);
	        String dockerImage = TaskParamParser.getJsonXpathPropert(externalTaskParam, "Image", String.class);
	        String dockerVarrayInq = TaskParamParser.getJsonXpathPropert(externalTaskParam, "VARRAY_INQ", String.class);
	        String arrayIP = "10.247.96:169";
	        String args = dockerVarrayInq+" " +arrayIP+ " "+ exportedMountPoint + " " + containerMountPoint + " "+ dockerImage + " " + dockerContainer;
	        
	        
	        String dockerCommand = "sudo sh "+scriptFileName+ " "+ args;
	        

	        SSHCommandExecutor sshExecutor = new SSHCommandExecutor();
	        sshExecutor.executor(dockerHost,dockerUser,dockerPassword,dockerCommand);
	    }
	    

	@Override
	public void postcheck() throws Exception {
		System.out.println("Docker mount service Task postcheck");
		
	}
	@Override
	public void postLaunch(String extenalTaskParam) throws Exception {
		System.out.println("Docker mount service postLuanch "+extenalTaskParam);
		
	}
	@Override
	public void getStatus() throws Exception {
		System.out.println("Docker mount service Task getStatus");
		
	}
	@Override
	public void destroy() {
		System.out.println("Docker mount service Task destroy");
		
	}
	
	
	public static void main(String[] args) throws Exception {
		CustomSampleTest customSample = new CustomSampleTest();
		String extenalTaskParam=null;
		customSample.createVolume(null);
	}


}

package com.emc.ctd.vipr.workflow.tasks;



import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;







import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.FunctionProvider;
import com.opensymphony.workflow.WorkflowException;

public class ViPRTasks implements FunctionProvider {

	@SuppressWarnings({ "rawtypes", "unchecked", "unused" })
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
			if (taskName.equals("CreateVolumeLite")) {
				System.out.println("Task executing... " + taskName);
				List<URI> volumeIds  = createVolume((VolumeCreate) obj);
				if (!volumeIds.isEmpty()) {
					transientVars.put("CurrentStepProcessResult",volumeIds);
					transientVars.put("STATUSCODE", "200");
					List<URI> rollback = (List<URI>)ps.getObject("WFRollback");
					rollback.addAll(volumeIds);
					ps.setObject("WFRollback",rollback);
					
				} else {
					transientVars.put("CurrentStepProcessResult",	volumeIds);
					transientVars.put("STATUSCODE", "-1");
				}
				
				
			} else if (taskName.equals("ExportVolumeLite")) {
				System.out.println("Task executing... " + taskName);
				List<URI> exportIds = exportVolumes((ExportCreateParam) obj);
				if (!exportIds.isEmpty()) {
					transientVars.put("CurrentStepProcessResult",exportIds);
					transientVars.put("STATUSCODE", "-200");
					List<URI> rollback = (List<URI>)ps.getObject("WFRollback");
					rollback.addAll(exportIds);
					ps.setObject("WFRollback",rollback);
				} else {
					transientVars.put("CurrentStepProcessResult",exportIds);
					transientVars.put("STATUSCODE", "-1");
				}
			} else if (taskName.equals("RollbackLite")) {
				List<URI> rollbackObj = (List<URI>)ps.getObject("WFRollback");
				rollback(rollbackObj);
			} else {
				System.out.println("Task not found " + taskName);
			}
		} catch (Exception e) {
			e.printStackTrace();
			List<String> rollbackObj = (List<String>)ps.getObject("WFRollback");

			throw new WorkflowException(e.getMessage());
		}
	}
	
	public List<URI> createVolume(VolumeCreate volumeParams) throws Exception {

		Tasks<VolumeRestRep> tasks = ViPRClientFactory.getViprClient()
				.blockVolumes().create(volumeParams).waitFor();
		List<URI> volumeIds = Lists.newArrayList();
		for (Task<VolumeRestRep> task : tasks.getTasks()) {
			URI volumeId = task.getResourceId();
			volumeIds.add(volumeId);
		}
		return volumeIds;

	}
	
	
	   public List<URI> exportVolumes(ExportCreateParam exportParam) {


		    String type=exportParam.getType();
	    	Cluster cluster=new Cluster();
			Host host=new Host();

			
			List<VolumeParam> volumes=exportParam.getVolumes();
			
			List<URI> volumeIds = new ArrayList<URI>();
			volumeIds.add(volumes.get(0).getId());
			Integer hlu = -1;
			
			List<URI> exportIds = new ArrayList<URI>();
			
			ExportPathParameters exportPathParameters = exportParam.getExportPathParameters();
			if (exportPathParameters != null){
				Integer minPaths=exportPathParameters.getMaxPaths();
				Integer maxPaths=exportPathParameters.getMinPaths();
				Integer pathsPerInitiator=exportPathParameters.getPathsPerInitiator();
				exportPathParameters.getStoragePorts();
				
			}
			
			boolean isHost=type.equals("Host") ? true : false;
			
			
			if (isHost) {
				HostRestRep rep = ViPRClientFactory.getViprClient().hosts().get(exportParam.getHosts().get(0));
				host.setId(rep.getId());
				exportParam.setName(ViPRClientFactory.getViprClient().hosts().get(rep.getId()).getName());
				cluster=null;


	        }
	        else {
	        	ClusterRestRep rep = ViPRClientFactory.getViprClient().clusters().get(exportParam.getClusters().get(0));
	        	cluster.setId(rep.getId());
				exportParam.setName(ViPRClientFactory.getViprClient().clusters().get(rep.getId()).getName());
	        	host=null;
	        }
		   
	        // See if an existing export exists for the host ports
	        ExportGroupRestRep export = null;
	        export = findExportByHostOrCluster(host, cluster, exportParam.getProject(), exportParam.getVarray(), volumes.get(0).getId(),isHost);
	        if (export == null){
	        	 Task<ExportGroupRestRep> task = ViPRClientFactory.getViprClient().blockExports().create(exportParam).waitFor();
	        	 URI exportId = task.getResourceId();
	        	 exportIds.add(exportId);
	        	 return exportIds;
	        } else {
	        	System.out.println("Update not supported");
	        	//ViPRClientFactory.getViprClient().blockExports().update(export.getId(), exportParam);
	        }
			return exportIds;




	    }

	   
	   

	private void rollback(List<URI> rollbackObjs) {

		Collections.reverse(rollbackObjs);
		try {
			Thread.sleep(5000);

			for (URI rollbackObj : rollbackObjs) {
				if (rollbackObj.toString().contains("Volume")) {
					ViPRClientFactory.getViprClient().blockVolumes().deactivate(rollbackObj).waitFor();
					Thread.sleep(5000);
				} else if (rollbackObj.toString().contains("Export")) {
					ViPRClientFactory.getViprClient().blockExports().deactivate(rollbackObj).waitFor();
					Thread.sleep(5000);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
	    
	private ExportGroupRestRep findExportByHostOrCluster(Host host, Cluster cluster, URI project,
			URI varray, URI volume, boolean isHost) {

		List<ExportGroupRestRep> exports;
		if (isHost){
			exports = ViPRClientFactory.getViprClient().blockExports().findByHost(host.getId(), project, varray);
		} else {
			exports = ViPRClientFactory.getViprClient().blockExports().findByCluster(cluster.getId(), project, varray);
		}
		if (volume != null) {
			for (ExportGroupRestRep export : exports) {

				for (ExportBlockParam param : export.getVolumes()) {
					if (param.getId().equals(volume)) {
						return export;
					}
				}

			}
		}
		return exports.isEmpty() ? null : exports.get(0);
	}
}

package com.emc.ctd.workflow.async;



import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//import com.emc.storageos.vasa.NameValuePair;
//import com.emc.storageos.vasa.ObjectFactory;
//import com.emc.storageos.vasa.StorageProfile;
//import com.emc.storageos.vasa.SyncManager;
//import com.emc.storageos.vasa.TaskInfo;
//import com.emc.storageos.vasa.TaskStateEnum;
//import com.emc.storageos.vasa.VasaServicePortType;
//import com.emc.storageos.vasa.sync.client.NativeVasaClientFactory;
//import com.emc.storageos.vasa.sync.client.VasaClientProtocol;


public class TaskAsyncJobWorker extends AsyncJobCallback<TaskInfo> {
	
	String taskId;
	
    public TaskAsyncJobWorker(String taskId) {
        super(Executors.newSingleThreadExecutor());
        this.taskId = taskId;
    }

    @Override
    public TaskInfo executeSynchronous(String jobIdentifier) throws AsyncJobException {
        
         
        TaskInfo taskinfo=null;
		try {
			//taskinfo = new VasaClientProtocol().testVirtualVolume(containerId,vvolType,storageProfile,sizeInMB,metadata,containerCookie);
			
//			VasaServicePortType trustedClientSession = NativeVasaClientFactory.getInstance().getTrusedClientSession();
//			
//			taskinfo = new VasaClientProtocol().getTaskUpdate(trustedClientSession, taskId);
//			String viprArrayId = SyncManager.getInstance().getArrayId();
//			//createTaskInfoArrayId
//			taskinfo.setArrayId(new ObjectFactory().createTaskInfoArrayId(viprArrayId));
			
//			if(taskinfo.getTaskState().equals(TaskStateEnum.SUCCESS.value()) && taskinfo.getName().equals("createVirtualVolume") ) {
//				VVOLMetadataStore.getInstance().updateVVVolsinStore(taskinfo.getTaskId(), (String)taskinfo.getResult().getValue());
//				
//			}
			
			
		} catch (Exception e) {
			throw new AsyncJobException(e.getMessage(),e.getCause());

		}
        
        return taskinfo;

    }



}
package com.emc.storageos.api.service.impl.resource;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CreateFileSystemSchedulingThread implements Runnable  {
	static final Logger _log = LoggerFactory.getLogger(CreateFileSystemSchedulingThread.class);
	private final FileService fileService;
    private VirtualArray varray;
    private Project project;
    private VirtualPool vpool;
    private VirtualPoolCapabilityValuesWrapper capabilities;
    private TaskList taskList;
    private String task;
    private ArrayList<String> requestedTypes;
    private FileSystemParam param;
    private FileServiceApi fileServiceImpl;

    public CreateFileSystemSchedulingThread(FileService fileService, VirtualArray varray, Project project,
            VirtualPool vpool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskList taskList, String task, ArrayList<String> requestedTypes,
                FileSystemParam param,
            FileServiceApi blockServiceImpl) {
    	
		this.fileService = fileService;
		this.varray = varray;
		this.project = project;
		this.vpool = vpool;
		this.capabilities = capabilities;
		this.taskList = taskList;
		this.task = task;
		this.requestedTypes = requestedTypes;
		this.param = param;
		this.fileServiceImpl = blockServiceImpl;
    }
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
            List recommendations = this.fileService._filePlacementManager.getRecommendationsForFileCreateRequest(
                    varray, project, vpool, capabilities);

            if (recommendations.isEmpty()) {
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(), varray.getId());
            }



            // Call out to the respective file service implementation to prepare
            // and create the fileshares based on the recommendations.
            fileServiceImpl.createFileSystems(param, project, varray, vpool, recommendations, taskList, task, capabilities);
        } catch (Exception ex) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                if (ex instanceof ServiceCoded) {
                    this.fileService._dbClient.error(FileShare.class, taskObj.getResource().getId(), taskObj.getOpId(), (ServiceCoded) ex);
                } else {
                    this.fileService._dbClient.error(FileShare.class, taskObj.getResource().getId(), taskObj.getOpId(),
                            InternalServerErrorException.internalServerErrors
                                    .unexpectedErrorVolumePlacement(ex));
                }
                _log.error(ex.getMessage(), ex);
                taskObj.setMessage(ex.getMessage());
                // Set the fileshare to inactive
                FileShare file = this.fileService._dbClient.queryObject(FileShare.class, taskObj.getResource().getId());
                file.setInactive(true);
                this.fileService._dbClient.updateAndReindexObject(file);
            }
        }
        _log.info("Ending scheduling/placement thread...");
		
	}
	
}

package com.emc.storageos.api.service.impl.placement;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.api.service.impl.resource.AbstractFileServiceApiImpl;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class DefaultFileServiceApiImpl extends AbstractFileServiceApiImpl<FileStorageScheduler>  {
	 private static final Logger _log = LoggerFactory.getLogger(DefaultFileServiceApiImpl.class);

	public DefaultFileServiceApiImpl(String protectionType) {
		super(protectionType);
		// TODO Auto-generated constructor stub
	}
	
	@Override
    public TaskList createFileSystems(FileSystemParam param, Project project, 
    		VirtualArray varray, VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags,
    		List<Recommendation> recommendations, TaskList taskList, String task, 
    		VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
		List<FileShare> fileList = null;
		List<FileShare> fileShares = new ArrayList<FileShare>();
		FileRecommendation placement = (FileRecommendation)recommendations.get(0);
		
		fileList = _scheduler.prepareFileSystem(param, task, taskList, project, 
				varray, vpool, recommendations, vpoolCapabilities, false);
		
//		_log.info(String.format(
//                "createFileSystem --- FileShare: %1$s, StoragePool: %2$s, StorageSystem: %3$s",
//                fileShare.getId(), placement.getSourceStoragePool(), placement.getSourceStorageSystem()));
		
		
		fileShares.addAll(fileList);
		//prepare the file descriptors
		final List<FileDescriptor> fileDescriptors = prepareFileDescriptors(fileShares, vpoolCapabilities, null, null);
		final FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);

        try {
            // Execute the volume creations requests
            controller.createFileSystems(fileDescriptors, task);
        } catch (InternalException e) {
            _log.error("Controller error when creating filesystems", e);
            //failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        } catch (Exception e) {
            _log.error("Controller error when creating filesystems", e);
            //failVolumeCreateRequest(task, taskList, preparedVolumes, e.getMessage());
            throw e;
        }
        return taskList;
       
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, String task) throws InternalException {

    }
    
    private List<FileDescriptor> prepareFileDescriptors(List<FileShare> filesystems, 
    		VirtualPoolCapabilityValuesWrapper cosCapabilities, String suggestedId, String migrationId) {

    	
        // Build up a list of FileDescriptors based on the fileshares
        final List<FileDescriptor> fileDescriptors = new ArrayList<FileDescriptor>();
        for (FileShare filesystem : filesystems) {
            FileDescriptor desc = new FileDescriptor(FileDescriptor.Type.FILE_DATA,
                    filesystem.getStorageDevice(), filesystem.getId(),
                    filesystem.getPool(), filesystem.getCapacity(), cosCapabilities, null, suggestedId);
            fileDescriptors.add(desc);
        }

        return fileDescriptors;
    }

}

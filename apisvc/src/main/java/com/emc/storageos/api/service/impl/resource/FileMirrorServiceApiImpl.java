package com.emc.storageos.api.service.impl.resource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileRPSchedular;
import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.file.FileCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class FileMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileRPSchedular>{
	
	

	private static final Logger _log = LoggerFactory.getLogger(FileMirrorServiceApiImpl.class);
	public FileMirrorServiceApiImpl() {
        super(null);
    }
	
	private DefaultFileServiceApiImpl _defaultFileServiceApiImpl;

    
	
	

	public DefaultFileServiceApiImpl getDefaultFileServiceApiImpl() {
		return _defaultFileServiceApiImpl;
	}

	public void setDefaultFileServiceApiImpl(
			DefaultFileServiceApiImpl defaultFileServiceApiImpl) {
		this._defaultFileServiceApiImpl = defaultFileServiceApiImpl;
	}

	@Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray, 
    		VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, 
    		List<Recommendation> recommendations, 
    		TaskList taskList, String taskId, 
    		VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
		List<URI> fsURIs = prepareRecommendedFileSystems(param, taskId, taskList, project, varray, vpool, flags, recommendations, vpoolCapabilities);
		
		// get file orchestration controller 
		final FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
		try {
			//prepare the file descriptors
			List<FileDescriptor> fileDescriptors = createFileDescriptors(recommendations.get(0), fsURIs, vpoolCapabilities);
            
            // Log volume descriptor information
            //logVolumeDescriptorPrecreateInfo(volumeDescriptors, task);
            
			//execute the call
            controller.createFileSystems(fileDescriptors, taskId);
            
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Controller error", e);
            }

            String errorMsg = String.format("Controller error: %s", e.getMessage());
            if (fsURIs != null) {
                for (URI fileURI : fsURIs) {
                    FileShare fileShare = _dbClient.queryObject(FileShare.class, fileURI);
//                    if (fileShare != null) {
//                        Operation op = new Operation();
//                        ServiceCoded coded = ServiceError.buildServiceError(
//                                ServiceCode.API_RP_VOLUME_CREATE_ERROR, errorMsg.toString());
//                        op.setMessage(errorMsg);
//                        op.error(coded);
//                        _dbClient.createTaskOpStatus(FileShare.class, fileURI, taskId, op);
//                        TaskResourceRep volumeTask = toTask(fileShare, taskId, op);
//                        if (fileShare.getPersonality() != null
//                                && fileShare.getPersonality().equals(
//                                        Volume.PersonalityTypes.SOURCE.toString())) {
//                            taskList.getTaskList().add(volumeTask);
//                        }
//                    }
                }
            }

            // If there was a controller error creating the volumes,
            // throw an internal server error and include the task
            // information in the response body, which will inform
            // the user what succeeded and what failed.
            throw APIException.badRequests.cannotCreateSRDFVolumes(e);
        }
        return null;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, boolean forceDelete, String task) throws InternalException {

    }
    
    private List<URI> prepareRecommendedFileSystems(final FileSystemParam param, final String task,
            final TaskList taskList, final Project project, final VirtualArray varray,
            final VirtualPool vpool, DataObject.Flag[] flags,
            final List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) {
    	List<URI> fileSystemURIs = new ArrayList<URI>();
        return fileSystemURIs;

    	
    }
    
    /**
     * Prep work to call the orchestrator to create the file descriptors
     * 
     * @param recommendation
     *            recommendation object from FileRecommendation
     * @param volumeURIs
     *            volumes already prepared
     * @param capabilities
     *            vpool capabilities
     * @return list of file descriptors
     * @throws ControllerException
     */
    private List<FileDescriptor> createFileDescriptors(final Recommendation recommendation,
            final List<URI> fileURIs, final VirtualPoolCapabilityValuesWrapper capabilities)
                    throws ControllerException {
    	List<FileDescriptor> descriptors = new ArrayList<FileDescriptor>();
    	
    	List<FileShare> preparedFileShares = _dbClient.queryObject(FileShare.class, fileURIs);
    	
    	for(FileShare fileshare : preparedFileShares) {
    		FileDescriptor.Type fileType = FileDescriptor.Type.FILE_RP_SOURCE;
    		FileDescriptor fileDescriptor = new FileDescriptor(fileType, fileshare.getStorageDevice(), 
    				fileshare.getId(), fileshare.getPool(), 
    				fileshare.getUsedCapacity(), capabilities, null, null);
    	}
    	
    	return descriptors;
    	
    }
    
    @Override
	protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(
			URI systemURI, List<URI> fileShareURIs, String deletionType,
			boolean forceDelete) {
		// TODO Auto-generated method stub
    	
		return null;
	}
}

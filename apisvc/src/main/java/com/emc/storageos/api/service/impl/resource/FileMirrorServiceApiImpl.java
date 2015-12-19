/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileMirrorSchedular;
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

public class FileMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileMirrorSchedular>{

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
    		VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, List<Recommendation> recommendations, 
    		TaskList taskList, String taskId, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
		//TBD
        return null;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, 
    								boolean forceDelete, String task) throws InternalException {
    	//TBD
    	return;
    }
    
    @Override
	protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(
			URI systemURI, List<URI> fileShareURIs, String deletionType,
			boolean forceDelete) {
		// TODO Auto-generated method stub
		return null;
	}
}

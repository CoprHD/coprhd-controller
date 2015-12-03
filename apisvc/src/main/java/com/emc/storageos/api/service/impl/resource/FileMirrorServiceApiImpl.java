package com.emc.storageos.api.service.impl.resource;
import java.net.URI;
import java.util.List;

import com.emc.storageos.api.service.impl.placement.FileRPSchedular;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class FileMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileRPSchedular>{

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
    		TaskList taskList, String task, 
    		VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        return null;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, String task) throws InternalException {

    }

}

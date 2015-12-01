package com.emc.storageos.api.service.impl.placement;
import java.net.URI;
import java.util.List;
import com.emc.storageos.api.service.impl.resource.AbstractFileServiceApiImpl;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class DefaultFileServiceApiImpl extends AbstractFileServiceApiImpl<FileStorageScheduler>  {

	public DefaultFileServiceApiImpl(String protectionType) {
		super(protectionType);
		// TODO Auto-generated constructor stub
	}
	
	@Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations, TaskList taskList, String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        return null;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, String task) throws InternalException {

    }

}

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileMirrorSchedular;
import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.FileSystemParam;

public class FileRemoteMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileMirrorSchedular> {
    
    private static final Logger _log = LoggerFactory.getLogger(FileRemoteMirrorServiceApiImpl.class);
    
    private FileMirrorServiceApiImpl _fileMirrorServiceApiImpl;

    public FileMirrorServiceApiImpl getFileMirrorServiceApiImpl() {
        return _fileMirrorServiceApiImpl;
    }

    public void setFileMirrorServiceApiImpl(FileMirrorServiceApiImpl _fileMirrorServiceApiImpl) {
        this._fileMirrorServiceApiImpl = _fileMirrorServiceApiImpl;
    }

    public FileRemoteMirrorServiceApiImpl() {
        super(null);
    }

    public FileRemoteMirrorServiceApiImpl(String protectionType) {
        super(protectionType);
        // TODO Auto-generated constructor stub
    }
    
    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray,
            VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, List<Recommendation> recommendations,
            TaskList taskList, String taskId, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        // TBD
        return getFileMirrorServiceApiImpl().createFileSystems(param, project, varray, vpool, tenantOrg, flags,
                recommendations, taskList, taskId, vpoolCapabilities);
    }

    @Override
    protected List getDescriptorsOfFileShareDeleted(URI systemURI, List fileShareURIs, String deletionType, boolean forceDelete) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, boolean forceDelete, String task)
            throws InternalException {
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, task);
    }

}

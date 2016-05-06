/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Joiner;

public class FileCreateWorkflowCompleter extends FileWorkflowCompleter{
	
	private static final long serialVersionUID = -494348560407624019L;
	@XmlTransient
    private List<FileDescriptor> _fileDescriptors = new ArrayList<FileDescriptor>();
	
	public FileCreateWorkflowCompleter(List<URI> fsUris, String task, List<FileDescriptor> fileDescriptors){
		super(fsUris, task);
												
        this._fileDescriptors = fileDescriptors;
    }
	
	public FileCreateWorkflowCompleter(URI fileUri, String task) {
        super(fileUri, task);
    }
	
	@Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {

        super.complete(dbClient, status, serviceCoded);

    }
	
	private void handleFileShareErrors(DbClient dbClient) {
        for (FileDescriptor fileDescriptor : FileDescriptor.getDescriptors(_fileDescriptors, FileDescriptor.Type.FILE_DATA)) {

            FileShare fileShare = dbClient.queryObject(FileShare.class, fileDescriptor.getFsURI());

            if (fileShare != null && (fileShare.getNativeId() == null || fileShare.getNativeId().equals(""))) {
                _log.info("No native id was present on FileShare {}, marking inactive", fileShare.getLabel());
                dbClient.markForDeletion(fileShare);
            }
        }
    }




}

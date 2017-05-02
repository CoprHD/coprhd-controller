/*
 * Copyright (c) 2012-2017 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemReduceParam;
import com.emc.vipr.client.Task;

public class ReduceFileSystem extends WaitForTask<FileShareRestRep> {
	
	private URI fileSystemId;
    private String newSize; 
    
	public ReduceFileSystem(URI fileSystemId, String newSize) {
		super();
		this.fileSystemId = fileSystemId;
		this.newSize = newSize;
		provideDetailArgs(fileSystemId, newSize);
	}
	public ReduceFileSystem(String fileSystemId, String newSize) {
        this(uri(fileSystemId), newSize);
    }

	
    
	@Override
	protected Task<FileShareRestRep> doExecute() throws Exception {
		FileSystemReduceParam reduce = new FileSystemReduceParam();
		reduce.setNewSize(newSize);
        return getClient().fileSystems().reduce(fileSystemId, reduce);
	}

}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

public class MirrorFileCreateTaskCompleter extends MirrorFileTaskCompleter {
	
	private URI vpoolChangeURI;
	public MirrorFileCreateTaskCompleter(Class clazz, List<URI> ids, String opId) {
		super(clazz, ids, opId);
		// TODO Auto-generated constructor stub
	}

	public MirrorFileCreateTaskCompleter(Class clazz, URI id, String opId) {
		super(clazz, id, opId);
		// TODO Auto-generated constructor stub
	}

	public MirrorFileCreateTaskCompleter(URI sourceURI, URI targetURI,
			final URI vPoolChangeUri, String opId) {
		super(sourceURI, targetURI, opId);
		vpoolChangeURI = vPoolChangeUri;
		// TODO Auto-generated constructor stub
	}

}

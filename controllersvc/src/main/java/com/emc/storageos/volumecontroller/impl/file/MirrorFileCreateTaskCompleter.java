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
	}

	public MirrorFileCreateTaskCompleter(Class clazz, URI id, String opId) {
		super(clazz, id, opId);
	}

	public MirrorFileCreateTaskCompleter(URI sourceURI, URI targetURI,
			final URI vPoolChangeUri, String opId) {
		super(sourceURI, targetURI, opId);
		vpoolChangeURI = vPoolChangeUri;
	}

}

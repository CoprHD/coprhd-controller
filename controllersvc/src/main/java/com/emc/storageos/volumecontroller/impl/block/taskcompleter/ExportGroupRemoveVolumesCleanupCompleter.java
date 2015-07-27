/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import com.emc.storageos.db.client.model.ExportGroup;

public class ExportGroupRemoveVolumesCleanupCompleter extends ExportTaskCompleter {
	private static final long serialVersionUID = -4771861722705068440L;

	public ExportGroupRemoveVolumesCleanupCompleter(URI id, String opId) {
		super(ExportGroup.class, id, opId);
	}

}

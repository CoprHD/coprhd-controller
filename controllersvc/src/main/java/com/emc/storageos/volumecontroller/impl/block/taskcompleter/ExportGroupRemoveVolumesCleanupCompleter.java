/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

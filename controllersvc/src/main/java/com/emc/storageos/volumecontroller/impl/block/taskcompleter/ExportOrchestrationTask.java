/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.model.ExportGroup;

import java.net.URI;

public class ExportOrchestrationTask extends ExportTaskCompleter {

    public ExportOrchestrationTask(URI id, String opId) {
        super(ExportGroup.class, id, opId);
    }

}

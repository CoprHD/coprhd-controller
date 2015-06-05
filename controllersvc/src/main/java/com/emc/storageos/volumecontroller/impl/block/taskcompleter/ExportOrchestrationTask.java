/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.model.ExportGroup;

import java.net.URI;

public class ExportOrchestrationTask extends ExportTaskCompleter {

    public ExportOrchestrationTask(URI id, String opId) {
        super(ExportGroup.class, id, opId);
    }

}

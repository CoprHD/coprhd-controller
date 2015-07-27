/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.ExportGroup;


public class ExportDeregisterInitiatorCompleter extends ExportTaskCompleter {

    public ExportDeregisterInitiatorCompleter(URI egUri, URI emUri, List<URI> initiatorURIs,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
    }
}

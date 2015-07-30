/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Volume;

/**
 * The Class VolumeUpdateCompleter.
 * It is used when some call is made to array for updating some properties on Volume(s).
 */
@SuppressWarnings("serial")
public class VolumeUpdateCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(VolumeUpdateCompleter.class);

    public VolumeUpdateCompleter(URI volUri, String task) {
        super(Volume.class, volUri, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public VolumeUpdateCompleter(List<URI> volUris, String task) {
        super(Volume.class, volUris, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }
}

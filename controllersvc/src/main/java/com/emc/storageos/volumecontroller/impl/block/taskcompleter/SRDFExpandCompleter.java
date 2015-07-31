/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.model.Volume;

import java.net.URI;
import java.util.List;

public class SRDFExpandCompleter extends SRDFTaskCompleter {

    public SRDFExpandCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }
}

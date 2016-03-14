/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume_group_snapshot_session_deactivate")
public class VolumeGroupSnapshotSessionDeactivateParam extends VolumeGroupSnapshotSessionOperationParam {

    public VolumeGroupSnapshotSessionDeactivateParam() {
    }

    public VolumeGroupSnapshotSessionDeactivateParam(Boolean partial, List<URI> snapshotSessions) {
        super(partial, snapshotSessions);
    }
}

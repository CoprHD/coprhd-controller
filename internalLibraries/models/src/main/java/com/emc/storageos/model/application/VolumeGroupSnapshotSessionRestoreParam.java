/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume_group_snapshot_session_restore")
public class VolumeGroupSnapshotSessionRestoreParam extends VolumeGroupSnapshotSessionOperationParam {

    public VolumeGroupSnapshotSessionRestoreParam() {
    }

    public VolumeGroupSnapshotSessionRestoreParam(Boolean partial, List<URI> snapshotSessions) {
        super(partial, snapshotSessions);
    }
}

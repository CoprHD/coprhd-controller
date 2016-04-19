/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume_group_full_copy_activate")
public class VolumeGroupFullCopyActivateParam extends VolumeGroupFullCopyOperationParam {

    public VolumeGroupFullCopyActivateParam() {
    }

    public VolumeGroupFullCopyActivateParam(Boolean partial, List<URI> fullCopies) {
        super(partial, fullCopies);
    }
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshot_share_acl_update")
public class SnapshotCifsShareACLUpdateParams extends CifsShareACLUpdateParams {

    private static final long serialVersionUID = -4847295792029726930L;

    public SnapshotCifsShareACLUpdateParams() {

    }

}

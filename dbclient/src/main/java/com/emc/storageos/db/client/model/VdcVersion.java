/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@SuppressWarnings("serial")
@Cf("VdcVersion")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
public class VdcVersion extends DataObject {
    private URI vdcId;
    private String version;

    @Name("vdcId")
    public URI getVdcId() {
        return vdcId;
    }

    public void setVdcId(URI vdcId) {
        this.vdcId = vdcId;
        this.setChanged("vdcId");
    }

    @Name("version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        this.setChanged("version");
    }
}

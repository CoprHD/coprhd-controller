/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;

import java.net.URI;

@Deprecated
@Cf("InitialSetup")
public class InitialSetup extends ModelObject {
    public static final URI SINGLETON_ID = URI.create("urn:storageos:InitialSetup:SINGLETON:");
    public static final String COMPLETE = "complete";

    // these fields added here only to help with migration to ZK configuration
    public static final String CONFIG_KIND = "portalsvc.setup";
    public static final String CONFIG_ID = "singleton";

    private boolean complete;

    @Name(COMPLETE)
    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(Boolean complete) {
        this.complete = complete;
        setChanged(COMPLETE);
    }

}

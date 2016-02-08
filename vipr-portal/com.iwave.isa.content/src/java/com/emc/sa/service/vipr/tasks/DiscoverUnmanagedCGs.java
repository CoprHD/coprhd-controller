/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.vipr.client.Task;

public class DiscoverUnmanagedCGs extends WaitForTask<ProtectionSystemRestRep> {

    private URI protectionSystemId;
    private UnmanagedNamespace unmanagedNamespace;

    public DiscoverUnmanagedCGs(String protectionSystemId, UnmanagedNamespace namespace) {
        this(uri(protectionSystemId), namespace);
    }

    public DiscoverUnmanagedCGs(URI protectionSystemId, UnmanagedNamespace namespace) {
        this.protectionSystemId = protectionSystemId;
        this.unmanagedNamespace = namespace;
        provideDetailArgs(protectionSystemId, unmanagedNamespace);
    }

    @Override
    protected Task<ProtectionSystemRestRep> doExecute() throws Exception {
        return getClient().protectionSystems().discover(protectionSystemId, unmanagedNamespace.toString());
    }

    public static enum UnmanagedNamespace {
        UNMANAGED_CGS, ALL
    }
}

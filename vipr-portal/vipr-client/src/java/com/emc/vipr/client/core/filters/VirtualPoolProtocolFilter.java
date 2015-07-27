/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import java.util.HashSet;
import java.util.Set;

public class VirtualPoolProtocolFilter<T extends VirtualPoolCommonRestRep> extends DefaultResourceFilter<T> {
    private Set<String> protocols;

    public VirtualPoolProtocolFilter(Set<String> protocols) {
        this.protocols = protocols;
    }

    public VirtualPoolProtocolFilter(String... protocols) {
        this.protocols = new HashSet<String>();
        for (String protocol: protocols) {
            this.protocols.add(protocol);
        }
    }

    @Override
    public boolean accept(T item) {
        if (item.getProtocols() == null) {
            return false;
        }
        for (String protocol : item.getProtocols()) {
            if (protocols.contains(protocol)) {
                return true;
            }
        }
        return false;
    }
}
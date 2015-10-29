/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.vpool.NamedRelatedVirtualPoolRep;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;

public class VirtualPoolUtils {
    public static final String FILE = "file";
    public static final String BLOCK = "block";
    public static final String VPLEX_LOCAL = "vplex_local";
    public static final String VPLEX_DISTRIBUTED = "vplex_distributed";
    public static final String OBJECT = "object";

    private static boolean isVplexLocal(String type) {
        return VPLEX_LOCAL.equals(type);
    }

    public static boolean isVplexDistributed(String type) {
        return VPLEX_DISTRIBUTED.equals(type);
    }

    public static boolean isHighAvailability(VirtualPoolHighAvailabilityParam ha) {
        return (ha != null) && (isVplexLocal(ha.getType()) || isVplexDistributed(ha.getType()));
    }

    public static List<NamedRelatedVirtualPoolRep> fileVpools(List<NamedRelatedVirtualPoolRep> pools) {
        return byType(pools, FILE);
    }

    public static List<NamedRelatedVirtualPoolRep> blockVpools(List<NamedRelatedVirtualPoolRep> pools) {
        return byType(pools, BLOCK);
    }

    public static List<NamedRelatedVirtualPoolRep> objectVpools(List<NamedRelatedVirtualPoolRep> pools) {
        return byType(pools, OBJECT);
    }
    
    private static List<NamedRelatedVirtualPoolRep> byType(List<NamedRelatedVirtualPoolRep> pools, String type) {
        List<NamedRelatedVirtualPoolRep> response = new ArrayList<NamedRelatedVirtualPoolRep>();
        for (NamedRelatedVirtualPoolRep pool : pools) {
            if (type.equalsIgnoreCase(pool.getVirtualPoolType())) {
                response.add(pool);
            }
        }
        return response;
    }
}

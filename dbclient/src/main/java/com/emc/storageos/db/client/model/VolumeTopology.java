/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Class captures the definition of a VolumeTopology. Currently this class is mostly a
 * place holder for future phases of the virtual pool simplification project. For now
 * it defines an enumeration of the various roles a volume can play in a volume topology
 * and an enumeration of the sites that comprise a topology. It also captures the 
 * source and copy performance parameters passed in a volume or mirror creation request
 * that override the corresponding vpool properties. Later it can be enhanced to become
 * a column family and hold volume topologies with specific performance and other settings 
 * that can be applied to a volume when it is provisioned as described in the vpool
 * simplification design specification.
 */
public class VolumeTopology {
    
    // Performance parameters for the source volume.
    private Map<VolumeTopologyRole, URI> sourcePerformanceParams;
    
    // Performance parameters for the copy volumes, keyed by the copy virtual array.
    private Map<URI, Map<VolumeTopologyRole, URI>> copyPerformanceParams;
    
    // Enumeration of volume topology sites.
    public static enum VolumeTopologySite {
        SOURCE, // The source site in the volume topology. 
        COPY, // The copy site in the volume topology.
    };

    // Enumeration of all roles a volume can play in a volume topology.
    public static enum VolumeTopologyRole {
        PRIMARY, // The primary volume at the topology site.
        PRIMARY_MIRROR, // The volume that is the mirror for the primary volume at the topology site.
        JOURNAL, // The journal volume for the primary volume at the topology site.
        STANDBY_JOURNAL, // The standby journal volume for the primary volume at the site in a Metropoint topology.
        HA, // The HA volume for a VPLEX distributed volume at the site. 
        HA_MIRROR, // The volume that is the mirror of the HA side volume for a VPLEX distributed volume at the site.
    };
 
    /**
     * Default Constructor
     */
    public VolumeTopology() {
        sourcePerformanceParams = new HashMap<>();
        copyPerformanceParams = new HashMap<>();
    }

    /**
     * Constructor
     * 
     * @param sourcePerformanceParams The performance parameters for the source volume in the topology.
     * @param copyPerformanceParams The performance parameters for the copy volumes in the topology.
     */
    public VolumeTopology(Map<VolumeTopologyRole, URI> sourcePerformanceParams, Map<URI,
            Map<VolumeTopologyRole, URI>> copyPerformanceParams) {
        if (sourcePerformanceParams != null) {
            this.sourcePerformanceParams = sourcePerformanceParams;
        } else {
            this.sourcePerformanceParams = new HashMap<>();
        }
        
        if (copyPerformanceParams != null) {
            this.copyPerformanceParams = copyPerformanceParams;
        } else {
            this.copyPerformanceParams = new HashMap<>();            
        }
    }
    
    /**
     * Getter for the source performance parameters.
     * 
     * @return The source performance parameters.
     */
    public Map<VolumeTopologyRole, URI> getSourcePerformanceParams() {
        return sourcePerformanceParams;
    }

    /**
     * Getter for the copy performance parameters.
     * 
     * @return The copy performance parameters.
     */
    public Map<URI, Map<VolumeTopologyRole, URI>> getCopyPerformanceParams() {
        return copyPerformanceParams;
    }

    /**
     * Gets the performance parameters for the component volume playing the 
     * passed role in the source volume topology.
     * 
     * @param role The volume topology role.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to a performance parameters instance or null if there are none for the passed role.
     */
    public PerformanceParams getPerformanceParamsForSourceRole(VolumeTopologyRole role, DbClient dbClient) {
        PerformanceParams performanceParams = null;
        if (!sourcePerformanceParams.isEmpty()) {
            URI performanceParamsURI = sourcePerformanceParams.get(role);
            if (!NullColumnValueGetter.isNullURI(performanceParamsURI)) {
                performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            }
        }
        return performanceParams;
    }
    
    /**
     * Gets the performance parameters for the copy at the passed virtual array.
     * 
     * @param varrayURI The URI of the varray for the copy.
     * 
     * @return The performance parameters for the copy at the passed virtual array or null.
     */
    public Map<VolumeTopologyRole, URI> getPerformanceParamsForCopy(URI varrayURI) {
        return copyPerformanceParams.get(varrayURI);
    }
    
    /**
     * Gets the performance parameters for the component volume playing the 
     * passed role in the copy volume topology for the copy in the passed 
     * virtual array.
     * 
     * @param varrayURI The URI of the varray for the copy.
     * @param role The volume topology role.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to a performance parameters instance or null if there are none for the passed role.
     */
    public PerformanceParams getPerformanceParamsForCopyRole(URI varrayURI, VolumeTopologyRole role, DbClient dbClient) {
        PerformanceParams performanceParams = null;
        if (!copyPerformanceParams.isEmpty()) {
            Map<VolumeTopologyRole, URI> copyParamsMap = getPerformanceParamsForCopy(varrayURI);
            if (copyParamsMap != null && !copyParamsMap.isEmpty()) {
                URI performanceParamsURI = copyParamsMap.get(role);
                if (!NullColumnValueGetter.isNullURI(performanceParamsURI)) {
                    performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
                }
            }
        }
        return performanceParams;
    }
}

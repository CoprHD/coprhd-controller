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
 * source and copy performance policies passed in a volume or mirror creation request
 * that override the corresponding vpool properties. Later it can be enhanced to become
 * a column family and hold volume topologies with specific performance and other settings 
 * that can be applied to a volume when it is provisioned as described in the vpool
 * simplification design specification.
 */
public class VolumeTopology {
    
    // Performance policies for the source volume.
    private Map<VolumeTopologyRole, URI> sourcePerformancePolicies;
    
    // Performance policies for the copy volumes, keyed by the copy virtual array.
    private Map<URI, Map<VolumeTopologyRole, URI>> copyPerformancePolicies;
    
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
        sourcePerformancePolicies = new HashMap<>();
        copyPerformancePolicies = new HashMap<>();
    }

    /**
     * Constructor
     * 
     * @param sourcePerformancePolicies The performance policies for the source volume in the topology.
     * @param copyPerformancePolicies The performance policies for the copy volumes in the topology.
     */
    public VolumeTopology(Map<VolumeTopologyRole, URI> sourcePerformancePolicies, Map<URI,
            Map<VolumeTopologyRole, URI>> copyPerformancePolicies) {
        if (sourcePerformancePolicies != null) {
            this.sourcePerformancePolicies = sourcePerformancePolicies;
        } else {
            this.sourcePerformancePolicies = new HashMap<>();
        }
        
        if (copyPerformancePolicies != null) {
            this.copyPerformancePolicies = copyPerformancePolicies;
        } else {
            this.copyPerformancePolicies = new HashMap<>();            
        }
    }
    
    /**
     * Getter for the source performance policies.
     * 
     * @return The source performance policies.
     */
    public Map<VolumeTopologyRole, URI> getSourcePerformancePolicies() {
        return sourcePerformancePolicies;
    }

    /**
     * Getter for the copy performance policies.
     * 
     * @return The copy performance policies.
     */
    public Map<URI, Map<VolumeTopologyRole, URI>> getCopyPerformancePolicies() {
        return copyPerformancePolicies;
    }

    /**
     * Gets the performance policy for the component volume playing the 
     * passed role in the source volume topology.
     * 
     * @param role The volume topology role.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to a performance policy instance or null if there are none for the passed role.
     */
    public PerformancePolicy getPerformancePolicyForSourceRole(VolumeTopologyRole role, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (!sourcePerformancePolicies.isEmpty()) {
            URI performancePolicyURI = sourcePerformancePolicies.get(role);
            if (!NullColumnValueGetter.isNullURI(performancePolicyURI)) {
                performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
            }
        }
        return performancePolicy;
    }
    
    /**
     * Gets the performance policies for the copy at the passed virtual array.
     * 
     * @param varrayURI The URI of the varray for the copy.
     * 
     * @return The performance policies for the copy at the passed virtual array or null.
     */
    public Map<VolumeTopologyRole, URI> getPerformancePoliciesForCopy(URI varrayURI) {
        return copyPerformancePolicies.get(varrayURI);
    }
    
    /**
     * Gets the performance policy for the component volume playing the 
     * passed role in the copy volume topology for the copy in the passed 
     * virtual array.
     * 
     * @param varrayURI The URI of the varray for the copy.
     * @param role The volume topology role.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to a performance policy instance or null if there are none for the passed role.
     */
    public PerformancePolicy getPerformancePolicyForCopyRole(URI varrayURI, VolumeTopologyRole role, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (!copyPerformancePolicies.isEmpty()) {
            Map<VolumeTopologyRole, URI> copyPoliciesMap = getPerformancePoliciesForCopy(varrayURI);
            if (copyPoliciesMap != null && !copyPoliciesMap.isEmpty()) {
                URI performancePolicyURI = copyPoliciesMap.get(role);
                if (!NullColumnValueGetter.isNullURI(performancePolicyURI)) {
                    performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
                }
            }
        }
        return performancePolicy;
    }
}

/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Class captures the definition of a VolumeTopology. Currently this class is mostly a
 * place holder for future phases of the virtual pool simplification project. For now
 * it simply defines an enumeration of the various roles a volume can play in a volume
 * topology. Later it can be enhanced to become a column family and hold volume topologies
 * with specific performance and other settings that can be applied to a volume when
 * it is provisioned.
 */
public class VolumeTopology {

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
}

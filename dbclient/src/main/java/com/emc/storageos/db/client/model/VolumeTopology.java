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
    
    // Enumeration of all roles a volume can play in a volume topology.
    public static enum VolumeTopologyRole {
        SOURCE, // The source volume in the topology. The primary side for a VPLEX distributed source volume.
        SOURCE_MIRROR, // The volume that is the mirror for the source volume.
        SOURCE_JOURNAL, // The journal volume for the source volume.
        SOURCE_STANDBY_JOURNAL, // The standby journal volume for the source volume in a Metropoint topology.
        SOURCE_HA, // The HA side volume for a VPLEX distributed source volume. 
        SOURCE_HA_MIRROR, // The volume that is the mirror of the HA side volume for a VPLEX distributed source volume.
        COPY, // A copy volume in the volume topology. The primary side for a VPLEX distributed copy volume.
        COPY_MIRROR, // The volume that is the mirror for the copy volume.
        COPY_JOURNAL, // The journal volume for the copy volume.
        COPY_HA, // The HA side volume for a VPLEX distributed copy volume. 
        COPY_HA_MIRROR, // The volume that is the mirror of the HA side volume for a VPLEX distributed copy volume.
    };
}

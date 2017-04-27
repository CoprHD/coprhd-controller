/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures the performance parameter settings when posting a request
 * to provision a new volume. An instance of this class specifies the URI 
 * of the PerformanceParams instance to be used for the given role in the 
 * volume topology.
 */
public class BlockPerformanceParamsMapEntry {
    
    // The role of the volume in the volume topology to which the performance
    // params are to be applied. For example, if the role is something like RP 
    // source journal, the performance params are to be applied to the RP source
    // journal volume.
    private String volumeTopologyRole;
    
    // The URI of the performance params instance to be applied to the volume 
    // playing the given role in the volume topology.
    private URI performanceParamsURI;

    /**
     * Default constructor
     */
    public BlockPerformanceParamsMapEntry() {
    }

    /**
     * Constructor
     * 
     * @param volumeTopologyRole The role of the volume in the overall volume's topology to which 
     *                           the performance parameters are to be applied.
     * @param performanceParamsURI The URI of a PerformanceParams instance.
     */
    public BlockPerformanceParamsMapEntry(String volumeTopologyRole, URI performanceParamsURI) {
        this.volumeTopologyRole = volumeTopologyRole;
        this.performanceParamsURI = performanceParamsURI;
    }

    /**
     * Getter for the role of the volume in the overall volume's topology to which the
     * performance parameters are to be applied.
     * 
     * Valid values:
     *  SOURCE = The source volume in the topology. The primary side for a VPLEX distributed source volume.
     *  SOURCE_MIRROR = The volume that is the mirror for the source volume.
     *  SOURCE_JOURNAL = The journal volume for the source volume.
     *  SOURCE_STANDBY_JOURNAL = The standby journal volume for the source volume in a Metropoint topology.
     *  SOURCE_HA = The HA side volume for a VPLEX distributed source volume. 
     *  SOURCE_HA_MIRROR = The volume that is the mirror of the HA side volume for a VPLEX distributed source volume.
     *  COPY = A copy volume in the volume topology. The primary side for a VPLEX distributed copy volume.
     *  COPY_MIRROR = The volume that is the mirror for the copy volume.
     *  COPY_JOURNAL = The journal volume for the copy volume.
     *  COPY_HA = The HA side volume for a VPLEX distributed copy volume. 
     *  COPY_HA_MIRROR = The volume that is the mirror of the HA side volume for a VPLEX distributed copy volume.
     * 
     * @return The volume role in the volume topology.
     */
    @XmlElement(name = "role", required = true)
    public String getRole() {
        return volumeTopologyRole;
    }

    public void setRole(String volumeTopologyRole) {
        this.volumeTopologyRole = volumeTopologyRole;
    }

    /**
     * The URI of the PerformanceParams instance.
     * 
     * @return The URI of the PerformanceParams instance.
     */
    @XmlElement(name = "id", required = true)
    public URI getId() {
        return performanceParamsURI;
    }

    public void setId(URI performanceParamsURI) {
        this.performanceParamsURI = performanceParamsURI;
    }
}

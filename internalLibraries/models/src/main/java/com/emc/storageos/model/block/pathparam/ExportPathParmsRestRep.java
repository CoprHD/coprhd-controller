/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.pathparam;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "export_path_parameters")
public class ExportPathParmsRestRep extends DataObjectRestRep {

    private String name;
    private URI id;
    private Integer maxPaths;
    private Integer pathsPerInitiator;
    private Integer minPaths;
    private Integer maxInitiatorsPerPort;

    /**
     * Name is the identifier for the export path parameters.
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * URI for the export path parameters.
     */
    @XmlElement(name = "id")
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    @XmlElement(name = "max_paths")
    @Range(min = 1, max = 65535)
    /**
     * The maximum number of storage paths (ports) that will be provisioned.
     */
    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }

    @XmlElement(name = "paths_per_initiator")
    @Range(min = 1, max = 65535)
    /**
     * The number of storage paths (ports) that will be assigned and zoned to each Initiator.
     */
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
    }

    @XmlElement(name = "min_paths")
    @Range(min = 1, max = 65535)
    /**
     * The minimum number of storage paths that must be
     * provisioned for a successful export.
     */
    public Integer getMinPaths() {
        return minPaths;
    }

    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * The number of Initiators that will be assigned and zoned to each Port.
     */
    @XmlElement(name = "initiators_per_port", required = false)
    @Range(min = 1, max = 65535)
    public Integer getMaxInitiatorsPerPort() {
        return maxInitiatorsPerPort;
    }

    public void setMaxInitiatorsPerPort(Integer maxInitiatorsPerPort) {
        this.maxInitiatorsPerPort = maxInitiatorsPerPort;
    }

}

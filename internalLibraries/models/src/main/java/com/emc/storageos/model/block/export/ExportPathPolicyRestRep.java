/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "export_path_policy")
public class ExportPathPolicyRestRep extends DataObjectRestRep{

    private URI id;
    private String name;
    private String description;
    private Integer maxPaths;
    private Integer pathsPerInitiator;
    private Integer minPaths;
    private List<URI> storagePorts;
    private Integer maxInitiatorsPerPort;

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

    /**
     * Name of the Export Path Param or Port Group
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Description for the Export Path Param or Port Group.
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The maximum number of storage paths (ports) that will be provisioned.
     */
    @XmlElement(name = "max_paths")
    @Range(min = 1, max = 65535)
    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }

    /**
     * The number of storage paths (ports) that will be assigned and zoned to each Initiator.
     */
    @XmlElement(name = "paths_per_initiator")
    @Range(min = 1, max = 65535)
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
    }

    /**
     * The minimum number of storage paths that must be
     * provisioned for a successful export.
     */
    @XmlElement(name = "min_paths")
    @Range(min = 1, max = 65535)
    public Integer getMinPaths() {
        return minPaths;
    }

    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * Optional list of storage ports to be used for the export.
     * Any ports that are listed must also be available in the applicable
     * virtual array(s) for the export group in order to be considered
     * for allocation.
     */
    @XmlElementWrapper(name = "storage_ports", required = false)
    @XmlElement(name = "storage_port")
    public List<URI> getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(List<URI> storagePorts) {
        this.storagePorts = storagePorts;
    }

    /**
     * The number of Initiators that will be assigned and zoned to each Port.
     */
    @XmlElement(name = "max_initiators_per_port", required = false)
    @Range(min = 1, max = 65535)
    public Integer getMaxInitiatorsPerPort() {
        return maxInitiatorsPerPort;
    }

    public void setMaxInitiatorsPerPort(Integer maxInitiatorsPerPort) {
        this.maxInitiatorsPerPort = maxInitiatorsPerPort;
    }

}

package com.emc.storageos.model.block.pathparam;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "export_path_parameters_update")
public class ExportPathUpdateParam {

    private String name;
    private Integer maxPaths;
    private Integer minPaths;
    private Integer pathsPerInitiator;
    private Integer maxInitiatorsPerPort;

    /**
     * The maximum number of storage paths (ports) that will be provisioned.
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

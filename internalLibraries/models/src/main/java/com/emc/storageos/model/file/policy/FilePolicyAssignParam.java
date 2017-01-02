/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

/**
 * @author jainm15
 */
import java.io.Serializable;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "assign_file_policy")
public class FilePolicyAssignParam implements Serializable {

    public FilePolicyAssignParam() {
        super();
    }

    private static final long serialVersionUID = 1L;

    // Level at which policy has to be applied..
    private String applyAt;

    private Boolean applyOnTargetSite;

    // Vpool assignment parameters.
    private FilePolicyVpoolAssignParam vpoolAssignParams;

    // Project assignment parameters.
    private FilePolicyProjectAssignParam projectAssignParams;

    // File System assignment parameters.
    private FilePolicyFileSystemAssignParam fileSystemAssignParams;

    // File replication topology information
    // Applicable only for replication type policies
    private Set<FileReplicationTopologyParam> fileReplicationtopologies;

    /**
     * Level at which policy has to applied.
     * Valid values are vpool, project, file_system
     * 
     * @return
     */
    @XmlElement(required = true, name = "apply_at")
    public String getApplyAt() {
        return this.applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
    }

    @XmlElement(name = "apply_on_target_site")
    public Boolean getApplyOnTargetSite() {
        return this.applyOnTargetSite;
    }

    public void setApplyOnTargetSite(Boolean applyOnTarget) {
        this.applyOnTargetSite = applyOnTarget;
    }

    @XmlElement(name = "vpool_assign_param")
    public FilePolicyVpoolAssignParam getVpoolAssignParams() {
        return this.vpoolAssignParams;
    }

    public void setVpoolAssignParams(FilePolicyVpoolAssignParam vpoolAssignParams) {
        this.vpoolAssignParams = vpoolAssignParams;
    }

    @XmlElement(name = "project_assign_param")
    public FilePolicyProjectAssignParam getProjectAssignParams() {
        return this.projectAssignParams;
    }

    public void setProjectAssignParams(FilePolicyProjectAssignParam projectAssignParams) {
        this.projectAssignParams = projectAssignParams;
    }

    @XmlElement(name = "filesystem_assign_param")
    public FilePolicyFileSystemAssignParam getFileSystemAssignParams() {
        return this.fileSystemAssignParams;
    }

    public void setFileSystemAssignParams(FilePolicyFileSystemAssignParam fileSystemAssignParams) {
        this.fileSystemAssignParams = fileSystemAssignParams;
    }

    @XmlElementWrapper(name = "file_replication_topologies")
    @XmlElement(name = "file_replication_topology")
    public Set<FileReplicationTopologyParam> getFileReplicationtopologies() {
        return fileReplicationtopologies;
    }

    public void setFileReplicationtopologies(Set<FileReplicationTopologyParam> fileReplicationtopologies) {
        this.fileReplicationtopologies = fileReplicationtopologies;
    }

}

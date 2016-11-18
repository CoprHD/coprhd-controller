/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

/**
 * @author jainm15
 */
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "assign_file_policy")
public class FilePolicyAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // Level at which policy has to be applied..
    private String applyAt;

    // Assigned vpools
    private FilePolicyVpoolAssignParam vpoolAssignParams;

    // Assigned projects
    private FilePolicyProjectAssignParam projectAssignParams;

    @XmlElement(required = true, name = "apply_at")
    public String getApplyAt() {
        return this.applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
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

}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class VirtualPoolProtectionRPParam {

    private Set<VirtualPoolProtectionVirtualArraySettingsParam> copies;
    private ProtectionSourcePolicy sourcePolicy;

    public VirtualPoolProtectionRPParam() {
    }

    public VirtualPoolProtectionRPParam(
            Set<VirtualPoolProtectionVirtualArraySettingsParam> copies,
            ProtectionSourcePolicy sourcePolicy) {
        this.copies = copies;
        this.sourcePolicy = sourcePolicy;
    }

    @XmlElementWrapper(name = "copies")
    /**
     * The Recoverpoint protection virtual array settings for a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolProtectionVirtualArraySettingsParam> getCopies() {
        if (copies == null) {
            copies = new LinkedHashSet<VirtualPoolProtectionVirtualArraySettingsParam>();
        }
        return copies;
    }

    public void setCopies(Set<VirtualPoolProtectionVirtualArraySettingsParam> copies) {
        this.copies = copies;
    }

    /**
     * The Recoverpoint protection source policy for a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "source_policy")
    public ProtectionSourcePolicy getSourcePolicy() {
        return sourcePolicy;
    }

    public void setSourcePolicy(ProtectionSourcePolicy sourcePolicy) {
        this.sourcePolicy = sourcePolicy;
    }

}

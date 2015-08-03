/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class VirtualPoolProtectionRPChanges {

    private Set<VirtualPoolProtectionVirtualArraySettingsParam> add;
    private Set<VirtualPoolProtectionVirtualArraySettingsParam> remove;
    private ProtectionSourcePolicy sourcePolicy;

    public VirtualPoolProtectionRPChanges() {
    }

    public VirtualPoolProtectionRPChanges(
            Set<VirtualPoolProtectionVirtualArraySettingsParam> add,
            Set<VirtualPoolProtectionVirtualArraySettingsParam> remove,
            ProtectionSourcePolicy sourcePolicy) {
        this.add = add;
        this.remove = remove;
        this.sourcePolicy = sourcePolicy;
    }

    @XmlElementWrapper(name = "add_copies")
    /**
     * Protection virtual array settings to add to the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolProtectionVirtualArraySettingsParam> getAdd() {
        if (add == null) {
            add = new LinkedHashSet<VirtualPoolProtectionVirtualArraySettingsParam>();
        }
        return add;
    }

    public void setAdd(Set<VirtualPoolProtectionVirtualArraySettingsParam> add) {
        this.add = add;
    }

    @XmlElementWrapper(name = "remove_copies")
    /**
     * Protection virtual array settings to remove from the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolProtectionVirtualArraySettingsParam> getRemove() {
        if (remove == null) {
            remove = new LinkedHashSet<VirtualPoolProtectionVirtualArraySettingsParam>();
        }
        return remove;
    }

    public void setRemove(Set<VirtualPoolProtectionVirtualArraySettingsParam> remove) {
        this.remove = remove;
    }

    /**
     * The protection source policy for the virtual pool.
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

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class VirtualPoolRemoteProtectionUpdateParam {

    private Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add;
    private Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove;
    
    public VirtualPoolRemoteProtectionUpdateParam(
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add,
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove) {
        this.add = add;
        this.remove = remove;
    }
    
    public VirtualPoolRemoteProtectionUpdateParam(){}
    
    @XmlElementWrapper(name = "add_remote_copies_settings")
    @XmlElement(name="add_remote_copy_setting", required = false)
    public Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getAdd() {
        if (null == add) {
            add = new LinkedHashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return add;
    }
    public void setAdd(Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add) {
        this.add = add;
    }
    
    @XmlElementWrapper(name = "remove_remote_copies_settings")
    @XmlElement(name="remove_remote_copy_setting", required = false)
    public Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemove() {
        if (null == remove) {
            remove = new LinkedHashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return remove;
    }
    
 
    public void setRemove(Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove) {
        this.remove = remove;
    }
}

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.valid.EnumType;

public class DiscoverNamespaceParam {
    
    public enum DiscoveryModules {
        MASKING,
        CONSISTENCYGROUP;
    }
    
    private String moduleName;
    
    private List<URI> modules;

    @EnumType(DiscoveryModules.class)
    @XmlElement(name = "moduleName", required = false)
    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
    
    /**
     * List of objects in database which requires rediscovery.
     * E.g. Objects include Export Masks, Consistency Groups, Volumes ..
     * @return
     */
    @XmlElement(name="modules" , required = false)
    public List<URI> getModules() {
        return modules;
    }

    public void setModules(List<URI> modules) {
        this.modules = modules;
    }
    
}

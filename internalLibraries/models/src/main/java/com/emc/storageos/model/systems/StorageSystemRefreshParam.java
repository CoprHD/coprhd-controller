/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.systems;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.systems.DiscoverNamespaceParam.DiscoveryModules;

@XmlRootElement(name = "storage_system_refresh")
public class StorageSystemRefreshParam {
    
    
    private List<DiscoverNamespaceParam> namespaceParams;
    
    private List<URI> masks;

    /**
     * List of export masks which needs to be rediscovered.
     * Based on the namespace provided, the rediscovered data would be used for detecting changes or sync the changes back.
     * @return
     */
    @XmlElementWrapper(required = false, name = "masks")
    @XmlElement(required = false, name = "mask")
    public List<URI> getMasks() {
        if (masks == null) {
            masks = new ArrayList<URI>();
        }
        return masks;
    }

    public void setMasks(List<URI> masks) {
        this.masks = masks;
    }

    /**
     * List of objects in database which needs rediscovery
     * E.g. MASKING - List of Export mask URIs 
     *      CONSISTENCYGROUPS - List of consistency group URIs in Database.
     * @return
     */
    @XmlElementWrapper(name = "rediscover_modules")
    @XmlElement(name = "rediscover_module", required = false)
    public List<DiscoverNamespaceParam> getNamespaceParams() {
        if (null == namespaceParams) {
            return new ArrayList<DiscoverNamespaceParam>();
        }
        return namespaceParams;
    }

    public void setNamespaceParams(List<DiscoverNamespaceParam> namespaceParams) {
        this.namespaceParams = namespaceParams;
    }
    
    
}

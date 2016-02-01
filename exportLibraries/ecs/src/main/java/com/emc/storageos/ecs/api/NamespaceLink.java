/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

public class NamespaceLink {
    private String rel;
    private String href;
    
    String getRel() {
        return rel;
    }
    
    void setRel(String rel) {
        this.rel = rel;
    }
    
    String getHref() {
        return href;
    }
    
    void setHref(String href) {
        this.href = href;
    }
}
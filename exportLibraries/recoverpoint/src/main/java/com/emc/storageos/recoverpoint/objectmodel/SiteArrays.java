/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.objectmodel;

import java.util.Set;

/**
 * A site's key and the arrays that are visible to it.
 * 
 */
public class SiteArrays {
    private RPSite _site;
    private Set<String> _arrays;

    public RPSite getSite() {
        return _site;
    }

    public void setSite(RPSite site) {
        this._site = site;
    }

    public Set<String> getArrays() {
        return _arrays;
    }

    public void setArrays(Set<String> arrays) {
        this._arrays = arrays;
    }
}

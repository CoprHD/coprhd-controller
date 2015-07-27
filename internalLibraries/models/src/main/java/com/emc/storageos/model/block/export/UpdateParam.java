/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.Set;

public abstract class UpdateParam {
    
    protected Set<URI> add;
    protected Set<URI> remove;
    
    public abstract Set<URI> getAdd();
    public abstract Set<URI> getRemove();
    public boolean hasAdded() {
        return getAdd() != null && getAdd().size() > 0;
    }
    public boolean hasRemoved() {
        return getRemove() != null && getRemove().size() > 0;
    }
    public boolean hasUpdates() {
        return hasAdded() || hasRemoved();
    }
  
}

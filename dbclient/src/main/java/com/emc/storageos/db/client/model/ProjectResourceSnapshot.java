/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Interface for all resource snapshot types, to get parent id
 */
public interface ProjectResourceSnapshot {
    /**
     * get parent id
     * @return
     */
    public NamedURI getParent();

    /**
     * get parent DataObject class
     * @return
     */
    public Class<? extends DataObject> parentClass();

    /**
     * get project
     * @return
     */
    public NamedURI getProject();
}

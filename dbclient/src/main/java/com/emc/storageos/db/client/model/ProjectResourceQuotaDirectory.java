/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.db.client.model;

/**
 * Interface for all resource Quota directory types, to get parent id
 */
public interface ProjectResourceQuotaDirectory extends ProjectResourceSnapshot {
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







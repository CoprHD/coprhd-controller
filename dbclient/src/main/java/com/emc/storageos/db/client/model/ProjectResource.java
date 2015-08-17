/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Interface for all resources under project, to get project
 */
public interface ProjectResource {
    /**
     * get project id
     * 
     * @return
     */
    public NamedURI getProject();

    /**
     * get tenant id of the containing project of the resource
     * 
     * @return
     */
    public NamedURI getTenant();

}

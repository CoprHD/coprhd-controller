/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Interface for all resource snapshot types, to get parent id
 */
public interface ProjectResourceSnapshot {
    /**
     * get parent id
     * 
     * @return
     */
    public NamedURI getParent();

    /**
     * get parent DataObject class
     * 
     * @return
     */
    public Class<? extends DataObject> parentClass();

    /**
     * get project
     * 
     * @return
     */
    public NamedURI getProject();
}

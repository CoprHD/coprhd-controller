/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {

    public BulkIdParam createBulkIdParam() {
        return new BulkIdParam();
    }

    public TagAssignment createTagAssignment() {
        return new TagAssignment();
    }

}

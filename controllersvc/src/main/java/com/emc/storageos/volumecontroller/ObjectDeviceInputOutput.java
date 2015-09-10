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
package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.StoragePool;

/**
 * Class defining input/output from Object storage device interface
 * to expose only the fields that are needed/can be modified by storage device implementations
 */
public class ObjectDeviceInputOutput {
    private String name;
    private String namespace;
    private String repGroup;
    private String retentionPeriod;
    private String blkSizeHQ;
    private String notSizeSQ;
    private String owner;

    /*
     * get and set of each members
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setRepGroup(String repGroup) {
        this.repGroup = repGroup;
    }

    public String getRepGroup() {
        return repGroup;
    }

    public void setRetentionPeriod(String retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public String getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setBlkSizeHQ(String blkSizeHQ) {
        this.blkSizeHQ = blkSizeHQ;
    }

    public String getBlkSizeHQ() {
        return blkSizeHQ;
    }

    public void setNotSizeSQ(String notSizeSQ) {
        this.notSizeSQ = notSizeSQ;
    }

    public String getNotSizeSQ() {
        return notSizeSQ;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

}

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

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACLUpdateParams;

/**
 * Class defining input/output from Object storage device interface
 * to expose only the fields that are needed/can be modified by storage device implementations
 */
public class ObjectDeviceInputOutput {
    private String name;
    private String namespace;
    private String devStoragePool;
    private Integer retentionPeriod;
    private Long blkSizeHQ;
    private Long notSizeSQ;
    private String owner;
        
    // New additions for Bucket ACL work
    private BucketACLUpdateParams bucketACLUpdateParams;
    private List<BucketACE> bucketAclToAdd = new ArrayList<>();
    private List<BucketACE> bucketAclToModify = new ArrayList<>();
    private List<BucketACE> bucketAclToDelete = new ArrayList<>();
    private List<BucketACE> existingBucketAcl = new ArrayList<>();

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

    public void setDevStoragePool(String devStoragePool) {
        this.devStoragePool = devStoragePool;
    }

    public String getDevStoragePool() {
        return devStoragePool;
    }

    public void setRetentionPeriod(Integer retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public Integer getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setBlkSizeHQ(Long blkSizeHQ) {
        this.blkSizeHQ = blkSizeHQ;
    }

    public Long getBlkSizeHQ() {
        return blkSizeHQ;
    }

    public void setNotSizeSQ(Long notSizeSQ) {
        this.notSizeSQ = notSizeSQ;
    }

    public Long getNotSizeSQ() {
        return notSizeSQ;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public BucketACLUpdateParams getBucketACLUpdateParams() {
        return bucketACLUpdateParams;
    }

    public void setBucketACLUpdateParams(BucketACLUpdateParams bucketACLUpdateParams) {
        this.bucketACLUpdateParams = bucketACLUpdateParams;
    }

    public List<BucketACE> getBucketAclToAdd() {
        return bucketAclToAdd;
    }

    public void setBucketAclToAdd(List<BucketACE> bucketAclToAdd) {
        this.bucketAclToAdd = bucketAclToAdd;
    }

    public List<BucketACE> getBucketAclToModify() {
        return bucketAclToModify;
    }

    public void setBucketAclToModify(List<BucketACE> bucketAclToModify) {
        this.bucketAclToModify = bucketAclToModify;
    }

    public List<BucketACE> getBucketAclToDelete() {
        return bucketAclToDelete;
    }

    public void setBucketAclToDelete(List<BucketACE> bucketAclToDelete) {
        this.bucketAclToDelete = bucketAclToDelete;
    }
    
    
    public List<BucketACE> getExistingBucketAcl() {
        return existingBucketAcl;
    }

    public void setExistingBucketAcl(List<BucketACE> existingBucketAcl) {
        this.existingBucketAcl = existingBucketAcl;
    }

    public void setAllBuckectAcl(BucketACLUpdateParams param) {

        bucketACLUpdateParams = param;

        if (param.getAclToAdd() != null && !param.getAclToAdd().getBucketACL().isEmpty()) {
            this.bucketAclToAdd = param.getAclToAdd().getBucketACL();
        }
        if (param.getAclToModify() != null && !param.getAclToModify().getBucketACL().isEmpty()) {
            this.bucketAclToModify = param.getAclToModify().getBucketACL();
        }
        if (param.getAclToDelete() != null && !param.getAclToDelete().getBucketACL().isEmpty()) {
            this.bucketAclToDelete = param.getAclToDelete().getBucketACL();
        }

    }
    
}

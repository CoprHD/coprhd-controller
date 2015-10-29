/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_buckets")
public class BucketBulkRep extends BulkRestRep {
    private List<BucketRestRep> buckets;

    /**
     * The list of buckets, returned as response to bulk
     * queries.
     * 
     * @valid none
     */
    @XmlElement(name = "buckets")
    public List<BucketRestRep> getBuckets() {
        if (buckets == null) {
            buckets = new ArrayList<BucketRestRep>();
        }
        return buckets;
    }

    public void setBuckets(List<BucketRestRep> objectBuckets) {
        this.buckets = objectBuckets;
    }

    public BucketBulkRep() {
    }

    public BucketBulkRep(List<BucketRestRep> objectBuckets) {
        this.buckets = objectBuckets;
    }
}

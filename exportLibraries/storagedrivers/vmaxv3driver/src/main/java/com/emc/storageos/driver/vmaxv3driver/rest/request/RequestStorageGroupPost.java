/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

import com.emc.storageos.driver.vmaxv3driver.base.RequestBody;

import java.util.List;

/**
 * The request class is used to generate the StorageGroup POST request like below:
 *
 * {
 *   "srpId": "SRP_1",
 *   "storageGroupId": "test2",
 *   "sloBasedStorageGroupParam": [
 *     {
 *       "num_of_vols": 3,
 *       "sloId": "Silver",
 *       "workloadSelection": "OLTP",
 *       "volumeAttribute": {
 *         "volume_size": "100",
 *         "capacityUnit": "GB"
 *       }
 *     },
 *     {
 *       "num_of_vols": 2,
 *       "sloId": "Gold",
 *       "workloadSelection": "OLAP",
 *       "volumeAttribute": {
 *         "volume_size": "2",
 *         "capacityUnit": "TB"
 *       }
 *     }
 *   ]
 * }
 *
 * Created by gang on 9/26/16.
 */
public class RequestStorageGroupPost extends RequestBody {
    private String srpId;
    private String storageGroupId;
    private List<SloBasedStorageGroupParam> sloBasedStorageGroupParam;

    @Override
    public String toString() {
        return "RequestStorageGroupPost{" +
            "srpId='" + srpId + '\'' +
            ", storageGroupId='" + storageGroupId + '\'' +
            ", sloBasedStorageGroupParam=" + sloBasedStorageGroupParam +
            '}';
    }

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

    public String getStorageGroupId() {
        return storageGroupId;
    }

    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public List<SloBasedStorageGroupParam> getSloBasedStorageGroupParam() {
        return sloBasedStorageGroupParam;
    }

    public void setSloBasedStorageGroupParam(List<SloBasedStorageGroupParam> sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }
}

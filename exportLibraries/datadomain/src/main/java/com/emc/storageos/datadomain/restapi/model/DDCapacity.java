/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by zeldib on 2/10/14.
 */
public class DDCapacity {

    private long total;

    private long used;

    private long available;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getUsed() {
        return used;
    }

    public void setUsed(long used) {
        this.used = used;
    }

    public long getAvailable() {
        return available;
    }

    public void setAvailable(long available) {
        this.available = available;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }
}

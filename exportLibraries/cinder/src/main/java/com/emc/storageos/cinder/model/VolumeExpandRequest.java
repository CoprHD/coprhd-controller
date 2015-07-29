/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.model;

import com.google.gson.annotations.SerializedName;

/**
 * 
 * {"os-extend":
 * {"new_size": 2}
 * }
 * 
 */
public class VolumeExpandRequest {

    @SerializedName("os-extend")
    public os_extend extend = new os_extend();

    public class os_extend {
        public long new_size;
    }
}

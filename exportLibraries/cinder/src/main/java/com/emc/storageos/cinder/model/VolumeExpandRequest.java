/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

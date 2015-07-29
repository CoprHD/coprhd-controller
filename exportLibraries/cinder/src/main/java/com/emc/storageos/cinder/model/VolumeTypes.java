/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

/**
 * Wrapper for Volume Types
 * 
 */
public class VolumeTypes {
    public class VolumeType {
        public String name;
        public String id;
        public Map<String, String> extra_specs;
    }

    public VolumeType[] volume_types;
}

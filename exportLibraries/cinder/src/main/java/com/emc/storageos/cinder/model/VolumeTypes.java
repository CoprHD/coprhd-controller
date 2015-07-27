/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

/**
 * Wrapper for Volume Types
 * 
 */
public class VolumeTypes {
    public class VolumeType{
        public String name;
        public String id;
        public Map<String, String> extra_specs;
    }

    public VolumeType[] volume_types;
}

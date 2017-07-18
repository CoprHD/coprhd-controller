/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractResponse;

/**
 * @author fengs5
 *
 */
public class VolumeType extends AbstractResponse {
    private String volumeId;
    private String type;
    private String emulation;
    private String ssid;
    private long allocated_percent;
    private double cap_gb;
    private double cap_mb;
    private long cap_cyl;
    private String status;
    private boolean reserved;
    private boolean pinned;
    private String physical_name;
    private String volume_identifier;
    private String wwn;
    private boolean encapsulated;
    private int num_of_storage_groups;
    private long num_of_front_end_paths;
    private String storageGroupId;
    private boolean snapvx_source;
    private boolean snapvx_target;
    private String cu_image_base_address;
    private boolean has_effective_wwn;
    private String effective_wwn;
    private String encapsulated_wwn;
    private SymmetrixPortKeyType symmetrixPortKey;
    private RdfGroupIdType rdfGroupId;

}

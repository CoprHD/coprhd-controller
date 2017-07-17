/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.bean;

/**
 * @author fengs5
 *
 */
public enum VolumeIdentifierChoiceType {
    none,
    identifier_name,
    identifier_name_plus_volume_id,
    identifier_name_plus_append_number
}

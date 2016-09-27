/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

/**
 * Marker interface for request bean in REST API(create/update) body parsing.
 * The request bean will be parsed from the SBSDK framework method call argument(s)
 * with a RestRequestConverter.
 *
 * Created by gang on 9/26/16.
 */
public interface RestRequest {
}

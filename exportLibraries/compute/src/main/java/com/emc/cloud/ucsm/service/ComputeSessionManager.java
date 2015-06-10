/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

public interface ComputeSessionManager {

    ComputeSession getSession(String serviceUri, String username, String password);

}

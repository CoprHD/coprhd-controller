/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service;

import java.io.IOException;

/**
 * Main API for coordinator server
 */
public interface Coordinator {
    public void start() throws IOException;

    public void stop();
}

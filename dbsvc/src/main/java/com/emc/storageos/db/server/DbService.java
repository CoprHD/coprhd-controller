/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import java.io.IOException;

/**
 * The main API for database service
 */
public interface DbService {
    /**
     * Starts database service and service beacon. If coordinator cluster cannot be
     * reached, beacon will keep retrying at preconfigured interval
     * 
     * @throws IOException if cannot bind to ports
     */
    public void start() throws IOException;

    /**
     * Stops service beacon and database service.
     * 
     */
    public void stop();

    /**
     * Stops service beacon and database service with node decommisioned.
     * Which could protect data and save the restart time. It's usually need a
     * system exit after the decommission.
     */
    public void stopWithDecommission();
}

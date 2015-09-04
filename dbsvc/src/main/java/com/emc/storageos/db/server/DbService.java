/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

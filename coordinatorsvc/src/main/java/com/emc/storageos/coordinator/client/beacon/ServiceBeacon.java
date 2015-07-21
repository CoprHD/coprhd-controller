/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.beacon;

import com.emc.storageos.coordinator.common.Service;

import java.io.IOException;

/**
 * The main API for service beacon management
 */
public interface ServiceBeacon {
   /**
    * Retrieve service information for this beacon.   This information
    * is published to coordinator cluster and looked up using
    * {@link com.emc.storageos.coordinator.client.service.CoordinatorClient} API.
    * 
    * @return service information including name, version, endpoint, etc.
    */
   public Service info();

   /**
    * Starts service beacon.   If coordinator cluster is / becomes unavailable,
    * default implementation continuously tries to connect sleeping some
    * preconfigured amount of time in between.
    */
   public void start();

   /**
    * Stops service beacon.  Attempts to cleanly remove service information from
    * coordinator cluster.   If coordinator cluster cannot be reached, default
    * implementation shuts down without cleanup - coordinator cluster will detect
    * that beacon has become unavailable and remove it automatically after a
    * configured timeout period.
    */
   public void stop();
}

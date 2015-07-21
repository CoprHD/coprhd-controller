/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service;

/**
 */
public interface GeoService {
   /**
    * Starts geo service and registers with coordinator cluster
    *
    * @throws Exception
    */
   public void start() throws Exception;

   /**
    * Unregisters from coordinator cluster and stops geo service
    *
    * @throws Exception
    */
   public void stop() throws Exception;
}

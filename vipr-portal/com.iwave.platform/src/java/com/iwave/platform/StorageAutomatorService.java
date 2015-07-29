/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

/**
 * The main API for storage automator service lifecycle management
 */
public interface StorageAutomatorService {
    /**
     * Starts storage automator service and registers with coordinator cluster
     * 
     * @throws Exception
     */
    public void start() throws Exception;

    /**
     * Unregisters from coordinator cluster and stops provisioning service
     * 
     * @throws Exception
     */
    public void stop() throws Exception;
}

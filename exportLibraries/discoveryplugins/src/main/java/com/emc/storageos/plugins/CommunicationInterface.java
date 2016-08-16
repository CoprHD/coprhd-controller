/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins;

/**
 * All Statistics Collection plugins must implement this ICommunicationinterface
 */
public interface CommunicationInterface {
    /**
     * This method is responsible for collecting Statistics information from
     * underlying data sources (Providers or from Arrays directly).
     * 
     * @param AccessProfile
     * @throws BaseCollectionException
     */
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException;

    /**
     * Scan Providers to detect Arrays.
     * 
     * @param accessProfile
     * @throws BaseCollectionException
     */
    public void scan(AccessProfile accessProfile) throws BaseCollectionException;

    /**
     * For each detected Array, discovery would get called.
     * 
     * @param accessProfile
     * @throws BaseCollectionException
     */
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException;

    public void discoverArrayAffinity(AccessProfile accessProfile)
            throws BaseCollectionException;

    /**
     * clean up the resources.
     */
    public void cleanup();
}

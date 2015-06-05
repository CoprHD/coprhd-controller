/**
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

    /**
     * clean up the resources.
     */
    public void cleanup();
}

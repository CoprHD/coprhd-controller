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

package com.emc.storageos.coordinator.common;

import java.io.IOException;
import java.net.URI;

/**
 * Service API for querying service information
 */
public interface Service {
    /**
     * Service name
     *
     * @return service name
     */
    public String getName();

    /**
     * Service version
     *
     * @return service version
     */
    public String getVersion();

    /**
     * Set service version
     *
     * @param version
     */
    public void setVersion(String version);

    /**
     * Service UUID
     *
     * @return service UUID
     */
    public String getId();

    /**
     * Node name as seen in SSH login.
     */
    public String getNodeName();

    /**
     * Service specified attribute
     *
     * @param key attribute key
     * @return attribute value for given key.  null if no such attribute exists.
     */
    public String getAttribute(String key);

    /**
     * Checks if service has been tagged with given string
     *
     * @param tag tag to check against
     * @return true tag has been applied.  false, otherwise.
     */
    public boolean isTagged(String tag);

    /**
     * Service endpoint URI.  If multiple endpoints are available for
     * this service, random one is returned
     *
     * @return service URI
     */
    public URI getEndpoint();

    /**
     * Retrieve endpoint URI with specific key
     *
     * @return null if no matching key is found
     */
    public URI getEndpoint(String key);

    /**
     * Serializes service information
     *
     * @return
     */
    public byte[] serialize() throws IOException;
}

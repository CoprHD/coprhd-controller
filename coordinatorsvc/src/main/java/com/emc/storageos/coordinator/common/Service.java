/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
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
     * Node id
     */
    public String getNodeId();

    /**
     * Node name as seen in SSH login.
     */
    public String getNodeName();

    /**
     * Service specified attribute
     * 
     * @param key attribute key
     * @return attribute value for given key. null if no such attribute exists.
     */
    public String getAttribute(String key);

    /**
     * Checks if service has been tagged with given string
     * 
     * @param tag tag to check against
     * @return true tag has been applied. false, otherwise.
     */
    public boolean isTagged(String tag);

    /**
     * Service endpoint URI. If multiple endpoints are available for
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

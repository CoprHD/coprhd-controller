/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Metering collector and aggregator version details along with device id
 */
@Cf("ObjectMeteringInfo")
public class ObjectMeteringInfo extends DataObject {

    private Long _collectorVersion;

    private Long _aggregatorVersion;

    private URI _deviceId;

    /**
     * return collector id
     * 
     * @return
     */

    @Name("collectorVersion")
    public Long getCollectorVersion() {
        return _collectorVersion;
    }

    public void setCollectorVersion(Long collectorVersion) {
        _collectorVersion = collectorVersion;
        setChanged("collectorVersion");
    }

    /**
     * return aggregator id
     * 
     * @return
     */

    @Name("aggregatorVersion")
    public Long getAggregatorVersion() {
        return _aggregatorVersion;
    }

    public void setAggregatorVersion(Long aggregatorVersion) {
        _aggregatorVersion = aggregatorVersion;
        setChanged("aggregatorVersion");
    }

    /**
     * return device id for the journal
     * 
     * @return
     */

    @Name("deviceId")
    public URI getDeviceId() {
        return _deviceId;
    }

    public void setDeviceId(URI deviceId) {
        _deviceId = deviceId;
        setChanged("deviceId");
    }

}
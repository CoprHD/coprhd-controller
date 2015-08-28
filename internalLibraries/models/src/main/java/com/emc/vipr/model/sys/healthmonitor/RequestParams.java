/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class that holds request params for node stats.
 */
@XmlRootElement
public class RequestParams {

    private int interval;

    public RequestParams() {
    }

    public RequestParams(int interval) {
        this.interval = interval;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }
}

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;

/**
 * The base testing class for all REST API call testing class.
 *
 * Created by gang on 6/23/16.
 */
public abstract class BaseRestTest {

    protected RestClient getClient() {
        // new RestClient("https", "lgloc227.lss.emc.com", 8443, "smc", "smc");
        return new RestClient("https", "lglw7150.lss.emc.com", 8443, "smc", "smc");
    }

    protected ArrayInfo getDefaultArray() {
        // "000196801468";
        return new ArrayInfo("000196801612", "SRP_1", "DF-1C", "12");
    }

    protected ArrayInfo getArrayWithFcPort() {
        return new ArrayInfo("000196701343", null, "FA-1D", "24");
    }

    protected ArrayInfo getArrayWithIscsiPort() {
        return new ArrayInfo("000196701343", null, "SE-4F", "11");
    }

    protected ArrayInfo getArrayWithRdfPort() {
        return new ArrayInfo("000196701343", null, "RF-2G", "26");
    }
}
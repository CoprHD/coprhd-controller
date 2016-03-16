/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lakhiv
 * 
 */
public class NetAppClient {

    RestClient client;
    private final Logger _log = LoggerFactory.getLogger(NetAppClient.class);

    public NetAppClient(RestClient restClient) {
        client = restClient;
    }
}
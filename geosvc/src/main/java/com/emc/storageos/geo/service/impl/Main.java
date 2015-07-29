/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geo.service.impl;

import com.emc.storageos.geo.service.GeoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * Use this to start geo service with a Spring configuration
 */
public class Main {
    private static final String SERVICE_BEAN = "geoservice";
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(args);
            GeoService geoService = (GeoService) ctx.getBean(SERVICE_BEAN);
            geoService.start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }
}

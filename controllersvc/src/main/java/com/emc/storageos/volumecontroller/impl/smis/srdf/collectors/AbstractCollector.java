/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public abstract class AbstractCollector implements CollectorStrategy {
    private static final Logger log = LoggerFactory.getLogger(AbstractCollector.class);

    protected DbClient dbClient;
    protected SRDFUtils utils;

    public AbstractCollector(DbClient dbClient, SRDFUtils utils) {
        this.dbClient = dbClient;
        this.utils = utils;
    }

}

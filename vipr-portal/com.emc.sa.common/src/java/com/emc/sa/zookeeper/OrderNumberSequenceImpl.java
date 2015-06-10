/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * * An Atomic sequence that provides the next unique OrderNumber throughout the ViPR cluster
 *
 * The number is stored in Zookeeper at the path /config/global/saordernumber
 * The lock "saordernumber" is used to gain access
 *
 * @author  dmaddison
 */
@Component
public class OrderNumberSequenceImpl implements OrderNumberSequence {
    private static Logger LOG = Logger.getLogger(OrderNumberSequenceImpl.class);

    private static final String LOCK_NAME="saordernumber";
    private static final String COUNTER_PATH="/config/sa/saordernumber";

    @Autowired
    private CoordinatorClient coordinatorClient;

    private InterProcessLock lock;
    private DistributedDataManager dataMgr;

    @PostConstruct
    public void start() {
        lock = coordinatorClient.getLock(LOCK_NAME);
        dataMgr = coordinatorClient.createDistributedDataManager(COUNTER_PATH);

        try {
            // Initialize the counter (if we're the first)
            lock.acquire();
            Object counterPathExists = dataMgr.checkExists(COUNTER_PATH);
            if ((counterPathExists == null) || (dataMgr.getData(COUNTER_PATH, false) == null)) {
                LOG.info("Initializing Order Number Sequence");

                if (counterPathExists == null) {
                    try {
                        dataMgr.createNode(COUNTER_PATH, false);
                    } catch (KeeperException.NodeExistsException e) {
                        LOG.warn("node already exists: ", e);
                    }
                }
                // TODO : Intialize from Database?
                dataMgr.putData(COUNTER_PATH, 0l);
            }
        } catch (Exception e) {
            throw new RuntimeException("Starting OrderNumber Sequence",e);
        }
        finally {
            try {
                lock.release();
            }
            catch (Exception e) {
                LOG.error("Error releasing Order Number lock", e);
            }
        }
    }

    /** Generates the next order number */
    public long nextOrderNumber() {
        try {
            lock.acquire();
            Long currentValue = (Long)dataMgr.getData(COUNTER_PATH, false);
            Long newOrderNumber = currentValue+1;
            dataMgr.putData(COUNTER_PATH, newOrderNumber);

            return newOrderNumber;
        }
        catch (Exception e) {
            throw new RuntimeException("Updating Order Number Lock",e);
        }
        finally {
            try {
                lock.release();
            }
            catch (Exception e) {
                LOG.error("Error releasing Order Number lock", e);
            }
        }
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @PreDestroy
    public void closeDataManager() {
        if (dataMgr != null) {
            dataMgr.close();
        }
    }
}

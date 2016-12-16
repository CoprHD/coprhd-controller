/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.OrderManager;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.sa.api.OrderService;

public class OrderServiceJobConsumer extends DistributedQueueConsumer<OrderServiceJob> {
    private final Logger log = LoggerFactory.getLogger(OrderServiceJobConsumer.class);

    private static int MAX_DB_RETRY = 30;
    DbClient dbClient;
    OrderManager orderManager;
    OrderService orderService;

    public OrderServiceJobConsumer(OrderService service, DbClient client, OrderManager manager) {
        dbClient = client;
        orderManager = manager;
        orderService = service;
    }

    /**
     * @param job The object provisioning job which is being worked on. This could be either creation or deletion job
     * @param callback This must be executed, after the item is processed successfully to remove the item
     *            from the distributed queue
     *
     * @throws Exception
     */
    @Override
    public void consumeItem(OrderServiceJob job, DistributedQueueItemProcessedCallback callback) throws Exception {

        log.info("The job job={} callback={}", job, callback);
        boolean error = false;

        try {
            long startTime = job.getStartTime();
            long endTime = job.getEndTime();
            List<URI> tids = job.getTenandIDs();
            log.info("lbyh tids={}", tids);

            OrderJobStatus jobStatus = orderService.queryJobInfo(OrderServiceJob.JobType.DELETE);

            if (jobStatus.getTotal() == -1) {
                //It's the first time to run the job, so get the total number of orders to be deleted
                long total = 0;
                for (URI tid : tids) {
                    AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getOrders(tid, startTime, endTime);
                    NamedElementQueryResultList ids = new NamedElementQueryResultList();
                    dbClient.queryByConstraint(constraint, ids);
                    for (NamedElementQueryResultList.NamedElement namedID : ids) {
                        URI id = namedID.getId();
                        log.info("lbyh0: id={}", id);
                        Order order = orderManager.getOrderById(id);
                        try {
                            orderManager.canBeDeleted(order);
                            total++;
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }

                jobStatus.setTotal(total);
                orderService.saveJobInfo(OrderServiceJob.JobType.DELETE, jobStatus);
            }

        /*
        long now = System.currentTimeMillis();
        long lastCompletedTimeStamp = job.getLastCompletedTimeStamp();
        long sleepTime = lastCompletedTimeStamp+432000*1000-now;
        if (sleepTime >0) {
            Thread.sleep(sleepTime);
        }
        */

            long nDeleted = 0;
            long nFailed = 0;
            long start = System.currentTimeMillis();
            for (URI tid : tids) {
                AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getOrders(tid, startTime, endTime);
                NamedElementQueryResultList ids = new NamedElementQueryResultList();
                dbClient.queryByConstraint(constraint, ids);
                for (NamedElementQueryResultList.NamedElement namedID : ids) {
                    URI id = namedID.getId();
                    log.info("lbyh id={}", id);
                    try {
                        orderManager.deleteOrder(id, tid.toString());
                        nDeleted++;
                    } catch (BadRequestException e) {
                        //TODO: change to debug level
                        log.error("lbyh failed to delete order {} e=", id, e);
                        nFailed++;
                    } catch (Exception e) {
                        log.error("lbyh1: failed to delete order={} e=", id, e);
                        error = true;
                        nFailed++;
                    }
                }
            }
            long end = System.currentTimeMillis();
            long speed = (end-start)/(nDeleted+nFailed);

            jobStatus.increaseCompleted(nDeleted);
            jobStatus.setFailed(nFailed);
            jobStatus.setTimeUsedPerOrder(speed);

            orderService.saveJobInfo(OrderServiceJob.JobType.DELETE, jobStatus);

            if (!error) {
                log.info("lbyh9: remove order job from the queue");
                callback.itemProcessed();
            }
        }catch (Exception e) {
            log.info("lbyhhh e=",e);
            throw e;
        }
    }
}

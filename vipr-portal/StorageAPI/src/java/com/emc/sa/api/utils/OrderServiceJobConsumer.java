/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.TimeSeriesConstraint;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.services.OperationTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.OrderManager;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.sa.api.OrderService;

public class OrderServiceJobConsumer extends DistributedQueueConsumer<OrderServiceJob> {
    private final Logger log = LoggerFactory.getLogger(OrderServiceJobConsumer.class);

    DbClient dbClient;
    OrderManager orderManager;
    OrderService orderService;

    AuditLogManager auditLogManager;

    public OrderServiceJobConsumer(OrderService service, AuditLogManager auditLogManager,
                                   DbClient client, OrderManager manager) {
        dbClient = client;
        orderManager = manager;
        orderService = service;

        this.auditLogManager = auditLogManager;
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

        try {
            OrderJobStatus jobStatus = orderService.queryJobInfo(OrderServiceJob.JobType.DELETE_ORDER);
            long startTime = jobStatus.getStartTime();
            long endTime = jobStatus.getEndTime();
            List<URI> tids = jobStatus.getTids();

            log.info("lbyk0 tids={} startTime={} endTime={}", tids, startTime, endTime);

            if (jobStatus.getTotal() == -1) {
                //It's the first time to run the job, so get the total number of orders to be deleted
                long total = 0;
                for (URI tid : tids) {
                    TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrders(tid, startTime, endTime);
                    NamedElementQueryResultList ids = new NamedElementQueryResultList();
                    dbClient.queryByConstraint(constraint, ids);
                    for (NamedElementQueryResultList.NamedElement namedID : ids) {
                        URI id = namedID.getId();
                        log.info("lbyk1: id={}", id);
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
                log.info("lbyk2: total={}", total);
                orderService.saveJobInfo(jobStatus);
            }

            long nDeleted = 0;
            long nFailed = 0;
            long start = System.currentTimeMillis();
            for (URI tid : tids) {
                TimeSeriesConstraint constraint = TimeSeriesConstraint.Factory.getOrders(tid, startTime, endTime);
                NamedElementQueryResultList ids = new NamedElementQueryResultList();
                dbClient.queryByConstraint(constraint, ids);
                log.info("lbyk6 tid={}", tid);
                for (NamedElementQueryResultList.NamedElement namedID : ids) {
                    URI id = namedID.getId();
                    log.info("lbyk3 id={}", id);
                    Order order = orderManager.getOrderById(id);
                    try {
                        orderManager.deleteOrder(order);
                        nDeleted++;
                        auditLog(order, true, jobStatus.getTenantId(), jobStatus.getUserId());
                        log.info("lbyk4 nDeleted={}", nDeleted);
                    } catch (BadRequestException e) {
                        //TODO: change to debug level
                        log.error("lbyk5 failed to delete order {} e=", id, e);
                        auditLog(order, false, jobStatus.getTenantId(), jobStatus.getUserId());
                        nFailed++;
                    } catch (Exception e) {
                        log.error("lbyk5: failed to delete order={} e=", id, e);
                        auditLog(order, false, jobStatus.getTenantId(), jobStatus.getUserId());
                        nFailed++;
                    }
                }
            }

            jobStatus.addCompleted(nDeleted);
            jobStatus.setFailed(nFailed);

            long end = System.currentTimeMillis();
            long speed = (end-start)/(nDeleted+nFailed);

            jobStatus.setTimeUsedPerOrder(speed);

            orderService.saveJobInfo(jobStatus);
            log.info("lbyk9 jobStatus={}", jobStatus);

            if (jobStatus.isFinished()) {
                log.info("lbyk7: remove order job from the queue");
                callback.itemProcessed();
            }
        }catch (Exception e) {
            log.info("lbyk8 e=",e);
            throw e;
        }

    }

    private void auditLog(Order order, boolean success, URI tid, URI uid) {
        String operationStatus = success ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE;
        auditLogManager.recordAuditLog(tid, uid, orderService.getServiceType(),
                OperationTypeEnum.DELETE_ORDER, System.currentTimeMillis(),
                operationStatus, null, order.auditParameters());
    }
}

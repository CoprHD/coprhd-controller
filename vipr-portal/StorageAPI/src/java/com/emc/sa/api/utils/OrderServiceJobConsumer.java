package com.emc.sa.api.utils;

import com.emc.sa.catalog.OrderManager;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

public class OrderServiceJobConsumer extends DistributedQueueConsumer<OrderServiceJob> {
    private final Logger log = LoggerFactory.getLogger(OrderServiceJobConsumer.class);

    private static int MAX_DB_RETRY = 30;
    DbClient dbClient;
    OrderManager orderManager;

    public OrderServiceJobConsumer(DbClient client, OrderManager manager) {
        dbClient = client;
        orderManager = manager;
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
            long startTime = job.getStartTime();
            long endTime = job.getEndTime();

        /*
        long now = System.currentTimeMillis();
        long lastCompletedTimeStamp = job.getLastCompletedTimeStamp();
        long sleepTime = lastCompletedTimeStamp+432000*1000-now;
        if (sleepTime >0) {
            Thread.sleep(sleepTime);
        }
        */

            boolean error = false;
            List<URI> tids = job.getTenandIDs();
            log.info("lbyh tids={}", tids);
            for (URI tid : tids) {
                AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getOrders(tid, startTime, endTime);
                NamedElementQueryResultList ids = new NamedElementQueryResultList();
                dbClient.queryByConstraint(constraint, ids);
                for (NamedElementQueryResultList.NamedElement namedID : ids) {
                    URI id = namedID.getId();
                    log.info("lbyh id={}", id);
                    try {
                        orderManager.deleteOrder(id, tid.toString());
                    } catch (BadRequestException e) {
                        //TODO: change to debug level
                        log.error("lbyh failed to delete order {} e=", id, e);
                    } catch (Exception e) {
                        log.error("lbyh1: failed to delete order={} e=", id, e);
                        error = true;
                    }
                }
            }

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

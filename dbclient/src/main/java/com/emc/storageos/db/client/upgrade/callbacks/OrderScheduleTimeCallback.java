/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.*;

import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to convert value of type field for an export group, from
 * Exclusive to Initiator.
 */
public class OrderScheduleTimeCallback extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory
            .getLogger(OrderScheduleTimeCallback.class);

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Handle Order schedule time ...");
        DbClient dbClient = getDbClient();
        List<URI> orders = dbClient.queryByType(Order.class, true);
        for (URI uri : orders) {
            Order order = dbClient.queryObject(Order.class, uri);
            if (order == null)
                continue;
            if (order.getScheduledEventId() == null) {
                if (order.getExecutionWindowId() == null ||
                    order.getExecutionWindowId().getURI().equals(ExecutionWindow.NEXT)) {
                    Calendar scheduleTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    scheduleTime.setTime(order.getLastUpdated());
                    order.setExecutionWindowId(new NamedURI(ExecutionWindow.NEXT, "NEXT"));
                    order.setScheduledTime(scheduleTime);
                } else {
                    // For original orders, set schedule time to
                    // either 1) the next execution window starting time
                    // or     2) the current time if it is in current execution window
                    ExecutionWindow executionWindow = dbClient.queryObject(ExecutionWindow.class, order.getExecutionWindowId().getURI());
                    if (executionWindow == null)
                        continue;
                    ExecutionWindowHelper helper = new ExecutionWindowHelper(executionWindow);
                    order.setScheduledTime(helper.getScheduledTime());
                }

                dbClient.updateObject(order);
            }
        }

        return;
    }
}
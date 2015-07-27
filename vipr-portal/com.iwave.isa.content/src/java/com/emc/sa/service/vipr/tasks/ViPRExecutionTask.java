/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;
import java.util.List;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.storageos.db.client.model.uimodels.Order;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.vipr.client.ViPRCoreClient;

public abstract class ViPRExecutionTask<T> extends ExecutionTask<T> {
    @Inject
    private ViPRCoreClient client;
    
    @Inject 
    private EncryptionProvider encryption;

    public ViPRCoreClient getClient() {
        return client;
    }

    public static URI uri(String id) {
        return ViPRExecutionUtils.uri(id);
    }

    public static List<URI> uris(List<String> ids) {
        return ViPRExecutionUtils.uris(ids);
    }

    protected URI getOrderTenant() {
        return ViPRExecutionUtils.getOrderTenant();
    }

    protected String decrypt(String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                return encryption.decrypt(Base64.decodeBase64(value));
            }
            catch (RuntimeException e) {
                throw new IllegalStateException(String.format("Failed to decrypt value: %s", e.getMessage()), e);
            }
        }
        return value;
    }

    /**
     * Adds the OrderNumber and the OrderId as machine Tags to the specified task
     * which links the Task to the order
     */
    protected void addOrderIdTag(URI taskId) {
        Order order = ExecutionUtils.currentContext().getOrder();
        if (order != null) {
            if (order.getId() != null) {
                MachineTagUtils.setTaskOrderIdTag(getClient(), taskId, order.getId().toString());
            }
            MachineTagUtils.setTaskOrderNumberTag(getClient(), taskId, order.getOrderNumber());
        }
    }
}

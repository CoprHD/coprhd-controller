/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

public enum OrderStatus {
    PENDING(false), EXECUTING(false), SUCCESS(true), PARTIAL_SUCCESS(true), ERROR(true),
    SCHEDULED(false), CANCELLED(true), APPROVAL(false), APPROVED(false), REJECTED(true);

    private boolean canBeDeleted = false;
    OrderStatus(boolean canBeDeleted) {
        this.canBeDeleted = canBeDeleted;
    }

    public boolean canBeDeleted() {
        return canBeDeleted;
    }
}

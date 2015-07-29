/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api;

/**
 * This class captures the progress of a cache-invalidate request
 * for a virtual volume when the request fails to complete within
 * the VPLEX specified 5 minute timeout period. In this case,
 * the cache-invalidate-status command is executed. The result is
 * parsed, and an instance of this class is created and updated
 * with the information contained within the response.
 */
public class VPlexCacheStatusInfo {

    // The status of the request.
    InvalidateStatus invalidateStatus = InvalidateStatus.IN_PROGRESS;

    // The reason why the cache invalidate failed, when it does fail.
    String invalidateFailedError = "";

    // Enum captures the states.
    public static enum InvalidateStatus {
        SUCCESS,
        FAILED,
        IN_PROGRESS
    };

    /**
     * Getter for the cache invalidate status.
     * 
     * @return An instance of InvalidateStatus specifying the current status.
     */
    public InvalidateStatus getCacheInvalidateStatus() {
        return invalidateStatus;
    }

    /**
     * Setter for the cache invalidate status.
     * 
     * @param status An instance of InvalidateStatus specifying the current status.
     */
    public void setCacheInvalidateStatus(InvalidateStatus status) {
        invalidateStatus = status;
    }

    /**
     * Getter for the failure message when the cache invalidate fails.
     * 
     * @return The failure message when the cache invalidate fails.
     */
    public String getCacheInvalidateFailedMessage() {
        return invalidateFailedError;
    }

    /**
     * Setter for the failure message when the cache invalidate fails.
     * 
     * @param errorMsg The failure message when the cache invalidate fails.
     */
    public void setCacheInvalidateFailedMessage(String errorMsg) {
        invalidateFailedError = errorMsg;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("CacheStatusInfo ( ");
        str.append(super.toString());
        str.append(", invalidateStatus: " + invalidateStatus.name());
        str.append(", invalidateFailedError: " + invalidateFailedError);
        str.append(" )");

        return str.toString();
    }
}

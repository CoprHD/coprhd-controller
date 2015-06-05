/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.retry.RetryPolicy;
import com.netflix.astyanax.retry.SleepingRetryPolicy;

/**
 * Retry policy that sleeps given amount of ms between query attempts
 */
public class QueryRetryPolicy extends SleepingRetryPolicy {
    private final long _sleepMs;

    public QueryRetryPolicy(int maxRetry, long sleepMs) {
        super(maxRetry);
        _sleepMs = sleepMs;
    }
    
    @Override
    public long getSleepTimeMs() {
        return _sleepMs;
    }

    @Override
    public RetryPolicy duplicate() {
        return new QueryRetryPolicy(getMax(), _sleepMs);
    }
}

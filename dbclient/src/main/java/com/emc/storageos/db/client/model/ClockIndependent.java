/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Make field db clock independent.  This is achieved by retaining all previous
 * values sorted according to ordinal values from enum.   No matters what timestamps
 * each write has, write value with highest ordinal wins.  Note this annotation must be
 * used with a TTL value to ensure old values are reaped from DB automatically.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClockIndependent {
    Class<? extends ClockIndependentValue> value();
}

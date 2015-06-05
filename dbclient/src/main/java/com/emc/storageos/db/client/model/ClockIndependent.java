/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

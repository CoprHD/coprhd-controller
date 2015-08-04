/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Indicate if corresponding CF should be created with special consideration with regard to reducing disk space footprint
 * Some of those options might include the following
 * - change compaction strategy
 * - change gc grace time
 * - changing ttl for some of the fields/classes.
 */
@Documented
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed
public @interface CompactionOptimized {
}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 * <p/>
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.lang.annotation.*;

/**
 * Indicate if corresponding CF should be created with special consideration with regard to reducing disk space footprint
 * Some of those options might include the following
 *      - change compaction strategy
 *      - change gc grace time
 *      - changing ttl for some of the fields/classes.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed
public @interface CompactionOptimized {
}

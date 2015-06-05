/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client;

import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.netflix.astyanax.model.Row;

import java.util.List;

/**
 */
public interface DbAggregatorItf {

    public void aggregate(Row<String, CompositeColumnName> row);

    public String[] getAggregatedFields();
}

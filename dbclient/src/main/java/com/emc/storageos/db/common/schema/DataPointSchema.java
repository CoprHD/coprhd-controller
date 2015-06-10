/*
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

package com.emc.storageos.db.common.schema;

import com.emc.storageos.db.client.model.TimeSeriesSerializer;

public class DataPointSchema extends DbSchema {

    public DataPointSchema() {
    }

    public DataPointSchema(Class<? extends TimeSeriesSerializer.DataPoint> clazz) {
        super(clazz);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DataPointSchema)) 
            return false;

        return super.equals(o);
    }
}

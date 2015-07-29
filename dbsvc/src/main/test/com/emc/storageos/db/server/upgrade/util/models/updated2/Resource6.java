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
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;

@Cf("Resource6")
public class Resource6 extends Resource3 {
    private Long dupTestFlags; // Test duplicated custom callback

    @Name("dupTestFlags")
    public Long getDupTestFlags() {
        return dupTestFlags;
    }

    public void setDupTestFlags(Long dupFlags) {
        this.dupTestFlags = dupFlags;
        setChanged("dupTestFlags");
    }
}

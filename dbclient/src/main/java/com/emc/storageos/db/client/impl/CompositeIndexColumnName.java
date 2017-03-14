/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.UUID;

public interface CompositeIndexColumnName {
    String getOne();

    String getTwo();

    String getThree();

    String getFour();

    UUID getTimeUUID();
}

package com.emc.storageos.db.client.impl;

import java.util.UUID;

/**
 * Created by brian on 10/28/16.
 */
public interface CompositeIndexColumnName {
    String getOne();

    String getTwo();

    String getThree();

    String getFour();

    UUID getTimeUUID();
}

/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.db.client;

import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;

import java.net.URI;

/**
 * Created by wangs12 on 7/25/2017.
 */
public interface DbViewQuery {
    <T extends DataObject> QueryResultList<T> listByView(Class<T> clazz, String viewName, String key, Object[] clusterKeys);

    void listVolumesByProject(URI project, int type, QueryResultList<Volume> volumes);
}

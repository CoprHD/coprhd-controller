/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * list of searched related resource respresentions
 */
public abstract class ResRepList<T extends RelatedResourceRep> extends QueryResultList<T> {
    private static final Logger _log = LoggerFactory.getLogger(ResRepList.class);

    public ResRepList() {
    }
}


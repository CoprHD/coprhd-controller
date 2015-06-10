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


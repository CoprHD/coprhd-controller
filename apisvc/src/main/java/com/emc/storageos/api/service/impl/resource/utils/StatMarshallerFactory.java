/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

public final class StatMarshallerFactory {
    private StatMarshallerFactory() {
        throw new AssertionError();
    }

    public static StatMarshaller getMarshaller(final MediaType type) {
        if (MediaType.APPLICATION_JSON_TYPE.equals(type)) {
            return new JSONStatMarshaller();
        } else if (MediaType.APPLICATION_XML_TYPE.equals(type)) {
            return new XMLStatMarshaller();
        } else {
            throw APIException.badRequests.unableToCreateMarshallerForMediaType(type.toString());
        }
    }
}

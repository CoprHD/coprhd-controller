/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;

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

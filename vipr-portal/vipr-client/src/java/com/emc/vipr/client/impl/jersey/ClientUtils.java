/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import javax.ws.rs.core.UriBuilder;
import java.util.List;

public class ClientUtils {
    /**
     * Utility method that adds a query param to the builder. Only
     * adds the query param if the value is not null.
     * 
     * @param builder The builder reference to add the query param
     * @param name The name of the query param
     * @param value The value of the query param
     */
    public static void addQueryParam(UriBuilder builder, String name, Object value) {
        if (value != null) {
            if (value instanceof List) {
                for (Object item : (List) value) {
                    builder.queryParam(name, item);
                }
            }
            else {
                builder.queryParam(name, value);
            }
        }
    }
}

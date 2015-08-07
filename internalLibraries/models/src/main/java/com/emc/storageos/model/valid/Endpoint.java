/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.valid;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to indicate that a field represents an communication endpoint that
 * can be used to export volumes or file systems. This annotation is mostly used
 * to validate the use input.
 * 
 * @author elalih
 * 
 */
@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Endpoint {
    EndpointType type() default EndpointType.ANY;

    public enum EndpointType {
        SAN("WWN, IQN or EUI port"), // any FC port: WWN, IQN or EUI
        WWN("WWN port"),
        IQN("IQN port"),
        EUI("EUI port"),
        ISCSI("IQN or EUI port"),
        IPV4("IPv4 address"),
        IPV6("IPv6 address"),
        IP("IP address"), // any IP port: IPV6 or IPV4
        HOSTNAME("host name"), // any host name short or fully qualified
        HOST("IP address or host name"), // any host address IPV4, IPV6 or hostname
        ANY("Network address"); // any valid end point: WWN, IQN, EUI, IPV4 or IPV6

        private EndpointType(String description) {
            this.description = description;
        }

        private String description;

        /**
         * A description of the endpoint.
         * 
         * @return
         */
        public String getDescription() {
            return description;
        }

    }
}

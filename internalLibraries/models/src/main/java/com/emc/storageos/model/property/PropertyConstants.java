/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.property;

public class PropertyConstants {
    public static final String IPADDR                          = "ipaddr";
    public static final String IPV6ADDR                        = "ipv6addr";
    public static final String STRING                          = "string";
    public static final String UINT64                          = "uint64";
    public static final String UINT32                          = "uint32";
    public static final String UINT16                          = "uint16";
    public static final String UINT8                           = "uint8";
    public static final String BOOLEAN                         = "boolean";
    public static final String PERCENT                         = "percent";
    public static final String URL                             = "url";
    public static final String EMAIL                           = "email";
    public static final String EMAILLIST                       = "emaillist";
    public static final String LICENSE                         = "license";
    public static final String HOSTNAME                        = "hostname";
    public static final String IPLIST                          = "iplist";
    public static final String ENCRYPTEDSTRING                 = "encryptedstring";
    // text is for multi-line, whereas string is for single line 
    public static final String TEXT                            = "text";
    public static final String ENCRYPTEDTEXT                   = "encryptedtext";

    // node/cluster property key
    public static final String NODE_ID_KEY                    = "node_id";
    public static final String NODE_COUNT_KEY                 = "node_count";

    // network related property key
    public static final String IPV4_ADDR_KEY                  = "network_%s_ipaddr";
    public static final String IPV4_GATEWAY_KEY              = "network_gateway";
    public static final String IPV4_NETMASK_KEY              = "network_netmask";
    public static final String IPV4_VIP_KEY                   = "network_vip";
    public static final String IPV4_ADDR_DEFAULT             = "0.0.0.0";

    public static final String IPV6_ADDR_KEY                 = "network_%s_ipaddr6";
    public static final String IPV6_GATEWAY_KEY              = "network_gateway6";
    public static final String IPV6_PREFIX_KEY               = "network_prefix_length";
    public static final String IPV6_VIP_KEY                  = "network_vip6";
    public static final String IPV6_ADDR_DEFAULT            = "::0";

    // property key/value pair delimiter
    public static final String DELIMITER                     = "=";
}

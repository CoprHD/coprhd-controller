/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
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
    public static final String STRICTHOSTNAME                  = "stricthostname";
    public static final String IPLIST                          = "iplist";
    public static final String ENCRYPTEDSTRING                 = "encryptedstring";
    // text is for multi-line, whereas string is for single line 
    public static final String TEXT                            = "text";
    public static final String ENCRYPTEDTEXT                   = "encryptedtext";
    
    // type added for Syslog Forwarder feature
    public static final String IPPORTLIST                          = "ipportlist";

    // node related property key
    public static final String NODE_ID_KEY = "node_id";
    public static final String NODE_COUNT_KEY = "node_count";

    // network related property key
    public static final String IPV4_ADDR_KEY = "network_%s_ipaddr";
    public static final String IPV4_GATEWAY_KEY = "network_gateway";
    public static final String IPV4_NETMASK_KEY = "network_netmask";
    public static final String IPV4_VIP_KEY = "network_vip";
    public static final String IPV4_ADDR_DEFAULT = "0.0.0.0";
    public static final String NETMASK_DEFAULT = "255.255.255.0";

    public static final String IPV6_ADDR_KEY = "network_%s_ipaddr6";
    public static final String IPV6_GATEWAY_KEY = "network_gateway6";
    public static final String IPV6_PREFIX_KEY = "network_prefix_length";
    public static final String IPV6_VIP_KEY = "network_vip6";
    public static final String IPV6_ADDR_DEFAULT = "::0";
    public static final String IPV6_PREFIX_LEN_DEFAULT = "64";

    // deployment related property keys and value sets.
    public static final String CONFIG_KEY_SCENARIO = "scenario";
    public static final String INIT_MODE = "init";
    public static final String INSTALL_MODE = "install";
    public static final String CONFIG_MODE = "config";
    public static final String REDEPLOY_MODE = "redeploy";
    public static final String PROPERTY_KEY_ALIVE_NODE = "alive_node_%s";

    // hardware related property keys
    public static final String PROPERTY_KEY_DISK = "disk";
    public static final String PROPERTY_KEY_DISK_CAPACITY = "disk_capacity";
    public static final String PROPERTY_KEY_NETIF = "network_interface";
    public static final String PROPERTY_KEY_CPU_CORE = "cpu_core";
    public static final String PROPERTY_KEY_MEMORY_SIZE = "memory_size";

    // internal hardware property key for hardware probing
    public static final String NODE_PROBE_KEY_DISK_MET_MIN_REQ = "met_min_req";
    public static final String NODE_PROBE_KEY_DISK_CAPACITY = "capacity";
    public static final String NODE_PROBE_KEY_DISK_HAS_VIPR_PARTITION = "has_vipr_partition";

    // minimum hardware requirements for hardware property
    public static final int MIN_REQ_CPU_CORE = 1;
    public static final int MIN_REQ_MEM_SIZE = 4032000; // ~4GB (hypervisor reserves little memory from configured memory)
    public static final int MIN_REQ_DISK_SIZE = 100;	 // TODO: for native installed env, the min disk is 122GB

    // hardware default values
    public static final String DISK_CAPACITY_UNIT_DEFAULT = "G";
    public static final String DATA_DISK_DEFAULT = "/dev/sdc";
    public static final String NETIF_DEFAULT = "eth0";

    // property key/value pair delimiter
    public static final String DELIMITER = "=";
}

/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.eventmanager;

/**
 * Event list
 */
public class EventList {
    /* NFS */
    public static final int SW_NFS_IDENTITY_QUERY_FAILED                   = 400140001;
    public static final int SW_SIQ_COALESCE_POLICY                         = 499940001;
    public static final int SW_AVSCAN_COALESCE                             = 499960001;

    /* SmartQuota events */
    public static final int QUOTA_THRESHOLD_VIOLATION                      = 500010001;
    public static final int QUOTA_NOTIFY_FAILED                            = 500010002;
    public static final int QUOTA_CONFIG_ERROR                             = 500010003;
    public static final int QUOTA_INTERNAL_ERROR                           = 500010004;
    public static final int QUOTA_REPORTGEN_ERROR                          = 500010005;

    /* Snapshot events */
    public static final int SNAP_CREATE_FAILED                             = 600010001;
    public static final int SNAP_DELETE_FAILED                             = 600010002;
    public static final int SNAP_LOCK_REMOVE_FAILED                        = 600010003;
    public static final int SNAP_SCHUDLE_CONFIG_FAILED                     = 600010004;
    public static final int SNAP_RESERVE_FULL                              = 600010005;

    /* Windows networking events */
    public static final int WINNET_TIME_SKEW                               = 700010001;
    public static final int WINNET_TIME_CONNECTIVITY_LOST                  = 700010003;
    public static final int WINNET_UPGRADE_ALERT                           = 700010004;
    public static final int WINNET_IDMAP_UIDMAP_FULL                       = 700020001;
    public static final int WINNET_IDMAP_GIDMAP_FULL                       = 700020002;
    public static final int WINNET_IDMAP_RULES_PARSE_FAIL                  = 700020003;
    public static final int WINNET_AUTH_ACCOUNT_MISSING                    = 700030001;
    public static final int WINNET_AUTH_DOMAIN_UNREACH                     = 700030002;
    public static final int WINNET_AUTH_PROV_INIT_FAIL                     = 700030003;
    public static final int WINNET_AUTH_SERV_UNAVAIL                       = 700030004;
    public static final int WINNET_AUTH_AD_SPN_MISSING                     = 700030005;
    public static final int WINNET_AUTH_AD_MACHACCT_INVALID                = 700030006;
    public static final int WINNET_AUTH_LDAP_SERVERS_UNREACH               = 700040001;
    public static final int WINNET_AUTH_NIS_SERVERS_UNREACH                = 700050001;

    public static final int WINNET_LWIO_PARAMETER_INVALID                  = 700100001;

    public static final int WINNET_COMPONENT_COALESCE                      = 799910001;
    public static final int WINNET_IDMAP_COALESCE                          = 799920001;
    public static final int WINNET_AUTH_COALESCE                           = 799930001;

    /* Filesystem events */
    public static final int FILESYS_ALLOC_ERROR                            = 800010002;
    public static final int FILESYS_IDI_ERROR                              = 800010003;
    public static final int FILESYS_VERIFY_ERROR                           = 800010004;
    public static final int FILESYS_DSR_FAILURE                            = 800010005;
    public static final int FILESYS_FDUSAGE                                = 800010006;
    public static final int FILESYS_RBM_CHECKSUM                           = 800010007;

    public static final int FILESYSTEM_COALESCE                            = 899990001;

    /* Hardware events */
    public static final int HW_SYSTEM_CLOCK_FAIL                           = 900010001;
    public static final int HW_SYSTEM_BATTERY_FAIL                         = 900010002;
    public static final int HW_SYSTEM_NVRAM_ERROR                          = 900010003;
    public static final int HW_SYSTEM_OPEN_CHASSIS                         = 900010004;
    public static final int HW_SYSTEM_DMI_LOG_ENTRY                        = 900010005;
    public static final int HW_SYSTEM_DMI_READ_FAIL                        = 900010006;
    public static final int HW_SYSTEM_ECC_POLICY_ERR                       = 900010007;
    public static final int HW_SYSTEM_I2C_BUS_FAIL                         = 900010008;
    public static final int	HW_SYSTEM_WRONG_DRIVE_RATIO			           = 900010009;

    public static final int[] event_ids = new int[]{
            SW_NFS_IDENTITY_QUERY_FAILED,
            SW_SIQ_COALESCE_POLICY,
            SW_AVSCAN_COALESCE,
            QUOTA_THRESHOLD_VIOLATION,
            QUOTA_NOTIFY_FAILED,
            QUOTA_CONFIG_ERROR,
            QUOTA_INTERNAL_ERROR,
            QUOTA_REPORTGEN_ERROR,
            SNAP_CREATE_FAILED,
            SNAP_DELETE_FAILED,
            SNAP_LOCK_REMOVE_FAILED,
            SNAP_SCHUDLE_CONFIG_FAILED,
            SNAP_RESERVE_FULL,
            WINNET_TIME_SKEW,
            WINNET_TIME_CONNECTIVITY_LOST,
            WINNET_UPGRADE_ALERT,
            WINNET_IDMAP_UIDMAP_FULL,
            WINNET_IDMAP_GIDMAP_FULL,
            WINNET_IDMAP_RULES_PARSE_FAIL,
            WINNET_AUTH_ACCOUNT_MISSING,
            WINNET_AUTH_DOMAIN_UNREACH,
            WINNET_AUTH_PROV_INIT_FAIL,
            WINNET_AUTH_SERV_UNAVAIL,
            WINNET_AUTH_AD_SPN_MISSING,
            WINNET_AUTH_AD_MACHACCT_INVALID,
            WINNET_AUTH_LDAP_SERVERS_UNREACH,
            WINNET_AUTH_NIS_SERVERS_UNREACH,
            WINNET_LWIO_PARAMETER_INVALID,
            WINNET_COMPONENT_COALESCE,
            WINNET_IDMAP_COALESCE,
            WINNET_AUTH_COALESCE,
            FILESYS_ALLOC_ERROR,
            FILESYS_IDI_ERROR,
            FILESYS_VERIFY_ERROR,
            FILESYS_DSR_FAILURE,
            FILESYS_FDUSAGE,
            FILESYS_RBM_CHECKSUM,
            FILESYSTEM_COALESCE,
            HW_SYSTEM_CLOCK_FAIL,
            HW_SYSTEM_BATTERY_FAIL,
            HW_SYSTEM_NVRAM_ERROR,
            HW_SYSTEM_OPEN_CHASSIS,
            HW_SYSTEM_DMI_LOG_ENTRY,
            HW_SYSTEM_DMI_READ_FAIL,
            HW_SYSTEM_ECC_POLICY_ERR,
            HW_SYSTEM_I2C_BUS_FAIL,
            HW_SYSTEM_WRONG_DRIVE_RATIO
    };
}

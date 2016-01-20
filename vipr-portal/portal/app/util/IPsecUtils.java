/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import com.emc.storageos.model.ipsec.IPsecStatus;

import static util.BourneUtil.getViprClient;

public class IPsecUtils {
    public static String rotateIPsecKey() {
        return getViprClient().ipsec().rotateIpsecKey();
    }

    public static IPsecStatus getIPsecStatus() {
        return getViprClient().ipsec().checkStatus();
    }
}

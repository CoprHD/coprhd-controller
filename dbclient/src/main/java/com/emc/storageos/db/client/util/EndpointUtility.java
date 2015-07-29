/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import com.emc.storageos.model.valid.Endpoint;

/**
 * Utility class for endpoint validation.
 * 
 * @author elalih
 * 
 */
public class EndpointUtility {

    // Regular Expression to match a host name.
    private static final String HOST_NAME_PATTERN =
            "^(?![0-9]+$)(?:([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";

    /**
     * Creates a new list of endpoints modified to upper or lower case based on
     * the endpoint type. Endpoints of type WWN are changed to be upper case. All
     * other types are changed to lower case.
     * 
     * @param list
     *            the list containing the endpoints
     * @return Returns a new list of endpoints with the correct case for their
     *         type.
     */
    public static List<String> changeCase(List<String> list) {
        if (list != null) {
            List<String> result = new ArrayList<String>();
            for (String element : list) {
                result.add(changeCase(element));
            }
            return result;
        }
        return null;
    }

    /**
     * Returns the endpoint with its case changed as appropriate fr its type.
     * Endpoints of type WWN are changed to be upper case. All other types are
     * changed to lower case.
     * 
     * @param element
     *            the endpoint
     * @return the endpoint with the correct case for its type
     */
    public static String changeCase(String element) {
        if (element == null) {
            return null;
        }
        if (WWNUtility.isValidWWN(element)) {
            return element.toUpperCase();
        } else {
            return element.toLowerCase();
        }
    }

    public static boolean isValidIpV4Address(String ipAddress) {
        return InetAddressUtils.isIPv4Address(ipAddress);
    }

    public static boolean isValidIpV6Address(String ipAddress) {
        return InetAddressUtils.isIPv6Address(ipAddress);
    }

    public static boolean isValidHostName(String hostName) {
        return hostName.matches(HOST_NAME_PATTERN);
    }

    public static boolean isValidEndpoint(String element, Endpoint.EndpointType type) {
        if (element == null) {
            return false;
        }
        if (Endpoint.EndpointType.WWN.equals(type)) {
            return WWNUtility.isValidWWN(element);
        } else if (Endpoint.EndpointType.IQN.equals(type)) {
            return iSCSIUtility.isValidIQNPortName(element);
        } else if (Endpoint.EndpointType.EUI.equals(type)) {
            return iSCSIUtility.isValidEUIPortName(element);
        } else if (Endpoint.EndpointType.ISCSI.equals(type)) {
            return iSCSIUtility.isValidEUIPortName(element) ||
                    iSCSIUtility.isValidIQNPortName(element);
        } else if (Endpoint.EndpointType.SAN.equals(type)) {
            return iSCSIUtility.isValidEUIPortName(element) ||
                    iSCSIUtility.isValidIQNPortName(element) ||
                    WWNUtility.isValidWWN(element) ||
                    SDCUtility.isValidSDC(element);
        } else if (Endpoint.EndpointType.IP.equals(type)) {
            return isValidIpV4Address(element) ||
                    isValidIpV6Address(element);
        } else if (Endpoint.EndpointType.IPV4.equals(type)) {
            return isValidIpV4Address(element);
        } else if (Endpoint.EndpointType.IPV6.equals(type)) {
            return isValidIpV6Address(element);
        } else if (Endpoint.EndpointType.HOSTNAME.equals(type)) {
            return isValidHostName(element);
        } else if (Endpoint.EndpointType.HOST.equals(type)) {
            return isValidIpV4Address(element) ||
                    isValidIpV6Address(element) ||
                    isValidHostName(element);
        } else if (Endpoint.EndpointType.ANY.equals(type) || type == null) {
            return isValidEndpoint(element, Endpoint.EndpointType.HOST) ||
                    isValidEndpoint(element, Endpoint.EndpointType.SAN);
        }
        return false;
    }
}

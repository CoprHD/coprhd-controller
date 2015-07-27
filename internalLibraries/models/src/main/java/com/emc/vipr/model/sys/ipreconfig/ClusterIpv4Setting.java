/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.vipr.model.sys.ipreconfig;

import com.emc.storageos.model.property.PropertyConstants;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import com.google.common.net.InetAddresses;

/**
 * Cluster Ipv4 Information
 */
public class ClusterIpv4Setting implements Serializable{
    private String network_vip;
    private List<String> network_addrs;
    private String network_netmask;
    private String network_gateway;

    public ClusterIpv4Setting() {}

    @XmlElement (name = "network_vip")
    public String getNetworkVip() {
        return network_vip;
    }

    public void setNetworkVip(String network_vip) {
        this.network_vip = network_vip;
    }

    @XmlElementWrapper(name = "network_addrs")
    @XmlElement (name = "network_addr")
    public List<String> getNetworkAddrs() {
        return network_addrs;
    }

    public void setNetworkAddrs(List<String> network_addrs) {
        this.network_addrs = network_addrs;
    }

    @XmlElement (name = "network_netmask")
    public String getNetworkNetmask() {
        return network_netmask;
    }

    public void setNetworkNetmask(String network_netmask) {
        this.network_netmask = network_netmask;
    }
    @XmlElement (name = "network_gateway")
    public String getNetworkGateway() {
        return network_gateway;
    }

    public void setNetworkGateway(String network_gateway) {
        this.network_gateway = network_gateway;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        ClusterIpv4Setting tgtobj = (ClusterIpv4Setting)obj;
        if (!network_vip.equals(tgtobj.getNetworkVip()))
            return false;
        if (!network_gateway.equals(tgtobj.getNetworkGateway()))
            return false;
        if (!network_netmask.equals(tgtobj.getNetworkNetmask()))
            return false;
        if(!network_addrs.equals(tgtobj.getNetworkAddrs()))
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer propStrBuf = new StringBuffer();
        propStrBuf.append(PropertyConstants.IPV4_VIP_KEY).append(PropertyConstants.DELIMITER).append(network_vip).append("\n");
        propStrBuf.append(PropertyConstants.IPV4_NETMASK_KEY).append(PropertyConstants.DELIMITER).append(network_netmask).append("\n");
        propStrBuf.append(PropertyConstants.IPV4_GATEWAY_KEY).append(PropertyConstants.DELIMITER).append(network_gateway).append("\n");
        int i=0;
        for (String network_addr: network_addrs) {
            String network_ipaddr_key = String.format(PropertyConstants.IPV4_ADDR_KEY, ++i);
            propStrBuf.append(network_ipaddr_key).append(PropertyConstants.DELIMITER).append(network_addr).append("\n");
        }
        return propStrBuf.toString();
    }

    /* Load from key/value property map
     */
    public void loadFromPropertyMap(Map<String, String> propMap)
    {
        String node_count = propMap.get(PropertyConstants.NODE_COUNT_KEY);

        setNetworkVip(propMap.get(PropertyConstants.IPV4_VIP_KEY));
        setNetworkGateway(propMap.get(PropertyConstants.IPV4_GATEWAY_KEY));
        setNetworkNetmask(propMap.get(PropertyConstants.IPV4_NETMASK_KEY));
        network_addrs = new LinkedList<String>();
        for (int i=1; i<= Integer.valueOf(node_count); i++) {
            String network_ipaddr_key = String.format(PropertyConstants.IPV4_ADDR_KEY, i);
            network_addrs.add(propMap.get(network_ipaddr_key));
        }
    }

    public boolean isDefault() {
        if (network_vip.equals(PropertyConstants.IPV4_ADDR_DEFAULT))
            return true;
        if (network_gateway.equals(PropertyConstants.IPV4_ADDR_DEFAULT))
            return true;
        for (String network_addr: network_addrs) {
            if (network_addr.equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate Ipv4 address format
     * @return
     */
    public boolean isValid() {
        if (!validateIpv4Addr(network_vip))
            return false;
        if (!validateIpv4Addr(network_gateway))
            return false;
        if (!validateIpv4Addr(network_netmask))
            return false;
        for (String network_addr: network_addrs) {
            if (!validateIpv4Addr(network_addr))
                return false;
        }

        return true;
    }

    private boolean validateIpv4Addr(String value) {
        try {
            return InetAddresses.isInetAddress(value) && InetAddresses.forString(value) instanceof Inet4Address;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate Ipv4 address duplication
     * @return
     */
    public boolean isDuplicated() {
        if(isDefault())
            return false;

        List<String> list = new ArrayList<String>();
        list.add(network_gateway);
        list.add(network_netmask);
        list.add(network_vip);
        for (String network_addr: network_addrs) {
            list.add(network_addr);
        }

        Set<String> set = new HashSet<String>(list);
        if(set.size() < list.size()){
            return true;
        }
        return false;
    }

    /*
     * Help method to check if the ipv4 addresses are on the same sub net
     * @param ipv4 addrList the list of addresses to be checked
     * @param mask the netmask
     * @return true if on the same net false otherwise
     */
    public boolean isOnSameNetworkIPv4() {
        List<String> list = new ArrayList<String>();
        list.add(network_gateway);
        list.add(network_vip);
        for (String network_addr: network_addrs) {
            list.add(network_addr);
        }

        try {
            byte[] m = InetAddress.getByName(network_netmask).getAddress();
            for (int i = 0; i < m.length; i++) {
                List<Integer> values = new ArrayList<Integer>();
                for (String ip : list) {
                    byte[] a = InetAddress.getByName(ip).getAddress();
                    values.add(a[i] & m[i]);
                }

                // check if all values are same (on the same subnet)
                if (values.size() == 0) {
                    return true;
                }
                int checkValue = values.get(0);
                for (int value : values) {
                    if (value != checkValue) {
                        return false;
                    }
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return true;
    }

    /*
     * Help method to check all the values in the list are the same
     * @param values the values to be checked
     * @return true if all the same value, false otherwise
     */
    private boolean areAllEqual(List<Integer> values) {
        if (values.size() == 0) {
            return true;
        }
        int checkValue = values.get(0);
        for (int value : values) {
            if (value != checkValue) {
                return false;
            }
        }
        return true;
    }

}


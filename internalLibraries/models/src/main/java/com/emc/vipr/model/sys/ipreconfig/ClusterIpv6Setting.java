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
import com.emc.storageos.model.valid.Range;
import com.google.common.net.InetAddresses;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import com.google.common.net.InetAddresses;

/**
 * Cluster Ipv6 Information
 */
public class ClusterIpv6Setting implements Serializable{
    private String network_vip6;
    private List<String> network_addrs;
    private Integer network_prefix_length;
    private String network_gateway6;

    public ClusterIpv6Setting() {}

    @XmlElement (name = "network_vip6")
    public String getNetworkVip6() {
        return network_vip6;
    }

    public void setNetworkVip6(String network_vip6) {
        this.network_vip6 = network_vip6;
    }

    @XmlElementWrapper(name = "network_addrs")
    @XmlElement (name = "network_addr")
    public List<String> getNetworkAddrs() {
        return network_addrs;
    }

    public void setNetworkAddrs(List<String> network_addrs) {
        this.network_addrs = network_addrs;
    }

    @XmlElement (name = "network_prefix_length")
    @Range(min=1,max=128)
    public Integer getNetworkPrefixLength() {
        return network_prefix_length;
    }

    public void setNetworkPrefixLength(Integer network_prefix_length) {
        this.network_prefix_length = network_prefix_length;
    }
    @XmlElement (name = "network_gateway6")
    public String getNetworkGateway6() {
        return network_gateway6;
    }

    public void setNetworkGateway6(String network_gateway6) {
        this.network_gateway6 = network_gateway6;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        ClusterIpv6Setting tgtobj = (ClusterIpv6Setting)obj;
        if (!network_vip6.equals(tgtobj.getNetworkVip6()))
            return false;
        if (!network_gateway6.equals(tgtobj.getNetworkGateway6()))
            return false;
        if (!network_prefix_length.equals(tgtobj.getNetworkPrefixLength()))
            return false;
        if(!network_addrs.equals(tgtobj.getNetworkAddrs()))
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer propStrBuf = new StringBuffer();
        propStrBuf.append(PropertyConstants.IPV6_VIP_KEY).append(PropertyConstants.DELIMITER).append(network_vip6).append("\n");
        propStrBuf.append(PropertyConstants.IPV6_PREFIX_KEY).append(PropertyConstants.DELIMITER).append(network_prefix_length).append("\n");
        propStrBuf.append(PropertyConstants.IPV6_GATEWAY_KEY).append(PropertyConstants.DELIMITER).append(network_gateway6).append("\n");
        int i = 0;
        for (String network_addr: network_addrs) {
            String network_ipaddr_key = String.format(PropertyConstants.IPV6_ADDR_KEY, ++i);
            propStrBuf.append(network_ipaddr_key).append(PropertyConstants.DELIMITER).append(network_addr).append("\n");
        }
        return propStrBuf.toString();
    }

    /* Load from key/value property map
     */
    public void loadFromPropertyMap(Map<String, String> propMap)
    {
        String node_count = propMap.get(PropertyConstants.NODE_COUNT_KEY);

        setNetworkVip6(propMap.get(PropertyConstants.IPV6_VIP_KEY));
        setNetworkGateway6(propMap.get(PropertyConstants.IPV6_GATEWAY_KEY));
        setNetworkPrefixLength(Integer.valueOf(propMap.get(PropertyConstants.IPV6_PREFIX_KEY)));
        network_addrs = new LinkedList<String>();
        for (int i=1; i<= Integer.valueOf(node_count); i++) {
            String network_ipaddr6_key = String.format(PropertyConstants.IPV6_ADDR_KEY, i);
            network_addrs.add(propMap.get(network_ipaddr6_key));
        }
    }
    public boolean isDefault() {
        if (network_vip6.equals(PropertyConstants.IPV6_ADDR_DEFAULT))
            return true;
        if (network_gateway6.equals(PropertyConstants.IPV6_ADDR_DEFAULT))
            return true;
        for (String network_addr: network_addrs) {
            if (network_addr.equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate Ipv6 address format
     * @return
     */
    public boolean isValid() {
        if (!validateIpv6Addr(network_vip6))
            return false;
        if (!validateIpv6Addr(network_gateway6))
            return false;
        for (String network_addr: network_addrs) {
            if (!validateIpv6Addr(network_addr))
                return false;
        }

        return true;
    }

    private boolean validateIpv6Addr(String value) {
        try {
            return InetAddresses.isInetAddress(value) && InetAddresses.forString(value) instanceof Inet6Address;
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * Validate Ipv6 address duplication
     * @return
     */
    public boolean isDuplicated() {
        if(isDefault())
            return false;

        List<String> list = new ArrayList<String>();
        list.add(network_gateway6);
        list.add(network_vip6);
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
     * Help method to check if the ipv6 addresses are on the same sub net
     * @return true if on the same net false otherwise
     */
    public boolean isOnSameNetworkIPv6() {
        List<String> list = new ArrayList<String>();
        list.add(network_gateway6);
        list.add(network_vip6);
        for (String network_addr: network_addrs) {
            list.add(network_addr);
        }

        try {
            byte[] ipv6Netmask = new byte[16];
            int prefixL = Integer.valueOf(network_prefix_length);
            int numberOfFullByte =  prefixL/8;
            for (int i =0; i < numberOfFullByte; i++) {
                ipv6Netmask[i] = (byte)255; // Those bytes are all 1s
            }
            int shiftAmount = 8 - prefixL%8;
            ipv6Netmask[numberOfFullByte] = (byte)(255 & (~0 << shiftAmount));
            for (int i = numberOfFullByte+1; i < 16; i++) {
                ipv6Netmask[i] = (byte)0; // Those bytes are all 0s
            }
            for (int i = 0; i < ipv6Netmask.length; i++) {
                List<Integer> values = new ArrayList<Integer>();
                for (String ip : list) {
                    byte[] a = InetAddress.getByName(ip).getAddress();
                    values.add(a[i] & ipv6Netmask[i]);
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

}


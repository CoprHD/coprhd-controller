/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.emc.storageos.model.property.PropertyConstants;
import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.services.util.MulticastUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.net.InetAddresses;

public class InstallerUtil {
    private static final Logger log = LoggerFactory.getLogger(InstallerUtil.class);

    public static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        try {
            out.writeObject(o);
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }

    public static Object deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return obj;
    }

    /**
     * List cluster configurations scanned over network.
     * 
     * @return configurations in a set
     */
    /**
     * List cluster configurations scanned over network.
     * 
     * @param netIf the network interface used to scan
     * @param serviceName the service name (e.g. release version) matched with the return set
     * @param scenario the boot mode matched with the return set
     * @return the set of cluster matched with the filters
     */
    public static Set<Configuration> scanClusters(String netIf, String serviceName, String scenario) {
        log.info("Scanning over network {} for available configuration", netIf);
        Set<Configuration> availableClusters = new HashSet<Configuration>();
        try {
            MulticastUtil multicastUtil = MulticastUtil.create(netIf);
            Map<String, Map<String, String>> clusters = multicastUtil.list(serviceName);
            for (Map<String, String> cluster : clusters.values()) {
                // get only the scenario asked for
                if (cluster != null && !cluster.isEmpty()
                        && cluster.get(PropertyConstants.CONFIG_KEY_SCENARIO).equals(scenario)) {
                    Configuration config = new Configuration();
                    config.setConfigMap(cluster);
                    availableClusters.add(config);
                    log.info("Scan found cluster: {}/{}", config.getNetworkVip(), config.toString());
                }
            }
            multicastUtil.close();
        } catch (IOException e) {
            log.error(
                    "Scan available cluster Configuration failed with exception: %s",
                    e.getMessage());
        }

        log.info("Scan found {} cluster(s) for '{}'", availableClusters.size(), scenario);
        return availableClusters;
    }

    /**
     * Check if the network addresses are on the same sub network.
     * 
     * @param map1 the ipv4 addresses
     * @param map2 the ipv6 addresses
     * @return the list of names not on the sub network
     */
    public static List<String> checkAddressesOnSameSubNetwork(LinkedHashMap<String, String> map1,
            LinkedHashMap<String, String> map2) {
        List<String> errorMsg = new ArrayList<String>();
        // check for ipv4
        if (!map1.isEmpty()) {
            Map<String, String> ipv4 = new HashMap<String, String>();
            ipv4.putAll(map1);
            String mask = ipv4.get(InstallerConstants.DISPLAY_LABEL_IPV4_NETMASK);
            // exclude netmask from the list when check on same network, add it back after
            ipv4.remove(InstallerConstants.DISPLAY_LABEL_IPV4_NETMASK);
            if (!isOnSameNetworkIPv4(ipv4.values(), mask)) {
                errorMsg.add("IPv4 addresses are not in the same subnet");
                log.warn("IPv4 addresses are not in the same subnet");
            }
        }
        // check for ipv6
        if (!map2.isEmpty()) {
            Map<String, String> ipv6 = new HashMap<String, String>();
            ipv6.putAll(map2);
            String prefixLength = ipv6.get(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX);
            // exclude prefix length from the list when check on same network
            ipv6.remove(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX);
            if (!isOnSameNetworkIPv6(ipv6.values(), prefixLength)) {
                errorMsg.add("IPv6 addresses are not in the same subnet");
                log.warn("IPv6 addresses are not in the same subnet");
            }
        }

        return errorMsg;
    }

    /**
     * Validate Ipv4 and Ipv6 address format
     * 
     * @param map1 the ipv4 addresses
     * @param map2 the ipv6 addresses
     * @return the invalid list for both ipv4 and ipv6
     */
    public static List<String> checkInvalidAddress(LinkedHashMap<String, String> map1,
            LinkedHashMap<String, String> map2) {
        List<String> invalidIpv4 = validateIPv4Addresses(map1);
        List<String> invalidIpv6 = validateIPv6Address(map2);
        List<String> invalid = new ArrayList<String>();
        invalid.addAll(invalidIpv4);
        invalid.addAll(invalidIpv6);
        if (!invalid.isEmpty()) {
            log.warn("There are invalid addresses entered: {}/{}", invalid.size(), invalid);
        }
        return invalid;
    }

    /**
     * Check if network is configured (not default).
     * 
     * @param ipv4 map of IPv4 addresses
     * @param ipv6 map of IPv6 addresses
     * @return true if both IPv4 and IPv6 addresses are not configured, false otherwise.
     */
    public static boolean ipAddressNotConfigured(LinkedHashMap<String, String> ipv4,
            LinkedHashMap<String, String> ipv6) {
        boolean ipv4IsDefault = isIpv4Default(ipv4);
        boolean ipv6IsDefault = isIpv6Default(ipv6);
        if (ipv4IsDefault && ipv6IsDefault) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isIpv6Default(
            LinkedHashMap<String, String> ipv6) {
        for (Entry<String, String> entry : ipv6.entrySet()) {
            if (entry.getValue().equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpv4Default(
            LinkedHashMap<String, String> ipv4) {
        for (Entry<String, String> entry : ipv4.entrySet()) {
            if (entry.getValue().equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there are duplicate addresses entered
     * 
     * @param map1 the ipv4 addresses
     * @param map2 the ipv6 addresses
     * @return the duplicate list for both ipv4 and ipv6
     */
    public static List<String> checkDuplicateAddress(LinkedHashMap<String, String> map1,
            LinkedHashMap<String, String> map2) {
        List<String> dup1 = checkDuplicate(map1);
        List<String> dup2 = checkDuplicate(map2);
        List<String> dup = new ArrayList<String>();
        dup.addAll(dup1);
        dup.addAll(dup2);
        if (!dup.isEmpty()) {
            log.warn("There are duplicate addresses entered: {}/{}", dup.size(), dup);
        }
        return dup;
    }

    /*
     * Remove the ": " from the display label in the map key
     * 
     * @param map the map to process
     * 
     * @return the new map
     */
    private static Map<String, String> mapHelper(LinkedHashMap<String, String> map) {
        Map<String, String> ret = new HashMap<String, String>();
        for (String key : map.keySet()) {
            ret.put(key.replace(": ", ""), map.get(key));
        }
        return ret;
    }

    /*
     * Help method to check if the ipv4 addresses are on the same sub net
     * 
     * @param ipv4 addrList the list of addresses to be checked
     * 
     * @param mask the netmask
     * 
     * @return true if on the same net false otherwise
     */
    private static boolean isOnSameNetworkIPv4(Collection<String> addrList, String mask) {
        try {
            byte[] m = InetAddress.getByName(mask).getAddress();
            for (int i = 0; i < m.length; i++) {
                List<Integer> values = new ArrayList<Integer>();
                for (String ip : addrList) {
                    byte[] a = InetAddress.getByName(ip).getAddress();
                    values.add(a[i] & m[i]);
                }
                // check if all values are same (on the same subnet)
                if (!areAllEqual(values)) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Got an UnknownHostException: {}", e.getMessage());
            return false;
        }
        return true;
    }

    /*
     * Help method to check if the ipv6 addresses are on the same sub net
     * 
     * @param ipv6 addrList the list of addresses to be checked
     * 
     * @param the prefix length of the subnet
     * 
     * @return true if on the same net false otherwise
     */
    private static boolean isOnSameNetworkIPv6(Collection<String> addrList, String prefixLength) {
        try {
            byte[] ipv6Netmask = new byte[16];
            int prefixL = Integer.valueOf(prefixLength);
            int numberOfFullByte = prefixL / 8;
            for (int i = 0; i < numberOfFullByte; i++) {
                ipv6Netmask[i] = (byte) 255; // Those bytes are all 1s
            }
            int shiftAmount = 8 - prefixL % 8;
            ipv6Netmask[numberOfFullByte] = (byte) (255 & (~0 << shiftAmount));
            for (int i = numberOfFullByte + 1; i < 16; i++) {
                ipv6Netmask[i] = (byte) 0; // Those bytes are all 0s
            }
            for (int i = 0; i < ipv6Netmask.length; i++) {
                List<Integer> values = new ArrayList<Integer>();
                for (String ip : addrList) {
                    byte[] a = InetAddress.getByName(ip).getAddress();
                    values.add(a[i] & ipv6Netmask[i]);
                }
                // check if all values are same (on the same subnet)
                if (!areAllEqual(values)) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Got an UnknownHostException: {}", e.getMessage());
            return false;
        }
        return true;
    }

    /*
     * Help method to check all the values in the list are the same
     * 
     * @param values the values to be checked
     * 
     * @return true if all the same value, false otherwise
     */
    private static boolean areAllEqual(List<Integer> values) {
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

    /*
     * Help method to check if duplicate addresses in the map
     * 
     * @param map1
     * 
     * @return
     */
    private static List<String> checkDuplicate(LinkedHashMap<String, String> inputMap) {
        Map<String, String> map = mapHelper(inputMap);
        List<String> dup = new ArrayList<String>();
        Multimap<String, String> multiMap =
                Multimaps.invertFrom(Multimaps.forMap(map),
                        HashMultimap.<String, String> create());
        for (Entry<String, Collection<String>> entry : multiMap.asMap().entrySet()) {
            Collection<String> values = entry.getValue();
            List<String> sortValues = new ArrayList<String>(values);
            Collections.sort(sortValues);
            if (values.size() > 1) {
                if (!entry.getKey().equals(PropertyConstants.IPV4_ADDR_DEFAULT)
                        && !entry.getKey().equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
                    // more than one entry has the same value, duplicated value found
                    dup.add(StringUtils.join(sortValues.toArray(), ", "));
                }
            }
        }
        log.debug("Found {} duplicates in map {}", dup.size(), inputMap);
        return dup;
    }

    /*
     * Validate ipv4 addresses for valid IPv4 format
     * 
     * @param map ipv4 address map
     * 
     * @return a list of invalid names
     */
    private static List<String> validateIPv4Addresses(LinkedHashMap<String, String> map) {
        List<String> statusList = new ArrayList<String>();
        for (Entry<String, String> entry : map.entrySet()) {
            boolean isValid = validateIpv4Addr(entry.getValue());
            if (!isValid) {
                statusList.add(entry.getKey().replace(": ", ""));
            }
        }
        return statusList;
    }

    /*
     * Validate ipv6 addresses for valid IPv6 format
     * 
     * @param map ipv6 address map
     * 
     * @return a list of invalid names
     */
    private static List<String> validateIPv6Address(LinkedHashMap<String, String> map) {
        List<String> statusList = new ArrayList<String>();

        Map<String, String> mapToCheck = new HashMap<String, String>();
        mapToCheck.putAll(map);

        // validate Ipv6 prefix length
        String prefixLength = mapToCheck.get(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX);
        if (prefixLength == null || prefixLength.isEmpty()
                || Integer.valueOf(prefixLength) < 0 || Integer.valueOf(prefixLength) > 128) {
            statusList.add(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX.replace(": ", ""));
        }

        // exclude Ipv6 prefix length for validation
        mapToCheck.remove(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX);
        for (Entry<String, String> entry : mapToCheck.entrySet()) {
            boolean isValid = validateIpv6Addr(entry.getValue());
            if (!isValid) {
                statusList.add(entry.getKey().replace(": ", ""));
            }
        }
        return statusList;
    }

    /*
     * Validate value is an IpAddr.
     * 
     * @param value
     * 
     * @return
     */
    private static boolean validateIpAddr(String value) {
        return InetAddresses.isInetAddress(value);
    }

    /*
     * Validate value is an IPv4 Address
     * 
     * @param value
     * 
     * @return
     */
    private static boolean validateIpv4Addr(String value) {
        try {
            return validateIpAddr(value) && InetAddresses.forString(value) instanceof Inet4Address;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Validate value is an IPv6 Address
     * 
     * @param value
     * 
     * @return
     */
    private static boolean validateIpv6Addr(String value) {
        try {
            return validateIpAddr(value) && InetAddresses.forString(value) instanceof Inet6Address;
        } catch (Exception e) {
            return false;
        }
    }

    // pad with " " to the right to the given length (n)
    /**
     * Pad white space to the right of the string to the given length
     * 
     * @param s the string
     * @param n the length of the padded string
     * @return the padded string
     */
    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    /*
     * IPv4 Network addresses conversion table between display label and property key
     */
    private static final Map<String, String> ipv4NameConversionTable;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
        for (int i = 1; i <= 5; i++) {
            aMap.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV4_NODE_ADDR, i),
                    String.format(PropertyConstants.IPV4_ADDR_KEY, i));
        }
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_VIP, PropertyConstants.IPV4_VIP_KEY);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_NETMASK, PropertyConstants.IPV4_NETMASK_KEY);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV4_GATEWAY, PropertyConstants.IPV4_GATEWAY_KEY);
        ipv4NameConversionTable = Collections.unmodifiableMap(aMap);
    }

    /*
     * Convert the IPv4 network display name/label to property key
     * 
     * @param inMap the input map
     * 
     * @return the map with property key
     */
    public static LinkedHashMap<String, String> convertIpv4DisplayNameToPropertyKey(LinkedHashMap<String, String> inMap) {
        LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
        for (Entry<String, String> entry : inMap.entrySet()) {
            String key = entry.getKey();
            if (ipv4NameConversionTable.containsKey(key)) {
                outMap.put(ipv4NameConversionTable.get(key), entry.getValue());
            } else {
                outMap.put(key, entry.getValue());
            }
        }
        return outMap;
    }

    /**
     * Get IPv4 network addresses with display labels
     * 
     * @return the map with display labels
     */
    public static LinkedHashMap<String, String> getIpv4DisplayMap(LinkedHashMap<String, String> networkIpv4Config) {
        Map<String, String> conversionTable = ovfKeyToDisplayTag(ipv4NameConversionTable);
        LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
        for (Entry<String, String> entry : networkIpv4Config.entrySet()) {
            String key = entry.getKey();
            if (conversionTable.containsKey(key)) {
                outMap.put(conversionTable.get(key), entry.getValue());
            } else {
                outMap.put(key, entry.getValue());
            }
        }
        return outMap;
    }

    /*
     * IPv6 Network addresses conversion table between display label and property key
     */
    private static final Map<String, String> ipv6NameConversionTable;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
        for (int i = 1; i <= 5; i++) {
            aMap.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV6_NODE_ADDR, i),
                    String.format(PropertyConstants.IPV6_ADDR_KEY, i));
        }
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_VIP, PropertyConstants.IPV6_VIP_KEY);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX, PropertyConstants.IPV6_PREFIX_KEY);
        aMap.put(InstallerConstants.DISPLAY_LABEL_IPV6_GATEWAY, PropertyConstants.IPV6_GATEWAY_KEY);
        ipv6NameConversionTable = Collections.unmodifiableMap(aMap);
    }

    /*
     * Convert the IPv6 network display name/label to property key
     * 
     * @param inMap the input map
     * 
     * @return the map with property key
     */
    public static LinkedHashMap<String, String> convertIpv6DisplayNameToPropertyKey(LinkedHashMap<String, String> inMap) {
        LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
        for (Entry<String, String> entry : inMap.entrySet()) {
            String key = entry.getKey();
            if (ipv6NameConversionTable.containsKey(key)) {
                outMap.put(ipv6NameConversionTable.get(key), entry.getValue());
            } else {
                outMap.put(key, entry.getValue());
            }
        }
        return outMap;
    }

    /**
     * Get IPv6 network addresses with display labels
     * 
     * @return the map with display labels
     */
    public static LinkedHashMap<String, String> getIpv6DisplayMap(LinkedHashMap<String, String> networkIpv6Config) {
        Map<String, String> conversionTable = ovfKeyToDisplayTag(ipv6NameConversionTable);
        LinkedHashMap<String, String> outMap = new LinkedHashMap<String, String>();
        for (Entry<String, String> entry : networkIpv6Config.entrySet()) {
            String key = entry.getKey();
            if (conversionTable.containsKey(key)) {
                outMap.put(conversionTable.get(key), entry.getValue());
            } else {
                outMap.put(key, entry.getValue());
            }
        }
        return outMap;
    }

    // help method to reverse map key value pairs
    private static Map<String, String> ovfKeyToDisplayTag(Map<String, String> map) {
        Map<String, String> rev = new HashMap<String, String>();
        for (Entry<String, String> entry : map.entrySet()) {
            rev.put(entry.getValue(), entry.getKey());
        }
        return rev;
    }
}

/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import static com.emc.storageos.coordinator.client.model.Constants.LINE_DELIMITER;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.services.util.Exec;

/**
 * this class holds all the values needed to create a key and certificate pair
 */
public class KeyCertificateAlgorithmValuesHolder {

    private static Logger log = LoggerFactory
            .getLogger(KeyCertificateAlgorithmValuesHolder.class);

    private static final String V1_SSL_CERT_PROPERTY_NAME = "system_ssl_cert_pem";

    private static final String NETWORK_VIP_PROPERTY_NAME = "network_vip";
    private static final String NETWORK_VIP6_PROPERTY_NAME = "network_vip6";
    public static final String NETWORK_NODE_IP_ADDRESS_PROPERTY_NAME_FORMAT =
            "network_%d_ipaddr";
    public static final String NETWORK_NODE_IP_ADDRESS6_PROPERTY_NAME_FORMAT =
            "network_%d_ipaddr6";
    private static final String NETWORK_STANDALONE_IP_ADDRESS_PROPERTY_NAME =
            "network_standalone_ipaddr";
    private static final int MAX_NUMBER_OF_NODES = 5;
    public static final String UNDEFINED_IPV4_ADDRESS = "0.0.0.0";
    public static final String UNDEFINED_IPV6_ADDRESS = "::0";

    public static final String DEFAULT_KEY_ALGORITHM = "RSA";
    public static final int FIPS_MINIMAL_KEY_SIZE = 2048;
    private static final int DEFAULT_CERTIFICATE_VALIDITY_IN_DAYS = 3650;

    // 14 days ago
    private final static int notBeforeOffset = -14;

    // injected via Spring
    private static String signingAlgorithm;
    private static String securedRandomAlgorithm;

    private int keySize = FIPS_MINIMAL_KEY_SIZE;
    private int certificateValidityInDays = DEFAULT_CERTIFICATE_VALIDITY_IN_DAYS;
    private Set<InetAddress> addresses;
    private String certificateCommonName;
    private final CoordinatorClient coordinator;

    /**
     * This constructor is used only when no need to get IP related properties.
     */
    public KeyCertificateAlgorithmValuesHolder() {
        this.coordinator = null;
    }

    public KeyCertificateAlgorithmValuesHolder(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
        loadIPsAndNames();
    }

    /**
     * loads all the cluster's IPs and the DNS names
     */
    public void loadIPsAndNames() {
        PropertyInfo props = coordinator.getPropertyInfo();
        if (props == null) {
            throw SecurityException.retryables.keystoreUnavailable();
        }
        addresses = new HashSet<InetAddress>();
        String ipAddress = props.getProperty(NETWORK_STANDALONE_IP_ADDRESS_PROPERTY_NAME);
        InetAddress inet;
        if (StringUtils.isNotBlank(ipAddress)
                && !ipAddress.equals(UNDEFINED_IPV4_ADDRESS)) {
            ipAddress = ipAddress.trim();
            inet = getInetAddress(ipAddress);
            addresses.add(inet);
            log.debug("Standalone ip address is:" + ipAddress
                    + ". Canonical host name is " + inet.getCanonicalHostName());
        }

        for (int index = 1; index <= MAX_NUMBER_OF_NODES; index++) {
            ipAddress =
                    props.getProperty(String.format(
                            NETWORK_NODE_IP_ADDRESS_PROPERTY_NAME_FORMAT, index));
            if (StringUtils.isNotBlank(ipAddress)
                    && !ipAddress.equals(UNDEFINED_IPV4_ADDRESS)) {
                ipAddress = ipAddress.trim();
                inet = getInetAddress(ipAddress);
                addresses.add(inet);
                log.debug("Node #" + index + " IPv4 address is:" + ipAddress
                        + ". Canonical host name is " + inet.getCanonicalHostName());
            }

            ipAddress =
                    props.getProperty(String.format(
                            NETWORK_NODE_IP_ADDRESS6_PROPERTY_NAME_FORMAT, index));
            if (StringUtils.isNotBlank(ipAddress)
                    && !ipAddress.equals(UNDEFINED_IPV6_ADDRESS)) {
                ipAddress = ipAddress.trim();
                inet = getInetAddress(ipAddress);
                addresses.add(inet);
                log.debug("Node #" + index + " IPv6 address is:" + ipAddress
                        + ". Canonical host name is " + inet.getCanonicalHostName());
            }
        }

        InetAddress ipv6VipAddress = null;
        InetAddress ipv4VipAddress = null;
        ipAddress = props.getProperty(NETWORK_VIP6_PROPERTY_NAME);
        if (StringUtils.isNotBlank(ipAddress)
                && !ipAddress.trim().equals(UNDEFINED_IPV6_ADDRESS)) {
            ipAddress = ipAddress.trim();
            ipv6VipAddress = getInetAddress(ipAddress);
            log.debug("VIP IPv6 address is:" + ipAddress + ". Canonical host name is "
                    + ipv6VipAddress.getCanonicalHostName());
            addresses.add(ipv6VipAddress);
        }

        ipAddress = props.getProperty(NETWORK_VIP_PROPERTY_NAME);
        if (StringUtils.isNotBlank(ipAddress)
                && !ipAddress.trim().equals(UNDEFINED_IPV4_ADDRESS)) {
            ipAddress = ipAddress.trim();
            ipv4VipAddress = getInetAddress(ipAddress);
            log.debug("VIP IPv4 address is:" + ipAddress + ". Canonical host name is "
                    + ipv4VipAddress.getCanonicalHostName());
            addresses.add(ipv4VipAddress);
        } else if (ipv6VipAddress == null) {
            ipv4VipAddress = addresses.iterator().next();
        }
        certificateCommonName = decideCommonName(ipv4VipAddress, ipv6VipAddress);
    }

    /**
     * @param ipv4VipAddress
     * @param ipv6VipAddress
     * @return
     */
    private String decideCommonName(InetAddress ipv4VipAddress, InetAddress ipv6VipAddress) {
        if (ipv4VipAddress == null) { // no ipv4 specified
            return ipv6VipAddress.getCanonicalHostName();
        }
        if (!ipv4VipAddress.getCanonicalHostName() // got dns name for VIP's ipv4 address
                .equals(ipv4VipAddress.getHostAddress())) {
            return ipv4VipAddress.getCanonicalHostName();
        }
        if (ipv6VipAddress != null // got the dns name for the VIP ipv6 address
                && !ipv6VipAddress.getCanonicalHostName().equalsIgnoreCase(
                        ipv6VipAddress.getHostAddress())) {
            // this will return the dns name
            return ipv6VipAddress.getCanonicalHostName();
        }
        // default to the ipv4 address
        return ipv4VipAddress.getHostAddress();
    }

    public String getV1Cert() {
        log.info("trying to get v1 ovf properties");
        final String[] cmd = { "/etc/systool", "--getoverrides" };
        final long _SYSTOOL_TIMEOUT = 120000; // 2 min
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Systool command failed. Result exit value: "
                    + result.getExitValue());
            return null;
        }

        String[] propsStrings = result.getStdOutput().split(LINE_DELIMITER);
        PropertyInfoExt props = new PropertyInfoExt(propsStrings);
        String keyAndCertPem = props.getProperty(V1_SSL_CERT_PROPERTY_NAME);
        if (keyAndCertPem == null) {
            log.info("Deprecated property " + V1_SSL_CERT_PROPERTY_NAME + " not configured in previous version.");
        }
        return keyAndCertPem;
    }

    /**
     * adds the host name of the specified ip
     * 
     * @param ipAddress
     */
    private static InetAddress getInetAddress(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            log.info("Could not find host dns name for " + ipAddress + ". Details: "
                    + e.getMessage());
            return null;
        }
    }

    /**
     * @return the signingAlgorithm
     */
    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    /**
     * @param algo
     *            the signingAlgorithm to set
     */
    public synchronized static void setSigningAlgorithm(String algo) {
        signingAlgorithm = algo;
        log.info("Signing Algorithm is {}", algo);
    }

    /**
     * @return the keySize
     */
    public int getKeySize() {
        return keySize;
    }

    /**
     * @param keySize
     *            the keySize to set
     */
    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    /**
     * @return the certificateValidityInDays
     */
    public int getCertificateValidityInDays() {
        return certificateValidityInDays;
    }

    /**
     * @param certificateValidityInDays
     *            the certificateValidityInDays to set
     */
    public void setCertificateValidityInDays(int certificateValidityInDays) {
        this.certificateValidityInDays = certificateValidityInDays;
    }

    /**
     * @return the ipsAndNames
     */
    public Set<InetAddress> getAddresses() {
        return addresses;
    }

    /**
     * @return the certificateCommonName
     */
    public String getCertificateCommonName() {
        return certificateCommonName;
    }

    /**
     * @param certificateCommonName
     *            the certificateCommonName to set
     */
    public void setCertificateCommonName(String certificateCommonName) {
        this.certificateCommonName = certificateCommonName;
    }

    public int getNotBeforeOffset() {
        return notBeforeOffset;
    }
}

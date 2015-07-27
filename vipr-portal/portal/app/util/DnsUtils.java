/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import play.Logger;

import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.net.InetAddresses;

/**
 * Utility class to deal with dynamic dns host resolution.
 * 
 * @author logelj
 *
 */
public final class DnsUtils {
    
    private static final String CONTEXT_ATTRIBUTE_A = "A";
    
    private static final String NAMING_FACTORY_KEY = "java.naming.factory.initial";
    
    private static final String NAMING_FACTORY_VALUE = "com.sun.jndi.dns.DnsContextFactory";
    
    private static final String NAMING_PROVIDER_URL_KEY = "java.naming.provider.url";
    
    private static final String DNS_URL_FORMAT = "dns://%s";

    /**
     * Resolves a host IP address given DNS servers and FQDN host
     * 
     * @param dnsServers
     *        set of DNS server IP addresses.
     * @param hostname
     *        the fully qualified hostname to lookup.
     * @return the ip address of the fully qualified host
     */
    public static final InetAddress getHostIpAddress(Set<String> dnsServers, String hostname) {
        if (dnsServers.isEmpty()) {
            throw new ViPRException(String.format("No nameservers provided to lookup IP address of '%s'", hostname));
        }
        ViPRException error = null;
        for (String dnsServer : dnsServers) {
            try {
                Attribute attr = getAttribute(dnsServer, hostname, CONTEXT_ATTRIBUTE_A);
                return InetAddress.getByName((String) attr.get());
            }
            catch (Exception e) {
                Logger.error(e, "Could not lookup IP address of '%s' using nameserver '%s'", hostname, dnsServer);
                error = new ViPRException(e.getMessage(), e);
            }
        }
        if (error != null) {
            throw error;
        }
        else {
            throw new ViPRException(String.format("Could not lookup IP address of '%s' using nameservers %s", hostname,
                    dnsServers));
        }
    }

    /**
     * Validates the hostname against a number of DNS servers.
     * 
     * @param dnsServers
     *        the collection of DNS servers.
     * @param hostname
     *        the hostname to validate.
     * @return true if the hostname is valid.
     */
    public static boolean validateHostname(Collection<String> dnsServers, String hostname) {
        for (String dnsServer : dnsServers) {
            if (validateHostname(dnsServer, hostname)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates the existence of a fully qualified host via a name server
     * 
     * @param dnsServer Domain Name Server ip address
     * @param hostname Fully qualified hostname
     * @return Flag indicating if the host is resolvable on the domain name server
     */
    public static boolean validateHostname(final String dnsServer, final String hostname) {
        try {
            getAttribute(dnsServer, hostname, CONTEXT_ATTRIBUTE_A);
        }
        catch (NamingException e) {
            Logger.error(e, e.getMessage());
            return false;
        }
        catch (UnknownHostException e) {
            Logger.error(e, e.getMessage());
            return false;
        }

        return true;
    }

    private static DirContext getContext(final String dnsServer) throws UnknownHostException, NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(NAMING_FACTORY_KEY, NAMING_FACTORY_VALUE);

        String urlString = String.format(DNS_URL_FORMAT, InetAddresses.toUriString(InetAddress.getByName(dnsServer)));
        env.put(NAMING_PROVIDER_URL_KEY, urlString);

        return new InitialDirContext(env);
    }

    private static Attribute getAttribute(String dnsServer, String hostname, String attrId)
            throws UnknownHostException, NamingException {
        Attributes attributes = getContext(dnsServer).getAttributes(hostname, new String[] { attrId });
        return attributes.get(attrId);
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.coordinator.client.service.impl;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import com.emc.storageos.coordinator.exceptions.NotConnectableException;

public class DualInetAddress {
	
	private static int[] parseInet4Address(String ip4String) {
		if (ip4String == null) {
			return null;
		}
		
		// Split into parts.
		final String[] parts = ip4String.split("\\.", 4 + 1);
		
		// Can't have more then 4 parts.
		if (parts.length > 4) {
			return null;
		}
		
		// Convert parts into octets.
		int[] octets = new int[4];
		for (int i = 0; i < parts.length; i++) {
			try {
				final int octet = Integer.parseInt(parts[i]);
				if (octet > 255 || octet < 0 || octet == 0 && parts[i].length() > 1) {
					return null;
				}
				octets[i] = octet;
			} catch (NumberFormatException e) {
				return null;
			}		
		}
		
		// Fill remaining octets with zeros.
		for (int i = parts.length; i < 4; i++) {
			octets[i] = 0;
		}
		
		return octets;
	}
	
	/**
	 * Normalize a string representing an IPv4 address to 
	 * the canonical representation with four decimal octets (d.d.d.d).
	 * 
	 * @param ip4String
	 * @return Normalized IPv4 string or null if the input string was invalid
	 */
	public static String normalizeInet4Address(String ip4String) {
		final int[] octets = parseInet4Address(ip4String);
		if (octets == null) {
			return null;
		}
		
		// Construct the canonical representation.
		StringBuilder sb = new StringBuilder();				
		for (int i = 0; i < 4; i++) {
			if (i > 0) {
				sb.append('.');
			}
			sb.append(octets[i]);
		}
		return sb.toString();
	}
	
	/**
	 * Normalize a string representing an IPv6 address to 
	 * the canonical representation with the longest sequence of zero
	 * hextets substituted with ::
	 * 
	 * @param ip6String
	 * @return Normalized IPv6 string or null if the input string was invalid
	 */
	public static String normalizeInet6Address(String ip6String) {
		if (ip6String == null) {
			return null;
		}
		
		// Split the input string into parts.
		String parts[] = ip6String.split(":", 8 + 2);

		// Special case for ffff:ff00:1.2.3.4
		if (parts.length > 1 && parts[parts.length - 1].length() > 0 && parts[parts.length - 1].indexOf('.') > 0) {
			final int[] octets = parseInet4Address(parts[parts.length - 1]);
			if (octets != null) {
				String[] parts2 = new String[parts.length + 1];
				for (int i = 0; i < parts.length - 1; i++) {
					parts2[i] = parts[i];
				}
				parts2[parts2.length - 2] = Integer.toHexString(octets[0] * 256 + octets[1]);
				parts2[parts2.length - 1] = Integer.toHexString(octets[2] * 256 + octets[3]);
				parts = parts2;
			}
		}

		// The address can have 2 to 8 colons.
		// Thus, we should have between 3 and 9 parts.
		if (parts.length < 3 || parts.length > 8 + 1) {
			return null;
		}
		
		// Check middle parts:
		// - Find index of an empty part (between ::)
		// - We must have only 0 or 1 empty parts
		// - Also, check that parts are four characters or less.
		int index = -1;
		for (int i = 1; i < parts.length - 1; i++) {
			if (parts[i].length() > 4) {
				return null;
			} else if (parts[i].length() == 0) {
				if (index != -1) {
					return null;
				}
				index = i;
			}
		}
		
		// Check first part 
		if (parts[0].length() > 4 || parts[0].length() == 0 && index != 1) {
			return null;
		}
		
		// Check last part
        if (parts[parts.length -1].length() > 4 || parts[parts.length -1].length() == 0 && index != parts.length - 2) {
        	return null;
		}
        
        // If there is no empty part we must have exactly 8 parts
		if (index == -1 && parts.length != 8) {
			return null;
		}
		
		// Convert parts to hextets.
		int hextets[] = new int[8];			
		for (int i = 0; i < parts.length; i++) {
			try {
				final int block = parts[i].length() == 0 ? 0 : Integer.parseInt(parts[i], 0x10);
				if (block > 0xffff || block < 0) {
					return null;
				}
				if (index == -1 || i < index) {
					hextets[i] = block;					
				} else {
					hextets[i + 8 - parts.length] = block;
				}
			} catch (NumberFormatException e) {
				return null;
			}		
		}
		
		// Fill skipped hextets with zeros
		if (index != -1) {
			for (int i = 0; i < 8 - parts.length; i++) {
				hextets[index + i] = 0;
			}
		}
		
		// Find the longest sequence of zero hextets.
		int longestIndex = -1;
		int longestLength = 0;
		for (int i = 0, k = -1, l = 0; i < 8; i++) {
			if (hextets[i] == 0) {
				if (k == -1) {
					k = i;
					l = 1;
				} else {
					l++;
				}
				if (l > longestLength) {
					longestIndex = k;
					longestLength = l;
				}
			} else {
				k = -1;
			}
		}

		// Construct the canonical representation.
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 8; i++) {
			if (longestIndex == -1 || i < longestIndex) {
				if (i > 0) {
					sb.append(':');
				}
				sb.append(Integer.toHexString(hextets[i]).toLowerCase());
			} else if (i == longestIndex) {
				sb.append(":");
				if (i + longestLength == 8) {
					sb.append(':');
				}
			} else if (longestIndex != -1 && i > longestIndex && i < longestIndex + longestLength) {
				continue;
			} else {
				sb.append(':');
				sb.append(Integer.toHexString(hextets[i]).toLowerCase());
			}
		}
		return sb.toString();
	}
	
	private static final String INVALID_INET4ADDRESS = normalizeInet4Address("0.0.0.0");
	private static final String INVALID_INET6ADDRESS = normalizeInet6Address("::0");
	
	private final String ip4;
	private final String ip6;
	
	private DualInetAddress() {
		this.ip4 = null;
		this.ip6 = null;
	}
	
	private DualInetAddress(String ip4, String ip6) {
        this.ip4 = (ip4 == null || ip4.equals(INVALID_INET4ADDRESS)) ? null : ip4;
        this.ip6 = (ip6 == null || ip6.equals(INVALID_INET6ADDRESS)) ? null : ip6;
	}
	
	public String getInet4() {
		return ip4;
	}
	
	public String getInet6() {
		return ip6;
	}
	
	public boolean hasInet4() {
		return getInet4() != null;
	}
	
	public boolean hasInet6() {
		return getInet6() != null;
	}
	
	public static DualInetAddress fromAddress(String ipString) throws UnknownHostException {
		if (ipString == null || ipString.isEmpty()) {
			return new DualInetAddress();
		}
		
		final String ip4 = normalizeInet4Address(ipString);
		if (ip4 != null) {
			return new DualInetAddress(ip4, null);
		}

		final String ip6 = normalizeInet6Address(ipString);
		if (ip6 != null) {
			return new DualInetAddress(null, ip6);
		}
	
		throw new UnknownHostException(ipString);
	}
	
	public static DualInetAddress fromAddresses(String ip4String, String ip6String) throws UnknownHostException {
		final String ip4 = normalizeInet4Address(ip4String);
		if (ip4String != null && !ip4String.isEmpty() && ip4 == null) {
			throw new UnknownHostException(ip4String);
		}
		
		final String ip6 = normalizeInet6Address(ip6String);
		if (ip6String != null && !ip6String.isEmpty() && ip6 == null) {
			throw new UnknownHostException(ip6String);
		}
		
		return new DualInetAddress(ip4, ip6);
	}
	
	private static final Pattern REGEX = Pattern.compile("^[0-9.]+$");
	
	public static DualInetAddress fromHostname(String hostname) throws UnknownHostException {
		try {
			return fromAddress(hostname);
		} catch (UnknownHostException e) {
			;
		}
		
		if (REGEX.matcher(hostname).matches()) {
			throw new UnknownHostException(hostname);
		}
			
		String ip4 = null;
		String ip6 = null;
		for (InetAddress addr : InetAddress.getAllByName(hostname)) {
			if (ip4 != null && ip6 != null) {
				break;
			} else if (ip4 == null && addr instanceof Inet4Address) {
				ip4 = normalizeInet4Address(addr.getHostAddress());
			} else if (ip6 == null && addr instanceof Inet6Address) {
				ip6 = normalizeInet6Address(addr.getHostAddress());
			}
		}
		return new DualInetAddress(ip4, ip6);
	}
	
	/**
     * A class representing connection peer points (client and server)
     */
    public static class ConnectableInetAddresses {
        private final String client;
        private final String server;

        public ConnectableInetAddresses(String c, String s) {
            client = c;
            server = s;
        }

        public String getClient() {
            return client;
        }

        public String getServer() {
            return server;
        }

    }

    /**
     * @return A pair of connectable client and server addresses.
     * - If both the client and server have IPv4 addresses, then IPv4 will be used.
     * - Otherwise, IPv6 addresses will be used.
     * 
     * @throws NotConnectableException if there is no common IP flavor between the client and the server.
     */
	public static ConnectableInetAddresses getConnectableAddresses(DualInetAddress client, DualInetAddress server) {
		if (client.hasInet4() && server.hasInet4()) {
			return new ConnectableInetAddresses(client.getInet4(), server.getInet4());
		}
		if (client.hasInet6() && server.hasInet6()) {
            return new ConnectableInetAddresses(client.getInet6(), server.getInet6());
        }
        throw CoordinatorException.fatals.notConnectableError("Unable to find connectable addresses for " + client + " and " + server);
	}
	
	/**
     * @return A connectable server address, assuming caller is the client.
     * 
     * @throws NotConnectableException if there is no common IP flavor between the client and the server.
     * 
     * @Refer getConnectableAddresses(DualInetAddress client, DualInetAddress server)
     */
    public String getConnectableAddress(DualInetAddress server) {
        return getConnectableAddresses(this, server).getServer();
    }


    /**
     * @return A connectable local address, assuming caller is the server.
     *
     * @throws NotConnectableException if there is no common IP flavor between the client and the server.
     *
     * @Refer getConnectableAddresses(DualInetAddress client, DualInetAddress server)
     */
    public String getConnectableLocalAddress(DualInetAddress client) {
        return getConnectableAddresses(client, this).getClient();
    }
    
    /**
     * A common address resolution method: 
     * - First, we try to treat client and server strings as a textual representation of InetAddress; 
     * - If this fails, then we query DNS, searching for both IPv4 and IPv6 addresses. 
     * - Ultimately, all names are represented as DualInetAddresses. Then,
     *   we use DualInetAddress.getConnectableAddresses() to find a matching pair.
     * 
     * @throws NotConnectableException
     */
    public static ConnectableInetAddresses getConnectableAddresses(String client, String server) throws UnknownHostException {
        return getConnectableAddresses(fromHostname(client), fromHostname(server));
    }

    /**
     * Return a connectable server address for the hostname provided, assuming caller is the client.
     * 
     * @throws UnknownHostException
     *             , NotConnectableException
     *
     * @Refer getConnectableAddresses(String client, String server)
     */
    public String getConnectableAddress(String server) throws UnknownHostException {
        return getConnectableAddress(fromHostname(server));
    }

    /**
     * Return a connectable server address for the hostname provided, assuming caller is the client.
     *
     * @throws UnknownHostException
     *             , NotConnectableException
     *
     * @Refer getConnectableAddresses(String client, String server)
     */
    public String getConnectableLocalAddress(String client) throws UnknownHostException {
        return getConnectableLocalAddress(fromHostname(client));
    }

	@Override
    public String toString() {
		if (ip4 != null && ip6 != null) {
			return ip4 + "," + ip6;
		} else if (ip4 != null) {
			return ip4;
		} else if (ip6 != null) {
			return ip6;
		} else {
			return "(null)";
		}
	}

    @Override
    public boolean equals(Object o) {
    	if (o == null) {
    		return false;
    	}
    	if (o == this) {
    		return true;
    	}
    	if (!(o instanceof DualInetAddress)) {
    		return false;
    	}
    	
        DualInetAddress d = (DualInetAddress) o;
        return toString().equals(d.toString());
    }

    @Override
    public int hashCode() {
        return 31 * ((ip4 != null) ? ip4.hashCode() : 0) + ((ip6 != null) ? ip6.hashCode() : 0);
    }
}

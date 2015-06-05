/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import net.sf.oval.Validator;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import net.sf.oval.context.OValContext;
import net.sf.oval.exception.OValException;

import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;

public class HostNameOrIpAddressCheck extends AbstractAnnotationCheck<HostNameOrIpAddress> {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE_KEY = "validation.hostNameOrIpAddress";
        
    private static final Pattern NAME_PART_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]*");

    @Override
    public void configure(HostNameOrIpAddress hostNameOrIpAddress) {
        setMessage(hostNameOrIpAddress.message());
    }

    @Override
    public boolean isSatisfied(Object validatedObject, Object value, OValContext context, Validator validator)
            throws OValException {
        if ((value == null) || (value.toString().length() == 0)) {
            return true;
        }
        String str = value.toString();
        return isValidHostNameOrIp(str);
    }

    public static boolean isValidHostNameOrIp(String value) {
        if (isValidIp(value)) {
            return true;
        }
        if (isValidHostName(value)) {
            return true;
        }
        return false;
    }

    public static boolean isValidIp(String value) {
        return validateInetAddress(value);
    }
    
    public static boolean validateInetAddress(final String address){
        
        try {
            InetAddress.getByName(address);
        }
        catch (UnknownHostException e) {
            return false;
        }
        
        return true;
        
    }
    
    public static boolean isInetAddressFormat(String address){
        return InetAddressUtils.isIPv4Address(address) || InetAddressUtils.isIPv6Address(address);
    }

    public static boolean isValidHostName(String value) {
        try {
            String[] parts = value.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                if (!NAME_PART_PATTERN.matcher(parts[i]).matches()) {
                    return false;
                }
            }
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
    
    public static boolean hasValidPort(String endpoint) {
        try {
            if (endpoint != null && !endpoint.isEmpty()) {
                if (endpoint.contains("]:")) {
                    String port = StringUtils.substringAfter(endpoint, "]:");
                    if (!StringUtils.isNumeric(port)) {
                        return false;
                    }
                } else if (endpoint.contains(":") && 
                        StringUtils.countMatches(endpoint, ":") == 1) {
                    String port = StringUtils.substringAfter(endpoint, ":");
                    if (!StringUtils.isNumeric(port)) {
                        return false;
                    }
                }
            }
            
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
    
    public static String trimPortFromEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            if (endpoint.contains("]:")) {
                endpoint = StringUtils.substringBefore(endpoint,"]:");
                endpoint = StringUtils.substringAfter(endpoint,"[");
            } else if (endpoint.contains(":") && StringUtils.countMatches(endpoint, ":") == 1) {
                endpoint = StringUtils.substringBefore(endpoint,":");
            }
        }
        return endpoint;
    }
}


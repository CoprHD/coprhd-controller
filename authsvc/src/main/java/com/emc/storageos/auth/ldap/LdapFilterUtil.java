/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.filter.HardcodedFilter;

/**
 * 
 * Utility class to substitute placeholders by values in ldap filters
 */
public class LdapFilterUtil {
    /**
     * Generate encoded filter to search for persons
     * @param rawFilter
     * @param username
     * @return encoded filter
     */
    public static String getPersonFilterWithValues(final String rawFilter, final String username) {
        String filter = rawFilter;
        String[] usernameParts = username.split("@");
        filter = filter.replace("%u", username);
        filter = filter.replace("%U", usernameParts[0]);
        if( usernameParts.length > 1) {
            filter = filter.replaceAll("%d", usernameParts[1]);
        }
        // Add parentheses around the filter string so that we can
        // AND it
        if( !filter.startsWith("(")) {
            filter = "("+filter+")";
        }
        Filter hardCodedFilter = new HardcodedFilter(filter);
        // Why is this not needed in the auth handler
        Filter personFilter = new EqualsFilter("objectClass", "person");
        AndFilter andFilter = new AndFilter();
        andFilter.and(hardCodedFilter);        
        andFilter.and(personFilter);
        return andFilter.encode();
    }
    
    /**
     * Generates an encoded filter for attribute query
     * @param attributeName to find
     * @return encoded filter
     */
    public static String getAttributeFilterWithValues(final String attributeName) {
        Filter hardCodedFilter = new HardcodedFilter("(lDAPDisplayName=" + attributeName + ")");
        Filter attributeFilter = new EqualsFilter("objectCategory", "attributeSchema");
        AndFilter andFilter = new AndFilter();
        andFilter.and(hardCodedFilter);        
        andFilter.and(attributeFilter);
        return andFilter.encode();
    }
    
}

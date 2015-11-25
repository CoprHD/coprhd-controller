/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;

import com.emc.storageos.db.client.model.NamedURI;

/**
 * This utility class is responsible to unset a column property value.
 * 
 * The current DB model does not support changing the column value to null reference.
 * after setting some value to it. Hence we should set an explicit null value.
 */
public class NullColumnValueGetter {
    /**
     * This method is used to unset a string data type column property.
     * 
     * For example, When an endpoint is removed from the network, if
     * the endpoint is a StoragePort, the value for the network for the
     * port needs to be reset to null. However, the database client does not
     * set null values, and it retains the previous invalid value. So instead,
     * we set to the value to this constant to indicate the port is not in a
     * network.
     * 
     * @return a null string.
     */
    private static final String NULL_STR = "null";
    
    /**
     * Constant return null URI.
     */
    private static final URI NULL_URI = URI.create(NULL_STR);
    
    /**
     * Constant return null URI.
     */
    private static final NamedURI NULL_NAMED_URI = new NamedURI(NULL_URI, NULL_STR);

    /**
     * Return a null String value.
     * 
     * @return
     */
    public static String getNullStr() {
        return NULL_STR;
    }

    /**
     * Return a null URI value.
     * 
     * @return
     */
    public static URI getNullURI() {
        return NULL_URI;
    }
    
    /**
     * Return a null Named URI value.
     * 
     * @return
     */
    public static NamedURI getNullNamedURI() {
        return NULL_NAMED_URI;
    }

    /**
     * Checks to see if a String value is null.
     * 
     * @param s the value to be checked
     * @return true if the String is not null
     */
    public static boolean isNotNullValue(String s) {
        return ((s != null) && !s.isEmpty() && !s.equals(NullColumnValueGetter.getNullStr()));
    }

    /**
     * Checks to see if a String value is null.
     * 
     * @param s the value to be checked
     * @return true if the String is null
     */
    public static boolean isNullValue(String s) {
        return !isNotNullValue(s);
    }

    /**
     * Checks if a uri value is either null or equals to a {@link #NULL_URI}.
     * 
     * @param uri the uri to be checked
     * @return true if the uri is either null or equals to a {@link #NULL_URI}.
     */
    public static boolean isNullURI(URI uri) {
        return ((uri == null) || uri.equals(NULL_URI) || (uri.toString() == null) || (uri.toString().length() == 0));
    }

    public static boolean isNullNamedURI(NamedURI uri) {
        return ((uri == null) || isNullURI(uri.getURI()) || !isNotNullValue(uri.getName()));
    }

    public static URI normalize(URI uri) {
        return isNullURI(uri) ? null : uri;
    }

    public static NamedURI normalize(NamedURI uri) {
        return isNullNamedURI(uri) ? null : uri;
    }

    /**
     * Returns the string value of the passed in object or "null" otherwise.
     * 
     * @param obj Supported as String, URI, and NamedURI for now
     * @return String value or "null" as default
     */
    public static String getStringValue(Object obj) {
        String value = NullColumnValueGetter.NULL_STR;

        if (obj != null) {
            if (obj instanceof String) {
                value = (String) obj;
            }
            else if (obj instanceof URI) {
                URI uri = (URI) obj;
                if (!isNullURI(uri)) {
                    value = uri.toString();
                }
            }
            else if (obj instanceof NamedURI) {
                NamedURI namedURI = (NamedURI) obj;
                if (!isNullNamedURI(namedURI)) {
                    value = namedURI.getName();
                }
            }
        }

        return value;
    }
}

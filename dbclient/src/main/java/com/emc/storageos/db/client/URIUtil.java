/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.VdcUtil;

public class URIUtil {
    private static final Logger log = LoggerFactory.getLogger(URIUtil.class);
    private static final int UUID_PARTS_COUNT = 3;
    private static final int VDC_PARTS_COUNT = 4;

    private static final String[] MODEL_PACKAGES = new String[] { "com.emc.storageos.db.client.model",
            "com.emc.storageos.db.client.model.UnManagedDiscoveredObjects",
            "com.emc.storageos.db.client.model.uimodels" };

    /** Pattern for finding the 'type' from an ID. */
    private static final Pattern TYPE_PATTERN = Pattern.compile("urn\\:storageos\\:([^\\:]+)");

    /** Null URI to use to unassign certain values. */
    public static final URI NULL_URI = uri("null");

    /**
     * creates a URI for an object of type clazz
     *
     * @param clazz
     * @return
     */
    public static URI createId(Class<? extends DataObject> clazz) {
        return newId(clazz, getLocation(clazz));
    }

    public static URI createId(Class<? extends DataObject> clazz, String uuid) {
        return newId(clazz, uuid, getLocation(clazz));
    }

    /**
     * Creates an ID for a particular class with a well known identifier. Used for creating identifiers for
     * internal objects that don't change throughout the lifetime of the system.
     */
    public static URI createInternalID(Class<? extends DataObject> clazz, String identifier) {
        return newId(clazz, identifier, "");
    }

    /**
     * creates a URI for an VirtualDataCenter object. no vdc short id required
     *
     * @return
     */
    public static URI createVirtualDataCenterId(String vdcId) {
        return newId(VirtualDataCenter.class, vdcId);
    }

    private static URI newId(Class<? extends DataObject> clazz, String vdcId) {
        return newId(clazz, UUID.randomUUID().toString(), vdcId);
    }

    private static URI newId(Class<? extends DataObject> clazz, String uuid, String vdcId) {
        return URI.create(String.format("urn:storageos:%1$s:%2$s:%3$s",
                clazz.getSimpleName(), uuid, vdcId));
    }

    public static Class getModelClass(URI id) {
        String typeName = getTypeName(id);

        for (String modelPackage : MODEL_PACKAGES) {
            try {
                return Thread.currentThread().getContextClassLoader().loadClass(modelPackage + "." + typeName);
            } catch (ClassNotFoundException ignore) {
                log.debug("load class failed: ", ignore);
            }
        }

        throw new RuntimeException("Unable to find Model Class for " + id);
    }

    @SuppressWarnings("rawtypes")
    public static boolean isType(URI uri, Class clazz) {
        String simpleName = clazz.getSimpleName();
        return uri.toString().startsWith("urn:storageos:" + simpleName);
    }

    /**
     * @return true if the string represents a valid URI and the URI is a valid ViPR URL else false
     */
    public static boolean isValid(String uri) {
        try {
            return isValid(new URI(uri));
        } catch (URISyntaxException e) {
            log.error("URISyntaxException : uri passed is {} ", uri, e);
            return false;
        }
    }

    /**
     * @return true if the uri represents a valid ViPR URI regardless of class
     */
    public static boolean isValid(final URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getSchemeSpecificPart() == null
                || !uri.getScheme().equals("urn")
                || !uri.getSchemeSpecificPart().startsWith("storageos")) {
            return false;
        }

        /*
         * (?i) - ignores case of letters in following parentheses
         * urn:storageos: - matches exact stting, this is consistent for all ViPR uris
         * [A-Z]+: - This will match the class with any number of letters followed by a colon
         * [A-F0-9]{8} - used for matchin UUID, This segment is 8 hex characters.
         * The full UUID pattern is all Hex characters seperated by '-' in specific quantities
         * :([A-Z0-9]+)? - any amount of letters or numbers preceded by a colon
         *
         * Only legal characters (letters(any case), numbers, '-', ':')
         */

        return uri.toString().matches("(?i)(urn:storageos:" +
                "[A-Z]+:" +
                "[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}" +
                ":([A-Z0-9]+)?)");
    }

    public static List<URI> toURIList(Collection<String> stringList) {
        List<URI> uriList = null;
        if (stringList != null && !stringList.isEmpty()) {
            uriList = new ArrayList<>();
            for (String uriStr : stringList) {
                uriList.add(URI.create(uriStr));
            }
        }
        return uriList;
    }

    public static String getTypeName(URI id) {
        return getTypeName(id.toString());
    }

    public static String getTypeName(String id) {
        Matcher m = TYPE_PATTERN.matcher(id);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String[] splitURI(URI id) {
        if (!isValid(id)) {
            return null;
        }
        return TYPE_PATTERN.split(id.toString())[1].split(":");
    }

    /**
     * Get the UUID embedded in the URI string, or null if none
     *
     * @param id a DataObject URI string
     * @return the uuid
     */
    public static String parseUUIDFromURI(URI id) {
        return (id != null) ? parsePartFromURI(id.toString(), UUID_PARTS_COUNT) : null;
    }

    /**
     * Get the VDC Id embedded in the URI string, or null if none
     *
     * @param id a DataObject URI string
     * @return the vdc id
     */
    public static String parseVdcIdFromURI(URI id) {
        return (id != null) ? parseVdcIdFromURI(id.toString()) : null;
    }

    /**
     * Get the VDC Id embedded in the URI, or null if none
     *
     * @param id a DataObject URI
     * @return the vdc id
     */
    public static String parseVdcIdFromURI(String id) {
        return parsePartFromURI(id, VDC_PARTS_COUNT);
    }

    /**
     * Get a part of Id embedded in the URI under given index, or null if none
     *
     * @param id a DataObject URI
     * @param index an index of the part
     * @return the vdc id
     */
    private static String parsePartFromURI(String id, int index) {
        String vdcId = null;

        if (id != null) {
            String[] segments = StringUtils.split(id, ':');
            if ((segments.length > index) && StringUtils.isNotBlank(segments[index])) {
                vdcId = segments[index];
            }
        }
        return vdcId;
    }

    public static <T extends DataObject> String getLocation(Class<T> clazz) {
        return KeyspaceUtil.isLocal(clazz) ? VdcUtil.getLocalShortVdcId() : KeyspaceUtil.GLOBAL;
    }

    /**
     * Gets the value of the URI as a string, returns null if the URI is null.
     *
     * @param value
     *            the URI.
     * @return the string value of the URI.
     */
    public static String asString(URI value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Converts a collection of URIs to a list of strings, null safe.
     *
     * @param values
     *            the URI values.
     * @return the String(s)
     */
    public static List<String> asStrings(Collection<URI> values) {
        List<String> results = new ArrayList<>();
        if (values != null) {
            for (URI value : values) {
                if (value != null) {
                    results.add(value.toString());
                }
            }
        }
        return results;
    }

    /**
     * Converts a string to a URI, null safe.
     *
     * @param value
     *            the string value.
     * @return the URI.
     */
    public static URI uri(String value) {
        return (value != null && value.length() > 0) ? URI.create(value) : null;
    }

    /**
     * Converts a collection of strings to a list of URIs, null safe.
     *
     * @param values
     *            the string values.
     * @return the URIs.
     */
    public static List<URI> uris(Collection<String> values) {
        List<URI> results = new ArrayList<URI>();
        if (values != null) {
            for (String value : values) {
                URI uri = uri(value);
                if (uri != null) {
                    results.add(uri);
                }
            }
        }
        return results;
    }

    /**
     * Converts an array of strings to a list of URIs, null safe.
     *
     * @param values
     *            the string values.
     * @return the URIs.
     */
    public static List<URI> uris(String... values) {
        if (values != null) {
            return uris(Arrays.asList(values));
        }
        else {
            return new ArrayList<URI>();
        }
    }

    /**
     * Determines if the IDs are equal (and non-null).
     *
     * @param first
     *            the first ID.
     * @param second
     *            the second ID.
     * @return true if and only if the IDs are non-null and equal.
     */
    public static boolean identical(URI first, URI second) {
        if ((first != null) && (second != null)) {
            return first.equals(second);
        }
        return false;
    }

    /**
     * Checks if the ID is null (or matches the NULL_URI).
     *
     * @param id
     *            the ID.
     * @return true if the ID is null.
     */
    public static boolean isNull(URI id) {
        return (id == null) || NULL_URI.equals(id);
    }

    /**
     * Returns uris for list of data objects.
     *
     * @param dataObjects
     * @return list of uris
     */
    public static List<URI> toUris(List<? extends DataObject> dataObjects) {
        List<URI> uris = new ArrayList<>();
        if (dataObjects != null) {
            for (DataObject dataObject : dataObjects) {
                uris.add(dataObject.getId());
            }
        }
        return uris;
    }

    /**
     * Filter a list of URIs for a specific type
     * 
     * @param uris
     *            list of URIs of any type
     * @param clazz
     *            class to filter in
     * @return a list of URIs of that type, or an empty list
     */
    public static List<URI> getURIsofType(Collection<URI> uris, Class clazz) {
        List<URI> returnIds = new ArrayList<URI>();
        if (uris == null) {
            return returnIds;
        }

        for (URI uri : uris) {
            if (URIUtil.isType(uri, clazz)) {
                returnIds.add(uri);
            }
        }
        return returnIds;
    }

}

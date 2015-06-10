/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.core.filters.ResourceFilter;

public class ResourceUtils {
    /** Null URI to use to unassign certain values. */
    public static final URI NULL_URI = uri("null");

    /**
     * Gets the ID of a data object, null safe.
     * 
     * @param value
     *        the data object.
     * @return the ID.
     */
    public static URI id(DataObjectRestRep value) {
        return value != null ? value.getId() : null;
    }

    /**
     * Gets the ID of a reference, null safe.
     * 
     * @param ref
     *        the resource reference.
     * @return the ID.
     */
    public static URI id(RelatedResourceRep ref) {
        return ref != null ? ref.getId() : null;
    }

    /**
     * Gets a list of IDs of data objects, null safe.
     * 
     * @param values
     *        list of data objects.
     * @return List of IDs.
     */
    public static List<URI> ids(Collection<? extends DataObjectRestRep> values) {
        List<URI> ids = new ArrayList<URI>();
        if (values != null) {
            for (DataObjectRestRep value : values) {
                ids.add(value.getId());
            }
        }
        return ids;
    }

    /**
     * Gets a list of IDs of data objects, null safe.
     * 
     * @param refs
     *        list of resource references.
     * @return List of IDs.
     */
    public static List<URI> refIds(Collection<? extends RelatedResourceRep> refs) {
        List<URI> ids = new ArrayList<URI>();
        if (refs != null) {
            for (RelatedResourceRep ref : refs) {
                ids.add(ref.getId());
            }
        }
        return ids;
    }

    /**
     * Gets the ID of the resource, as a string.
     * 
     * @param value
     *        the resource.
     * @return the string ID.
     */
    public static String stringId(DataObjectRestRep value) {
        return asString(id(value));
    }

    /**
     * Gets the ID of the reference, as a string.
     * 
     * @param ref
     *        the resource reference.
     * @return the string ID.
     */
    public static String stringId(RelatedResourceRep ref) {
        return asString(id(ref));
    }

    /**
     * Gets the IDs of the resources, as a list of strings.
     * 
     * @param values
     *        the resources.
     * @return the string ID.
     */
    public static List<String> stringIds(Collection<? extends DataObjectRestRep> values) {
        List<String> ids = new ArrayList<String>();
        if (values != null) {
            for (DataObjectRestRep value : values) {
                ids.add(stringId(value));
            }
        }
        return ids;
    }

    /**
     * Gets the IDs of the references, as a list of strings.
     * 
     * @param refs
     *        the resource references.
     * @return the list of string IDs.
     */
    public static List<String> stringRefIds(Collection<? extends RelatedResourceRep> refs) {
        List<String> ids = new ArrayList<String>();
        if (refs != null) {
            for (RelatedResourceRep ref : refs) {
                ids.add(stringId(ref));
            }
        }
        return ids;
    }

    /**
     * Gets the name of a data object, null safe
     * 
     * @param value
     *        the data object.
     * @return the name of the data object.
     */
    public static String name(DataObjectRestRep value) {
        return value != null ? value.getName() : null;
    }

    /**
     * Gets the name of a named reference, null safe
     * 
     * @param ref
     *        the named reference.
     * @return the name of the reference.
     */
    public static String name(NamedRelatedResourceRep ref) {
        return ref != null ? ref.getName() : null;
    }

    /**
     * Gets the names of the data objects, null safe
     * 
     * @param values
     *        the data objects.
     * @return the names of the data objects.
     */
    public static List<String> names(Collection<? extends DataObjectRestRep> values) {
        List<String> names = new ArrayList<String>();
        if (values != null) {
            for (DataObjectRestRep value : values) {
                names.add(value.getName());
            }
        }
        return names;
    }

    /**
     * Gets the name of the references, null safe
     * 
     * @param refs
     *        the named references.
     * @return the names of the references.
     */
    public static List<String> refNames(Collection<? extends NamedRelatedResourceRep> refs) {
        List<String> names = new ArrayList<String>();
        if (refs != null) {
            for (NamedRelatedResourceRep ref : refs) {
                names.add(ref.getName());
            }
        }
        return names;
    }

    /**
     * Finds a data object within a collection by ID.
     * 
     * @param values
     *        the data objects.
     * @param id
     *        the ID of the value to find.
     * @return the value, or null if not found.
     */
    public static <T extends DataObjectRestRep> T find(Collection<T> values, URI id) {
        if ((values != null) && (id != null)) {
            for (T value : values) {
                if (id.equals(id(value))) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Finds a resource reference within a collection by ID.
     * 
     * @param resources
     *        the resource references.
     * @param id
     *        the ID of the reference to find.
     * @return the resource reference, or null if not found.
     */
    public static <T extends RelatedResourceRep> T findRef(Collection<T> resources, URI id) {
        if ((resources != null) && (id != null)) {
            for (T resource : resources) {
                if (id.equals(id(resource))) {
                    return resource;
                }
            }
        }
        return null;
    }

    /**
     * Defaults the list to an empty list if null.
     * 
     * @param value
     *        the list value.
     * @return the original list if non-null, otherwise it creates a new list.
     */
    public static <T> List<T> defaultList(List<T> value) {
        if (value == null) {
            return new ArrayList<T>();
        }
        else {
            return value;
        }
    }

    /**
     * Determines if the item is active.
     * 
     * @param value
     *        the item.
     * @return true if the item is active (inactive is not set, or is set to FALSE)
     */
    public static boolean isActive(DataObjectRestRep value) {
        return (value != null) && !Boolean.TRUE.equals(value.getInactive());
    }

    /**
     * Determines if the item is not internal.
     *
     * @param value
     *        the item.
     * @return true if the item is active (inactive is not set, or is set to FALSE)
     */
    public static boolean isNotInternal(DataObjectRestRep value) {
        return (value != null) && !Boolean.TRUE.equals(value.getInternal());
    }

    /**
     * Maps a collection of resources by their IDs.
     * 
     * @param resources
     *        the resources to map.
     * @return the map of ID->resource.
     */
    public static <T extends DataObjectRestRep> Map<URI, T> mapById(Collection<T> resources) {
        Map<URI, T> map = new LinkedHashMap<URI, T>();
        for (T resource : resources) {
            map.put(resource.getId(), resource);
        }
        return map;
    }

    /**
     * Maps a collection of references by their ID to their name.
     *
     * @param references
     *        the references to map.
     * @return the map of ID->name.
     */
    public static Map<URI,String> mapNames(Collection<? extends NamedRelatedResourceRep> references) {
        Map<URI,String> map = new LinkedHashMap<URI,String>();
        for (NamedRelatedResourceRep ref : references) {
            map.put(ref.getId(), ref.getName());
        }
        return map;
    }

    /**
     * Gets the value of the URI as a string, returns null if the URI is null.
     * 
     * @param value
     *        the URI.
     * @return the string value of the URI.
     */
    public static String asString(URI value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Converts a string to a URI, null safe.
     * 
     * @param value
     *        the string value.
     * @return the URI or null if the value does not represent a valid URI.
     */
    public static URI uri(String value)  {
        try {
            return (value != null && value.length() > 0) ? URI.create(value) : null;
        } catch(IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Converts a collection of strings to a list of URIs, null safe.
     * 
     * @param values
     *        the string values.
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
     *        the string values.
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
     * Determines if the IDs of the given values match.
     * 
     * @param first
     *        the first reference.
     * @param second
     *        the second reference.
     * @return true if the values point to the same ID.
     */
    public static boolean idEquals(RelatedResourceRep first, RelatedResourceRep second) {
        return equals(id(first), id(second));
    }

    /**
     * Determines if the IDs of the given values match.
     * 
     * @param first
     *        the resource reference.
     * @param second
     *        the resource object.
     * @return true if the values point to the same ID.
     */
    public static boolean idEquals(RelatedResourceRep first, DataObjectRestRep second) {
        return equals(id(first), id(second));
    }

    /**
     * Determines if the IDs of the given values match.
     * 
     * @param first
     *        the resource object.
     * @param second
     *        the resource reference.
     * @return true if the values point to the same ID.
     */
    public static boolean idEquals(DataObjectRestRep first, RelatedResourceRep second) {
        return equals(id(first), id(second));
    }

    /**
     * Determines if the IDs are equal (and non-null).
     * 
     * @param first
     *        the first ID.
     * @param second
     *        the second ID.
     * @return true if and only if the IDs are non-null and equal.
     */
    public static boolean equals(URI first, URI second) {
        if ((first != null) && (second != null)) {
            return first.equals(second);
        }
        return false;
    }

    /**
     * Checks if the ID is null (or matches the NULL_URI).
     * 
     * @param id
     *        the ID.
     * @return true if the ID is null.
     */
    public static boolean isNull(URI id) {
        return (id == null) || NULL_URI.equals(id);
    }

    /**
     * Creates a named reference to the resource. The reference will have no selfLink set.
     * 
     * @param resource
     *        the resource.
     * @return the named reference to the resource.
     */
    public static NamedRelatedResourceRep createNamedRef(DataObjectRestRep resource) {
        return (resource != null) ? new NamedRelatedResourceRep(id(resource), null, name(resource)) : null;
    }

    /**
     * Applies a resource filter to the collection of resources.
     * 
     * @param resources
     *        the resources to filter.
     * @param filter
     *        the filter to apply.
     */
    public static <T extends DataObjectRestRep> void applyFilter(Collection<T> resources, ResourceFilter<T> filter) {
        if (filter != null) {
            Iterator<T> iter = resources.iterator();
            while (iter.hasNext()) {
                T resource = iter.next();
                if (!filter.accept(resource)) {
                    iter.remove();
                }
            }
        }
    }
}

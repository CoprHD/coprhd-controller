/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;

/**
 * A utility class for changing between {@link StringMap} and {@link List}
 *
 */
public class StringSetUtil {

    /**
     * Creates and return a {@link List} from the values of a {@link StringSet}
     *
     * @param curSet the {@link java.util.Collection<String>} containing the values
     * @return the {@link List}
     */
    public static List<URI> stringSetToUriList(Collection<String> curSet) {
        List<URI> list = new ArrayList<URI>();
        if (curSet != null && !curSet.isEmpty()) {
            for (String strUri : curSet) {
                list.add(URI.create(strUri));
            }
        }
        return list;
    }

    /**
     * Returns a set of strings from a list of URIs
     *
     * @param uris the list of URIs
     * @return a set of strings
     */
    public static Set<String> uriListToSet(List<URI> uris) {
        Set<String> set = new HashSet<String>();
        if (uris != null && !uris.isEmpty()) {
            for (URI uri : uris) {
                set.add(uri.toString());
            }
        }
        return set;
    }

    /**
     * Returns a set of strings from a list of URIs
     *
     * @param uris the list of URIs
     * @return a set of strings
     */
    public static StringSet uriListToStringSet(List<URI> uris) {
        StringSet set = new StringSet();
        if (uris != null && !uris.isEmpty()) {
            for (URI uri : uris) {
                set.add(uri.toString());
            }
        }
        return set;
    }

    /**
     * Returns a set of strings from a list of URIs
     *
     * @param uris the Set of URIs
     * @return a set of strings
     */
    public static StringSet uriSetToStringSet(Set<URI> uris) {
        StringSet set = new StringSet();
        if (uris != null && !uris.isEmpty()) {
            for (URI uri : uris) {
                set.add(uri.toString());
            }
        }
        return set;
    }

    /**
     * Returns a StringSet from the keySet of a StringMap
     *
     * @param map StringMap
     * @return Returns an empty set if the map is null, otherwise the keySet.
     */
    public static StringSet getStringSetFromStringMapKeySet(StringMap map) {
        StringSet result = new StringSet();
        if (map == null) {
            return result;
        }
        result.addAll(map.keySet());
        return result;
    }

    /**
     * Return true if there is any intersection in the StringSets a and b.
     *
     * @param a StringSet
     * @param b StringSet
     * @return true if they have a common member, false otherwise
     */
    public static boolean hasIntersection(StringSet a, StringSet b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (String s : a) {
            if (b.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * If set1 and set2 are before and after respectively, return the items
     * that existed before and no longer exist.
     *
     * @param set1
     * @param set2
     * @return
     */
    public static StringSet getRemoved(StringSet set1, StringSet set2) {
        StringSet removed = new StringSet();
        if (set1 != null) {
            if (set2 == null) {
                removed.addAll(set1);
            } else {
                for (String str : set1) {
                    if (!set2.contains(str)) {
                        removed.add(str);
                    }
                }
            }
        }
        return removed;
    }

    /**
     * If set1 and set2 are before and after respectively, return the items
     * that exists now that did not exist before.
     *
     * @param set1
     * @param set2
     * @return
     */
    public static StringSet getAdded(StringSet set1, StringSet set2) {
        StringSet added = new StringSet();
        if (set2 != null) {
            if (set1 == null) {
                added.addAll(set2);
            } else {
                for (String str : set2) {
                    if (!set1.contains(str)) {
                        added.add(str);
                    }
                }
            }
        }
        return added;
    }

    /**
     * Compares that two sets do not have the same entries
     *
     * @param set1
     * @param set2
     * @return
     */
    public static boolean isChanged(StringSet set1, StringSet set2) {
        return !getRemoved(set1, set2).isEmpty() ||
                !getRemoved(set2, set1).isEmpty();
    }

    /**
     * Compares that two sets have the same entries
     * 
     * @param set1
     * @param set2
     * @return
     */
    public static boolean areEqual(StringSet set1, StringSet set2) {
        return getRemoved(set1, set2).isEmpty() &&
                getRemoved(set2, set1).isEmpty();
    }

    /**
     * Returns a set of strings from a collection of dataObject
     *
     * @param dataObjects the list of dataObjects
     * @return a set of strings
     */
    public static <T extends DataObject> StringSet objCollectionToStringSet(Collection<T> dataObjects) {
        StringSet set = new StringSet();
        if (dataObjects != null && !dataObjects.isEmpty()) {
            for (T dataObject : dataObjects) {
                set.add(dataObject.getId().toString());
            }
        }
        return set;
    }

    private static StringSet EMPTY_STRING_SET = new StringSet();

    /**
     * This function can be used to prevent NPEs. It will either return the EMPTY_STRING_SET
     * or the set.
     *
     * @param set - StringSet
     * @return Returns a StringSet
     */
    public static StringSet get(StringSet set) {
        return set == null ? EMPTY_STRING_SET : set;
    }

    /**
     * Takes the inputList and removes any duplicates
     *
     * @param inputList<T> [in/out] - List to process and update
     */
    public static <T> void removeDuplicates(List<T> inputList) {
        // Only run if the list is non-null and has more than one element
        if (inputList != null && inputList.size() > 1) {
            List<T> listOfUniqueElements = new ArrayList<>(new LinkedHashSet<T>(inputList));
            // LinkedHashSet will create a linked list of unique elements. If duplicates
            // have been removed, then the resulting list should be shorter than the
            // inputList. Check for this condition, so that we can update the inputList.
            if (listOfUniqueElements.size() < inputList.size()) {
                inputList.clear();
                inputList.addAll(listOfUniqueElements);
            }
        }
    }
}

/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;

public class DataObjectChangeAnalyzer {

    public static class Change {
        public String _key;                 // key (field name)
        public Object _left;                // left object
        public Object _right;               // right object
        public String name;                 // plain name of the change

        public Change(String key, Object left, Object right, String name) {
            _key = key;
            _left = left;
            _right = right;
            this.name = name;
        }

        @Override
        public String toString() {
            StringBuffer output = new StringBuffer(_key);
            output.append(" (source is ").append(_left);
            output.append(" and target is ").append(_right);
            output.append(" and name is ").append(name);
            output.append(")");
            return output.toString();
        }
    }

    private static final String NOT_NULL = "not null";

    /**
     * Returns true if the two Objects are equal, that is:
     * both null, or both non-null and calling a.equals(b)
     * returns true.
     * 
     * @param obj1 Object
     * @param obj2 Object
     * @return true iff equal
     */
    private static boolean isEqual(Object obj1, Object obj2) {
        Object a = obj1;
        Object b = obj2;

        /*
         * For some reason, when UI reset a string field, it set
         * literal "null" string, instead of null field.
         * Also, once database set nullable number field to 0, it can not reset
         * back to null.
         * Thus, provide workaround check by setting null for value of "null" or 0
         */
        if (b != null &&
                !(b.toString().contains(" ")) && (NullColumnValueGetter.isNullValue(b.toString()) ||
                (b instanceof Number && ((Number) b).equals(new Integer(0))))) {
            b = null;
        }

        if (a != null &&
                !(a.toString().contains(" ")) && (NullColumnValueGetter.isNullValue(a.toString()) ||
                (a instanceof Number && ((Number) a).equals(new Integer(0))))) {
            a = null;
        }

        if (a == null && b == null) {
            return true;
        }
        if (a != null && b != null) {
            return a.equals(b);
        }
        return false;
    }

    /**
     * Makes a Change entry for each key that isn't the same in
     * the two Stringsets a and b. The Change key is of the form
     * name.key where name is the annotation name of the Stringset,
     * and key is a key in the StringSet.
     * 
     * @param a StringSet
     * @param b StringSet
     * @param name - The name annotation of this StringSet, used in key
     * @param changes
     */
    private static void analyzeStringSets(StringSet a, StringSet b,
            String name, HashMap<String, Change> changes) {
        if (a != null) {
            Iterator<String> iter = a.iterator();
            while (iter.hasNext()) {
                String val = iter.next();
                if (b != null && b.contains(val)) {
                    continue;
                }
                String key = name + "." + val;
                Change change = new Change(key, val, null, name);
                changes.put(key, change);
            }
        }
        if (b != null) {
            Iterator<String> iter = b.iterator();
            while (iter.hasNext()) {
                String val = iter.next();
                if (a != null && a.contains(val)) {
                    continue;
                }
                String key = name + "." + val;
                Change change = new Change(key, null, val, name);
                changes.put(key, change);
            }
        }
    }

    /**
     * Makes a Change entry for each key in StringSet a that isn't in
     * the StringSet b. The Change key is of the form
     * name.key where name is the annotation name of the Stringset,
     * and key is a key in the StringSet.
     * 
     * @param a StringSet
     * @param b StringSet
     * @param name - The name annotation of this StringSet, used in key
     * @param changes
     */
    private static void analyzeNewStringSetContainsOldStringSetValues(StringSet a, StringSet b,
            String name, HashMap<String, Change> changes) {
        if (a != null) {
            Iterator<String> iter = a.iterator();
            while (iter.hasNext()) {
                String val = iter.next();
                if (b != null && b.contains(val)) {
                    continue;
                }
                String key = name + "." + val;
                Change change = new Change(key, val, null, name);
                changes.put(key, change);
            }
        } else if (a == null && b != null) {
            String key = name;
            Change change = new Change(key, null, NOT_NULL, name);
            changes.put(key, change);
        }
    }

    /**
     * Records differences between StringMaps a and b.
     * Here the Change key is name.key where name is the annotation name
     * and key is the StringMap key, and the objects are the values in the StringMap.
     * 
     * @param a StringMap
     * @param b StringMap
     * @param name value in the Name Annotation
     * @param changes Map of changes being built.
     */
    private static void analyzeStringMaps(StringMap a, StringMap b,
            String name, HashMap<String, Change> changes) {
        if (a != null) {
            for (String key : a.keySet()) {
                if (b != null && b.containsKey(key)
                        && a.get(key).equals(b.get(key))) {
                    continue;
                }
                Object bval = (b != null) ? b.get(key) : null;
                Change change = new Change(name + "." + key, a.get(key), bval, name);
                changes.put(change._key, change);
            }
        }
        if (b != null) {
            for (String key : b.keySet()) {
                if (a != null && a.containsKey(key)
                        && b.get(key).equals(a.get(key))) {
                    continue;
                }
                Object aval = (a != null) ? a.get(key) : null;
                Change change = new Change(name + "." + key, aval, b.get(key), name);
                changes.put(change._key, change);
            }
        }
    }

    /**
     * Records differences between two StringSetMaps. Here the Change keys
     * are the name.key where name is the name annotation value and key is
     * the key in the StringSetMap, and the objects are the StringSets.
     * 
     * @param a StringSetMap
     * @param b StringSetMap
     * @param name Name annotation value
     * @param changes Change set being generated
     */
    private static void analyzeStringSetMaps(StringSetMap a, StringSetMap b,
            String name, HashMap<String, Change> changes) {
        if (a != null) {
            for (String key : a.keySet()) {
                if (b != null && b.containsKey(key)
                        && a.get(key).equals(b.get(key))) {
                    continue;
                }
                Object bval = (b != null) ? b.get(key) : null;
                Change change = new Change(name + "." + key, a.get(key), bval, name);
                changes.put(change._key, change);
            }
        }
        if (b != null) {
            for (String key : b.keySet()) {
                if (a != null && a.containsKey(key)
                        && b.get(key).equals(a.get(key))) {
                    continue;
                }
                Object aval = (a != null) ? a.get(key) : null;
                Change change = new Change(name + "." + key, aval, b.get(key), name);
                changes.put(change._key, change);
            }
        }
    }

    /**
     * Scans the methods looking for ones annotated with the Name annotation.
     * When found (if not excluded), invokes the method on each of the DataObjects
     * and then compares the results.
     * 
     * @param left
     * @param right
     * @param changes
     * @param included -- If not null, only fields in included are checked.
     * @param excluded -- Fields that are excluded are not checked. Must not be null.
     * @param contained -- If not null, values for fields in contained are checked.
     */
    private static void lookForChanges(DataObject left, DataObject right,
            HashMap<String, Change> changes,
            Set<String> included, Set<String> excluded, Set<String> contained) {
        Class refClass = left.getClass();
        Method[] methods = refClass.getMethods();
        for (Method method : methods) {
            boolean contain = false;
            // We only analyze methods that have the "Name" annotation
            Name nameAnn = method.getAnnotation(Name.class);
            if (nameAnn == null) {
                continue;
            }
            String key = nameAnn.value();
            // If contained is not null and it contains the key set contain flag to true
            if (contained != null && contained.contains(key)) {
                contain = true;
            }// If included is not null, and does not contain the name, exclude it.
            else if (included != null && !included.contains(key)) {
                continue;
            }
            // Skip any excluded annotation names
            if (excluded.contains(key)) {
                continue;
            }
            Class type = method.getReturnType();
            try {
                Object obja = method.invoke(left);
                Object objb = method.invoke(right);
                if (type == StringSet.class) {
                    if (contain) {
                        analyzeNewStringSetContainsOldStringSetValues((StringSet) obja, (StringSet) objb, key, changes);
                    } else {
                        analyzeStringSets((StringSet) obja, (StringSet) objb, key, changes);
                    }
                } else if (type == StringMap.class) {
                    analyzeStringMaps((StringMap) obja, (StringMap) objb, key, changes);
                } else if (type == StringSetMap.class) {
                    analyzeStringSetMaps((StringSetMap) obja, (StringSetMap) objb, key, changes);
                } else {
                    if (!isEqual(obja, objb)) {
                        Change change = new Change(key, obja, objb, nameAnn.value());
                        changes.put(key, change);
                    }
                }
            } catch (IllegalAccessException ex) {
                throw new ServiceCodeException(ServiceCode.UNFORSEEN_ERROR,
                        ex, ex.getMessage(), new String[] {});
            } catch (InvocationTargetException ex) {
                throw new ServiceCodeException(ServiceCode.UNFORSEEN_ERROR,
                        ex, ex.getMessage(), new String[] {});
            }
        }
    }

    /**
     * Analyze the two DataObjects left and right, noting the differences
     * in a Map of field name (as given by the @Name annotation)
     * (or fieldName.key) to values for the left and right DataObjects.
     * Any field names included in the excludedNames array will not be processed.
     * If the includedNames is non-null, and not empty, *only* includedNames will
     * be processed.
     * <p>
     * This routine works by reflection and calls only the gettrs for fields that are annotated with @Name("xxx"). It understands basic
     * object types, such as Boolean, String, Integer, and StringSet, StringMap, and StringSetMap.
     * 
     * @param left DataObject
     * @param right DataObject
     * @param includedNames -- if non-null and not empty, only included fields are compared
     * @param excludedNames -- if non-null, contains fields that should not be compared
     * @param containNames -- if non-null, contains fields that should be compared for containment.
     *            If anything thats in left but not in right is flagged and fails containment check.
     * @return A Map of field name (or field name . key) to values for left and right.
     */
    public static Map<String, Change> analyzeChanges(
            DataObject left, DataObject right,
            String[] includedNames, String[] excludedNames, String[] containNames) {
        HashSet<String> included = null;
        if (includedNames != null && includedNames.length > 0) {
            included = new HashSet<String>(Arrays.asList(includedNames));
        }
        if (excludedNames == null) {
            excludedNames = new String[] {};
        }
        HashSet<String> contained = null;
        if (containNames != null && containNames.length > 0) {
            contained = new HashSet<String>(Arrays.asList(containNames));
        }
        HashSet<String> excluded = new HashSet<String>(Arrays.asList(excludedNames));
        HashMap<String, Change> changes = new HashMap<String, Change>();
        if (left.getClass() != right.getClass()) {
            throw APIException.badRequests.unexpectedClass(left.getClass().getSimpleName(), right
                    .getClass().getSimpleName());
        }
        lookForChanges(left, right, changes, included, excluded, contained);
        return changes;
    }
}

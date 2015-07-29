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
package com.emc.storageos.db.joiner;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;

/**
 * This object represents a single Class Query.
 * 
 * @author watson
 * 
 * @param <T extends DataObject>
 */
class JClass<T extends DataObject> {
    private static final Logger _log = LoggerFactory.getLogger(JClass.class);
    Class<T> clazz;                 // Java class of table being joined
    String alias;                   // the alias for this join
    ClassMetaData md;               // meta-data for this type
    long maxCacheSize = Long.MAX_VALUE;   // initialized no restriction
    private Set<URI> uris = new HashSet<URI>();   // URIs for this query
    String joinToAlias;             // join to alias
    String joinToField;             // field (attribute) name in joinTo class
    String field;                   // field in this class
    List<JSelection> selections = new ArrayList<JSelection>();    // Selections applied to this class for result
    private Map<URI, T> cachedObjects = new HashMap<URI, T>();  // cached objects
    boolean cacheValid = false;     // set true if the cache contains valid results
    private Map<URI, Set<URI>> joinMap = new HashMap<URI, Set<URI>>();
    Set<JClass> subJClasses = null;
    int index;                      // index in the jClasses list that orders evaluation of queries

    JClass(Class<T> clazz, String alias, int index) {
        this.clazz = clazz;
        this.alias = alias;
        this.index = index;
        md = new ClassMetaData(clazz);
        // If this class is abstract, process the sub-classes to find the real joins needed.
        if (md.isAbstract()) {
            long time = System.currentTimeMillis();
            Set<Class<? extends DataObject>> subClasses = md.getSubclasses(clazz);
            time = System.currentTimeMillis() - time;
            _log.info("Subclass calculation time: " + time + " class: " + clazz.getSimpleName());
            if (subClasses.isEmpty()) {
                throw new JoinerException("No meta-data for: " + clazz.getSimpleName());
            }
            // Process each sub-class and make a JClass for the sub-class.
            subJClasses = new HashSet<JClass>();
            for (Class subClass : subClasses) {
                JClass subJClass = new JClass(subClass, alias + "." + subClass.getSimpleName(), index);
                subJClasses.add(subJClass);
            }
        }
    }

    /**
     * Returns an iterator for the database objects from this query term.
     * 
     * @param engine QueryEngine used to lookup objects
     * @return Iterator<T> that will iterate through the result objects
     */
    Iterator<T> iterator(QueryEngine engine) {
        if (cacheValid) {
            return cachedObjects.values().iterator();
        } else {
            return new JClassIterator<T>(this, engine);
        }
    }

    /**
     * Querys for a particular object given its URI. This method handles
     * subclasses correctly, and will query whatever subclass Column Family
     * is needed for the particular URI.
     * 
     * @param engine -- the Query Engine to be used for the request
     * @param uri -- The URI of the requested Object, either of type T or a subclass
     * @return
     */
    T queryObject(QueryEngine engine, URI uri) {
        if (md.isAbstract()) {
            String type = URIUtil.getTypeName(uri);
            for (JClass subJc : getSubJClasses()) {
                if (subJc.getClazz().getSimpleName().equals(type)) {
                    return (T) engine.queryObject(subJc.getClazz(), uri);
                }
            }
        } else {
            return engine.queryObject(getClazz(), uri);
        }
        return null;
    }

    /**
     * Add an object to the cache if the cache is enabled and the cache is not
     * already at maximum capacity. Otherwise disable the cache and clear contents.
     * 
     * @param object
     */
    void addToCache(T object) {
        if (cacheValid == true) {
            if (cachedObjects.size() < maxCacheSize) {
                URI uri = object.getId();
                cachedObjects.put(uri, object);
            } else {
                cacheValid = false;
                cachedObjects.clear();
            }
        }
    }

    /**
     * Add a mapping between an object in the joinTo (i.e. the query
     * results this class is joined to) and an object in this query result.
     * The joinToMap maps each URI in the joinTo result to the set of objects
     * in this result. It is used for constructing output representations.
     * 
     * @param joinToURI -- URI of object in joinToAlias query result
     * @param thisURI -- URI of result in this query result
     */
    void addToJoinMap(URI joinToURI, URI thisURI) {
        if (joinMap.get(joinToURI) == null) {
            joinMap.put(joinToURI, new HashSet<URI>());
        }
        joinMap.get(joinToURI).add(thisURI);
    }

    Class<T> getClazz() {
        return clazz;
    }

    void setClazz(Class<T> clazz) {
        this.clazz = clazz;
    }

    String getAlias() {
        return alias;
    }

    void setAlias(String alias) {
        this.alias = alias;
    }

    ClassMetaData getMetaData() {
        return md;
    }

    void setMd(ClassMetaData md) {
        this.md = md;
    }

    long getMaxCacheSize() {
        return maxCacheSize;
    }

    void setMaxCacheSize(long maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    Set<URI> getUris() {
        return uris;
    }

    void setUris(Set<URI> uris) {
        this.uris = uris;
    }

    String getJoinToAlias() {
        return joinToAlias;
    }

    void setJoinToAlias(String joinToAlias) {
        this.joinToAlias = joinToAlias;
    }

    String getJoinToField() {
        return joinToField;
    }

    void setJoinToField(String joinToField) {
        this.joinToField = joinToField;
    }

    List<JSelection> getSelections() {
        return selections;
    }

    void setSelections(List<JSelection> selections) {
        this.selections = selections;
    }

    Map<URI, T> getCachedObjects() {
        return cachedObjects;
    }

    void setCachedObjects(Map<URI, T> cachedObjects) {
        this.cachedObjects = cachedObjects;
    }

    boolean isCacheValid() {
        return cacheValid;
    }

    void setCacheValid(boolean cacheValid) {
        this.cacheValid = cacheValid;
    }

    String getField() {
        return field;
    }

    void setField(String field) {
        this.field = field;
    }

    Map<URI, Set<URI>> getJoinMap() {
        return joinMap;
    }

    void setJoinMap(Map<URI, Set<URI>> joinMap) {
        this.joinMap = joinMap;
    }

    Set<JClass> getSubJClasses() {
        return subJClasses;
    }

    void setSubJClasses(Set<JClass> subJClasses) {
        this.subJClasses = subJClasses;
    }
}

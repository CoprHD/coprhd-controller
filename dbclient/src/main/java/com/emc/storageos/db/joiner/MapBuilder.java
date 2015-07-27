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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MapBuilder's job is to construct arbitrarily complex maps of query results.
 * Maps can be nested like this: Map<A, Map<B, Set<C>>> which means A maps to a map
 * of B to a set of C objects. To construct such a map, you would
 * pushList("A").pushList("B").pushSet("C").map().
 * So the pushes are read left to right just like the items in the nested maps.
 * 
 * Two things are complicated about this code:
 * 1. It needs to understand how to make a map Map<A,Set<C>> where A is joined to B and
 * B is joined to C. That is, it needs to traverse intermediate joins not used in the maps.
 * 2. It needs to understand how to traverse the joins in reverse, that is it needs to be
 * able to make a map Map<B, Set<A>> where A is joined to B. That is what reverseDuples()
 * is about.
 * @author root
 *
 */
class MapBuilder {
    
    List<MapBuilderTerm> terms = new ArrayList<MapBuilderTerm>();
    Joiner joiner;
    Map previousResult = null;
    
    /**
     * Constructor.
     * @param joiner
     */
    MapBuilder(Joiner joiner) {
        this.joiner = joiner;
    }
    
    /**
     * Add a term to the map builder.
     * @param type
     * @param jclass
     */
    void addTerm(MapBuilderTermType type, JClass jclass, String alias) {
        MapBuilderTerm newTerm = new MapBuilderTerm();
        newTerm.type = type;
        newTerm.jclass = jclass;
        newTerm.alias = alias;
        terms.add(newTerm);
    }
    
    Map buildMapStructure() {
        if (terms.size() < 2) throw new JoinerException("Map must consist of at least two terms");
        // For the 2nd through nth terms, search backwards finding the join path to previous term
        for (int i = 1; i < terms.size(); i++) {
            List<JClass> joinPath = computeJoinPath(terms.get(i-1), terms.get(i));
            terms.get(i-1).joinPath = joinPath;
        }
        // Now, starting at last two terms, compute the duples URIs representing what is joined.
        // Then format the Map for this item.
        for (int i=terms.size()-2; i >= 0; i--) {
            Map<URI, Set<URI>> duples = computeDuples(terms.get(i).joinPath);
            if (! terms.get(i).alias.equals(terms.get(i).joinPath.get(0).getAlias())) {
                // If working in reverse, reverse the duples.
                duples = reverseDuples(duples);
            }
            terms.get(i).duples = duples;
            terms.get(i).map = computeMap(terms.get(i), terms.get(i+1));
        }
        
        // Finalize the output map, ditching the URI keys
        Map resultMap = new HashMap();
        for (Map map : terms.get(0).map.values()) {
            for (Object key : map.keySet()) {
                resultMap.put(key,  map.get(key));
            }
        }
        return resultMap;
    }
    
    /**
     * Reverses the duples between two classes.
     * @param duples
     * @return
     */
    private Map<URI, Set<URI>> reverseDuples(Map<URI, Set<URI>> duples) {
        Map<URI, Set<URI>> reversed = new HashMap<URI, Set<URI>>();
        for (URI uri : duples.keySet()) {
            for (URI value : duples.get(uri)) {
                if (!reversed.containsKey(value)) {
                    reversed.put(value, new HashSet<URI>());
                }
                reversed.get(value).add(uri);
            }
        }
        return reversed;
    }
    
    /**
     * Returns the path to traverse joins between term1 and term2.
     * It may be from term1 to term2, or it may be from term2 to term1.
     * It will start with the lowest indexed join class and work toward the highest.
     * @param term1
     * @param term2
     * @return
     */
    private List<JClass> computeJoinPath(MapBuilderTerm term1, MapBuilderTerm term2) {
        List<JClass> joinPath = new ArrayList<JClass>();
        JClass jc = null;
        if (term2.jclass.index > term1.jclass.index) {
            joinPath.add(term2.jclass);
            String joinToAlias = term2.jclass.getJoinToAlias();
            if (joinToAlias == null) throw new JoinerException(String.format(
                    "Cannot follow %s back to %s", term2.alias, term1.alias));
            // Go backwards from our term to where term0 was computed
            do {
                jc = joiner.lookupAlias(joinToAlias);
                if (jc == null) throw new JoinerException(String.format("Cannot find table for alias %s", joinToAlias));
                joinPath.add(jc);
                joinToAlias = jc.getJoinToAlias();
            } while (joinToAlias != null && jc != term1.jclass);
        } else {
            joinPath.add(term1.jclass);
            String joinToAlias = term1.jclass.getJoinToAlias();
            // Go backwards from our term0 to where term was computed
            do {
                jc = joiner.lookupAlias(joinToAlias);
                if (jc == null) throw new JoinerException(String.format("Cannot find table for alias %s", joinToAlias));
                joinPath.add(jc);
                joinToAlias = jc.getJoinToAlias();
            } while (joinToAlias != null && jc != term2.jclass);
        }
        Collections.reverse(joinPath);
        return joinPath;
    }
    
    private Map<URI, Set<URI>> computeDuples(List<JClass> joinPath) {
        Map<URI, Set<URI>> duples = new HashMap<URI, Set<URI>>();
        JClass jc = joinPath.get(0);
        Set<URI> uris = jc.getUris();
        for (URI key : uris) {
            Set<URI> matchSet = iterateDuples(key, joinPath, 1);
            if (matchSet != null && !matchSet.isEmpty()) duples.put(key,  matchSet);
        }
        return duples;
    }
    
    private Set<URI> iterateDuples(URI key, List<JClass> joinPath, int joinPathIndex) {
        // If this is the last JClass in the join list, just return
        // the URI set determined by the key.
        if ((joinPath.size()-1) == joinPathIndex) {
            Map<URI, Set<URI>> joinMap = joinPath.get(joinPathIndex).getJoinMap();
            return joinMap.get(key);
        }
        // Otherwise, iterate through our values and recurse to next joinPath.
        Set<URI> result = new HashSet<URI>();
        Map<URI, Set<URI>> joinMap = joinPath.get(joinPathIndex).getJoinMap();
        Set<URI> joinResults = joinMap.get(key);
        if (joinResults != null) {
            for (URI uri : joinResults) {
                Set<URI> dupleURIs = iterateDuples(uri, joinPath, joinPathIndex + 1);
                if (dupleURIs == null) continue;
                result.addAll(dupleURIs);
            }
        }
        return result;
    }
    
    /**
     * Generates a Map<URI, Map<T1, T2>> where T1 and T2 are the expected return
     * types for Term1 and Term2 and T1.id matches URI.
     * @param term1
     * @param term2
     * @return
     */
    private Map<URI, Map> computeMap(MapBuilderTerm term1, MapBuilderTerm term2) {
        Map<URI, Map> outputMap = new HashMap<URI, Map>();
        for (URI uri : term1.duples.keySet()) {
            Map map = new HashMap();
            Object object1 = getObject(term1.alias, uri, term1.type);
            if (object1 == null) continue;
            Object object2 = null;
            object2 = getObject(term2.alias, term1.duples.get(uri), term2.type, term2.map);
            if (object2 == null) continue;
            map.put(object1, object2);
            outputMap.put(uri,  map);
        }
        return outputMap;
    }
    
    /**
     * Return a single object based on type
     * @param alias
     * @param uri
     * @param type
     * @return
     */
    private Object getObject(String alias, URI uri, MapBuilderTermType type) {
        if (type == MapBuilderTermType.URI) return uri;
        return joiner.find(alias, uri);
    }
    
    /**
     * Return a collection object based on type
     * @param alias
     * @param uris
     * @param type
     * @param term2Map -- Map to be included as result of this map.
     * @return
     */
    private Object getObject(String alias, Set<URI> uris, MapBuilderTermType type, Map<URI, Map> term2Map) {
        if (term2Map != null) {
            Map resultMap = new HashMap();
            for (Map map : term2Map.values()) {
                for (Object key : map.keySet()) {
                    resultMap.put(key,  map.get(key));
                }
            }
            return resultMap;
        }
        if (type == MapBuilderTermType.URI) return uris;
        if (type == MapBuilderTermType.LIST) {
            ArrayList list = new ArrayList();
            for (URI uri : uris) {
                Object object = joiner.find(alias,  uri);
                if (object != null) list.add(object);
            }
            return list;
        }
        if (type == MapBuilderTermType.SET){
            HashSet set = new HashSet();
            for (URI uri : uris) {
                Object object = joiner.find(alias,  uri);
                if (object != null) set.add(object);
            }
            return set;
        }
        return null;
    }
}

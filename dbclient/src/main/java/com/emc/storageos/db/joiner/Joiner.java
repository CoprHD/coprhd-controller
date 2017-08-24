/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * This class implements the Joiner API, which is described at:
 * asdwiki.isus.emc.com:8443/display/OS/Joiner (search for Joiner).
 * 
 * @author watson
 * 
 *         The Joiner API is an abstraction built on top of DbClient (currently)
 *         or some other arbitrary low lever database access layer facility.
 * 
 *         Joiner provides the following facilities:
 *         1. You can construct one or more queries that join multiple classes together based
 *         on RelationalIndexes.
 *         2. Match operators are provided which allow filtering of the results of a table
 *         based on selection criteria. The match operators will use an index if present,
 *         but are able to filter on non-indexed fields as well.
 *         3. You can retrieve results in a wide variety of Java friendly formats, such
 *         as Iterators, Lists or Sets of Objects, and arbitrary Maps between objects in
 *         multiple classes.
 *         4. Internally Joiner uses iterators for accessing objects. If the number of objects
 *         retrieved for a class is less than the JClass.maxCacheSize parameter, then a cache
 *         of the result objects is kept in the JClass. Otherwise only the URIs of the results
 *         are stored.
 *         5. Joins return left outer joins
 * 
 */
public class Joiner {
    private static final Logger log = LoggerFactory.getLogger(Joiner.class);
    private int start = 0;              // index at which to start query
    private List<JClass> jClasses = new ArrayList<JClass>();
    private Map<String, JClass> aliasMap = new HashMap<String, JClass>();
    JClass lastJClass = null;       // pointer to the last class query that was defined
    QueryEngine engine = null;      // the engine used to run low lever queries
    MapBuilder mapBuilder = null;   // the MapBuilder used to build maps

    private DbClient _dbClient;     // dbClient implementation

    public Joiner(DbClient dbClient) {
        this._dbClient = dbClient;
        this.engine = new DbClientQueryEngine(_dbClient);
    }

    /**
     * Starts a new query not related to a previous class query.
     * 
     * @param clazz -- Class extending DataObject
     * @param alias -- String identifier
     * @return
     */
    public <T extends DataObject> Joiner join(Class<T> clazz, String alias) {
        if (!jClasses.isEmpty()) {
            throw new JoinerException("Illegal use of Joiner; starting a new join in the middle of a join chain");
        }
        JClass jc = new JClass(clazz, alias, jClasses.size());
        jClasses.add(jc);
        lastJClass = jc;
        aliasMap.put(alias, jc);
        return this;
    }

    /**
     * Join A to B where B.field contains A's id
     * 
     * @param joinToAlias -- previous alias of A
     * @param clazz -- B Class
     * @param alias -- B alias
     * @param field -- B field (StringSet, StringMap, ...) containing A.id
     * @return Joiner
     */
    public <T extends DataObject> Joiner join(String joinToAlias, Class<T> clazz, String alias, String field) {
        JClass jc = new JClass(clazz, alias, jClasses.size());
        jc.setJoinToAlias(joinToAlias);
        jc.setField(field);
        jClasses.add(jc);
        lastJClass = jc;
        aliasMap.put(alias, jc);
        return this;
    }

    /**
     * Join A to B where A.field contains B's id
     * 
     * @param joinToAlias -- previous alias of A
     * @param joinToField -- field in A (StringSet, StringMap, ...) containing B.id
     * @param clazz -- B Class
     * @param alias -- B alias
     * @return Joiner
     */
    public <T extends DataObject> Joiner join(String joinToAlias, String joinToField, Class<T> clazz, String alias) {
        JClass jc = new JClass(clazz, alias, jClasses.size());
        jc.setJoinToAlias(joinToAlias);
        jc.setJoinToField(joinToField);
        jClasses.add(jc);
        lastJClass = jc;
        aliasMap.put(alias, jc);
        return this;
    }

    /**
     * Starts a new query not related to a previous class query.
     * 
     * @param clazz -- Class extending DataObject
     * @param alias -- String identifier
     * @param ids -- URI[] start with list of ids
     * @return
     */
    public Joiner join(Class<? extends DataObject> clazz, String alias, URI... ids) {
        return join(clazz, alias, new ArrayList<URI>(Arrays.asList(ids)));
    }

    /**
     * @param clazz -- Class extending DataObject
     * @param alias -- String identifier
     * @param ids -- Collection<URI> start with list of ids
     * @return
     */
    /**
     * Starts a new query not related to a previous class query filtering the data before joining
     * 
     * @param clazz
     * @param alias
     * @param filter can be a list of URI's or a list of DataObjects
     * @return
     */
    public <T extends DataObject> Joiner join(Class<? extends DataObject> clazz, String alias, Collection filter) {
        if (!jClasses.isEmpty()) {
            throw new JoinerException("Illegal use of Joiner; starting a new join in the middle of a join chain");
        }
        JClass jc = new JClass(clazz, alias, jClasses.size());
        jClasses.add(jc);
        lastJClass = jc;
        aliasMap.put(alias, jc);
        if (filter == null || filter.isEmpty()) {
            return this;
        } else if (URI.class.isAssignableFrom(filter.iterator().next().getClass())) {
            return match("Id", filter);
        } else if (DataObject.class.isAssignableFrom(filter.iterator().next().getClass())) {
            List<URI> ids = new ArrayList<URI>();
            for (DataObject obj : (Collection<DataObject>) filter) {
                ids.add(obj.getId());
            }
            return match("Id", ids);
        } else {
            // TODO : should this be an exception instead of this?
            return this;
        }
    }

    /**
     * Starts a new query not related to a previous class query.
     * 
     * @param clazz -- Class extending DataObject
     * @param alias -- String identifier
     * @param ids -- URI[] start with list of ids
     * @return
     */
    public Joiner join(Class<? extends DataObject> clazz, String alias, DataObject... objs) {
        return join(clazz, alias, new ArrayList<DataObject>(Arrays.asList(objs)));
    }

    /**
     * Filter the matching objects for the one(s) whoose named field
     * is equal to value. If the field is a collection, matches
     * when any value in the collection matches value.
     * 
     * @param field -- Field to be matched against
     * @param value -- value to be matched
     * @return Joiner
     */
    public Joiner match(String field, Object... value) {
        return match(field, Arrays.asList(value));
    }

    /**
     * Filter the matching objects for the one(s) whoose named field
     * is equal to value. If the field is a collection, matches
     * when any value in the collection matches value.
     * 
     * @param field -- Field to be matched against
     * @param value -- value to be matched
     * @return Joiner
     */
    public Joiner match(String field, Collection<? extends Object> value) {
        if (lastJClass == null) {
            throw new JoinerException("Must have join before match");
        }
        JSelection js = new JSelection();
        js.setField(field);
        js.setValues(new HashSet<Object>(value));
        lastJClass.getSelections().add(js);
        return this;
    }

    /**
     * Sets max cache size of previous query.
     * 
     * @param maxCacheSize
     */
    public Joiner max(int maxCacheSize) {
        lastJClass.setMaxCacheSize(maxCacheSize);
        return this;
    }

    /**
     * Executes all the queries that have been defined so far.
     * This means the results set for each Join have been computed, and
     * are either saved as a set of URIs or Objects.
     * 
     * @return Joiner
     */
    public Joiner go() {
        executeQuery();
        return this;
    }

    /**
     * Returns the set of URIs for a given Class Query
     * 
     * @param alias -- identifies the Class Query
     * @return Set<URI>
     */
    public Set<URI> uris(String alias) {
        JClass jc = lookupAlias(alias);
        return jc.getUris();
    }

    /**
     * Pushes the set of URIs for a Class Query for use by the
     * Map Builder.
     * 
     * @param alias -- identifies the Class query
     * @return
     */
    public Joiner pushUris(String alias) {
        if (mapBuilder == null) {
            mapBuilder = new MapBuilder(this);
        }
        mapBuilder.addTerm(MapBuilderTermType.URI, lookupAlias(alias), alias);
        return this;
    }

    /**
     * Returns an iterator for the given Class Query.
     * 
     * @param alias -- identifies the Class query
     * @return Iterator<T> where <T extends DataObject>
     */
    public <T extends DataObject> Iterator<T> iterator(String alias) {
        JClass jc = lookupAlias(alias);
        return jc.iterator(engine);
    }

    /**
     * Returns a partial result iterator for the given Class Query
     * by looking up the URIs for a given join map key (i.e. the id from the joinTo).
     * So for example if A is directly joined to B, and you invoke this on the bAlias,
     * then the joinMapKey would be a URI from A. All the corresponding B results for
     * for given A key are returned.
     * 
     * @param alias == alias of the B classs
     * @param joinMapKey -- A URI from the class B was joined to.
     * @return -- All the results in B for a corresponding specific value in A.
     */
    public <T extends DataObject> Iterator<T> iterator(String alias, URI joinMapKey) {
        JClass jc = lookupAlias(alias);
        Map<URI, Set<URI>> joinMap = jc.getJoinMap();
        if (joinMap == null) {
            return new HashSet<T>().iterator();
        }
        Set<URI> uriSet = joinMap.get(joinMapKey);
        if (joinMap.get(joinMapKey) == null) {
            return new HashSet<T>().iterator();
        }
        return new JClassIterator(jc, engine, uriSet.iterator());
    }

    /**
     * Returns a Set<T> for a given class query.
     * 
     * @param alias -- identifies the Class query
     * @return Set<T> where <T extends DataObject>
     */
    public <T extends DataObject> Set<T> set(String alias) {
        JClass jc = lookupAlias(alias);
        if (jc.cacheValid == false) {
            jc.setCacheValid(true);
            Set<URI> uris = jc.getUris();
            for (URI uri : uris) {
                T object = (T) jc.queryObject(engine, uri);
                jc.getCachedObjects().put(object.getId(), object);
            }
        }
        return new HashSet<T>(jc.getCachedObjects().values());
    }

    /**
     * Pushes a Set<T> for use by the Map builder.
     * 
     * @param alias -- identifies the Class query
     * @return Joiner
     */
    public Joiner pushSet(String alias) {
        if (mapBuilder == null) {
            mapBuilder = new MapBuilder(this);
        }
        mapBuilder.addTerm(MapBuilderTermType.SET, lookupAlias(alias), alias);
        return this;
    }

    /**
     * Returns a List<T> of the result obejcts for a Class query.
     * 
     * @param alias -- identifies the Class query.
     * @return List<T> where <T extends DataObject>
     */
    public <T extends DataObject> List<T> list(String alias) {
        JClass jc = lookupAlias(alias);
        if (jc.cacheValid == false) {
            jc.setCacheValid(true);
            Set<URI> uris = jc.getUris();
            for (URI uri : uris) {
                T object = (T) jc.queryObject(engine, uri);
                jc.getCachedObjects().put(object.getId(), object);
            }
        }
        return new ArrayList<T>(jc.getCachedObjects().values());
    }

    /**
     * Pushes a List<T> for use by the Map Builder.
     * 
     * @param alias -- Identifies the Class query.
     * @return Joiner
     */
    public Joiner pushList(String alias) {
        if (mapBuilder == null) {
            mapBuilder = new MapBuilder(this);
        }
        mapBuilder.addTerm(MapBuilderTermType.LIST, lookupAlias(alias), alias);
        return this;
    }

    /**
     * Builds map(s) from the pushed objects. See the Wiki documentation for
     * examples of how this works. This returns an arbitrarily complicated
     * nested Map(s).
     * 
     * @return Map (types determined by what is pushed)
     */
    public Map map() {
        if (mapBuilder == null) {
            throw new JoinerException("No map items pushed");
        }
        Map outputMap = mapBuilder.buildMapStructure();
        mapBuilder = null;      // reset so can do another map
        return outputMap;
    }

    /**
     * Returns an object from the given query result. If the cache
     * is valid, it is returned from the cache. Otherwise it is
     * returned from a new query by id.
     * 
     * @param alias - Identifies query result
     * @param uri - URI identifying a unique object
     * @return instance of object extending DataObject
     */
    public <T extends DataObject> T find(String alias, URI uri) {
        JClass jc = lookupAlias(alias);
        if (jc.isCacheValid()) {
            Map<URI, T> cachedObjects = jc.getCachedObjects();
            return cachedObjects.get(uri);
        }
        return (T) jc.queryObject(engine, uri);
    }

    /**
     * Executes any Class queries (and selections) that have been defined but
     * not evaluated yet.
     */
    private void executeQuery() {
        for (int i = start; i < jClasses.size(); i++) {
            queryClass(jClasses.get(i));
        }
        start = jClasses.size();
    }

    /**
     * Implements join and selection for a Class Query.
     * Algorithm:
     * 1. If indexed selection(s) are used, determine the possible URIs for the result
     * set of this Class Query from the intersection of the selection criteria.
     * 2. Execute the class query (joining with a previous class if specified).
     * 3. Do all the selctions (including those that are not indexed.)
     * 
     * @param jc
     */
    private void queryClass(JClass jc) {
        // Handle an indexed selections.
        // All the index selections are logically ANDed together.
        Set<URI> selectionUris = null;
        List<JSelection> selectionList = jc.getSelections();
        for (JSelection js : selectionList) {
            // id field is a special case; no need to query anything; just add the id field values to selectionURI's
            Set<URI> allValueUris = new HashSet<URI>();
            if (jc.getMetaData().isId(js.getField())) {

                for (Object value : js.getValues()) {
                    if (value == null || value.equals("")) {
                        continue;
                    }
                    allValueUris.add(URI.create(value.toString()));
                }
                if (selectionUris == null) {
                    selectionUris = new HashSet<URI>();
                    selectionUris.addAll(allValueUris);
                } else {
                    selectionUris = andUriSet(selectionUris, allValueUris);
                }

            } else if (jc.getMetaData().isAltIdIndex(js.getField()) 
                    || jc.getMetaData().isPrefixIndex(js.getField())) {

                // Process alternate id indexes. These are logically ANDed together.
                for (Object value : js.getValues()) {
                    if (value == null || value.equals("")) {
                        continue;
                    }
                    Constraint constraint = jc.getMetaData().buildConstraint(js.getField(), value.toString());
                    allValueUris.addAll(engine.queryByConstraint(constraint));
                }
                if (selectionUris == null) {
                    selectionUris = new HashSet<URI>();
                    selectionUris.addAll(allValueUris);
                } else {
                    selectionUris = andUriSet(selectionUris, allValueUris);
                }
            }
        }

        if (jc.getSubJClasses() != null) {
            joinSubClasses(jc);
        } else if (jc.getJoinToField() != null) {
            // join B.id in A.field
            joinBid2A(jc, selectionUris);
        } else if (jc.getField() != null) {
            // join A.id in B.field
            joinAid2B(jc, selectionUris);
        } else if (jc.getJoinToAlias() == null) {
            // Independent query not joining to previous result
            Set<URI> uris = selectionUris;
            if (uris == null) {
                uris = engine.queryByType(jc.getClazz());
            }
            // Iterate through the URIs for result
            Iterator iter = engine.queryIterObject(jc.getClazz(), uris);
            jc.setCacheValid(true);
            while (iter.hasNext()) {
                DataObject dobj = (DataObject) iter.next();
                // If selections are specified, make sure they pass.
                if (testSelections(jc, dobj) == false) {
                    continue;
                }
                jc.addToCache(dobj);
                jc.getUris().add(dobj.getId());
            }
        } else {
            throw new JoinerException("Unrecognized join");
        }
    }

    /**
     * Join A to B where B's id is in A's joinToField
     * Here the assumption is that we have already computed the necessary A's,
     * either by previous joins or selections on A columns, as the normal case.
     * If the number of potential As is less than the number of potential Bs,
     * we will just find all the Bs in the As by iterating.
     * Otherwise if the number of potential Bs ls less than the number of potential As,
     * we use a containment constraint to find the A's containing each B and eliminate
     * any A's found that are not in the A result set.
     * 
     * @param jc
     */
    private void joinBid2A(JClass jc, Set<URI> selectionUris) {
        JClass joinToClass = lookupAlias(jc.joinToAlias);
        String joinToField = jc.getJoinToField();
        Set<URI> bURIs = selectionUris;
        if (bURIs == null) {
            bURIs = engine.queryByType(jc.getClazz());
        }
        jc.setCacheValid(true);

        // Determine if A's joinToField is indexed.
        boolean aIndexed = joinToClass.getMetaData().isIndexed(joinToField);

        if (!aIndexed || joinToClass.getUris().size() < bURIs.size()) {
            // Common case; A is already bounded smaller than B universe.
            // Therefore we iterate through the As and select only the corresponding Bs.
            Iterator aIter = joinToClass.iterator(engine);
            while (aIter.hasNext()) {
                DataObject object = (DataObject) aIter.next();
                Method method = getGettr(joinToClass, joinToField);
                if (method == null) {
                    throw new JoinerException("Cannot find gettr for join: " + jc.getField());
                }
                Object values = null;
                try {
                    values = method.invoke(object);
                } catch (Exception ex) {
                    log.warn("failed to invoke {} ", method.getName());
                }
                for (URI uri : bURIs) {
                    if (uriInObject(uri, values)) {
                        DataObject bobj = engine.queryObject(jc.getClazz(), uri);
                        if (testSelections(jc, bobj) == false) {
                            continue;
                        }
                        jc.addToJoinMap(object.getId(), uri);
                        jc.getUris().add(uri);
                        jc.addToCache(bobj);
                    }
                }
            }
        } else {
            // Uncommon case... A is bounded larger than the B universe.
            // So use a constraint query.
            for (URI bURI : bURIs) {
                Constraint constraint = joinToClass.getMetaData().buildConstraint(bURI, joinToClass.getClazz(), joinToField);
                Set<URI> aURIs = engine.queryByConstraint(constraint);
                for (URI aURI : aURIs) {
                    // If aURI is not in the result set of A, then skip it
                    if (!joinToClass.getUris().contains(aURI)) {
                        continue;
                    }
                    DataObject object = (DataObject) engine.queryObject(jc.getClazz(), bURI);
                    if (testSelections(jc, object) == false) {
                        continue;
                    }
                    jc.addToJoinMap(aURI, bURI);
                    jc.addToCache(object);
                    jc.getUris().add(bURI);
                }
            }
        }
    }

    /**
     * Join B to A where A's ID is in B's "field"
     * B's field must be indexed.
     * Here we find only the Bs that contain A in the specified field using
     * a containment constraint (i.e. the id of A is contained in B's field).
     * 
     * @param jc
     */
    private void joinAid2B(JClass jc, Set<URI> selectionUris) {
        JClass joinToClass = lookupAlias(jc.joinToAlias);
        Set<URI> joinToUris = joinToClass.getUris();
        boolean manualMatch = false;
        jc.setCacheValid(true);
        for (URI aURI : joinToUris) {
            Set<URI> bURIs = null;
            if (jc.getMetaData().isIndexed(jc.getField())) {
                Constraint constraint = jc.getMetaData().buildConstraint(aURI, jc.getClazz(), jc.getField());
                bURIs = engine.queryByConstraint(constraint);
            } else {
                log.info(String.format("Joiner suboptimal query %s.%s should be indexed to join to %s",
                        jc.getClazz().getSimpleName(), jc.getField(), joinToClass.getClazz().getSimpleName()));
                bURIs = engine.queryByType(jc.getClazz());
                manualMatch = true;
            }
            for (URI bURI : bURIs) {
                // Skip any objects that will not pass selection index
                if (selectionUris != null && !selectionUris.contains(bURI)) {
                    continue;
                }
                DataObject object = (DataObject) engine.queryObject(jc.getClazz(), bURI);

                // If the join field was not indexed, manually match A's UID against the B field.
                if (manualMatch) {
                    Method method = getGettr(jc, jc.getField());
                    if (method == null) {
                        throw new JoinerException("Cannot find gettr for join: " + jc.getField());
                    }
                    Object values = null;
                    try {
                        values = method.invoke(object);
                    } catch (Exception ex) {
                        log.warn("failed to invoke method {}", method.getName());
                    }
                    if (!uriInObject(aURI, values)) {
                        continue;
                    }
                }

                if (testSelections(jc, object) == false) {
                    continue;
                }
                jc.addToJoinMap(aURI, bURI);
                jc.addToCache(object);
                jc.getUris().add(bURI);
            }
        }
    }

    /**
     * Performs a join of each of the subclasses, followed by a union of the results.
     * 
     * @param superJc
     */
    private void joinSubClasses(JClass superJc) {
        Set<JClass> subJClasses = superJc.getSubJClasses();
        for (JClass subJc : subJClasses) {
            subJc.setJoinToAlias(superJc.getJoinToAlias());
            subJc.setJoinToField(superJc.getJoinToField());
            subJc.setField(superJc.getField());
            subJc.setMaxCacheSize(superJc.getMaxCacheSize());
            subJc.setSelections(superJc.getSelections());
            queryClass(subJc);

            // Add in the results.
            superJc.getUris().addAll(subJc.getUris());
            log.info("Processing subclass: " + subJc.getClazz().getSimpleName() + " count: " + subJc.getUris().size());
            subJc.getUris().clear();
            Map<URI, Set<URI>> subJoinMap = subJc.getJoinMap();
            for (Entry<URI, Set<URI>> entry : subJoinMap.entrySet()) {
                if (superJc.getJoinMap().get(entry.getKey()) == null) {
                    superJc.getJoinMap().put(entry.getKey(), new HashSet<URI>());
                }
                Map<URI, Set<URI>> superJoinMap = superJc.getJoinMap();
                superJoinMap.get(entry.getKey()).addAll(entry.getValue());
            }
            subJc.getJoinMap().clear();
            Map<URI, Object> subCachedObjects = subJc.getCachedObjects();
            superJc.getCachedObjects().putAll(subCachedObjects);
            subCachedObjects.clear();
        }
    }

    /**
     * Selections are logically AND-ed together. At least for now.
     * 
     * @param jc - JClass we are applying the selections to
     * @param dobj - an object of type T corresponding to JClass that we test
     *            to see if passes selection criteria
     * @return true if selected, false if not
     */
    private <T extends DataObject> boolean testSelections(JClass jc, T dobj) {
        List<JSelection> list = jc.getSelections();
        for (JSelection js : list) {
            if (testSelection(jc, js, dobj) == false) {
                return false;
            }
        }
        return true;
    }

    private <T extends DataObject> boolean testSelection(JClass jc, JSelection js, T dobj) {
        Method gettr = getGettr(jc, js.getField());
        if (gettr == null) {
            return false;
        }
        try {
            Object value = gettr.invoke(dobj);
            for (Object match : js.getValues()) {
                if (value instanceof StringSet) {
                    StringSet valueSet = (StringSet) value;
                    if (valueSet.contains(match.toString())) {
                        return true;
                    }
                } else if (value instanceof URI && match instanceof String) {
                    if (value.toString().equals(match)) {
                        return true;
                    }
                } else if (value instanceof String && match instanceof URI) {
                    if (match.toString().equals(value)) {
                        return true;
                    }
                } else if (match.equals(value)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            throw new JoinerException("Could not invoke gettr: " + js.getField());
        }
        return false;
    }

    private Method getGettr(JClass jc, String fieldName) {
        Class clazz = jc.getClazz();
        try {
            Method method = jc.getMetaData().getGettr(fieldName);
            return method;
        } catch (Exception ex) {
            throw new JoinerException("No getter for: " + clazz.getSimpleName() + " : " + fieldName);
        }
    }

    /**
     * Return true if the passed uri is contained in the Object
     * 
     * @param uri -- a URI
     * @param object -- StringSet, StringMap, StringSetMap, or just String
     * @return true if contained, false if not
     */
    private boolean uriInObject(URI uri, Object object) {
        if (NullColumnValueGetter.isNullURI(uri)) {
            return false;
        }
        if (uri == null) {
            return false;
        }
        if (object instanceof StringSet) {
            StringSet set = (StringSet) object;
            return (set.contains(uri.toString()));
        }
        if (object instanceof StringMap) {
            StringMap map = (StringMap) object;
            return (map.containsKey(uri.toString()));
        }
        if (object instanceof StringSetMap) {
            StringSetMap map = (StringSetMap) object;
            return (map.containsKey(uri.toString()));
        }
        return object.toString().equals(uri.toString());
    }

    void printTuples(URI uri, int indent, int startIndex) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < indent; i++) {
            buf.append(" ");
        }
        buf.append(uri);
        buf.append(" -> ");
        for (int i = startIndex; i < jClasses.size(); i++) {
            JClass jc = jClasses.get(i);
            Map<URI, Set<URI>> joinMap = jc.getJoinMap();
            if (joinMap.containsKey(uri)) {
                buf.append(jc.getJoinMap().get(uri).toString());
                log.info(buf.toString());
                Set<URI> children = joinMap.get(uri);
                for (URI child : children) {
                    printTuples(child, indent + 4, startIndex + 1);
                }
            }
        }
    }

    public Joiner printTuples(String alias) {
        int index = 0;
        JClass jc = null;
        int startIndex = 0;
        for (startIndex = 0; startIndex < jClasses.size(); startIndex++) {
            jc = jClasses.get(startIndex);
            if (jc.getAlias().equals(alias)) {
                break;
            }
        }
        if (!jc.getAlias().equals(alias)) {
            return this;
        }
        Set<URI> uris = jc.getUris();
        for (URI uri : uris) {
            printTuples(uri, 0, startIndex);
        }
        return this;
    }

    /**
     * Ands the first set with the second and returns the result
     * 
     * @param set1
     * @param set2
     * @return
     */
    private Set<URI> andUriSet(Set<URI> set1, Set<URI> set2) {
        HashSet<URI> resultSet = new HashSet<URI>();
        for (URI uri : set1) {
            if (set2.contains(uri)) {
                resultSet.add(uri);
            }
        }
        return resultSet;
    }

    /**
     * Lookup an alias in the alias map. Throw exception if not found.
     * 
     * @param alias
     * @return
     */
    JClass lookupAlias(String alias) {
        if (!aliasMap.containsKey(alias)) {
            throw new JoinerException("Cannot find alias: " + alias);
        }
        return aliasMap.get(alias);
    }
}

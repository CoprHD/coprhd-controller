/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.gc;

import java.beans.PropertyDescriptor;
import java.net.URI;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.DependencyTracker;
import com.emc.storageos.db.server.DbsvcTestBase;

/**
 *  garbage collector tests
 */
public class GarbageCollectorTests extends DbsvcTestBase {
    private static final Logger _log = LoggerFactory.getLogger(GarbageCollectorTests.class);
    private GarbageCollectionExecutor _gcExecutor;
    private DbClientImpl _dbClient = (DbClientImpl)getDbClient();
    private DataObjectScanner _scanner;

    private void initGCExecutor() {
        _scanner = new DataObjectScanner();
        _scanner.setPackages("com.emc.storageos.db.client.model");
        _scanner.init();

        _gcExecutor = new GarbageCollectionExecutor();
        _gcExecutor.setDataObjectScanner(_scanner);
   
        GarbageCollectionExecutorLoop gcExecutorLoop = new LocalGCExecutorLoop();
        gcExecutorLoop.setDbClient(_dbClient);
        gcExecutorLoop.setCoordinator(_dbClient.getCoordinatorClient());
        gcExecutorLoop.setGcDelayMins(0);
        
        _gcExecutor.setGcExecutor(gcExecutorLoop);
      
    }
    
    private void setFieldValue(DependencyTracker.Dependency dependency, DataObject obj, URI ref) throws Exception {
        PropertyDescriptor pd = dependency.getColumnField().getPropertyDescriptor();
        Assert.assertNotNull(pd);
        Object value = null;
        if (URI.class.isAssignableFrom(pd.getPropertyType())) {
            value = ref;
        } else if (NamedURI.class.isAssignableFrom(pd.getPropertyType())) {
            value = new NamedURI(ref, "reference");
        } else if (StringSet.class.isAssignableFrom(pd.getPropertyType())) {
            StringSet set = new StringSet();
            set.add(ref.toString());
            value = set;
        } else if (StringMap.class.isAssignableFrom(pd.getPropertyType())) {
            StringMap map = new StringMap();
            map.put(ref.toString(), "test");
            value = map;
        } else {
            throw new Exception(String.format("FIX IT: type: %s on %s", pd.getPropertyType().getSimpleName(),
                    dependency.getType().getSimpleName()));
        }
        pd.getWriteMethod().invoke(obj, value);
    }
    
    public class TypeURI {
        Class<? extends DataObject> _type;
        URI _uri;
        public  TypeURI(Class<? extends DataObject> t, URI u) {
            _type = t;
            _uri = u;
        }
    }
    
    private void createChildren(Class<? extends DataObject> clazz, DataObject obj, 
                                int level, Map<Integer, List<TypeURI>> childrenMap)
            throws Exception {
        if (level < 0) {
            return;
        }
        String labelSuffix = "checkGCOnLevel" + level;
        if (childrenMap.get(level) == null) {
            childrenMap.put(level, new ArrayList<TypeURI>());
        }
        List<TypeURI> children = new ArrayList<TypeURI>();
        List<DependencyTracker.Dependency> dependencies =
                _scanner.getDependencyTracker().getDependencies(clazz);
        for (DependencyTracker.Dependency dependency: dependencies) {
            if (_scanner.getDependencyTracker().getExcludedTypes().contains(dependency.getType())) {
                   continue;
            }
            if (dependency.getType().isAnnotationPresent(DbKeyspace.class)
                    && dependency.getType().getAnnotation(DbKeyspace.class).value()
                            .equals(Keyspaces.GLOBAL)) {
                continue;
            }
            DataObject depObj = dependency.getType().newInstance();
            depObj.setId(URIUtil.createId(dependency.getType()));
            depObj.setLabel(String.format("%s:%s", dependency.getType().getSimpleName(), labelSuffix));
            setFieldValue(dependency, depObj, obj.getId());
            createChildren(dependency.getType(), depObj, level-1, childrenMap);
            _dbClient.createObject(depObj);
            children.add(new TypeURI(dependency.getType(), depObj.getId()));
        }
        if (!children.isEmpty()) {
            childrenMap.get(level).addAll(children);
        }
    }

    private void checkGCOnLevelN(int level, Class<? extends DataObject> clazz) throws Exception {
        _log.info("Level {}: type: {}", level, clazz.getSimpleName());
        String levelSuffix = "checkGCOnLevel" + level;
        DataObject obj = clazz.newInstance();
        obj.setId(URIUtil.createId(clazz));
        obj.setLabel(String.format("%s:%s", clazz.getSimpleName(), levelSuffix));
        
        if (level > 0) {
            // first, create a full chain, all the way to the root
            Map<Integer, List<TypeURI>> allChildren = new HashMap<Integer, List<TypeURI>>();
            createChildren(clazz, obj, level, allChildren);
            obj.setInactive(true);
            _dbClient.createObject(obj);
            _gcExecutor.runNow();
            DataObject read = _dbClient.queryObject(clazz, obj.getId());
            Assert.assertNotNull(read);
            Assert.assertTrue(read.getInactive());
            // start with deleting lowest first
            for (int i = 0; i <= level; i++) {
                List<TypeURI> children =  allChildren.get(i);
                if (!children.isEmpty()) {
                    for (TypeURI child: children) {
                        read = _dbClient.queryObject(child._type, child._uri);
                        read.setInactive(true);
                        _dbClient.updateAndReindexObject(read);
                        _log.info("marking inactive : {}", child._uri);
                    }                  
                    _gcExecutor.runNow();
                    for (TypeURI child: children) {
                        read = _dbClient.queryObject(child._type, child._uri);
                        Assert.assertNull(read);
                    }
                    if ( i != level) {
                        read = _dbClient.queryObject(clazz, obj.getId());
                        Assert.assertNotNull(read);
                    }
                }
            }
            // last level should have deleted the main object also
        } else {
            // no dependents
            _dbClient.updateAndReindexObject(obj);
            _gcExecutor.runNow();
            DataObject read = _dbClient.queryObject(clazz, obj.getId());
            read.setInactive(true);
            _dbClient.updateAndReindexObject(read);
             _gcExecutor.runNow();
        }
        
        DataObject read = _dbClient.queryObject(clazz, obj.getId());
        if (read != null) {
            String dep = new DependencyChecker(_dbClient, _scanner.getDependencyTracker()).checkDependencies(read.getId(), read.getClass(), false);
            Assert.fail(String.format("Object with id %s is not recycled because of dependency: %s", read.getId().toString(), dep));
        }

    }

    @Test
    public void testAllLevels() throws Exception {
        initGCExecutor();
        int all = _scanner.getDependencyTracker().getLevels();
        for (int i = 0; i < all; i++) {         
            _log.info("testing level {}", i);
            List<Class<? extends DataObject>> types = _scanner.getDependencyTracker().getTypesInLevel(i);
            for (Class<? extends DataObject> type :  types) {
                if (!type.isAnnotationPresent(DbKeyspace.class)
                        || type.getAnnotation(DbKeyspace.class).value()
                                .equals(Keyspaces.LOCAL)) {
                    checkGCOnLevelN(i, type);
                }
            }
        }
        // make sure everything we created is deleted at this point
        URIQueryResultList list = new URIQueryResultList();
        _dbClient.queryByConstraint(
                DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                        TenantOrg.class, 0), list);
        List<URI> gotUris = new ArrayList<URI>();
        for(Iterator<URI> iterator = list.iterator(); iterator.hasNext(); ) {
            gotUris.add(iterator.next());
        }
        Assert.assertTrue(gotUris.isEmpty());
    }

    @Test
    public void testDependencyChecker() throws Exception {
        int num_projects  = 10;
        int num_fs = 110;
        
        initGCExecutor();
        DependencyChecker checker = new DependencyChecker(_dbClient, _scanner);
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("GOLD");
        vpool.setType("file");
        vpool.setProtocols(new StringSet());
        _dbClient.createObject(vpool);

        List<URI> activeProjects = new ArrayList<URI>();
        for(int i =0; i < num_projects; i++) {
            Project p = new Project();
            p.setId(URIUtil.createId(Project.class));
            p.setLabel("dependency test project");
            _dbClient.createObject(p);
            activeProjects.add(p.getId());
        }

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));

        Map<URI, List<URI>> activeFileSystems = new HashMap<URI, List<URI>>();
        for (int i = 0; i < num_fs; i++) {
            String label = "fileshare dependency tests " + i;
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            fs.setLabel(label);
            fs.setCapacity(102400L);
            fs.setVirtualPool(vpool.getId());
            SMBShareMap shareMap = new SMBShareMap();
            shareMap.put("test", new SMBFileShare("blah", "blah", "blah", "blah", 1));
            fs.setSMBFileShares(shareMap);

            URI proj = activeProjects.get(new Random().nextInt(activeProjects.size()));
            fs.setProject(new NamedURI(proj, label));
            _dbClient.createObject(fs);
            if (!activeFileSystems.containsKey(proj)) {
                activeFileSystems.put(proj, new ArrayList<URI>());    
            }
            activeFileSystems.get(fs.getProject().getURI()).add(fs.getId());
        }

        Assert.assertNotNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));     
        for (int i =0; i < num_projects; i++) {
            if (activeFileSystems.containsKey(activeProjects.get(i))) {
                Assert.assertNotNull(checker.checkDependencies(activeProjects.get(i), Project.class, true));
            } else {
                Assert.assertNull(checker.checkDependencies(activeProjects.get(i), Project.class, true));
            }
        }
        
        List<URI> inactiveRefProjects = new ArrayList<URI>();
        List<URI> lastRefProjects = new ArrayList<URI>();
        for (Map.Entry<URI, List<URI>> entry: activeFileSystems.entrySet()) {
            if (entry.getValue().size() % 3 == 0) {
                // inactive all refs   
                for (URI uri: entry.getValue()) {
                    FileShare fs = new FileShare();
                    fs.setId(uri);
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                }
                inactiveRefProjects.add(entry.getKey());
            } else if (entry.getValue().size() % 3 == 2) {
                // inactive all but last ref
                for (int i = 0; i < entry.getValue().size()-1; i++) {
                    FileShare fs = new FileShare();
                    fs.setId(entry.getValue().get(i));
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                }
                lastRefProjects.add(entry.getKey());
            }  else {
                continue;
            }
        }

        Assert.assertNotNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        for (int i =0; i < num_projects; i++) {
            URI p = activeProjects.get(i);
            if (inactiveRefProjects.contains(p)) {
                Assert.assertNull(checker.checkDependencies(p, Project.class, true));
            } else {
                Assert.assertNotNull(checker.checkDependencies(p, Project.class, true));
                if (lastRefProjects.contains(p)) {
                    URI lastFs = activeFileSystems.get(p).get(activeFileSystems.get(p).size()-1);
                    FileShare fs = new FileShare();
                    fs.setId(lastFs);
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                } else {
                    for (URI uri: activeFileSystems.get(p)) {
                        FileShare fs = new FileShare();
                        fs.setId(uri);
                        fs.setInactive(true);
                        _dbClient.updateAndReindexObject(fs);
                    }
                }
            } 
        }

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));        
        for (int i =0; i < num_projects; i++) {
            Assert.assertNull(checker.checkDependencies(activeProjects.get(i), Project.class, true));
            Project p = new Project();
            p.setId(activeProjects.get(i));
            p.setInactive(true);
            _dbClient.updateAndReindexObject(p);
        }

        _gcExecutor.runNow();
        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, false));

        vpool.setInactive(true);
        _dbClient.updateAndReindexObject(vpool);
        _gcExecutor.runNow();
        Assert.assertNull(_dbClient.queryObject(VirtualPool.class, vpool.getId()));
    }
    
}

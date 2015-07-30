/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.model.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Class for building/keeping all the dependency information for different DataObjects in DB
 */
public class DependencyTracker {
    private static final Logger log = LoggerFactory.getLogger(DependencyTracker.class);

    public class Dependency {
        private Class<? extends DataObject> _type;
        private ColumnField _field;

        public Dependency(Class<? extends DataObject> type, ColumnField field) {
            _type = type;
            _field = field;
        }

        public Class<? extends DataObject> getType() {
            return _type;
        }

        public ColumnField getColumnField() {
            return _field;
        }
    }

    private Map<Class<? extends DataObject>, List<Dependency>> _dependencyMap;
    private List<Class<? extends DataObject>> _excluded;
    private Map<Integer, List<Class<? extends DataObject>>> _levels;

    public DependencyTracker() {
        _dependencyMap = new HashMap<Class<? extends DataObject>, List<Dependency>>();
        _excluded = new ArrayList<Class<? extends DataObject>>();
    }

    public void addDependency(Class<? extends DataObject> refType, Class<? extends DataObject> onType, ColumnField field) {
        if (_excluded.contains(refType)) {
            return;
        }
        if (!_dependencyMap.containsKey(refType)) {
            _dependencyMap.put(refType, new ArrayList<Dependency>());
        }
        _dependencyMap.get(refType).add(new Dependency(onType, field));
    }

    public void includeClass(Class<? extends DataObject> refType) {
        if (!_dependencyMap.containsKey(refType)) {
            _dependencyMap.put(refType, new ArrayList<Dependency>());
        }
    }

    public void excludeClass(Class<? extends DataObject> refType) {
        if (_dependencyMap.containsKey(refType)) {
            _dependencyMap.remove(refType);
        }
        _excluded.add(refType);
    }

    public List<Class<? extends DataObject>> getExcludedTypes() {
        return Collections.unmodifiableList(_excluded);
    }

    public List<Dependency> getDependencies(Class<? extends DataObject> clazz) {
        return _dependencyMap.containsKey(clazz) ?
                Collections.unmodifiableList(_dependencyMap.get(clazz)) :
                new ArrayList<Dependency>();
    }

    public void buildDependencyLevels() {
        _levels = new HashMap<Integer, List<Class<? extends DataObject>>>();
        Integer level = 0;
        List<Class<? extends DataObject>> visited = new ArrayList();
        Set<Class<? extends DataObject>> all = new HashSet(_dependencyMap.keySet());

        while (all.size() != visited.size()) {
            _levels.put(level, new ArrayList<Class<? extends DataObject>>());
            for (Class<? extends DataObject> entry : all) {

                if (visited.contains(entry))
                {
                    continue; // already handled
                }

                List<Dependency> dependencies = _dependencyMap.get(entry);

                // first level - leaf nodes, should not have dependents
                if (level == 0 && !dependencies.isEmpty()) {
                    continue;
                }

                boolean addDependency = true;
                for (Dependency dependency : dependencies) {
                    if (visited.contains(dependency.getType()) &&
                            !_levels.get(level).contains(dependency.getType())) {
                        // visited, but, not on the same level - satisfied dependency
                        continue;
                    } else if (dependency.getType().equals(entry) ||
                            _excluded.contains(dependency.getType())) {
                        // special cases - satisfied dependency
                        // 1. self reference - only true for TenantOrg for now,
                        // since the root tenant is never deleted, it can go on one level
                        // 2. reference to excluded type
                        continue;
                    } else {
                        // else - missing dependency, I am not on this level
                        addDependency = false;
                        break;
                    }
                }
                // we are here, check if we missed any dependencies
                if (addDependency) {
                    _levels.get(level).add(entry);
                    visited.add(entry);
                }
            }
            assert (!_levels.get(level).isEmpty());
            level++;
        }
    }

    public int getLevels() {
        return _levels.keySet().size();
    }

    public List<Class<? extends DataObject>> getTypesInLevel(int level) {
        return (_levels.containsKey(level)) ?
                Collections.unmodifiableList(_levels.get(level)) :
                new ArrayList<Class<? extends DataObject>>();
    }

    @Override
    public String toString() {
        Iterator<Map.Entry<Class<? extends DataObject>, List<Dependency>>> iterator =
                _dependencyMap.entrySet().iterator();
        StringBuilder str = new StringBuilder();
        str.append("\n");
        while (iterator.hasNext()) {
            Map.Entry<Class<? extends DataObject>, List<Dependency>> entry = iterator.next();
            str.append(entry.getKey().getSimpleName() + " depends on: ");
            if (entry.getValue().isEmpty()) {
                str.append("None");
            } else {
                for (Iterator<Dependency> it2 = entry.getValue().iterator(); it2.hasNext();) {
                    Dependency dependency = it2.next();
                    str.append("\n\t" + dependency.getType().getSimpleName() + ":" + dependency.getColumnField().getName());
                }
            }
            str.append("\n");
        }
        str.append("\n");
        Iterator<Map.Entry<Integer, List<Class<? extends DataObject>>>> iterator2 =
                _levels.entrySet().iterator();
        while (iterator2.hasNext()) {
            Map.Entry<Integer, List<Class<? extends DataObject>>> levelEntry = iterator2.next();
            str.append("Level " + levelEntry.getKey().toString());
            str.append("\n");
            for (Iterator<Class<? extends DataObject>> clazzIt = levelEntry.getValue().iterator(); clazzIt.hasNext();) {
                str.append("\n\t").append(clazzIt.next().getSimpleName());
            }
            str.append("\n\n");
        }
        return str.toString();
    }
}

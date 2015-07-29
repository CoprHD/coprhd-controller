/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.model.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class provides method for checking dependencies
 * used by GC and apisvc decommission api
 */
public class DependencyChecker {
    private static final Logger _log = LoggerFactory.getLogger(DependencyChecker.class);
    final private DependencyTracker _dependencyTracker;
    final private DbClient _dbClient;

    /**
     * Constructor takes db client and DependencyTracker
     * 
     * @param dbClient
     * @param dependencyTracker
     */
    public DependencyChecker(DbClient dbClient, DependencyTracker dependencyTracker) {
        _dbClient = dbClient;
        _dependencyTracker = dependencyTracker;
    }

    /**
     * Constructor takes db client and DataObjectScanner
     * 
     * @param dbClient
     * @param dataObjectScanner
     */
    public DependencyChecker(DbClient dbClient, DataObjectScanner dataObjectScanner) {
        _dbClient = dbClient;
        _dependencyTracker = dataObjectScanner.getDependencyTracker();
    }

    /**
     * checks to see if any references exist for this uri
     * uses dependency list created from relational indices
     * 
     * @param uri id of the DataObject
     * @param type DataObject class name
     * @param onlyActive if true, checks for active references only (expensive)
     * @return null if no references exist on this uri, return the type of the dependency if exist
     */
    public String checkDependencies(URI uri, Class<? extends DataObject> type, boolean onlyActive) {
        return checkDependencies(uri, type, onlyActive, null);
    }

    /**
     * checks to see if any references exist for this uri
     * uses dependency list created from relational indices
     * 
     * @param uri id of the DataObject
     * @param type DataObject class name
     * @param onlyActive if true, checks for active references only (expensive)
     * @param excludeTypes optional list of classes that can be excluded as dependency
     * @return null if no references exist on this uri, return the type of the dependency if exist
     */
    public String checkDependencies(URI uri, Class<? extends DataObject> type, boolean onlyActive,
            List<Class<? extends DataObject>> excludeTypes) {
        List<DependencyTracker.Dependency> dependencies = _dependencyTracker.getDependencies(type);
        // no dependencies - nothing to do
        if (dependencies.isEmpty()) {
            return null;
        }

        for (DependencyTracker.Dependency dependency : dependencies) {
            if (excludeTypes != null) {
                if (excludeTypes.contains(dependency.getType())) {
                    continue;
                }
            }
            // Query relational index to see if any dependents exist
            ContainmentConstraint constraint =
                    new ContainmentConstraintImpl(uri, dependency.getType(), dependency.getColumnField());
            URIQueryResultList list = new URIQueryResultList();
            _dbClient.queryByConstraint(constraint, list);
            if (list.iterator().hasNext()) {
                if (!onlyActive || checkIfAnyActive(list, dependency.getType())) {
                    _log.info("{}: active references of type {} found",
                            uri.toString(), dependency.getType().getSimpleName());
                    return dependency.getType().getSimpleName();
                }
            }
        }
        return null;
    }

    /**
     * Checks if any of the uris from the list are active
     * 
     * @param uris
     * @param type
     * @return true if active uri found, false otherwise
     */
    public boolean checkIfAnyActive(URIQueryResultList uris, Class<? extends DataObject> type) {
        Iterator<URI> uriIterator = uris.iterator();
        while (uriIterator.hasNext()) {
            int added = 0, found = 0;
            List<URI> urisToQuery = new ArrayList<URI>();
            for (int i = 0; (i < 100) && uriIterator.hasNext(); i++) {
                urisToQuery.add(uriIterator.next());
                added++;
            }
            List<? extends DataObject> results = _dbClient.queryObjectField(type, "inactive", urisToQuery);
            for (DataObject obj : results) {
                found++;
                if (!obj.getInactive()) {
                    return true;
                }
            }
            if (found != added) {
                return true;
            }
        }
        return false;
    }
}

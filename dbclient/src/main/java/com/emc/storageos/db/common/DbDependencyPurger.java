/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.common;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 *  Class provides method for checking dependencies
 *  used by controller and api svc
 */
public class DbDependencyPurger {

    private static final Logger _log = LoggerFactory.getLogger(DbDependencyPurger.class);
    final private DependencyTracker _dependencyTracker;
    final private DbClient _dbClient;
    final static int MAX_PERSISTENCE_LIMIT = 1000;

    /**
     * Constructor takes db client and DependencyTracker
     * @param dbClient
     * @param dependencyTracker
     */
    public DbDependencyPurger(DbClient dbClient, DependencyTracker dependencyTracker) {
        _dbClient = dbClient;
        _dependencyTracker = dependencyTracker;
    }

    /**
     * Constructor takes db client and DataObjectScanner
     * @param dbClient
     * @param dataObjectScanner
     */
    public DbDependencyPurger(DbClient dbClient, DataObjectScanner dataObjectScanner) {
        _dbClient = dbClient;
        _dependencyTracker = dataObjectScanner.getDependencyTracker();
    }

    /**
     * checks to see if any references exist for this uri
     * uses dependency list created from relational indices
     * @param id id of the DataObject
     * @param type DataObject class name
     * @return true if dependents found, false otherwise
     */
    public <T extends DataObject> void purge (URI id, Class<T>  type) throws DatabaseException {

        T dataObj = _dbClient.queryObject(type,id);
        if (dataObj != null) {
            if( !dataObj.getInactive() ) {
                dataObj.setInactive(true);
                _dbClient.persistObject(dataObj);
            }
            Set<URI> cleared = new HashSet<URI>();
            purge(dataObj, cleared);

             _log.info("Deactivated db_object: type = {}, id = {}", type.toString(), id );
        }
    }

    private <T extends DataObject>  void purge(T dataObj, Set<URI> visited ) throws DatabaseException {

        if(dataObj == null || visited.contains(dataObj.getId())){
            return;
        }

        visited.add(dataObj.getId());

        Class<? extends DataObject> type = dataObj.getClass();

        List<DependencyTracker.Dependency> dependencyList = _dependencyTracker.getDependencies(type);

        try {
            for( DependencyTracker.Dependency dependence : dependencyList) {
                // Query relational index to see if any dependents exist
                Class<? extends DataObject> childType = dependence.getType();
                ColumnField childField = dependence.getColumnField();

                ContainmentConstraint constraint = new ContainmentConstraintImpl(dataObj.getId(),childType, childField);
                URIQueryResultList list = new URIQueryResultList();
                _dbClient.queryByConstraint(constraint, list);
                if (!list.iterator().hasNext())
                    continue;

                HashSet<URI> childSet = new HashSet(); // used to remove efficiently identical URIs from the list.
                while(list.iterator().hasNext()) {
                    childSet.clear();
                    while(list.iterator().hasNext()) {
                        childSet.add(list.iterator().next());

                        if(childSet.size() >= MAX_PERSISTENCE_LIMIT) {
                            break;
                        }
                    }

                    List<URI> childUriList = new ArrayList(childSet);

                    List<? extends DataObject> children =  _dbClient.queryObjectField(childType,childField.getName(),childUriList);
                    List<DataObject>  decommissionedChildren = new ArrayList();
                    for (DataObject childObj : children) {
                        switch (childField.getType()) {
                            case TrackingSet:
                            case TrackingMap:
                                // assume @indexByKey is set

                                java.beans.PropertyDescriptor pd = childField.getPropertyDescriptor();
                                Object fieldValue = pd.getReadMethod().invoke(childObj);
                                boolean deactivateChildObj = false;
                                if (fieldValue != null) { // should be always true.
                                    if (AbstractChangeTrackingMap.class.isAssignableFrom(pd.getPropertyType()))  {
                                        AbstractChangeTrackingMap<?> trackingMap = (AbstractChangeTrackingMap<?>)fieldValue;
                                        trackingMap.remove(dataObj.getId().toString());
                                        if(trackingMap.isEmpty() && childField.deactivateIfEmpty()) {
                                            deactivateChildObj = true;
                                        }
                                    } else if (AbstractChangeTrackingSet.class.isAssignableFrom(pd.getPropertyType())) {
                                        AbstractChangeTrackingSet trackingSet = (AbstractChangeTrackingSet)fieldValue;
                                        trackingSet.remove(dataObj.getId().toString());
                                        if(trackingSet.isEmpty() && childField.deactivateIfEmpty()) {
                                            deactivateChildObj = true;
                                        }
                                    }
                                    if (deactivateChildObj) {
                                        purge(childObj,visited);
                                        childObj.setInactive(true);
                                        _log.info("Deactivated db_object: type = {}, id = {}", childType.toString(), childObj.getId() );
                                    }
                                }
                                break;
                            default:   {
                                purge(childObj,visited);

                                childObj.setInactive(true);
                                _log.info("Deactivated db_object: type = {}, id = {}", childType.toString(), childObj.getId() );
                            }
                        }
                        decommissionedChildren.add(childObj);
                    }
                    _dbClient.persistObject(decommissionedChildren);
                }
            }
        }
        //should never get here....
        catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
            _log.error("Unexpected purge error", e);
            throw DatabaseException.fatals.purgeFailed(e);
        }
    }
}

/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Iterator;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ResourceLimitCheckUtils {
    private final static Logger log = LoggerFactory.getLogger(ResourceLimitCheckUtils.class);
    
    public static String PROJECT_RESOURCE_LIMIT_VOLUMES = "max_num_of_volumes_per_project";
    public static String PROJECT_RESOURCE_LIMIT_SNAPSHOTS = "max_num_of_snapshots_per_project";
    
    private static String PROJECT = "project";
    
    public static int USE_RATE_WARNING = 90;
    public static int USE_RATE_ERROR = 100;
    
    public static void validateVolumeLimitPerProject(Project project, DbClient dbClient, CoordinatorClient coordinator) {
        boolean underLimit = checkLimits(Volume.class, PROJECT, project.getId(), PROJECT_RESOURCE_LIMIT_VOLUMES, dbClient, coordinator);
        if (!underLimit){
            throw APIException.badRequests.reachVolumeLimitPerProject(project.getLabel());
        }
    }
    
    public static void validateBlockSnapshotLimitPerProject(URI projectId, DbClient dbClient, CoordinatorClient coordinator) {
        boolean underLimit = checkLimits(BlockSnapshot.class, PROJECT, projectId, PROJECT_RESOURCE_LIMIT_SNAPSHOTS, dbClient, coordinator);
        if (!underLimit){
            throw APIException.badRequests.reachSnapshotLimitPerProject(projectId.toString());
        }
    }
    
    protected static <T extends DataObject> boolean checkLimits(Class<T> clazz, 
                                                                 String fieldName, 
                                                                 URI groupById,
                                                                 String limitName,
                                                                 DbClient dbClient, 
                                                                 CoordinatorClient coordinator) {

        
        long count = getNumberOfResources(clazz, fieldName, groupById, dbClient);
        long limit = getResourceLimit(coordinator, limitName);
        log.info(String.format("Check %s limit for %s: number of objects %d, predefined limit %d", clazz.getSimpleName(), groupById, count, limit));
        int useRate = (int) (count * 100 / limit);
        if (useRate > USE_RATE_ERROR) {
            log.error(String.format("Reach or exceed limit %s - number of objects %d, predefined limit %d", clazz.getSimpleName(), count, limit));
            return false;
        } else if (useRate > USE_RATE_WARNING){
            log.warn(String.format("Approach limit %s - number of objects %d, predefined limit %d", clazz.getSimpleName(), count, limit));
        }
        return true;
    }
    
    protected static <T extends DataObject> int getNumberOfResources(Class<T> clazz, 
                                                                      String fieldName, 
                                                                      URI groupById,
                                                                      DbClient dbClient) {
        DataObjectType doType = TypeMap.getDoType(clazz);
        ColumnField field = doType.getColumnField(fieldName);
        Constraint constraint = new ContainmentConstraintImpl(groupById, clazz, field);
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(constraint, result);
        Iterator<URI> iter = result.iterator();
        int cnt = 0;
        while(iter.hasNext()) {
            cnt ++;
            iter.next();
        }
        return cnt;
    }
    
    protected static long getResourceLimit(CoordinatorClient coordinator, String name) {
        PropertyInfo sysprops = coordinator.getPropertyInfo();
        String value = sysprops.getProperty(name);
        return NumberUtils.toLong(value);
    }
}

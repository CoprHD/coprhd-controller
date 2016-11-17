/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class LimitCheckUtils {
    private final static Logger log = LoggerFactory.getLogger(LimitCheckUtils.class);
    
    public static String LIMIT_NUM_VOLUMES_PER_PROJECT = "max_num_of_volumes_per_project";
    public static int USE_RATE_WARNING = 90;
    public static int USE_RATE_ERROR = 100;
    
    public static void validateVolumeLimitPerProject(Project project, DbClient dbClient, CoordinatorClient coordinator) {
        boolean underLimit = checkLimits(Volume.class, "provisionedCapacity", "project", 
                            project.getId().toString(), LIMIT_NUM_VOLUMES_PER_PROJECT, dbClient, coordinator);
        if (!underLimit){
            throw APIException.badRequests.reachVolumeLimitPerProject(project.getLabel());
        }
    }
    
    protected static <T extends DataObject> boolean checkLimits(Class<T> clazz, 
                                                         String field, 
                                                         String groupbyField, 
                                                         String groupbyValue,
                                                         String limitName,
                                                         DbClient dbClient, 
                                                         CoordinatorClient coordinator) {
        long count = CustomQueryUtility.aggregatedPrimitiveField(dbClient, clazz, groupbyField, groupbyValue, field).getCount();
        long limit = getLimitByName(coordinator, limitName);
        log.info(String.format("Check %s limit for %s: number of objects %d, predefined limit %d", clazz.getSimpleName(), groupbyValue, count, limit));
        int useRate = (int) (count * 100 / limit);
        if (useRate > USE_RATE_ERROR) {
            log.error(String.format("Reach or exceed limit %s - number of objects %d, predefined limit %d", clazz.getSimpleName(), count, limit));
            return false;
        } else if (useRate > USE_RATE_WARNING){
            log.warn(String.format("Approach limit %s - number of objects %d, predefined limit %d", clazz.getSimpleName(), count, limit));
        }
        return true;
    }
    
    protected static long getLimitByName(CoordinatorClient coordinator, String name) {
        PropertyInfo sysprops = coordinator.getPropertyInfo();
        String value = sysprops.getProperty(name);
        return NumberUtils.toLong(value);
    }
}

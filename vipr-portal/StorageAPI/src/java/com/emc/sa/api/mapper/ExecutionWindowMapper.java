/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.vipr.client.core.util.ResourceUtils.asString;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.ExecutionWindowCommonParam;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.google.common.base.Function;

public class ExecutionWindowMapper implements Function<ExecutionWindow,ExecutionWindowRestRep>{
    
    public static final ExecutionWindowMapper instance = new ExecutionWindowMapper();
    
    public static ExecutionWindowMapper getInstance() {
        return instance;
    }
    
    private ExecutionWindowMapper() {
    }

    public static ExecutionWindowRestRep map(ExecutionWindow from) {
        if (from == null) {
            return null;
        }
        ExecutionWindowRestRep to = new ExecutionWindowRestRep();
        mapDataObjectFields(from, to);
        
        to.setDayOfMonth(from.getDayOfMonth());
        to.setDayOfWeek(from.getDayOfWeek());
        to.setExecutionWindowLength(from.getExecutionWindowLength());
        to.setExecutionWindowLengthType(from.getExecutionWindowLengthType());
        to.setExecutionWindowType(from.getExecutionWindowType());
        to.setHourOfDayInUTC(from.getHourOfDayInUTC());
        to.setLastDayOfMonth(from.getLastDayOfMonth());
        to.setMinuteOfHourInUTC(from.getMinuteOfHourInUTC());
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));

        return to;
    }
    
    public ExecutionWindowRestRep apply(ExecutionWindow resource) {
        return map(resource);
    }
    
    public static ExecutionWindow createNewObject(ExecutionWindowCreateParam param) {
        ExecutionWindow newObject = new ExecutionWindow();
        newObject.setTenant(asString(param.getTenant()));
        
        updateObject(newObject, param);
        
        return newObject;
    }        
    
    public static void updateObject(ExecutionWindow object, ExecutionWindowCommonParam param) {
        if (param.getLastDayOfMonth() != null) {
            object.setLastDayOfMonth(param.getLastDayOfMonth());
        }
        if (param.getDayOfMonth() != null) {
            object.setDayOfMonth(param.getDayOfMonth());
        }
        if (param.getDayOfWeek() != null) {
            object.setDayOfWeek(param.getDayOfWeek());
        }
        if (param.getExecutionWindowLength() != null) {
            object.setExecutionWindowLength(param.getExecutionWindowLength());
        }
        if (param.getExecutionWindowLengthType() != null) {
            object.setExecutionWindowLengthType(param.getExecutionWindowLengthType());
        }
        if (param.getExecutionWindowType() != null) {
            object.setExecutionWindowType(param.getExecutionWindowType());
        }
        if (param.getHourOfDayInUTC() != null) {
            object.setHourOfDayInUTC(param.getHourOfDayInUTC());
        }
        if (param.getMinuteOfHourInUTC() != null) {
            object.setMinuteOfHourInUTC(param.getMinuteOfHourInUTC());
        }
        if (param.getName() != null) {
            object.setLabel(param.getName());
        }
    }
    
    public static ExecutionWindow writeToTempWindow(ExecutionWindowCommonParam param) {
        ExecutionWindow newObject = new ExecutionWindow();
        updateObject(newObject, param);
        
        return newObject;
    }
    
}

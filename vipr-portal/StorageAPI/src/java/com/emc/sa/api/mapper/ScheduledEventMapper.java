/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.emc.sa.util.TextUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.*;
import com.google.common.collect.Lists;

public class ScheduledEventMapper {

    public static ScheduledEventRestRep map(ScheduledEvent from) {
        if (from == null) {
            return null;
        }
        ScheduledEventRestRep to = new ScheduledEventRestRep();
        mapDataObjectFields(from, to);

        if (from.getCatalogServiceId() != null) {
            to.setCatalogService(toRelatedResource(ResourceTypeEnum.CATALOG_SERVICE, from.getCatalogServiceId()));
        }
        if (from.getExecutionWindowId() != null) {
            to.setExecutionWindow(toRelatedResource(ResourceTypeEnum.EXECUTION_WINDOW, from.getExecutionWindowId().getURI()));
        }

        to.setLatestOrderId(from.getLatestOrderId());
        to.setScheduleInfo(from.getScheduleInfo());
        to.setEventStatus(from.getEventStatus().toString());

        return to;
    }

    public static ScheduledEvent createNewObject(URI tenantId, URI scheduledEventId, ScheduledEventCreateParam param) {
        ScheduledEvent newObject = new ScheduledEvent();
        newObject.setId(scheduledEventId);
        newObject.setTenant(tenantId.toString());
        newObject.setCatalogServiceId(param.getOrderCreateParam().getCatalogService());

        newObject.setEventType(param.getScheduleInfo().getReoccurrence() == 1 ? ScheduledEventType.ONCE : ScheduledEventType.REOCCURRENCE);
        newObject.setScheduleInfo(param.getScheduleInfo());
        newObject.setEventStatus(ScheduledEventStatus.PENDING);

        return newObject;
    }

    public static void updateObject(Order object, OrderCommonParam param) {

    }


}

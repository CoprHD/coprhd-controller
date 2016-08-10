/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.nio.charset.Charset;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledEventMapper {
    private static final Logger log = LoggerFactory.getLogger(ScheduledEventMapper.class);
    private static Charset UTF_8 = Charset.forName("UTF-8");

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
        to.setEventStatus(from.getEventStatus().toString());
        try {
            to.setScheduleInfo(ScheduleInfo.deserialize(Base64.decodeBase64(from.getScheduleInfo().getBytes(UTF_8))));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        
        try {
            OrderCreateParam orderCreateParam = OrderCreateParam.deserialize(Base64.decodeBase64(from.getOrderCreationParam().getBytes(UTF_8)));
            to.setOrderCreateParam(orderCreateParam);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return to;
    }

}

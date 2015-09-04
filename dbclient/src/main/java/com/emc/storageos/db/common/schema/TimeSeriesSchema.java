/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common.schema;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.TimeSeries;

public class TimeSeriesSchema extends DbSchema {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesSchema.class);

    private String serializerType;

    public TimeSeriesSchema() {
    }

    public TimeSeriesSchema(Class<? extends TimeSeries> clazz) {
        super(clazz);
        try {
            serializerType = clazz.newInstance().getSerializer().getClass().getName();
        } catch (Exception e) {
            log.error("Failed to get serializer type:", e);
        }
    }

    public void setSerializerType(String serializerType) {
        this.serializerType = serializerType;
    }

    @XmlElement(name = "serializer_type")
    public String getSerializerType() {
        return serializerType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TimeSeriesSchema))
            return false;

        TimeSeriesSchema schema = (TimeSeriesSchema) o;

        if (!schema.serializerType.equals(serializerType))
            return false;

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), serializerType);
    }
}

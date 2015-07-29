/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.adapters;

import java.util.Calendar;
import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * An XML Calendar marshaler based on JAXB API
 */
public class CalendarAdapter extends XmlAdapter<Long, Calendar> {

    @Override
    public Long marshal(Calendar v) throws Exception {
        return v.getTimeInMillis();
    }

    @Override
    public Calendar unmarshal(Long v) throws Exception {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(v);
        return c;
    }
}

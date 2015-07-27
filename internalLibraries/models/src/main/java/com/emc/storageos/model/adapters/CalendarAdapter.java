/*
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

package com.emc.storageos.model.adapters;

import java.util.Calendar;
import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 *  An XML Calendar marshaler based on JAXB API
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

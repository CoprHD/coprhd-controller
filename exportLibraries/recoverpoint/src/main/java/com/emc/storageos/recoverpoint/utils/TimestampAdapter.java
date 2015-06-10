/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.utils;

import java.sql.Timestamp;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * XML Adapter to be able to show Timestamp objects in XML feeds.
 * 
 */
public class TimestampAdapter extends XmlAdapter<String, Timestamp> {
	@Override
	public String marshal(Timestamp v) {
		return "" + v.getTime();
	}
	@Override
	public Timestamp unmarshal(String v) {
		return new Timestamp((new Long(v)).longValue());
	}
}

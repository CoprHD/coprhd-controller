/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
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

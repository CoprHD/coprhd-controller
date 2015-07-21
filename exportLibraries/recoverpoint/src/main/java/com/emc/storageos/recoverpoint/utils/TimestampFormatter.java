/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.utils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Timestamp formatter helper class
 * 
 */
public class TimestampFormatter {

	/**
	 * Timestamp -> String
	 * @param v timestamp object
	 * @return String representing the readable time/date
	 */
	public static String toString(Timestamp v) {
		if (v!=null) {
			return  new SimpleDateFormat("EEE MM/dd/yyyy hh:mm:ss a").format(v);
		}
		return null;
	}
	
	/**
	 * Calendar -> String
	 * @param v calendar object
	 * @return String representing the readable time/date
	 */
	public static String toString(Calendar v) {
		if (v!=null) {
			return  new SimpleDateFormat("EEE MM/dd/yyyy hh:mm:ss a").format(v.getTime());
		}
		return null;
	}
	
	/**
	 * Timestemp -> String date only, no time
	 * @param v Timestamp
	 * @return String representing the readable date
	 */
	public static String toStringMMDDYY(Timestamp v) {
		if(v!=null)
		{
			return new SimpleDateFormat("MM/dd/yyyy").format(v);
		}
		return null;
	}
}

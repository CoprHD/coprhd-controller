/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import java.math.BigInteger;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import com.emc.ema.EmaApiEventType;
import com.emc.storageos.systemservices.impl.eventhandler.beans.AlertEvent;

public class AlertEventAnalyser {
	
	   // Enhance this function with the advanced log crawler and analyser
		public AlertEvent parseEventLogs(String logFilePath){
			
			AlertEvent event = new AlertEvent();

			// parse the logs and get the information

			event.setSymptomCode("BourneAlert");
			event.setSeverity(EmaApiEventType.EMA_EVENT_SEVERITY_CRITICAL_STR);
			event.setComponent("");
			event.setSubcomponent("");
			event.setDescription("");
			event.setErrorFilepath("");
			event.setEventData("0x1 0x3 0x5");

			GregorianCalendar cal = new GregorianCalendar();
			try {
				event.setFirstTime(DatatypeFactory.newInstance()
						.newXMLGregorianCalendar(cal));
				event.setLastTime(DatatypeFactory.newInstance()
						.newXMLGregorianCalendar(cal));
			} catch (DatatypeConfigurationException e) {
				e.printStackTrace();
			}
			event.setCount(new BigInteger("1"));
			event.setCallHome("Yes");
			
			return event;
			
		}
}

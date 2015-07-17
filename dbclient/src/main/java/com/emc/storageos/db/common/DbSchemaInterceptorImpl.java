/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.common;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExcludeFromGarbageCollection;

@SuppressWarnings("serial")
public class DbSchemaInterceptorImpl extends DbSchemaScannerInterceptor {
	private static final Logger logger = LoggerFactory.getLogger(DbSchemaInterceptorImpl.class);
	
	private Map<String, List<String>> cfFieldsIgnoreMap = new HashMap<String, List<String>>(){{
		List<String> vpFields = new ArrayList<String>();
		vpFields.add("hostIOLimitBandwidthSet");
		vpFields.add("hostIOLimitIOPsSet");
		put("VirtualPool", vpFields);

		List<String> taskFields = new ArrayList<String>();
		taskFields.add("error");
		taskFields.add("pending");
		taskFields.add("ready");
		taskFields.add("associatedResourcesList");
		put("Task", taskFields);
		
		List<String> serializerFields = new ArrayList<String>();
		serializerFields.add("serializer");
		put("AuditLogs", serializerFields);
		put("Events", serializerFields);
		put("Stats", serializerFields);	
		
		List<String> statFields = new ArrayList<String>();
		statFields.add("allocatedCapacity");
		statFields.add("bandwidthIn");
		statFields.add("bandwidthOut");
		statFields.add("idleTimeCounter");
		statFields.add("ioTimeCounter");
		statFields.add("kbytesTransferred");
		statFields.add("objCount");
		statFields.add("provisionedCapacity");
		statFields.add("queueLength");
		statFields.add("readHitIOs");
		statFields.add("readIOs");
		statFields.add("realSize");
		statFields.add("smdSize");
		statFields.add("snapshotCapacity");
		statFields.add("snapshotCount");
		statFields.add("totalIOs");
		statFields.add("umdSize");
		statFields.add("userSize");
		statFields.add("writeHitIOs");
		statFields.add("writeIOs");
		put("Stat", statFields);
	}};
	
	private List<String> cfClsAntnList = new ArrayList<String>(){{
		this.add("ExcludeFromGarbageCollection");
		
	}};
	
	@Override
	public boolean isFieldIgnored(String cfName, String fieldName) {
		if(this.cfFieldsIgnoreMap == null){
			return false;
		}
		
		List<String> ignoredFields = this.cfFieldsIgnoreMap.get(cfName);
		if(ignoredFields!=null && ignoredFields.contains(fieldName)){
			logger.info("ignore field, class:{}/field:{}", cfName, fieldName);
			return true;
		}
		
		return false;
	}
	
	@Override
    public boolean isClassAnnotationIgnored(String cfName, String annotationName) {
		if(this.cfClsAntnList==null || this.cfClsAntnList.isEmpty()){
			return false;
		}
		
		return this.cfClsAntnList.contains(annotationName);
    }    
}
/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.emc.storageos.coordinator.client.model.Constants.*;


public final class SoftwareVersionMetadata {
	private static final Logger log = LoggerFactory.getLogger(SoftwareVersionMetadata.class);
	private static String IMAGE_FILE_PATH_TEMPLATE = "/.volumes/bootfs/%s/rootimg";
	
	// Suppress Sonar violation of Lazy initialization of static fields should be synchronized
	// This method is only used in test case, safe to suppress
	@SuppressWarnings("squid:S2444")
	public static void setimageFileTemplate(String template) {
		IMAGE_FILE_PATH_TEMPLATE = template;
	}

	private static final String READ_ONLY_ACCESS_MODE = "r";
	private static final int VERSION_METADATA_LENGTH = 20480;
	private static final String DOWNGRADE_TO_TAG = "downgrade_to:";
	private static final String UPGRADE_FROM_TAG = "upgrade_from:";
	public SoftwareVersion version;
	public List<SoftwareVersion> upgradeFromVersionsList;
	public List<SoftwareVersion> downgradeToVersionsList;
	
	private SoftwareVersionMetadata(SoftwareVersion c, List<SoftwareVersion> uL, List<SoftwareVersion> dL) {
		version = c;
		upgradeFromVersionsList = uL;
		downgradeToVersionsList = dL;	
	}
	
	public static SoftwareVersionMetadata getInstance(SoftwareVersion v) throws IOException {
		
		List<SoftwareVersion> upgradeFromList = new ArrayList<SoftwareVersion>();
		List<SoftwareVersion> downgradeToList = new ArrayList<SoftwareVersion>();
		
		String viprMetadataString = readFile(v);
		
		for (String s : viprMetadataString.split("\n")){
			// The metadata file contains multiple lines of properties
			if (s.startsWith(UPGRADE_FROM_TAG) && !s.trim().endsWith(":")) {
				for (String versionStr : s.substring(UPGRADE_FROM_TAG.length()).split(",")){
					upgradeFromList.add(new SoftwareVersion(versionStr));
				}
			}
			if (s.startsWith(DOWNGRADE_TO_TAG) && !s.trim().endsWith(":")) {
				for (String versionStr : s.substring(DOWNGRADE_TO_TAG.length()).split(",")){
					downgradeToList.add(new SoftwareVersion(versionStr));
				}
			}
		}
		return new SoftwareVersionMetadata(v, upgradeFromList, downgradeToList);
	}
	
	private static String readFile(SoftwareVersion v) throws IOException {
		
		String imageFilePath = String.format(IMAGE_FILE_PATH_TEMPLATE, v.toString());
		File f = new File(imageFilePath); 
		long size = f.length();
		RandomAccessFile file = null;
		try {
			file = new RandomAccessFile(f, READ_ONLY_ACCESS_MODE);
		} catch (FileNotFoundException e) {
			log.error("Image file doesn't exist! Stack trace: ", e);
			throw e;
		}
		byte[] buffer = new byte[VERSION_METADATA_LENGTH];
		try {
			file.seek(size-TRAILER_LENGTH-VERSION_METADATA_LENGTH); // Skip until the beginning of the version metadata
			file.read(buffer, 0, VERSION_METADATA_LENGTH);
		} catch (IOException e) {
			log.error("IOException while extract upgrade from version info! Stack trace: ", e);
			throw e;
		} finally {
			try {
				file.close();
			} catch (IOException e) {
			    log.debug(String.format("IOException is throwed when closing file %s", f.getAbsolutePath()), e);
			}
		}
		
		String viprMetadataString = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buffer)).toString();
		return viprMetadataString;
	}
}

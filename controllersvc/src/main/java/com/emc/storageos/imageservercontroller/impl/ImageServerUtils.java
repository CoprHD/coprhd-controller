/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.emc.storageos.imageservercontroller.exceptions.ImageServerControllerException;

public class ImageServerUtils {
	
	public static InputStream getResourceAsStream(String pathToResource) {
		Resource resource = new ClassPathResource(pathToResource);
		try {
			return resource.getInputStream();
		} catch (IOException e) {
			throw ImageServerControllerException.exceptions.unableToOpenResourceAsStream(pathToResource, e);
		}
	}

	public static String getResourceAsString(String pathToResource) {
		InputStream in = getResourceAsStream(pathToResource);
		byte[] b;
		try {
			b = new byte[in.available()];
			in.read(b);
			in.close();
		} catch (IOException e) {
			throw ImageServerControllerException.exceptions.unableToReadResource(pathToResource, e);
		}
		return new String(b);
	}

	public static StringBuilder replaceAll(StringBuilder sb, String oldValue, String newValue) {
		int start = sb.indexOf(oldValue);
		while (start != -1) {
			sb.replace(start, start + oldValue.length(), newValue);
			start = sb.indexOf(oldValue, start + newValue.length());
		}
		return sb;
	}
	
	/**
	 * Expect strings in following 2 formats:
	 * 		422f1dfd-ce07-2820-494e-bf3b9d5e74d8
	 * 		422f1dfdce072820494ebf3b9d5e74d8
	 * @param str
	 * @return
	 */
	public static UUID uuidFromString(String str) {
		if (str == null) {
			return null;
		}
		if (str.length() == 32) {
			// looks like dashes are missing, insert dashes
			StringBuilder sb = new StringBuilder(str);
			sb.insert(8, '-');
			sb.insert(13, '-');
			sb.insert(18, '-');
			sb.insert(23, '-');
			return UUID.fromString(sb.toString());
		} else {
			return UUID.fromString(str);
		}
	}

	/**
	 * Expect strings in following 2 formats:
	 * 		422f1dfd-ce07-2820-494e-bf3b9d5e74d8
	 * 		422f1dfdce072820494ebf3b9d5e74d8
	 * @param str
	 * @return
	 */
	public static boolean isUuid(String str) {
		try {
			uuidFromString(str);
		} catch (IllegalArgumentException e) {
			return false;
		}
		return true;
	}

	/**
	 * Expects key/value pairs delimited by new line like this:
	 * abc=123
	 * xyz=505
	 * @param str
	 * @return
	 */
	public static Properties stringToProperties(String str) {
		Properties result = new Properties();
		String[] arr = str.split("\n");		
		for (String pair : arr) {
			String[] pairArr = pair.trim().split("=");
			if (pairArr.length == 2 && !pairArr[0].trim().startsWith("#")) {
				result.put(pairArr[0].trim(), pairArr[1].trim());
			}
		}
		return result;
	}
}

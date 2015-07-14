/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 
*/
package com.emc.storageos.vasa;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import com.emc.storageos.vasa.util.XmlParser;

public class Config {

	private static final Logger _log = Logger.getLogger(Config.class);

	private static Config _config;
	private XmlParser _configParser;

	private Config() {
		final String methodName = "Config(): ";
		final String CONFIG_FILE_PATH = System.getProperty("vasa.config");
		_configParser = new XmlParser();
		try {
			_log.debug(methodName + " loading config file: " + CONFIG_FILE_PATH);
			_configParser.loadFile(CONFIG_FILE_PATH);
		} catch (ParserConfigurationException e) {
			_log.error(methodName + "Unable to parse XML content ", e);
		} catch (SAXException e) {
			_log.error(methodName + "Unable to parse XML content ", e);
		} catch (IOException e) {
			_log.error(methodName + "Unable to load file: " + CONFIG_FILE_PATH, e);
		}
	}

	public static synchronized Config getInstance() {

		if (_config == null) {
			_config = new Config();
		}
		return _config;
	}

	public String getConfigValue(String configName) {

		final String methodName = "getConfigValue(): ";
		String value = null;

		try {
			value = _configParser.getString(configName, null);
		} catch (XPathExpressionException e) {
			_log.error(methodName + "Unable to resolve XPath: " + configName, e);
		}
		return value;
	}

}


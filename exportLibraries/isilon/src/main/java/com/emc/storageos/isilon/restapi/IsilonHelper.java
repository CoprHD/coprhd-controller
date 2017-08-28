package com.emc.storageos.isilon.restapi;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IsilonHelper {
	private static volatile IsilonApi _client;
	private static volatile IsilonApiFactory _factory = new IsilonApiFactory();
	private static final Logger logger = LoggerFactory.getLogger(IsilonHelper.class);
	private static volatile String properties_file = null;
	private static volatile Map<String, Properties> properties = null;

	public static void setup() throws URISyntaxException {
		String ip = get("ISI_IP");
		String userName = get("ISI_USER");
		String password = get("ISI_PASSWD");
		URI deviceURI = new URI("https", null, ip, 8080, "/", null, null);
		_factory = new IsilonApiFactory();
		_factory.init();
		_client = _factory.getRESTClient(deviceURI, userName, password);
	}

	private static void readConfig() throws Exception {
		Properties props = new Properties();
		InputStream in = null;
		try {
			in = new FileInputStream(properties_file);
			if (in != null) {
				props.load(in);
			}
			if (properties == null) {
				properties = new ConcurrentHashMap<String, Properties>();
			}
			if (properties.get(properties_file) == null) {
				properties.put(properties_file, props);
			}
		} catch (FileNotFoundException e) {
			logger.error(String.format("Could not locate the file %s ", properties_file));
			logger.error(e.getMessage(), e);
			throw e;
		} catch (IOException ex) {
			logger.error(String.format("Could not read the file %s ", properties_file));
			logger.error(ex.getMessage(), ex);
			throw ex;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				logger.error("Failed while closing inputstream");
				logger.error(e.getMessage(), e);
			}
		}
	}

	public static String get(String propertyName) {
		String propertyValue = "";
		try {
			if (propertyName == null || propertyName.isEmpty()) {
				logger.error("Property name is not supplied. Please provide a property Name");
				return "";
			}
			readConfig();
			Properties property = properties.get(properties_file);
			if (property != null) {
				propertyValue = property.getProperty(propertyName);
				if (propertyValue == null) {
					logger.error(String.format("Property %s not found in the properties file %s ", propertyName,
							properties_file));
				}
			} else {
				logger.error("Failed while loading property file {}", properties_file);
			}
		} catch (Exception e) {
			logger.error(String.format("Failed while getting the property %s at %s ", propertyName, properties_file));
			logger.error(e.getMessage(), e);
		}
		return propertyValue;
	}

	public static void main(String args[]) throws Exception {
		try {
			String fspath;
			properties_file = args[0];
			setup();
			// Operations to be performed..
			String op = args[1];

			switch (op) {
			case "check_for_dir":
				fspath = args[2];
				if (_client.existsDir(fspath)) {
					System.out.println("directory with path" + fspath + " exists on Array");
					System.exit(0);
				} else {
					System.out.println("directory with path" + fspath + " doesn't exists on Array");
					System.exit(1);
				}
				break;
			case "create_directory":
				fspath = args[2];
				_client.createDir(fspath);
				break;
			case "ku":

				break;
			default:
				System.out.println("INVALID COMMAND!!" + "Valid Commands are:" + getAllCommands());
				System.exit(1);
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println(e.getLocalizedMessage());
			System.exit(1);
		}
	}

	private static ArrayList<String> getAllCommands() {
		ArrayList<String> commands = new ArrayList<>();
		commands.add("check_for_dir");
		commands.add("create_directory");
		return commands;
	}
}

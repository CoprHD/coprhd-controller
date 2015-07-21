/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * VasaServiceTest.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */
package com.emc.storageos.vasa;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntitiesResponse;
import com.emc.storageos.vasa.VasaServiceStub.HostInitiatorInfo;
import com.emc.storageos.vasa.VasaServiceStub.MountInfo;
import com.emc.storageos.vasa.VasaServiceStub.QueryArrays;
import com.emc.storageos.vasa.VasaServiceStub.QueryArraysResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForFileSystem;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForFileSystemResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForLun;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedCapabilityForLunResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedLunsForPortResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedPortsForProcessorResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedProcessorsForArrayResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageCapabilitiesResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageFileSystems;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageFileSystemsResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageLuns;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageLunsResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryStoragePortsResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryStorageProcessorsResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntityResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForFileSystems;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForFileSystemsResponse;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForLuns;
import com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForLunsResponse;
import com.emc.storageos.vasa.VasaServiceStub.RegisterVASACertificateResponse;
import com.emc.storageos.vasa.VasaServiceStub.SetContextResponse;
import com.emc.storageos.vasa.VasaServiceStub.StorageArray;
import com.emc.storageos.vasa.VasaServiceStub.UsageContext;
import com.emc.storageos.vasa.VasaServiceStub.VasaProviderInfo;
import com.emc.storageos.vasa.VasaServiceStub.VendorModel;

/*
 *  VasaServiceTest Junit test case
 */

public class VasaServiceTest extends junit.framework.TestCase {

	// private static String CONFIG_SERVICE_URL = "serviceURL";

	private static final String STORAGEPROCESSOR_IDENTIFIER_PREFIX = "urn:storageos:StorageProcessor:";
	private static final String STORAGEARRAY_IDENTIFIER_PREFIX = "urn:storageos:StorageArray:";
	private static final String STORAGEPORT_IDENTIFIER_PREFIX = "urn:storageos:StoragePort:";

	private static String VASA_SESSIONID_STR = "VASASESSIONID";
	private static final String INVALID_SESSION_ID = "0";
	private static com.emc.storageos.vasa.VasaServiceStub _stub;
	private static Properties _prop;
	private static KeyStore keystore;
	private static String _vasaSessionId;
	private static String arrayId;
	private static String[] portIds;
	private static String[] proccessorIds;
	private static String[] fileSystemIds;
	private static String[] volumeIds;
	private static String[] storageCapabilityIds;

	static {

		try {
			ClientConfig config = ClientConfig.getInstance();
			_prop = config.getProperties();
		} catch (IOException e1) {
			System.out.println(e1);
		}

		try {
			_stub = new com.emc.storageos.vasa.VasaServiceStub("https://"
					+ _prop.getProperty(ClientConfig.SERVICE_HOST)
					+ ":9083/storageos-vasasvc/services/vasaService");
		} catch (AxisFault e) {
			System.out.println(e);
		}

	}

	/**
	 * Auto generated test method
	 */

	private static KeyStore createKeyStore(final URL url, String keystoreType,
			final String password) throws java.lang.Exception {
		assert url != null;

		InputStream is = null;
		try {
			KeyStore keystore = KeyStore.getInstance(keystoreType);
			is = url.openStream();
			keystore.load(is, password != null ? password.toCharArray() : null);
			return keystore;
		} 	catch (java.lang.Exception e) {
			System.out.println("Could not create keystore " + e);
			throw e;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (java.lang.Exception e) {
					System.out.println(e);
				}
			}
		}
	}

	/**
	 * getCertificate()
	 */
	private static String getCertificate(KeyStore keyStore, String aliasName,
			Boolean trusted) throws java.lang.Exception {
		try {
			Enumeration<String> aliases = keyStore.aliases();

			if (!aliasName.equals("")) {
				/*
				 * search for a certificate with the given aliasName first
				 */
				while (aliases.hasMoreElements()) {
					String alias = aliases.nextElement();
					System.out.println("Found certificate for alias " + alias);
					if (alias.equals(aliasName)) {
						X509Certificate tc = (X509Certificate) keyStore
								.getCertificate(alias);
						if (trusted) {
							try {
								tc.checkValidity();
								// System.out.println("Found trusted certificate "
								// + alias);
							} catch (java.lang.Exception e) {
								System.out.println("Certificate " + alias
										+ " is not trusted");
								throw e;
							}
						}
						return wrapCertificateBytes(tc);
					}
				}
				throw new java.lang.Exception("no matching certificate found");
			}

			aliases = keyStore.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				X509Certificate tc = (X509Certificate) keyStore
						.getCertificate(alias);

				if (trusted) {
					try {
						tc.checkValidity();
						System.out
								.println("Found trusted certificate " + alias);
						return wrapCertificateBytes(tc);
					} catch (java.lang.Exception e) {
						// skip untrusted certificate
					}
				} else {
					if (!keyStore.isCertificateEntry(alias)) {
						System.out.println("Found private key certificate "
								+ alias);
						return wrapCertificateBytes(tc);
					}
				}
			}
			throw new java.lang.Exception("no matching certificate found");
		} catch (java.lang.Exception e) {
			throw e;
		}
	}

	private static String wrapCertificateBytes(X509Certificate tc)
			throws java.lang.Exception {
		/**
		 * get PkiPath PKCS#7 encoding and wrap it in a base64 text format
		 */
		String b64 = encodeToString(tc.getEncoded(), Boolean.TRUE);
		String str = new String("-----BEGIN CERTIFICATE-----\n\n").concat(b64)
				.concat("\n-----END CERTIFICATE-----");
		return str;
	}

	private static String encodeToString(byte[] sArr, boolean lineSep) {
		/**
		 * By default the encoder has a line length of 76 and a separator of
		 * CRLF when chunking is enabled.
		 */
		return new String(Base64.encodeBase64(sArr, true));
	}

	/**
	 * Auto generated test method
	 */

	// Create an ADBBean and provide it as the test object
	public org.apache.axis2.databinding.ADBBean getTestObject(
			java.lang.Class type) throws java.lang.Exception {
		return (org.apache.axis2.databinding.ADBBean) type.newInstance();
	}

	private static void useExistingSession() {
		_stub._getServiceClient().getOptions().setManageSession(true);
		Options options = _stub._getServiceClient().getOptions();
		options.setProperty(HTTPConstants.COOKIE_STRING, VASA_SESSIONID_STR
				+ "=" + _vasaSessionId);
		_stub._getServiceClient().setOptions(options);
	}

	public static void disableCertificateValidation() {
		// this method is basically bypasses certificate validation.
		// Bourne appliance uses expired certificate!

		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				// return new X509Certificate[0];
				return null;
			}

			public void checkClientTrusted(final X509Certificate[] certs,
					final String authType) {
			}

			public void checkServerTrusted(final X509Certificate[] certs,
					final String authType) {
			}
		} };

		final HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(final String hostname,
					final SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting trust manager
		try {
			final SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
		} catch (final Exception e) {
			System.out.println("Unexpeted error occured" + e);
		}
	}

	@Override
	protected void setUp() throws Exception {

		System.setProperty("javax.net.ssl.trustStore",
				_prop.getProperty(ClientConfig.KEYSTORE_PATH));
		System.setProperty("javax.net.ssl.trustStorePassword",
				_prop.getProperty(ClientConfig.KEYSTORE_PASSWORD));
		System.setProperty("javax.net.ssl.trustStoreType", "jks");

		System.setProperty("javax.net.ssl.keyStore",
				_prop.getProperty(ClientConfig.KEYSTORE_PATH));
		System.setProperty("javax.net.ssl.keyStorePassword",
				_prop.getProperty(ClientConfig.KEYSTORE_PASSWORD));
		System.setProperty("javax.net.ssl.keyStoreType", "jks");

		disableCertificateValidation();
	}

	public void testRegisterVASACertificate() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.RegisterVASACertificate registerVASACertificate117 = (com.emc.storageos.vasa.VasaServiceStub.RegisterVASACertificate) getTestObject(com.emc.storageos.vasa.VasaServiceStub.RegisterVASACertificate.class);

		final String modelId = "EMC ViPR";

		registerVASACertificate117.setUserName(_prop
				.getProperty(ClientConfig.USERNAME));

		registerVASACertificate117.setPassword(_prop
				.getProperty(ClientConfig.PASSWORD));

		String keyStoreFileURL = "file:"
				+ _prop.getProperty(ClientConfig.KEYSTORE_PATH);
		keystore = createKeyStore(new URL(keyStoreFileURL), "JKS", //NOSONAR ("Lazy initialization of "static" fields should be "synchronized" : Synchronize this lazy initialization of 'keystore'")
				_prop.getProperty(ClientConfig.KEYSTORE_PASSWORD)); 

		String certificate = getCertificate(keystore,
				_prop.getProperty(ClientConfig.CERT_ALIAS), true);

		registerVASACertificate117.setNewCertificate(certificate);

		RegisterVASACertificateResponse response = _stub
				.registerVASACertificate(registerVASACertificate117);

		VasaProviderInfo vpInfo = response.get_return();
		VendorModel[] model = vpInfo.getSupportedVendorModel();

		assertEquals(modelId, model[0].getModelId());

	}

	public void testSetContext() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.SetContext request = (com.emc.storageos.vasa.VasaServiceStub.SetContext) getTestObject(com.emc.storageos.vasa.VasaServiceStub.SetContext.class);

		UsageContext uc = new UsageContext();

		String configMountPointList = _prop
				.getProperty(ClientConfig.MOUNTPOINT_LIST);

		if (configMountPointList != null
				&& configMountPointList.trim().length() > 0) {
			List<MountInfo> mountPoints = new ArrayList<MountInfo>();

			for (String mountPoint : configMountPointList.split(",")) {

				String[] part = mountPoint.split(":");
				MountInfo info = new MountInfo();
				info.setServerName(part[0].trim());
				info.setFilePath(part[1].trim());
				mountPoints.add(info);
			}
			uc.setMountPoint(mountPoints.toArray(new MountInfo[0]));
		}

		String configISCSIIdList = _prop.getProperty(ClientConfig.ISCSIID_LIST);

		if (configISCSIIdList != null && configISCSIIdList.trim().length() > 0) {

			List<HostInitiatorInfo> hostIdList = new ArrayList<VasaServiceStub.HostInitiatorInfo>();

			for (String iSCSId : configISCSIIdList.split(",")) {
				HostInitiatorInfo hostInitiatorInfo = new HostInitiatorInfo();
				hostInitiatorInfo.setIscsiIdentifier(iSCSId);
				hostIdList.add(hostInitiatorInfo);
			}

			uc.setHostInitiator(hostIdList.toArray(new HostInitiatorInfo[0]));

		}

		request.setUsageContext(uc);

		SetContextResponse response = _stub.setContext(request);
		VasaProviderInfo vpInfo = response.get_return();
		String sessionId = vpInfo.getSessionId();
		_vasaSessionId = sessionId;

		System.out.println("NEW SESSION ID: [" + _vasaSessionId + "]");
		assertFalse(INVALID_SESSION_ID.equals(_vasaSessionId));

	}

	/**
	 * Auto generated test method
	 */
	public void testQueryCatalog() throws java.lang.Exception {
		useExistingSession();
		assertNotNull(_stub.queryCatalog());

	}

	public void testGetNumberOfEntitiesForStorageArray()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);

		useExistingSession();
		getNumberOfEntities89.setEntityType("StorageArray");

		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() == 1);

	}

	public void testQueryArrays() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryArrays queryArraysRequest = (QueryArrays) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryArrays.class);

		useExistingSession();
		queryArraysRequest.setArrayUniqueId(null);

		QueryArraysResponse response = _stub.queryArrays(queryArraysRequest);

		StorageArray[] storageArrays = response.get_return();

		if (storageArrays.length == 1) {
			arrayId = storageArrays[0].getUniqueIdentifier(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for arrayId”)
			assertTrue(arrayId.startsWith(STORAGEARRAY_IDENTIFIER_PREFIX));
		} else {
			assertTrue(false);
		}

	}

	public void testGetNumberOfEntitiesForStorageProcessor()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);

		useExistingSession();
		getNumberOfEntities89.setEntityType("StorageProcessor");

		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() == 1);

	}

	public void testQueryUniqueIdentifiersForEntityTypeStorageProcessor()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity queryUniqueIdRequest = (com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity.class);

		useExistingSession();
		queryUniqueIdRequest.setEntityType("StorageProcessor");

		QueryUniqueIdentifiersForEntityResponse response = _stub
				.queryUniqueIdentifiersForEntity(queryUniqueIdRequest);

		proccessorIds = response.get_return(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for proccessorIds”)

		if (proccessorIds.length > 0) {

			assertTrue(proccessorIds[0]
					.startsWith(STORAGEPROCESSOR_IDENTIFIER_PREFIX));
		} else {

			assertTrue(false);

		}

	}

	public void testQueryStorageProcessors() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryStorageProcessors request = (com.emc.storageos.vasa.VasaServiceStub.QueryStorageProcessors) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryStorageProcessors.class);

		useExistingSession();
		request.setSpUniqueId(proccessorIds);

		QueryStorageProcessorsResponse response = _stub
				.queryStorageProcessors(request);

		assertTrue(response.get_return().length > 0);

	}

	public void testgetNumberOfEntitiesForStoragePort()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);

		useExistingSession();
		getNumberOfEntities89.setEntityType("StoragePort");

		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() > 0);

	}

	public void testQueryUniqueIdentifiersForEntityTypeStoragePort()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity queryUniqueIdRequest = (com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryUniqueIdentifiersForEntity.class);

		useExistingSession();
		queryUniqueIdRequest.setEntityType("StoragePort");

		QueryUniqueIdentifiersForEntityResponse response = _stub
				.queryUniqueIdentifiersForEntity(queryUniqueIdRequest);

		portIds = response.get_return(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for portIds”)

		if (portIds.length > 0) {

			assertTrue(portIds[0].startsWith(STORAGEPORT_IDENTIFIER_PREFIX));
		}

	}

	public void testQueryAssociatedProcessorsForArray()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedProcessorsForArray request = (com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedProcessorsForArray) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedProcessorsForArray.class);

		useExistingSession();
		request.setArrayUniqueId(new String[] { arrayId });

		QueryAssociatedProcessorsForArrayResponse response = _stub
				.queryAssociatedProcessorsForArray(request);

		assertTrue(response.get_return().length > 0);

	}

	public void testQueryAssociatedPortsForProcessor()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedPortsForProcessor request = (com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedPortsForProcessor) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedPortsForProcessor.class);

		useExistingSession();
		request.setSpUniqueId(proccessorIds);

		QueryAssociatedPortsForProcessorResponse response = _stub
				.queryAssociatedPortsForProcessor(request);

		assertTrue(response.get_return().length > 0);

	}

	public void testQueryStoragePorts() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryStoragePorts request = (com.emc.storageos.vasa.VasaServiceStub.QueryStoragePorts) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryStoragePorts.class);

		useExistingSession();
		request.setPortUniqueId(portIds);

		QueryStoragePortsResponse response = _stub.queryStoragePorts(request);

		assertTrue(response.get_return().length > 0);

	}

	public void testQueryAssociatedLunsForPort() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedLunsForPort request = (com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedLunsForPort) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryAssociatedLunsForPort.class);
		useExistingSession();
		request.setPortUniqueId(portIds);
		QueryAssociatedLunsForPortResponse response = _stub
				.queryAssociatedLunsForPort(request);

		/*
		 * for (VasaAssociationObject assoc : response.get_return()) { for
		 * (BaseStorageEntity entity : assoc.getAssociatedId()) {
		 * volumeIdList.add(entity.getUniqueIdentifier()); } }
		 * 
		 * volumeIds = volumeIdList.toArray(new String[0]);
		 */

		assertTrue(response.get_return().length > 0);
	}

	public void testGetNumberOfEntitiesForStorageLun()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);

		getNumberOfEntities89.setEntityType("StorageLun");

		useExistingSession();
		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() > 0);

	}

	public void testQueryUniqueIdentifiersForLuns() throws Exception {

		QueryUniqueIdentifiersForLuns request = (QueryUniqueIdentifiersForLuns) getTestObject(QueryUniqueIdentifiersForLuns.class);
		System.out.println("Array ID: [" + arrayId + "]");
		request.setArrayUniqueId(arrayId);
		useExistingSession();
		QueryUniqueIdentifiersForLunsResponse response = _stub
				.queryUniqueIdentifiersForLuns(request);

		volumeIds = response.get_return(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for volumeIds”)

		assertTrue(volumeIds.length > 0);
	}

	public void testQueryStorageLuns() throws Exception {

		QueryStorageLuns request = (QueryStorageLuns) getTestObject(QueryStorageLuns.class);
		useExistingSession();
		request.setLunUniqueId(volumeIds);

		QueryStorageLunsResponse response = _stub.queryStorageLuns(request);

		assertTrue(response.get_return().length > 0);
	}

	public void testgetNumberOfEntitiesForFileSystems()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);
		useExistingSession();
		getNumberOfEntities89.setEntityType("StorageFileSystem");

		useExistingSession();
		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() > 0);

	}

	public void testQueryUniqueIdentifiersForFileSystems() throws Exception {

		QueryUniqueIdentifiersForFileSystems request = (QueryUniqueIdentifiersForFileSystems) getTestObject(QueryUniqueIdentifiersForFileSystems.class);
		useExistingSession();
		request.setFsUniqueId(arrayId);
		QueryUniqueIdentifiersForFileSystemsResponse response = _stub
				.queryUniqueIdentifiersForFileSystems(request);

		fileSystemIds = response.get_return(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for fileSystemIds”)

		assertTrue(fileSystemIds.length > 0);

	}

	public void testQueryStorageFileSystems() throws Exception {

		QueryStorageFileSystems request = (QueryStorageFileSystems) getTestObject(QueryStorageFileSystems.class);
		request.setFsUniqueId(fileSystemIds);
		useExistingSession();
		QueryStorageFileSystemsResponse response = _stub
				.queryStorageFileSystems(request);

		assertTrue(response.get_return().length > 0);
	}

	public void testQueryAssociatedCapabilitiesForFileSystems()
			throws Exception {

		QueryAssociatedCapabilityForFileSystem request = (QueryAssociatedCapabilityForFileSystem) getTestObject(QueryAssociatedCapabilityForFileSystem.class);
		useExistingSession();
		request.setFsUniqueId(fileSystemIds);
		QueryAssociatedCapabilityForFileSystemResponse response = _stub
				.queryAssociatedCapabilityForFileSystem(request);

		assertTrue(response.get_return().length > 0);
	}

	public void testQueryAssociatedCapabilitiesForStorageLuns()
			throws Exception {

		QueryAssociatedCapabilityForLun request = (QueryAssociatedCapabilityForLun) getTestObject(QueryAssociatedCapabilityForLun.class);
		useExistingSession();
		request.setLunUniqueId(volumeIds);
		QueryAssociatedCapabilityForLunResponse response = _stub
				.queryAssociatedCapabilityForLun(request);

		assertTrue(response.get_return().length > 0);
	}

	public void testGetNumberOfEntitiesForStorageCapabilities()
			throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities getNumberOfEntities89 = (com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.GetNumberOfEntities.class);
		useExistingSession();
		getNumberOfEntities89.setEntityType("StorageCapability");
		useExistingSession();
		GetNumberOfEntitiesResponse response = _stub
				.getNumberOfEntities(getNumberOfEntities89);

		assertTrue(response.get_return() > 0);

	}

	public void testQueryUniqueIdentifiersForStorageCapabilites()
			throws Exception {
		QueryUniqueIdentifiersForEntity request = (QueryUniqueIdentifiersForEntity) getTestObject(QueryUniqueIdentifiersForEntity.class);
		request.setEntityType("StorageCapability");
		useExistingSession();
		QueryUniqueIdentifiersForEntityResponse response = _stub
				.queryUniqueIdentifiersForEntity(request);

		storageCapabilityIds = response.get_return(); //NOSONAR ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for storageCapabilityIds”)

		assertTrue(storageCapabilityIds.length > 0);

	}

	/**
	 * Auto generated test method
	 */
	public void testQueryStorageCapabilities() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.QueryStorageCapabilities request = (com.emc.storageos.vasa.VasaServiceStub.QueryStorageCapabilities) getTestObject(com.emc.storageos.vasa.VasaServiceStub.QueryStorageCapabilities.class);
		useExistingSession();
		request.setCapabilityUniqueId(storageCapabilityIds);
		QueryStorageCapabilitiesResponse response = _stub
				.queryStorageCapabilities(request);
		assertTrue(response.get_return().length > 0);

	}

	public void testUnregisterVASACertificate() throws java.lang.Exception {

		com.emc.storageos.vasa.VasaServiceStub.UnregisterVASACertificate unregisterVASACertificate129 = (com.emc.storageos.vasa.VasaServiceStub.UnregisterVASACertificate) getTestObject(com.emc.storageos.vasa.VasaServiceStub.UnregisterVASACertificate.class);
		useExistingSession();

		String certificate = getCertificate(keystore,
				_prop.getProperty(ClientConfig.CERT_ALIAS), true);
		unregisterVASACertificate129.setExistingCertificate(certificate);
		_stub.unregisterVASACertificate(unregisterVASACertificate129);

	}

}


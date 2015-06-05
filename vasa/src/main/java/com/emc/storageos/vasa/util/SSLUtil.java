/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.emc.storageos.vasa.util;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vmware.vim.vasa._1_0.InvalidArgument;
import com.vmware.vim.vasa._1_0.InvalidCertificate;
import com.vmware.vim.vasa._1_0.InvalidSession;
import com.vmware.vim.vasa._1_0.StorageFault;

//import de.hunsicker.jalopy.storage.History.Method;

/**
 * Helper functions for handling SSL certificates.
 */
public class SSLUtil {
	private String trustStoreFileName;
	private String trustStorePassword;
	private boolean mustUseSSL;
	private Log log = LogFactory.getLog(SSLUtil.class);
	public static final int HASH_LENGTH = 20;
	public static String VASA_SESSIONID_STR = "VASASESSIONID";

	/**
	 * Constructor
	 */
	public SSLUtil(String fileName, String password, boolean SSLOnly) {
		trustStoreFileName = fileName;
		trustStorePassword = password;
		mustUseSSL = SSLOnly;
	}

	/**
	 * return the value of the given HTTP cookie
	 * 
	 * @param cookieName
	 */
	public String getCookie(String cookieName) throws InvalidSession {
		MessageContext currentMessageContext = MessageContext
				.getCurrentMessageContext();
		if (currentMessageContext == null) {
			throw FaultUtil.InvalidSession("No current message context");
		}

		HttpServletRequest req = (HttpServletRequest) currentMessageContext
				.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
		if (req == null) {
			throw FaultUtil.InvalidSession("No HTTP Servlet Request");
		}

		Cookie[] cookies = req.getCookies();
		if (cookies == null) {
			return null;
		}

		for (int i = 0; i < cookies.length; i++) {
			if (cookies[i].getName().equals(cookieName)) {
				return cookies[i].getValue();
			}
		}
		return null;
	}

	/**
	 * set the given HTTP cookie
	 * 
	 * @param cookieName
	 * @param cookieValue
	 */
	public void setCookie(String cookieName, String cookieValue)
			throws InvalidSession {
		MessageContext currentMessageContext = MessageContext
				.getCurrentMessageContext();
		if (currentMessageContext == null) {
			throw FaultUtil.InvalidSession("No current message context");
		}

		HttpServletResponse resp = (HttpServletResponse) currentMessageContext
				.getProperty(HTTPConstants.MC_HTTP_SERVLETRESPONSE);
		if (resp == null) {
			throw FaultUtil.InvalidSession("No HTTP Servlet Response");
		}

		Cookie cookie = new Cookie(cookieName, cookieValue);
		resp.addCookie(cookie);
	}

	/**
	 * setHttpResponse
	 * 
	 * @param sc
	 */
	public void setHttpResponse(SessionContext sc) throws InvalidSession {
		if (sc != null) {
			setCookie(VASA_SESSIONID_STR, sc.getSessionId());
		}
	}

	private void checkHttpForValidVASASession() throws InvalidSession {
		/*
		 * Check for a valid VASA Session.
		 */
		final String methodName = "checkHttpForValidVASASession(): ";
		String sessionId = getCookie(VASA_SESSIONID_STR);

		log.debug(methodName + "Current session ID[" + sessionId + "]");

		if (sessionId == null) {
			throw FaultUtil
					.InvalidSession("No valid VASA SessionId in HTTP header");
		}
		try {
			SessionContext sc = SessionContext
					.lookupSessionContextBySessionId(sessionId);
			if (sc == null) {
				throw FaultUtil.InvalidSession("Invalid VASA SessionId "
						+ sessionId + " in HTTP header");
			}
		} catch (Exception e) {
			throw FaultUtil.InvalidSession("Could not find session context "
					+ e);
		}
	}

	public void checkForUniqueVASASessionId() throws InvalidSession {
		/*
		 * Check for a valid VASA Session.
		 */
		final String methodName = "checkForUniqueVASASessionId(): ";
		String sessionId = getCookie(VASA_SESSIONID_STR);
		log.debug(methodName + " Current session ID: [" + sessionId + "]");
		if (sessionId != null) {
			boolean isPreviouslyUsedSessionId = SessionContext
					.IsPreviouslyUsed(sessionId);
			log.debug(methodName + " Is this session ID used previously? ["
					+ isPreviouslyUsedSessionId + "]");

			if (isPreviouslyUsedSessionId) {
				throw FaultUtil
						.InvalidSession("This session Id is not unique. It is previously used:["
								+ sessionId + "]");
			}
		}

	}

	private void checkHttpForValidSSLSession(HttpServletRequest req)
			throws InvalidSession, InvalidCertificate {
		/*
		 * Check for a valid SSL Session.
		 */
		X509Certificate[] sslCerts = (X509Certificate[]) req
				.getAttribute("javax.servlet.request.X509Certificate");
		if ((sslCerts == null) || (sslCerts.length == 0)) {
			throw FaultUtil
					.InvalidSession("No SSL Client Certificate attached to HTTPS session");
		}

		if (!certificateIsTrusted(sslCerts[0])) {
			throw FaultUtil
					.InvalidSession("No Trusted SSL Client Certificate attached to HTTPS session");
		}

		/**
		 * Note that a certificate that is trusted by this server, but one that
		 * has not necessarily been registered via a call to
		 * registerVASACertficate() will be accepted as valid.
		 */
	}

	/**
	 * checkHttpRequest
	 * 
	 * The term "Session" is overloaded. A Session can refer to either a SSL
	 * session or it can refer to a VASA session.
	 * 
	 * If there is an error in either of the Session configurations, then this
	 * routine will throw the InvalidSession expection.
	 * 
	 * @param validClientCertificateNeeded
	 * @param validSessionIdNeeed
	 */
	public String checkHttpRequest(boolean validSSLSessionNeeded,
			boolean validVASASessionNeeded) throws InvalidSession {
		final String methodName = "checkHttpRequest(): ";
		try {
			/*
			 * Check for a valid context.
			 */
			log.trace(methodName + "Entry with inputs validSSLSessionNeeded["
					+ validSSLSessionNeeded + "] validVASASessionNeeded["
					+ validVASASessionNeeded + "]");
			MessageContext currentMessageContext = MessageContext
					.getCurrentMessageContext();
			if (currentMessageContext == null) {
				throw FaultUtil.InvalidSession("No current message context");
			}

			String clientAddress = (String) currentMessageContext
					.getProperty("REMOTE_ADDR");
			// log.debug("Request from client at ip addr: " + clientAddress);

			HttpServletRequest req = (HttpServletRequest) currentMessageContext
					.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
			if (req == null) {
				throw FaultUtil.InvalidSession("No HTTP Servlet Request");
			}

			/**
			 * Get SSL data
			 */
			String sslSessionId = (String) req
					.getAttribute("javax.servlet.request.ssl_session");
			if (sslSessionId == null) {
				/**
				 * This is not an SSL connection. If the service is not allowing
				 * none-SSL connections, throw an exception. Otherwise check for
				 * a valid VASA session if necessary.
				 */
				if (!mustUseSSL) {
					if (validVASASessionNeeded) {
						checkHttpForValidVASASession();
					}
					log.trace(methodName + "Exit returning clientAddress["
							+ clientAddress + "]");
					return clientAddress;
				} else {
					throw FaultUtil.InvalidSession("Must use SSL connection");
				}
			}

			/*
			 * At this point, it is known that there is a well formed HTTPS
			 * session.
			 */
			if (validSSLSessionNeeded) {
				checkHttpForValidSSLSession(req);
			}

			if (validVASASessionNeeded) {
				checkHttpForValidVASASession();
			}
			log.trace(methodName + "Exit returning clientAddress["
					+ clientAddress + "]");
			return clientAddress;
		} catch (InvalidCertificate ic) {
			// InvalidCertificate can be thrown by certificateIsTrusted
			log.error(methodName + "invalid certificate exception ", ic);
			throw FaultUtil.InvalidSession("Non trusted certificate.");
		} catch (InvalidSession is) {
			log.error(methodName + "invalid session exception ", is);
			throw is;
		} catch (Exception e) {
			log.error(methodName + "Exception occured ", e);
			throw FaultUtil
					.InvalidSession(
							"checkHttpSession unexpected exception. Convert to InvalidSession.",
							e);
		}
	}

	/**
	 * getCertificateThumbprint
	 * 
	 * @param cert
	 */
	public String getCertificateThumbprint(Certificate cert)
			throws InvalidArgument {

		// Compute the SHA-1 hash of the certificate.
		try {
			byte[] encoded;
			try {
				encoded = cert.getEncoded();
			} catch (CertificateEncodingException cee) {
				cee.printStackTrace();
				throw FaultUtil.InvalidArgument(
						"Error reading certificate encoding: "
								+ cee.getMessage(), cee);
			}

			MessageDigest sha1;
			try {
				sha1 = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				throw FaultUtil.InvalidArgument(
						"Could not instantiate SHA-1 hash algorithm", e);
			}
			sha1.update(encoded);
			byte[] hash = sha1.digest();

			if (hash.length != HASH_LENGTH) {
				throw FaultUtil.InvalidArgument("Computed thumbprint is "
						+ hash.length + " bytes long, expected " + HASH_LENGTH);
			}

			StringBuilder thumbprintString = new StringBuilder(hash.length * 3);
			for (int i = 0; i < hash.length; i++) {
				if (i > 0) {
					thumbprintString.append(":");
				}
				String hexByte = Integer.toHexString(0xFF & (int) hash[i]);
				if (hexByte.length() == 1) {
					thumbprintString.append("0");
				}
				thumbprintString.append(hexByte);
			}

			return thumbprintString.toString().toUpperCase();
		} catch (InvalidArgument ia) {
			throw ia;
		} catch (Exception e) {
			throw FaultUtil.InvalidArgument("Exception: " + e);
		}
	}

	/**
	 * buildCertificate Build a certificate from a Base64 formatted, PKCS#7
	 * encoding of the certificate
	 * 
	 * @param certString
	 */
	public Certificate buildCertificate(String certString)
			throws InvalidCertificate {
		try {
			String base64Cert = formatCertificate(certString);
			InputStream inBytes = new ByteArrayInputStream(
					base64Cert.getBytes());
			CertificateFactory cf = CertificateFactory.getInstance("X.509");

			assert inBytes.available() > 0;
			Certificate certificate = cf.generateCertificate(inBytes);
			inBytes.close();

			return certificate;
		} catch (Exception e) {
			log.debug("buildCertificate: error " + e
					+ " converted to InvalidCertificate.");
			throw FaultUtil.InvalidCertificate("Could not build certificate");
		}
	}

	private String formatCertificate(String cert) {
		final String HEADER = "-----BEGIN CERTIFICATE-----";
		final String FOOTER = "-----END CERTIFICATE-----";

		if (cert.trim().startsWith(HEADER)) {
			return cert;
		}

		StringBuffer sb = new StringBuffer();
		sb.append(HEADER);
		sb.append("\n");
		sb.append(cert.trim());
		sb.append("\n");
		sb.append(FOOTER);

		return sb.toString();
	}

	/**
	 * 
	 * Format of the alias is: "vpc-<integer>" For example, "vpc-3"
	 */
	private String getAlias(String clientAddress) throws InvalidCertificate {
		int count = 0;
		String certAliasBase = new String("vpc-");

		String certAlias = certAliasBase.concat(Integer.toString(count));
		while (getCertificateFromAlias(certAlias) != null) {
			/**
			 * Need to make sure that certAlias is not already in the
			 * trustStore. If it is, create a different alias so as not to
			 * overwrite an existing certificate.
			 */
			count++;
			certAlias = certAliasBase.concat(Integer.toString(count));
		}

		log.debug("getCertificateFromAlias() " + certAlias
				+ " for certificate from " + clientAddress);
		return certAlias;
	}

	/**
	 * addCertifcateToTrustStore
	 * 
	 * @param certNameRoot
	 *            ,
	 * @param certToAdd
	 */
	public void addCertificateToTrustStore(String certNameRoot,
			Certificate certToAdd) throws InvalidArgument {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);

			ts.load(is, trustStorePassword.toCharArray());
			is.close();

			String certAlias = getAlias(certNameRoot);
			ts.setCertificateEntry(certAlias, certToAdd);

			FileOutputStream out = new FileOutputStream(trustStoreFileName);
			ts.store(out, trustStorePassword.toCharArray());
			out.close();

			log.debug("Certificate with alias " + certAlias
					+ " added to truststore");
		} catch (Exception e) {
			throw FaultUtil.InvalidArgument("Exception " + e);
		}
	}

	/**
	 * removeCertifcateFromTrustStore
	 * 
	 * @param certToAdd
	 */
	public void removeCertificateFromTrustStore(Certificate certToRemove)
			throws InvalidArgument {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);

			ts.load(is, trustStorePassword.toCharArray());
			is.close();

			Enumeration<String> aliases = ts.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if (ts.isCertificateEntry(alias)) {
					X509Certificate tc = (X509Certificate) ts
							.getCertificate(alias);
					if (tc.equals(certToRemove)) {
						ts.deleteEntry(alias);
					}
				}
			}

			FileOutputStream out = new FileOutputStream(trustStoreFileName);
			ts.store(out, trustStorePassword.toCharArray());
			out.close();
		} catch (Exception e) {
			throw FaultUtil.InvalidArgument("Exception " + e);
		}
	}

	/**
	 * certificateIsTrusted
	 * 
	 * @param certToCheck
	 */
	public boolean certificateIsTrusted(Certificate certToCheck)
			throws InvalidCertificate {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);

			ts.load(is, trustStorePassword.toCharArray());
			is.close();

			Enumeration<String> aliases = ts.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if (ts.isCertificateEntry(alias)) {
					/**
					 * certificate is trusted
					 */
					X509Certificate tc = (X509Certificate) ts
							.getCertificate(alias);
					try {
						tc.checkValidity();
						/**
						 * certificate is valid
						 */
						if (tc.equals(certToCheck)) {
							return true;
						} else {
							log.warn("Certificate [" + alias
									+ "] is not valid.");
						}
					} catch (CertificateNotYetValidException e) {
						log.error("Certificate is not yet valid: ", e);
						throw e;
					} catch (CertificateExpiredException e) {
						log.error("Certificate is expired: ", e);
						throw e;
					} /*catch (InvalidCertificate e) {
						throw e;
					}*/
				}
			}
			return false;
		} catch (Exception e) {
			throw FaultUtil.InvalidCertificate("Exception: " + e);
		}
	}

	/**
	 * getCertificateAlias
	 * 
	 * @param cert
	 */
	public String getCertificateAlias(Certificate cert)
			throws InvalidCertificate {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);
			ts.load(is, trustStorePassword.toCharArray());
			is.close();
			return ts.getCertificateAlias(cert);
		} catch (Exception e) {
			throw FaultUtil.InvalidCertificate("Exception: " + e);
		}
	}

	/**
	 * getCertificateFromAlias return the certificate corresponding to this
	 * alias
	 * 
	 * @param certString
	 */
	public Certificate getCertificateFromAlias(String certAlias)
			throws InvalidCertificate {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);
			ts.load(is, trustStorePassword.toCharArray());
			is.close();

			return ts.getCertificate(certAlias);
		} catch (Exception e) {
			throw FaultUtil.InvalidCertificate("Exception: " + e);
		}
	}

	/**
	 * thumbprintIsTrusted
	 * 
	 * @param thumbprint
	 */
	public void thumbprintIsTrusted(String thumbprint)
			throws InvalidCertificate {
		try {
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream is = new FileInputStream(trustStoreFileName);

			ts.load(is, trustStorePassword.toCharArray());
			is.close();

			Enumeration<String> aliases = ts.aliases();

			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if (ts.isCertificateEntry(alias)) {
					/**
					 * certificate is trusted
					 */
					X509Certificate tc = (X509Certificate) ts
							.getCertificate(alias);
					if (thumbprint.equals(getCertificateThumbprint(ts
							.getCertificate(alias)))) {
						try {
							tc.checkValidity();
							return;
						} catch (Exception e) {
							throw FaultUtil.InvalidCertificate(
									"cert with thumprint is not valid", e);
						}
					}
				}
			}
			throw FaultUtil
					.InvalidCertificate("could not find certifcate that matches thumbprint");
		} catch (InvalidCertificate ic) {
			throw ic;
		} catch (Exception e) {
			throw FaultUtil.InvalidCertificate("Exception: " + e);
		}
	}

	/**
	 * Stop and restart the SSL connection so that the tomcat server will
	 * re-read the certificates from the truststore file.
	 * 
	 */
	public void refreshTrustStore() throws Exception {
		try {
			// MBeanServer mBeanServer = MBeanUtils.createServer();
			MBeanServer mBeanServer = ManagementFactory
					.getPlatformMBeanServer();
			Set names = mBeanServer.queryNames(new ObjectName("*:*"), null);

			Iterator it = names.iterator();
			while (it.hasNext()) {
				ObjectName oname = (ObjectName) it.next();

				MBeanInfo minfo = mBeanServer.getMBeanInfo(oname);

				String mBeanInfoClass = minfo.getClassName();
				boolean condition = "org.apache.catalina.mbeans.ConnectorMBean"
						.equals(mBeanInfoClass)
						|| "org.mortbay.jetty.security.SslSocketConnector"
								.equals(mBeanInfoClass)
						|| "org.eclipse.jetty.server.ssl.SslSocketConnector"
								.equals(mBeanInfoClass);

				if (condition) {
					String protocol = (String) mBeanServer.getAttribute(oname,
							"protocol");
					if (protocol.toLowerCase().startsWith("http")) {
						boolean isSecure = ((mBeanServer.getAttribute(oname,
								"secure") != null) && (mBeanServer
								.getAttribute(oname, "secure").toString()
								.equalsIgnoreCase("true")));
						boolean isSchemeHTTPS = ((mBeanServer.getAttribute(
								oname, "scheme") != null) && (mBeanServer
								.getAttribute(oname, "scheme").toString()
								.equalsIgnoreCase("https")));

						if (isSecure && isSchemeHTTPS) {
							log.debug("Restarting SSL Connector on port "
									+ (Object) mBeanServer.getAttribute(oname,
											"port"));
							Object params[] = {};
							String signature[] = {};

							/**
							 * Stop and restart the connector to get it to
							 * re-read the certificate trustfile
							 */
							mBeanServer
									.invoke(oname, "stop", params, signature);
							mBeanServer.invoke(oname, "start", params,
									signature);
						}
					}
				}
			}
		} catch (Exception e) {
			log.debug("Did not restart SSL Connector: " + e);
			e.printStackTrace();
			throw e;
		}
	}

	public SessionContext getCurrentSessionContext() throws InvalidSession,
			StorageFault {

		String sessionId = getCookie(VASA_SESSIONID_STR);

		return SessionContext.lookupSessionContextBySessionId(sessionId);

	}
}


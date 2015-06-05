/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.http.ssl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;


public class PermissiveX509KeyManager implements KeyManager {
	
    public KeyManager[] getPermissiveX509KeyManager() throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, GeneralSecurityException {
		KeyStore keyStore;
		keyStore = KeyStore.getInstance(System.getProperty("javax.net.ssl.keyStoreType"));
		FileInputStream ksfis = new FileInputStream(System.getProperty("javax.net.ssl.keyStore"));
		char[] kspasswd = System.getProperty("javax.net.ssl.keyStorePassword").toCharArray();
		keyStore.load(ksfis, kspasswd);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
		
		kmf.init(keyStore,kspasswd);
        return kmf.getKeyManagers();
    }
    

    
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.utils;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.Crypt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.systemservices.impl.util.LocalPasswordHandler;



public class LocalPasswordHandlerTest extends LocalPasswordHandlerTestBase {

    private static final String SYSTEM_ENCPASSWORD_FORMAT = "system_%s_encpassword";  //NOSONAR ("squid:S2068 Suppressing sonar violation of hard-coded password")

    /**
     * local user sysmonitor
     */
    public static final String LOCAL_SYSMON = "sysmonitor";
    /**
     * local user root
     */
    public static final String LOCAL_ROOT = "root";

    /**
     * local user svcuser
     */
    public static final String LOCAL_SVCUSER = "svcuser"; 
    
    /**
     * local user proxyuser
     */
    public static final String LOCAL_PROXYUSER = "proxyuser";
    
    @Before
    public void setUp() {
        // fill in the fake ovf repository
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_ROOT), "");
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_SYSMON), "");
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_PROXYUSER), "");
        setPropsMetaData();
        _encryptionProvider.start();
    }

    @Test
    public void testCheckUserExists() {

        LocalPasswordHandler ph  = getPasswordHandler();
        ph.setLocalUsers(createLocalUsers());

        Assert.assertTrue(ph.checkUserExists(LOCAL_ROOT));

        Assert.assertTrue(ph.checkUserExists(LOCAL_SYSMON));
        
        Assert.assertTrue(ph.checkUserExists(LOCAL_PROXYUSER));

        Assert.assertFalse(ph.checkUserExists("fakeuser"));

    }
 
    @Test
    public void testSetAndVerifyUserPassword() throws Exception {

        String newPassword = "newPassword123";  //NOSONAR ("squid:S2068 Suppressing sonar violation of hard-coded password")
        
        LocalPasswordHandler ph  = getPasswordHandler();
        
        changeAndVerifyUserPassword(LOCAL_ROOT, newPassword, "hashed", ph);
        changeAndVerifyUserPassword(LOCAL_SYSMON, newPassword, "hashed", ph);
        changeAndVerifyUserPassword(LOCAL_PROXYUSER, newPassword, "encrypted", ph);     
    }

    @Test
    public void testResetUserPassword() throws Exception {

        String resetPassword = "freshPassword123";  //NOSONAR ("squid:S2068 Suppressing sonar violation of hard-coded password")
        
        LocalPasswordHandler ph  = getPasswordHandler();
        
        changeAndVerifyUserPassword(LOCAL_ROOT, resetPassword, "hashed", ph);
        changeAndVerifyUserPassword(LOCAL_PROXYUSER, resetPassword, "encrypted", ph);      
    }
    
    private void changeAndVerifyUserPassword(String username, String password, String security, LocalPasswordHandler ph)
            throws Exception{
    	
    	if (security.equals("hashed")) {
    		ph.setUserPassword(username, password, false);
    		String storedHashed = _passwordProps.getProperty(
                    String.format(SYSTEM_ENCPASSWORD_FORMAT, username));
            String hashed = Crypt.crypt(password, storedHashed);
            Assert.assertTrue(hashed.equals(storedHashed));
    	}
    	else if (security.equals("encrypted")) {
    		ph.setUserEncryptedPassword(LOCAL_PROXYUSER, password, false);
            String storedPassword = _encryptionProvider.decrypt(Base64.decodeBase64(_passwordProps.getProperty(
                    String.format(SYSTEM_ENCPASSWORD_FORMAT, username)).getBytes("UTF-8")));
            Assert.assertTrue(storedPassword.equals(password));          
    	}
    	
    }
      
    private Map<String, StorageOSUser> createLocalUsers() {

        Map<String, StorageOSUser> locals = new HashMap<String, StorageOSUser>(); 
        locals.put(LOCAL_ROOT, new StorageOSUser(
                        LOCAL_ROOT,
                        ""));
        locals.put(LOCAL_SYSMON,
                new StorageOSUser(
                        LOCAL_SYSMON, ""));
        locals.put(LOCAL_PROXYUSER,
        		new StorageOSUser(
        				LOCAL_PROXYUSER, ""));
        
        return locals;
    }
}

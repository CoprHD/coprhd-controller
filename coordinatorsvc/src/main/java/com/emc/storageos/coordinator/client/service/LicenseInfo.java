/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.coordinator.client.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;

public class LicenseInfo {
    private static final Logger _log = LoggerFactory.getLogger(LicenseInfo.class);
    // property constants for coordinator.	
    public static final String LICENSE_TYPE = "licenseType";
    public static final String LICENSE_TEXT = "licenseText";
    public static final String EXPIRATION_DATE = "expirationDate";
    // coordinator encoding variables.
    public static final String LICENSE_SEPARATOR = "\29";
    public static final String ENCODING_SEPARATOR = "\0";
    public static final String ENCODING_EQUAL = "=";
    // used to denote that there is no defined value yet in coordinator service
    public static final String VALUE_NOT_SET = "NA";
	
    // license type
    protected LicenseType _licenseType;
	
    // license expiration date per license
    protected String _expirationDate;
    // coordinator target property if for both controller and object store
    // license data.
    public static final String TARGET_PROPERTY_ID = "global";
	
    // coordinator values for storing list of license features starting 1.1. Starting Vipr 1.1 
    // all license features' are to be stored together in one data node in coordinator. 
    public static final String LICENSE_INFO = "viprLicenseInfo";
    public static final String LICENSE_INFO_TARGET_PROPERTY = "viprLicenseInfoProperty";
	
	
    /**
     * Default constructor
     */
    public LicenseInfo() { }		
		

    /**
     * Constructor which constructs a LicenseInfo object with an expiration date. 
     * @param expirationDate
     */
    public LicenseInfo(LicenseType licenseType, String expirationDate) {
        this._licenseType = licenseType;
        this._expirationDate = expirationDate;
    }
		
    public LicenseType getLicenseType() {
        return _licenseType;
    }

    public void setLicenseType(LicenseType licenseType) {
        this._licenseType = licenseType;
    }
	
    public String getExpirationDate() {
        return _expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this._expirationDate = expirationDate;
    }
	
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append("licenseType=");
        s.append(_licenseType.toString());
        s.append(" expirationDate=");
        s.append(_expirationDate);
        return s.toString();
    }
		
    /**
     * Method used for decoding a list of licenses from the string. 
     * @param infoStr
     * @return a list of decoded license info
     * @throws Exception
     */
    public static List<LicenseInfo> decodeLicenses(String infoStr) throws Exception {
        _log.info("Retrieving licenses from coordinator service");
        List<LicenseInfo> licenseList = new ArrayList<LicenseInfo>();
        if (infoStr != null && !infoStr.isEmpty()) {
            for (String licenseStr : infoStr.split(LICENSE_SEPARATOR)) {                
                String expireDate = null;
                LicenseType licenseType = null;
            	for (String licenseProps : licenseStr.split(ENCODING_SEPARATOR)) {
                    String[] licenseProp = licenseProps.split(ENCODING_EQUAL);
                    if(licenseProp.length < 2 )
                        continue;
                    
                    if (licenseProp[0].equalsIgnoreCase(LICENSE_TYPE)) {    			            			    	
                        licenseType = LicenseType.findByValue(licenseProp[1]);
                    }
                    if (licenseProp[0].equalsIgnoreCase(EXPIRATION_DATE)) {    			        
                        expireDate = licenseProp[1];
                    }
                    if(licenseType != null && expireDate != null) {
                        licenseList.add(new LicenseInfo(licenseType, expireDate));
                        break;
                    }
                    
                 }
             }
        }
        return licenseList;
    }
}

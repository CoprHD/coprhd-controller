/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.upgrade.beans;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.db.client.model.EncryptionProvider;


public class SoftwareUpdate {

    private static volatile EncryptionProvider _encryptionProvider;
    private static volatile List<String> _catalogServerHostNames;
    private static volatile String _catalogKey;
    private static volatile String _catalogCategory;
    private static volatile String _catalogLanguage;
    private static volatile String _catalogEnvironment;
    private static String _catalogMajorRev = "";
    private static String _catalogMinorRev  = "";
    private static String _catalogBuildNumber = "";
    
    private static final String EMC_CATALOG_POST_CONTENT =  
            "<SOAP-ENV:Envelope " +
            "xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:SOAP-ENC=\"http://schemas.xmlsoap.org/soap/encoding/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:tns=\"{0}/ClariionWSL.DownloadCatalog.svc\" > " +
            "<SOAP-ENV:Body> " +
            "<tns:downloadUpdates xmlns:tns=\"{0}/ClariionWSL.DownloadCatalog.svc\"> " +
            "<downloadUpdatesIN > <productModel xsi:type=\"xsd:string\" >{1}</productModel > " +
            "<category xsi:type=\"xsd:string\" >{2}</category > " +
            "<majorRevision xsi:type=\"xsd:string\" >{3}</majorRevision > " +
            "<minorRevision xsi:type=\"xsd:string\" >{4}</minorRevision > " +
            "<language xsi:type=\"xsd:string\" >{5}</language > " +
            "<build xsi:type=\"xsd:string\" >{6}</build > " +
            "<environment xsi:type=\"xsd:string\" >{7}</environment > " +
            "</downloadUpdatesIN > " +
            "</tns:downloadUpdates > " +
            "</SOAP-ENV:Body> " +
            "</SOAP-ENV:Envelope>";
    private static final String EMC_SSO_AUTH_SERVICE_LOGIN_POST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><user><password>{0}</password><username>{1}</username></user>";
    
    protected SoftwareUpdate() {
        
    }
    
    public void setCatalogServerHostNames(List<String> catalogServerHostNames) {
        _catalogServerHostNames = catalogServerHostNames;        
    }
    
    public void setCatalogName(final String catalogName) {
        String[] catalogParts = catalogName.split("\\.");
        if( catalogParts.length < 4) {
            throw APIException.internalServerErrors.invalidObject("catalog name");
        }
        _catalogKey = catalogParts[0];
        _catalogCategory = catalogParts[1];
        _catalogLanguage = catalogParts[2];
        _catalogEnvironment = catalogParts[3];
    }
    public void setEncryptionProvider( EncryptionProvider encryptionProvider ) {
        _encryptionProvider = encryptionProvider;
    }
    public static boolean isCatalogServer(final URL url) {
        if( null == _catalogServerHostNames ) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("catalog server host names");
        }
        for(String catalogServerHostName : _catalogServerHostNames ) {
            if(catalogServerHostName.equalsIgnoreCase(url.getHost())) {
                return true;
            }
        }
        return false;
    }
    
    public static String getCatalogPostContent(final URL url) {
        if( null == _catalogKey || null ==  _catalogCategory 
                || null == _catalogLanguage || null == _catalogEnvironment ) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("catalog name");
        }
        
        return MessageFormat.format(EMC_CATALOG_POST_CONTENT, 
                new Object[] { url, _catalogKey, _catalogCategory, 
                        _catalogMajorRev, _catalogMinorRev, 
                        _catalogLanguage, _catalogBuildNumber, 
                        _catalogEnvironment });
    }
    
    public static String getDownloadLoginContent(final String username, final String encryptedPassword) throws UnsupportedEncodingException {
        return MessageFormat.format(EMC_SSO_AUTH_SERVICE_LOGIN_POST_CONTENT, _encryptionProvider.decrypt(Base64.decodeBase64(encryptedPassword.getBytes("UTF-8"))), username);
    }
}

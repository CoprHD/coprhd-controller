/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.model.usergroup.UserAttributeParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.AttributesMapper;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Map from LDAP attributes to a storageOS user object
 */
public class StorageOSUserMapper implements AttributesMapper {

    private static final Logger _log = LoggerFactory.getLogger(StorageOSUserMapper.class);
    
    private String _username;
    private String _distinguishedNameAttribute;
    private Map<String, List<String>> _attrKeyValueMap;
    
    public StorageOSUserMapper(String username, String distinguishedNameAttribute, Map<String, List<String>> attrKeyValueMap) {
        super();
        _username = username;
        _attrKeyValueMap = attrKeyValueMap;
        _distinguishedNameAttribute = distinguishedNameAttribute;
    }
    
    /*
     * @see org.springframework.ldap.core.AttributesMapper#mapFromAttributes(javax.naming.directory.Attributes)
     * creates StorageOSUserDAO from attributes
     */
    @Override
    public Object mapFromAttributes(Attributes attributes) throws NamingException {
        StorageOSUserDAO storageOSUser = new StorageOSUserDAO();
        storageOSUser.setUserName(_username);
        NamingEnumeration<? extends Attribute> attributesEnumeration = attributes.getAll();
        while(attributesEnumeration.hasMoreElements()) {
            Attribute attribute = attributesEnumeration.nextElement();
            NamingEnumeration<?> attributeValues = attribute.getAll();
            if( attribute.getID().equals(_distinguishedNameAttribute)) {
                if( null != attribute.get(0) ) {
                    storageOSUser.setDistinguishedName(attribute.get(0).toString());
                }
            }
            List<String> values = new ArrayList<String>();
            while( attributeValues.hasMoreElements()) {
                values.add(attributeValues.nextElement().toString()); 
            }
            _attrKeyValueMap.put(attribute.getID(), values);

            //Add the returned attributes from the AD/LDAP to the user.
            UserAttributeParam userAttributeParam = new UserAttributeParam(attribute.getID(), new HashSet(values));
            String attributeString = userAttributeParam.toString();
            storageOSUser.addAttribute(attributeString);
            _log.debug("Adding attribute {} to user", attributeString);
        }
        return storageOSUser;
    }
}

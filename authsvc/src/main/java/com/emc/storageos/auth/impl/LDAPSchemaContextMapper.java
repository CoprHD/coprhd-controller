/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.AbstractContextMapper;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema context mapper the LDAP.
 *
 */
public class LDAPSchemaContextMapper extends AbstractContextMapper {
	
	private static final Logger _log = LoggerFactory.getLogger(StorageOSPersonAttributeDao.class);
	
	// Should extract objectClass from NAME 'objectClass'
	private static final String REGEX_PATTERN_TO_EXTRACT_SINGLE_NAME = "NAME '(.*?)'";
	
	// Should extract 'cn' 'commonname' from // NAME ( 'cn' 'commonname' )
	private static final String REGEX_PATTERN_TO_EXTRACT_MULTIPLE_NAME = "NAME \\( (.*?) \\)";
	
	// Should extract cn from 'cn'. 
	//This is used only in case of multiple values present attribute type NAME.
	private static final String REGEX_PATTERN_TO_EXTRACT_WITHIN_SINGLE_QUOTATIONS = "'(.*?)'";

	/* (non-Javadoc)
	 * @see org.springframework.ldap.core.support.AbstractContextMapper#doMapFromContext(org.springframework.ldap.core.DirContextOperations)
	 */
	@Override
	protected Object doMapFromContext(DirContextOperations ctx) {
		NamingEnumeration<?extends Attribute> attributes = ctx.getAttributes().getAll();
        List<String> nameList = new ArrayList<String>();
        while (attributes.hasMoreElements()){
        	Attribute attribute = attributes.nextElement();
        	try {
				NamingEnumeration<?> values = attribute.getAll();
				while(values.hasMoreElements()){
					String attributeValue = (String) values.nextElement();
					Matcher matcher = Pattern.compile(REGEX_PATTERN_TO_EXTRACT_SINGLE_NAME).matcher(attributeValue);
					if(matcher.find()){
						nameList.add(matcher.group(1));
					}else{
						nameList.addAll(extractNameFromListOfNames(attributeValue));
					}
				}
			} catch (NamingException e) {
				_log.error("Exception in Schema mapping from Context {} ", e.toString());
			}
        }
        
        return nameList;
	}

	/**
	 * Method to extract the individual name (or string) if the attribute value
	 * contains multiple names (or strings) in a list.
	 * Eg.  Extracts cn and commonname from NAME ( 'cn' 'commonname' ) and
	 * return cn and commonname in a list of strings.
	 * 
	 * @param attributeValue - A string that contains the multiple strings in the 
	 * 							pattern NAME ( 'cn' 'commonname' )
	 * 
	 * @return extracted list of individual strings that matches the regex pattern. 
	 */
	private List<String> extractNameFromListOfNames(String attributeValue) {
		List<String> nameList = new ArrayList<String>();
		Matcher matcher = Pattern.compile(REGEX_PATTERN_TO_EXTRACT_MULTIPLE_NAME).matcher(attributeValue);
		while (matcher.find()) {
			String nameWithQuotes = matcher.group(1);
			Matcher quotesMatcher = Pattern.compile(REGEX_PATTERN_TO_EXTRACT_WITHIN_SINGLE_QUOTATIONS).matcher(nameWithQuotes);
			while(quotesMatcher.find()){
				String nameWithoutQuotes = quotesMatcher.group(1);
				nameList.add(nameWithoutQuotes);
			}
		}
		
		return nameList;
	}

}

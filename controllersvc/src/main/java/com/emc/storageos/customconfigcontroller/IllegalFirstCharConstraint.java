/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.util.Map;
import java.util.regex.Pattern;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

@SuppressWarnings("serial")
public class IllegalFirstCharConstraint extends IllegalCharsConstraint {

	@Override
	public String applyConstraint(String dataField, String systemType) {
	    String first  = dataField.substring(0,1);
	    return dataField.replace(first, super.applyConstraint(first, systemType));
	}
    
    @Override
    public void validate(String dataField, String systemType) {
        String illegalCharsRegex = getValueFromMap(getIllegalCharsRegexMap(), systemType);
        Pattern pattern = Pattern.compile(illegalCharsRegex);
        String start = dataField.substring(0,1);
        if (pattern.matcher(start).find()) {
            throw CustomConfigControllerException.exceptions.illegalFirstCharConstraintViolated(dataField, systemType, start );
        }
    }

    /**
     * At some point this should move into a base class
     * @param systemType
     * @return
     */
	private String getValueFromMap(Map<String, String> map, String systemType) {
        String chars = map.get(CustomConfigConstants.DEFAULT_KEY);
        if ( map.containsKey(systemType)) {
            chars = map.get(systemType);
        }
        return chars;
    }

}

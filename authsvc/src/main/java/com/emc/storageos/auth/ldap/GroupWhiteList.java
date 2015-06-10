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
package com.emc.storageos.auth.ldap;

import java.util.regex.Pattern;

/**
 * Group filter white list.
 * 
 */
public class GroupWhiteList {
    private String _type;
    private String[] _values;
    private Pattern[] _compiledPatterns;

    public static final GroupWhiteList SID = new GroupWhiteList("objectSid",
        "*");

    /**
     * Default constructor
     */
    public GroupWhiteList() {
        super();
    }

    /**
     * @param type
     * @param value
     */
    public GroupWhiteList(String type, String value) {
        this(type, new String[] { value });
    }

    /**
     * @param type
     * @param values
     */
    public GroupWhiteList(String type, String[] values) {
        super();
        _type = type;
        _values = values.clone();
        _compiledPatterns = getCompiledPatterns();
    }

    /**
     * Get Group White List type. Possible values can be objectSid, CN, DN,
     * sAMAccountName, etc. All non-binary attributes and objectSid can be used
     * as White List type.
     * 
     * @return Group White List type
     */
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
    }

    /**
     * @return White List values for the specified White List type.
     */
    public String[] getValues() {
        return _values;
    }

    public void setValue(String value) {
        setValues(new String[] { value });
    }

    public void setValues(String[] values) {
        _values = values.clone();
    }

    /**
     * @return compiled list of white list values
     */
    public Pattern[] getCompiledPatterns() {
        if (_compiledPatterns != null)
            return _compiledPatterns;

        if (_values != null && _values.length > 0) {
            _compiledPatterns = new Pattern[_values.length];

            if (_values != null) {
                int i = 0;
                for (String value : _values) {
                    _compiledPatterns[i] = Pattern.compile(value.replace("*",
                        ".*"), Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
                    i++;
                }
            }
        }
        return _compiledPatterns;
    }
}

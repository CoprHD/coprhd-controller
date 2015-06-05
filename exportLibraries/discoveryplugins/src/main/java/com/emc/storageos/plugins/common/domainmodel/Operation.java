/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.common.domainmodel;

import java.util.List;

import com.emc.storageos.plugins.common.Processor;


public class Operation {
    private List<Object> _arguments;
    private String       _result;
    private String       _method;
    private String       _executionCycles;
    private Processor    _processor;
    private String        message;
    private String        supportedVersion;
    /**
     * instance to execute to all operations. It will depend on the interface
     * type. Ex. for SMI : WBEMClient, for REST : httpClient.
     */
    private Object       _instance;
    
    private String _type;
    

    public void set_arguments(List<Object> _arguments) {
        this._arguments = _arguments;
    }

    public List<Object> get_arguments() {
        return _arguments;
    }

    public void set_result(String _result) {
        this._result = _result;
    }

    public String get_result() {
        return _result;
    }

    public void set_method(String _method) {
        this._method = _method;
    }

    public String get_method() {
        return _method;
    }

    public void set_processor(Processor _processor) {
        this._processor = _processor;
    }

    public Processor get_processor() {
        return _processor;
    }

    /**
     * @return the instance
     */
    public Object getInstance() {
        return _instance;
    }

    /**
     * @param instance
     *            the instance to set
     */
    public void setInstance(final Object instance) {
        _instance = instance;
    }
   
    public void setExecutionCycles(String executionCycles) {
        _executionCycles = executionCycles;
    }
    /**
     * Number of executions on an operation.
     * Number of times, an operation needs to get executed.
     * @return
     */
    public String getExecutionCycles() {
        return _executionCycles;
    }
    
    public void set_type(String _type) {
        this._type = _type;
    }
    
    /**
     * Class Type of the Argument.
     * String.Class, CIMObjectPath.class
     * @return
     */
    public String get_type() {
        return _type;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }

    /**
     * Get the supported version to execute this operation.
     * @return the filter
     */
    public String getSupportedVersion() {
        return supportedVersion;
    }

    /**
     * Set the supported version to execute this operation.
     * @param filter the filter to set
     */
    public void setSupportedVersion(String supportedVersion) {
        this.supportedVersion = supportedVersion;
    }
}

/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.iwave.ext.vmware;

/**
 * 
 * EsxVersion
 * 
 * @author kumara4
 *
 */
public class EsxVersion {

    /**
     * Esx version
     */
    private String version;

    /**
     * Default constructor
     */
    public EsxVersion() {
        super();
    }

    /**
     * Constructor
     * 
     * @param version string
     */
    public EsxVersion(String version) {
        this.version = version;
    }

    /**
     * Return version of Esx
     * 
     * @return
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set version
     * 
     * @param version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return this.version;
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

/**
 * 
 * EsxVersion
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
     * @param version string
     */
    public EsxVersion(String version) {
        this.version = version;
    }
    
    /**
     * Return version of Esx
     * @return
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set version
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

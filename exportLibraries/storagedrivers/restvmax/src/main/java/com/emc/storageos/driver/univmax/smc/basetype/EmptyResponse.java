/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.basetype;

public class EmptyResponse extends DefaultResponse {

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "EmptyResponse [getMessage()=" + getCustMessage() + ", getHttpStatusCode()=" + getHttpStatusCode() + "]";
    }

}

/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.basetype;

public interface IParameter {

    String bean2Json();

    /**
     * Translate ViPR pojo to this pojo
     * 
     * @param viprPojo
     */
    void fromViprPojo(Object viprPojo);
}

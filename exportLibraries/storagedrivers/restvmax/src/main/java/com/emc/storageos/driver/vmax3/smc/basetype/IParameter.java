/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public interface IParameter {

    String bean2Json();

    /**
     * Translate ViPR pojo to this pojo
     * 
     * @param viprPojo
     */
    <T> void fromViprPojo(T viprPojo);
}

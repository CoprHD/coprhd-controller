package com.emc.storageos.blockorchestrationcontroller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public aspect ViPRWrapper {

    private static final Logger log = LoggerFactory.getLogger(ViPRWrapper.class);

    pointcut greeting() : execution(* BlockOrchestrationDeviceController.createVolumes(..));
    
    after() returning() : greeting() {
        log.info(" hello!");
    }
}

/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.recoverpoint;

import java.io.PrintWriter;
import java.util.Map;

import com.emc.storageos.volumecontroller.impl.plugins.metering.XMLStatsDumpGenerator;
/**
 * Dump the RP stat records using the below header & tailer format
 * to identify the RP stats. 
 *
 */
public class RPXMLStatsDumpGenerator extends XMLStatsDumpGenerator {

    @Override
    protected void delegateheader(PrintWriter writer) {
        writer.println("<rpStats>");
    }

    @Override
    protected void delegatetailer(PrintWriter writer) {
        writer.println("</rpStats>");
    }

    @Override
    protected String generateUniqueKey(Map<String, Object> keyMap) {
        return (String) keyMap.get("serialID") + "-rp-";
    }

}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

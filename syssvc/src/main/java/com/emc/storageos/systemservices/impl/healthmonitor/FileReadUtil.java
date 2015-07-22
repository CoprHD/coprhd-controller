/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.systemservices.exceptions.SyssvcInternalException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileReadUtil {
    private static final Logger _log = LoggerFactory.getLogger(FileReadUtil.class);

    /**
     * Read a file by it's full filePath. Log the exceptions if encountered and return
     * an empty string. Let the caller check for null/empty return.
     *
     * @return String
     */
    public static String[] readLines(final String filePath) throws IOException,
            SyssvcInternalException {
        BufferedReader reader = null;
        try {
            List<String> lines = new ArrayList<String>();
            String line;
            reader = new BufferedReader(new FileReader(filePath));
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            if (lines.isEmpty()) {
                throw SyssvcException.syssvcExceptions.syssvcInternalError("File " + filePath + " is empty.");
            }
            return lines.toArray(new String[lines.size()]);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Reads and returns entire file as one string delimited by space.
     */
    public static String readFirstLine(final String filePath) throws IOException,
            SyssvcInternalException {
        return readLines(filePath)[0];
    }
}

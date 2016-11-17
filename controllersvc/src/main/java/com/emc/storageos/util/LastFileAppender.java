/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.FileAppender;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Log file appender that can specifically handle the logfile getting deleted
 * while the services are still running. All the googling in the world was not
 * able to find a good technical solution to this issue, so we'll keep the log4j
 * pattern, but the append() method here will be capable of writing out to the logfile
 * in the case of the file being deleted while the services are up.
 */
public class LastFileAppender extends FileAppender {
    FileOutputStream fop = null;

    @Override
    public void append(LoggingEvent event) {
        if (checkLogFileExist()) {
            super.append(event);
        } else {
            try {
                StringBuffer sb = new StringBuffer(event.getMessage() + "\n");
                // Last chance, if file is deleted, write manually.
                fop.write(sb.toString().getBytes());
                fop.flush();
            } catch (IOException e) {
                // Ignore, used for debug only
            }
        }
    }

    private boolean checkLogFileExist() {
        File logFile = new File(super.fileName);

        // If we're using fop, act like we're not using the base logger.
        if (fop != null) {
            return false;
        }

        if (!logFile.exists()) {
            try {
                super.closeWriter();
                logFile.createNewFile();
                fop = new FileOutputStream(logFile);
                super.createWriter(fop);
                return false;
            } catch (IOException e) {
                System.out.println("Error while create new log file.");
            }
        }
        return true;
    }
}
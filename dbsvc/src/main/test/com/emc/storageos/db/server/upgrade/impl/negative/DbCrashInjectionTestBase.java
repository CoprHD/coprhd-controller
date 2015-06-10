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
package com.emc.storageos.db.server.upgrade.impl.negative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedReader; 
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.reflect.Method;
import java.beans.*;

import com.emc.storageos.db.server.upgrade.util.MigrationServer;
import static com.emc.storageos.db.server.upgrade.util.DbSchemaChanger.InjectModeEnum;

/**
 * tests services crash and recover in upgrade scenario
 */
public class DbCrashInjectionTestBase{
    private static final Logger log = LoggerFactory.getLogger(DbCrashInjectionTestBase.class);

    protected void upgradeNegativeTest(Method method, InjectModeEnum mode) throws Exception {
        if (method == null) {
            throw new IllegalArgumentException("method should not be null");
        }

        log.info("inject point: {}, mode: {}", method.toString(), mode.toString());

        int killResult = startMigrationProcess(method, mode);
        if (killResult == 0 ){
            log.error("Failed to make service crash!");
            return;
        }

        log.info("Begin to remigration...");
        startMigrationProcess(null, null);
    }

    private int startMigrationProcess(Method method, InjectModeEnum mode) throws Exception {
        String classPath = System.getProperty("java.class.path");
        ProcessBuilder processBuilder = null;
        if (method == null) {
            processBuilder = new ProcessBuilder("java",
                                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000",
                                "-cp", classPath, MigrationServer.class.getName());
        } else {
            processBuilder = new ProcessBuilder("java", 
                                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000", 
                                "-cp", classPath, MigrationServer.class.getName(),
                                method.getDeclaringClass().getName(), method.getName(), mode.toString());
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(new File("."));
        final Process pMigration = processBuilder.start();
        doWaitFor(pMigration);

        return pMigration.exitValue();
    }

    private void doWaitFor(Process process) {
        startReadStreamThread(process.getInputStream());
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.warn("e=", e);
        }
    }

    private void startReadStreamThread(final InputStream is) {
        new Thread() {
            public void run() {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                try {
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        log.info("{}", line);
                    }
                } catch (IOException e) {
                     log.error("e=", e);
                } finally{
                    try {
                        br.close();
                    } catch (IOException e) {
                        log.error("e=", e);
                    }
                }
            }
        }.start();
    }
}

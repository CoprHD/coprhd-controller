/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.server.upgrade.DbStepSkipUpgradeTestBase;
import static com.emc.storageos.db.server.upgrade.util.DbSchemaChanger.InjectModeEnum;

public class MigrationServer extends DbStepSkipUpgradeTestBase {
    private static final Logger log = LoggerFactory.getLogger(MigrationServer.class);

    private static String getInsertCodes() {
        StringBuilder codes = new StringBuilder();
        codes.append("System.out.println(\"To insert codes...\");");
        codes.append("System.exit(-1);");

        return codes.toString();
    }

    private static void insertCodes(String className, String methodName, String injectMode) throws Exception {
        log.info("className={}, methodName={}", className, methodName);

        DbSchemaChanger changer = new DbSchemaChanger(className);
        changer.insertCodes(methodName, getInsertCodes(), InjectModeEnum.valueOf(injectMode));
    }

    public void startMigration(String className, String methodName, String injectMode) {
        try {
            setup();

            stopAll();
            startDb(initalVersion, initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
            prepareData1();
            prepareData2();

            stopAll();
            insertCodes(className, methodName, injectMode);
            startDb(initalVersion, secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
            log.info("passed the migration, begin to check result");

            verifyAll();
            stop();
        } catch (IllegalStateException e) {
            log.info("Catch the negative test exception:", e);
        } catch (Exception e) {
            log.info("Catch the negative test exception:", e);
        }
    }

    public void startMigration() {
        try {
            setup();

            stopAll();
            startDb(initalVersion, secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
            log.info("passed the migration, begin to check result");

            verifyAll();
            stop();
        } catch (IllegalStateException e) {
            log.info("Catch the negative test exception:", e);
        } catch (Exception e) {
            log.info("Catch the negative test exception:", e);
        }
    }

    public static void main(String[] args) {
        final MigrationServer migrationServer = new MigrationServer();
        int argCnt = args.length;
        if (argCnt == 0) {
            migrationServer.startMigration();
        } else if (argCnt == 3) {
            migrationServer.startMigration(args[0], args[1], args[2]);
        } else {
            log.error("the count of parameters is invalid.");
        }
        System.exit(0);
    }
}

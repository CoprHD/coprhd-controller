/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbtest2;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;


/**
 * Class provided simple cli for DB, to dump records in user readable format
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private enum Command {
        LIST,
        QUERY,
        DELETE,
        SHOW_DEPENDENCY,
        COUNT,
        GET_RECORDS,
        GLOBALLOCK,
        DUMP_SCHEMA,
        DUMP_SECRETKEY,
        RESTORE_SECRETKEY,
        RECOVER_VDC_CONFIG,
        GEOBLACKLIST,
        CHECK_DB,
        REPAIR_DB,
        REBUILD_INDEX,
        RUN_MIGRATION_CALLBACK,
        DUMP_ORDERS
    };


    public static void main(String[] args) throws Exception {

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("dbtest2-conf.xml");

        DbClientTest test = (DbClientTest) ctx.getBean("dbClientTest");
        test.init();
        int writeCount = Integer.parseInt(args[0]);
        test.write(writeCount);

        System.exit(0);

    }
}

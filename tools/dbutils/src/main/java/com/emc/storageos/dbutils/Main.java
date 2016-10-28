/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbutils;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.dbutils.CommandHandler.*;

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
        RUN_MIGRATION_CALLBACK
    };

    private static final String TYPE_EVENTS = "events";
    private static final String TYPE_STATS = "stats";
    private static final String TYPE_AUDITS = "audits";

    public static final String LIST_LIMIT = "-limit";    
    public static final String INACTIVE = "-inactive";
    public static final String MODIFICATION_TIME = "-mf";
    public static final String FILTER = "-filter";

    public static final String RECOVER_DUMP = "-dump";
    public static final String RECOVER_LOAD = "-recover";

    public static final String DELETE_FILE = "-file";

    public static final String BLACKLIST = "blacklist";

    public static final String CF_NAME = "-cf";

    private static DBClient _client = null;

    private static final String LOG_FILE_PATH = "/opt/storageos/logs/dbutils.log";
    private static final String PID_FILE_PATH = "/var/run/storageos/dbutils.pid";
    private static final String STORAGEOS_USER = "storageos";
    private static final String STORAGEOS_GROUP = "storageos";
    private static final String SKIP_MIGRATION_CHECK = "-bypassMigrationCheck";

    /**
     * Dump usage
     */
    private static void usage() {
        System.out.printf("Usage: %n");
        System.out.printf("\t%s [%s <n>] [%s] [%s] [%s <criterias>] <Column Family Name>%n",
                Command.LIST.name().toLowerCase(), LIST_LIMIT, INACTIVE, MODIFICATION_TIME, FILTER);
        System.out.printf("\t\t%s <n>\t List paginated with a limit of <n>, "
                + "if <n> is missing, default is 100.%n", LIST_LIMIT);
        System.out.printf("\t\t%s\t List including inactive=true object ids.%n", INACTIVE);
        System.out.printf("\t\t%s\t\t Show the latest modified field of each record.%n", MODIFICATION_TIME);
        System.out.printf("\t\t%s <criterias>\t Filter with <criterias>, e.g, -filter resource=\"<resource id>\" -filter pending=true.%n", FILTER);
        System.out.printf("\t%s [%s] <Column Family Name> <id>%n", Command.QUERY.name().toLowerCase(), MODIFICATION_TIME);
        System.out.printf("\t\t%s\t\t Show the latest modified field of the record.%n", MODIFICATION_TIME);
        System.out.printf("\t%s <%s/%s/%s> <file_prefix> [<YEAR/MONTH/DAY/HOUR>]%n",
                Command.LIST.name().toLowerCase(), TYPE_EVENTS, TYPE_STATS, TYPE_AUDITS);
        System.out.printf("\t%s [-force] <Column Family Name> <id/-file file_path>%n", Command.DELETE.name().toLowerCase());
        System.out
                .printf("\t\t%s <file_path>\tEvery single line in this file is an object id, multiple object ids should be separated to different line.%n",
                        DELETE_FILE);
        System.out.printf("\t%s <Column Family Name> [id]%n", Command.SHOW_DEPENDENCY.name().toLowerCase());
        System.out.printf("\t\t%s\t\t Print out the exact dependency references for this specific id if exist.%n", "id");
        System.out.printf("\t%s [%s] <Column Family Name>%n",
                Command.COUNT.name().toLowerCase(), INACTIVE);
        System.out.printf("\t\t%s\t Count including inactive object ids.%n", INACTIVE);
        System.out.printf("\t%s <%s/%s/%s> <START TIME> <END TIME>[eg:2012/05/18/15]%n",
                Command.GET_RECORDS.name().toLowerCase(), "Events", "Stats", "AuditLogs");
        System.out.printf("\t%s %s %s %s %s %s%n",
                Command.GLOBALLOCK.name().toLowerCase(), "CREATE", "<lock name>", "<owner>", "(<mode>)", "(<timeout>)");
        System.out.printf("\t\tNote: For <mode>, could be GL_NodeSvcShared_MODE or GL_VdcShared_MODE(default).%n");
        System.out.printf("\t\t    : For <timeout>, unit is millisecond and 0 (default) means never expired.%n");
        System.out.printf("\t%s %s %s %n",
                Command.GLOBALLOCK.name().toLowerCase(), "READ", "<lock name>");
        System.out.printf("\t%s %s %s %n",
                Command.GLOBALLOCK.name().toLowerCase(), "DELETE", "<lock name>");
        System.out.printf("\t%s <schema version> <dump filename>%n",
                Command.DUMP_SCHEMA.name().toLowerCase());
        System.out.printf("\t%s <dump filename>%n",
                Command.DUMP_SECRETKEY.name().toLowerCase());
        System.out.printf("\t%s <restore filename>%n",
                Command.RESTORE_SECRETKEY.name().toLowerCase());
        System.out.printf("\t%s [%s] [%s] Recover Vdc.%n",
                Command.RECOVER_VDC_CONFIG.name().toLowerCase(), RECOVER_DUMP, RECOVER_LOAD);
        System.out.printf("\t%s [%s] [%s] Geodb blacklist.%n",
                Command.GEOBLACKLIST.name().toLowerCase(), "-reset|set", "<vdc short id>");
        System.out.printf("\t%s [Column Family Name]\tCheck data consistency of the whole database%n",
                Command.CHECK_DB.name().toLowerCase());
        System.out.printf("\t\tColumn Family Name\t Only check specific CF.%n");
        System.out.printf("\t%s -db|-geodb [-new] [-crossVdc]%n",
                Command.REPAIR_DB.name().toLowerCase());
        System.out.printf("\t\tNote: %s option can only be executed as %s user%n",
                Command.REPAIR_DB.name().toLowerCase(), STORAGEOS_USER);
        System.out.printf("\t -bypassMigrationCheck%n");
        System.out
                .printf("\t\tNote: it's used with other commands together only when migration fail, dbutils still work even migration fail if you pass this option%n");
        System.out.printf("\t%s <file_path>%n",
                Command.REBUILD_INDEX.name().toLowerCase());
        System.out.printf("\t\t Note: use the genereated file to rebuild the index%n");
        System.out.printf("\t%s %s <Column Family Name>%n",
                Command.REBUILD_INDEX.name().toLowerCase(), CF_NAME);
        System.out.printf("\t\t Note: rebuild specific CF.%n");
        System.out.printf("\t%s <file_path>%n",
                Command.RUN_MIGRATION_CALLBACK.name().toLowerCase());
        System.out.printf("\t\t Note: run specified migration callback.%n");
    }

    /**
     * Stop client and exit
     */
    private static void stop() {
        if (_client != null) {
            _client.stop();
        }
        System.exit(0);
    }

    private static void deleteDbutilsPidFile() {
        File dbutilsPidFile = new File(PID_FILE_PATH);
        boolean delStatus = false;
        try {
            if (dbutilsPidFile.exists()) {
                delStatus = dbutilsPidFile.delete();
            }
        } catch (SecurityException e) {
            if (delStatus == false) {
                log.warn("Failed to delete dbutils pid file: {}", dbutilsPidFile.getPath());
            }
        }
    }

    private static void changeLogFileOwner() throws Exception {
        File f = new File(LOG_FILE_PATH);
        if (f.exists()) {
            PosixFileAttributeView dbutilsLogFile = Files.getFileAttributeView(f.toPath(),
                    PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
            UserPrincipal storageosUser = lookupService.lookupPrincipalByName(STORAGEOS_USER);
            GroupPrincipal storageosGroup = lookupService.lookupPrincipalByGroupName(STORAGEOS_GROUP);
            dbutilsLogFile.setOwner(storageosUser);
            dbutilsLogFile.setGroup(storageosGroup);
        }
    }

    private static void migrateMyOrderIndexes(){
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        long start = System.currentTimeMillis();
        try {
            DataObjectType doType = TypeMap.getDoType(Order.class);
            AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(Order.SUBMITTED_BY_USER_ID), "root",
                    0, 0);
            _client._dbClient.queryByConstraint(constraint, queryResults);
        } catch (DatabaseException e) {
            throw e;
        }

        long i = 0;
        URI id = null;
        long end1 = 0; //read 500000
        long end2 = 0; // read 100000
        long end = 0;
        for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
            id = namedElement.getId();
            i++;
            // log.info("i={} id={}",i, id);
            if (i == 500000) {
                end1 = System.currentTimeMillis();
                System.out.println("Read 500000: "+ (end1 - start));
                System.out.println("i= "+ i);
            }else if (i == 100000) {
                end2 = System.currentTimeMillis();
                System.out.println("Read 100000: "+ (end2 - start));
                System.out.println("i= "+ i);
            }
        }

        end = System.currentTimeMillis();
        System.out.println("Read 1000000: "+ (end - start));
        System.out.println("i= "+ i);
        System.out.println("last id= "+ id);
        System.exit(1);
    }

    private static void queryMyOrderIndexes(){
        System.out.println("start...");
        long start = System.currentTimeMillis();
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        long end1 = 0; //read 500000
        long end2 = 0; // read 100000
        long end = 0;

        long i = 0;
        URI id = null;

        /*

        try {
            DataObjectType doType = TypeMap.getDoType(Order.class);

            AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(Order.SUBMITTED_BY_USER_ID), "root",
                0, 0);
            _client._dbClient.queryByConstraint(constraint, queryResults);
        } catch (DatabaseException e) {
            throw e;
        }

        for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
            id = namedElement.getId();
            i++;
            // log.info("i={} id={}",i, id);
            if (i == 500000) {
                end1 = System.currentTimeMillis();
                System.out.println("Read 500000: "+ (end1 - start));
                System.out.println("i= "+ i);
            }else if (i == 100000) {
                end2 = System.currentTimeMillis();
                System.out.println("Read 100000: "+ (end2 - start));
                System.out.println("i= "+ i);
            }
        }

        end = System.currentTimeMillis();
        System.out.println("Read 1000000: "+ (end - start));
        System.out.println("i= "+ i);
        System.out.println("last id= "+ id);
        */

        URI startId= null;
        int maxCount = 10000;
        // int maxCount = 10;
        i= 0;
        try {
            DataObjectType doType = TypeMap.getDoType(Order.class);

            long endTime = new Date().getTime();
            boolean eof = false;
            while (!eof) {
                eof = true;

                AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField(Order.SUBMITTED_BY_USER_ID), "root",
                        1, endTime);

                // log.info("lbyd2 startId={}", startId);
                _client._dbClient.queryByConstraint(constraint, queryResults, startId, maxCount);

                for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
                    eof = false;
                    startId = namedElement.getId();
                    i++;
                    // log.info("i={} id={}", i, startId);
                    if (i == 100000) {
                    // if (i == 10) {
                        end1 = System.currentTimeMillis();
                        System.out.println("Read2 100000: "+ (end1 - start));
                        System.out.println("i= "+ i);
                        System.out.println("id="+startId);
                        // log.info("lbyd0: id3={}", startId);
                    }else if (i == 500000) {
                    //   }else if (i == 20) {
                        end2 = System.currentTimeMillis();
                        System.out.println("Read2 500000: "+ (end2 - start));
                        System.out.println("i2= "+ i);
                        System.out.println("id2="+startId);
                        // log.info("lbyd1: id={}", startId);
                     //   System.exit(1);
                    }

                    if ( (i > 500000) && (i % 100000 == 0)) {
                        System.out.println("i="+i);
                        System.out.println("id2="+startId);
                    }
                }
            }
        } catch (DatabaseException e) {
            throw e;
        }

        end = System.currentTimeMillis();
        System.out.println("Read2 1000000: "+ (end - start));
        System.out.println("i2= "+ i);
        System.out.println("last id= "+ startId);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Start to collect performance info");
        /*
        deleteDbutilsPidFile();
        changeLogFileOwner();
        if (args.length == 0) {
            usage();
            return;
        }

        boolean skipMigrationCheck = skipMigrationCheck(args);
        // it's a hack of passed arg since we already hard-coded
        // parameter position in args array.
        if (skipMigrationCheck) {
            args = removeMigrationCheckArg(args);
        }

        Command cmd;
        try {
            cmd = Command.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid command " + args[0]);
            usage();
            return;
        }

        if (cmd == Command.REPAIR_DB) {
            if (!System.getProperty("user.name").equals(STORAGEOS_USER)) {
                System.err.println("Please su to storageos user to do \"db repair\" operation");
                System.exit(1);
            }
            System.exit(CommandHandler.repairDb(args));
        }
        */

        try {
            // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            // This is a CLI application and main method will not be called by multiple threads
            //_client = new DBClient(skipMigrationCheck); // NOSONAR ("squid:S2444")
            _client = new DBClient(true); // NOSONAR ("squid:S2444")

            _client.init();
            queryMyOrderIndexes();

            /*
            CommandHandler handler = null;
            boolean result = false;

            switch (cmd) {
                case LIST:
                    _client.init();
                    handler = new ListHandler(args, _client);
                    break;
                case QUERY:
                    _client.init();
                    handler = new QueryHandler(args, _client);
                    break;
                case DELETE:
                    _client.init();
                    handler = new DeleteHandler(args);
                    break;
                case SHOW_DEPENDENCY:
                    _client.init();
                    handler = new DependencyHandler(args);
                    break;
                case COUNT:
                    _client.init();
                    handler = new CountHandler(args);
                    break;
                case GET_RECORDS:
                    _client.init();
                    handler = new RecordHandler(args);
                    break;
                case GLOBALLOCK:
                    _client.init();
                    handler = new GlobalLockHandler(args);
                    break;
                case DUMP_SCHEMA:
                    _client.init();
                    handler = new DumpSchemaHandler(args);
                    break;
                case DUMP_SECRETKEY:
                    _client.init();
                    handler = new DumpKeyHandler(args);
                    break;
                case RESTORE_SECRETKEY:
                    _client.init();
                    handler = new RestoreKeyHandler(args);
                    break;
                case RECOVER_VDC_CONFIG:
                    _client.init();
                    handler = new RecoverVdcHandler(args);
                    break;
                case GEOBLACKLIST:
                    _client.init();
                    handler = new GeoBlacklistHandler(args);
                    break;
                case CHECK_DB:
                    _client.init();
                    handler = new CheckDBHandler(args);
                    break;
                case REBUILD_INDEX:
                    _client.init();
                    handler = new RebuildIndexHandler(args);
                    break;
                case RUN_MIGRATION_CALLBACK:
                    _client.init();
                    handler = new RunMigrationCallback(args);
                    break;    
                default:
                    throw new IllegalArgumentException("Invalid command ");
            }
            handler.process(_client);
            */
        } catch (Exception e) {
            System.err.println("Exception e=" + e);
            log.error("Exception e=", e);
            usage();
        } finally {
            stop();
        }
    }

    private static String[] removeMigrationCheckArg(String[] args) {
        List<String> tmpArgs = new ArrayList<String>();
        for (String arg : args) {
            if (arg != null && arg.equals(SKIP_MIGRATION_CHECK)) {
                continue;
            }
            tmpArgs.add(arg);
        }
        return tmpArgs.toArray(new String[tmpArgs.size()]);
    }

    private static boolean skipMigrationCheck(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.equals(SKIP_MIGRATION_CHECK)) {
                return true;
            }
        }
        return false;
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.dbutils;

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
    };

    private static final String TYPE_EVENTS = "events";
    private static final String TYPE_STATS = "stats";
    private static final String TYPE_AUDITS = "audits";
    
    private static final String LIST_ACTIVE = "-activeonly";
    private static final String LIST_LIMIT = "-limit";
    
    public static final String RECOVER_DUMP = "-dump";
    public static final String RECOVER_LOAD = "-recover";

    public static final String DELETE_FILE = "-file";
    
    public static final String BLACKLIST = "blacklist";
    
    private static DBClient _client = null;

    /**
     * Dump usage
     */
    private static void usage() {
        System.out.printf("Usage: \n");
        System.out.printf("\t%s [%s <n>] [%s] <Column Family Name>\n",
                Command.LIST.name().toLowerCase(), LIST_LIMIT, LIST_ACTIVE);
        System.out.printf("\t\t%s <n>\t List paginated with a limit of <n>, "
                + "if <n> is missing, default is 100.\n", LIST_LIMIT);
        System.out.printf("\t\t%s\t List exclude inactive object ids.\n", LIST_ACTIVE);
        System.out.printf("\t%s <Column Family Name> <id>\n", Command.QUERY.name().toLowerCase());
        System.out.printf("\t%s <%s/%s/%s> <file_prefix> [<YEAR/MONTH/DAY/HOUR>]\n",
                Command.LIST.name().toLowerCase(), TYPE_EVENTS, TYPE_STATS, TYPE_AUDITS);
        System.out.printf("\t%s [-force] <Column Family Name> <id/-file file_path>\n", Command.DELETE.name().toLowerCase());
        System.out.printf("\t\t%s\t<file_path>\tEvery single line in this file is an object id, multiple object ids should be separated to different line.\n", DELETE_FILE);
        System.out.printf("\t%s [%s] <Column Family Name>\n",
                Command.COUNT.name().toLowerCase(), LIST_ACTIVE);
        System.out.printf("\t\t%s\t Count exclude inactive object ids.\n", LIST_ACTIVE);
        System.out.printf("\t%s <%s/%s/%s> <START TIME> <END TIME>[eg:2012/05/18/15]\n",
                Command.GET_RECORDS.name().toLowerCase(), "Events", "Stats", "AuditLogs");
        System.out.printf("\t%s %s %s %s %s %s\n",
                Command.GLOBALLOCK.name().toLowerCase(), "CREATE", "<lock name>", "<owner>", "(<mode>)", "(<timeout>)");
        System.out.printf("\t\tNote: For <mode>, could be GL_NodeSvcShared_MODE or GL_VdcShared_MODE(default).\n");
        System.out.printf("\t\t    : For <timeout>, unit is millisecond and 0 (default) means never expired.\n");
        System.out.printf("\t%s %s %s \n",
                Command.GLOBALLOCK.name().toLowerCase(), "READ", "<lock name>");
        System.out.printf("\t%s %s %s \n",
                Command.GLOBALLOCK.name().toLowerCase(), "DELETE", "<lock name>");
        System.out.printf("\t%s <schema version> <dump filename>\n",
                Command.DUMP_SCHEMA.name().toLowerCase());
        System.out.printf("\t%s <dump filename>\n",
                Command.DUMP_SECRETKEY.name().toLowerCase());
        System.out.printf("\t%s <restore filename>\n",
                Command.RESTORE_SECRETKEY.name().toLowerCase());
        System.out.printf("\t%s [%s] [%s] Recover Vdc.\n",
                Command.RECOVER_VDC_CONFIG.name().toLowerCase(), RECOVER_DUMP, RECOVER_LOAD);
        System.out.printf("\t%s [%s] [%s] Geodb blacklist.\n",
                Command.GEOBLACKLIST.name().toLowerCase(), "-reset|set", "<vdc short id>");
        System.out.printf("\t%s Check correctness for URI and serialize in db\n",
                Command.CHECK_DB.name().toLowerCase());
        System.out.printf("\t%s -db|-geodb [-new] [-crossVdc]\n",
                Command.REPAIR_DB.name().toLowerCase());

    }

    /**
     * Stop client and exit
     */
    private static void stop() {
        if (_client != null ) {
            _client.stop();
        }
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 ) {
            usage();
            return;
        }

        Command cmd;
        try {
            cmd = Command.valueOf(args[0].trim().toUpperCase());
        }catch (IllegalArgumentException e) {
            System.err.println("Invalid command "+args[0]); 
            usage();
            return;
        }

        if (cmd == Command.REPAIR_DB) {
            System.exit(CommandHandler.repairDb(args));
        }

        try {
            _client = new DBClient();

            CommandHandler handler = null;
            boolean result = false;

            switch (cmd) {
                case LIST: 
                    _client.init();
                    handler = new ListHandler(args, _client);
                     break;
                case QUERY:
                    _client.init();
                    handler = new QueryHandler(args);
                    break;
                case DELETE:
                    _client.init();
                    handler = new DeleteHandler(args);
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
                    handler = new CheckDBHandler();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid command "); 
            }
            handler.process(_client);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Exception e="+e);
            usage();
        } finally {
            stop();
        }
    }
}

/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static DbCli dbCli = null;
    private static String ID_MARK = "-i";
    private static String FILE_MARK = "-f";
    private static final String LIST_ACTIVE = "-activeonly";
    private static final String LIST_LIMIT = "-limit";
    private static final String REGEX_NUMBERS = "\\d+";
    private static final String SKIP_MIGRATION_CHECK = "-bypassMigrationCheck";

    private static ClassPathXmlApplicationContext ctx = null;

    private enum Command {
        HELP {
            int validArgs(String[] args) {
                return 0;
            }
        },
        SHOW_CF {
            int validArgs(String[] args) {
                return 0;
            }
        },
        SHOW_SCHEMA {
            int validArgs(String[] args) {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Invalid command:need at least 2 arguments");
                }
                return 0;
            }
        },
        DUMP {
            int validArgs(String[] args) {
                if (args.length < 6) {
                    throw new IllegalArgumentException("Invalid command:need at least 6 arguments");
                }
                if (!args[1].equals(ID_MARK) || !args[3].equals(FILE_MARK)) {
                    throw new IllegalArgumentException(String.format("Invalid parameter: %s", args[1]));
                }
                return 0;
            }
        },
        LOAD {
            int validArgs(String[] args) {
                if (args.length < 3) {
                    throw new IllegalArgumentException("Invalid command:need at least 3 arguments");
                }
                if (!args[1].equals(FILE_MARK)) {
                    throw new IllegalArgumentException(String.format("Invalid parameter: %s", args[1]));
                }
                return 0;
            }
        },
        DELETE {
            int validArgs(String[] args) {
                if (args.length < 4) {
                    throw new IllegalArgumentException("Invalid command:need at least 4 arguments");
                }
                if (!args[1].equals(ID_MARK)) {
                    throw new IllegalArgumentException(String.format("Invalid parameter: %s", args[1]));
                }
                return 0;
            }
        },
        LIST {
            int validArgs(String[] args) {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Invalid command:need at least 2 arguments");
                }

                // List all records.
                if (args[1].equalsIgnoreCase(LIST_ACTIVE) ||
                        args[1].equalsIgnoreCase(LIST_LIMIT) ||
                        !args[1].equalsIgnoreCase(ID_MARK)) {
                    return 0;
                }

                // List records based on ids
                if (args.length < 4) {
                    throw new IllegalArgumentException("Invalid command:need at least 4 arguments");
                }
                if (!args[1].equals(ID_MARK)) {
                    throw new IllegalArgumentException(String.format("Invalid parameter: %s", args[1]));
                }
                return 1;
            }
        },
        CREATE {
            int validArgs(String[] args) {
                if (args.length < 3) {
                    throw new IllegalArgumentException("Invalid command:need at least 3 arguments");
                }
                if (!args[1].equals(FILE_MARK)) {
                    throw new IllegalArgumentException(String.format("Invalid parameter: %s", args[1]));
                }
                return 0;
            }
        };

        abstract int validArgs(String[] args);
    }

    private static void usage() {
        System.out.println("Usage: ");
        System.out.println(String.format("\t%s %s <File Name>", Command.CREATE.name().toLowerCase(), FILE_MARK));
        System.out.println(String.format("\t%s %s \"id1,id2,...\" <Column Family Name>",
                Command.DELETE.name().toLowerCase(), ID_MARK));
        System.out.println(String.format("\t%s %s \"id1,id2,...\" %s <File Name> <Column Family Name>",
                Command.DUMP.name().toLowerCase(), ID_MARK, FILE_MARK));
        System.out.println(String.format("\t%s %s <File Name>", Command.LOAD.name().toLowerCase(), FILE_MARK));
        System.out.println(String.format("\t%s %s \"id1,id2,...\" <Column Family Name>",
                Command.LIST.name().toLowerCase(), ID_MARK));
        System.out.println(String.format("\t%s [%s <n>] [%s] <Column Family Name>",
                Command.LIST.name().toLowerCase(), LIST_LIMIT, LIST_ACTIVE));
        System.out.println(String.format("\t\t%s <n>\t List paginated with a limit of <n>, "
                + "if <n> is missing, default is 100.", LIST_LIMIT));
        System.out.println(String.format("\t\t%s\t List exclude inactive object ids.", LIST_ACTIVE));
        System.out.println(String.format("\t%s", Command.HELP.name().toLowerCase()));
        System.out.println(String.format("\t%s", Command.SHOW_CF.name().toLowerCase()));
        System.out.println(String.format("\t%s <Column Family Name>", Command.SHOW_SCHEMA.name().toLowerCase()));
        System.out.printf("\t -bypassMigrationCheck%n");
        System.out
                .printf("\t\tNote: it's used with other commands together only when migration fail, dbcli still work even migration fail if you pass this option\n");

    }

    public static void main(String[] args) {
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

        try {

            // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            // This is a CLI application and main method will not be called by multiple threads
            ctx = new ClassPathXmlApplicationContext("/dbcli-conf.xml"); // NOSONAR ("squid:S2444")
            dbCli = (DbCli) ctx.getBean("dbcli"); // NOSONAR ("squid:S2444")
            dbCli.start(skipMigrationCheck);

            String cfName = null;
            String[] ids;
            String fileName;
            switch (cmd) {
                case HELP:
                    usage();
                    break;
                case SHOW_CF:
                    try {
                        dbCli.printCfMaps();
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in printing column families' info.", e));
                        log.error("Exception in printing column families' info.", e);
                    }
                    break;
                case SHOW_SCHEMA:
                    cmd.validArgs(args);
                    cfName = args[1];
                    try {
                        dbCli.printFieldsByCf(cfName);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in printing %s's fields info.", e, cfName));
                        log.error("Exception in priting {}'s fields info.", cfName, e);
                    }
                    break;
                case DUMP:
                    cmd.validArgs(args);
                    ids = args[2].split(",");
                    fileName = args[4];
                    cfName = args[5];
                    dbCli.initDbClient();
                    try {
                        dbCli.queryForDump(cfName, fileName, ids);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in dumping column family:%s into file:%s.", e, cfName, fileName));
                        log.error("Exception in dumping column family:{} into file.", cfName, e);
                    }
                    break;
                case LOAD:
                    cmd.validArgs(args);
                    fileName = args[2];
                    if (!new File(fileName).exists()) {
                        throw new IllegalArgumentException(String.format("File: %s does not exist.", fileName));
                    }
                    dbCli.initDbClient();
                    try {
                        dbCli.loadFileAndPersist(fileName);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in load file:%s into database.", e, fileName));
                        log.error("Exception in loading file{} into database.", fileName, e);
                    }
                    break;
                case DELETE:
                    cmd.validArgs(args);
                    ids = args[2].split(",");
                    cfName = args[3];
                    dbCli.initDbClient();
                    try {
                        dbCli.deleteRecords(cfName, ids, true);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in deleting column family.", e));
                        log.error("Exception in deleting column familiy.", e);
                    }
                    break;
                case LIST:
                    dbCli.initDbClient();

                    int result = cmd.validArgs(args);

                    if (result == 0) {
                        processListArgs(args, dbCli);
                        cfName = args[args.length - 1];
                        try {
                            dbCli.listRecords(cfName);
                        } catch (Exception e) {
                            System.out.println(String.format("Exception %s in listing records.", e));
                            log.error("Exception in listing records.", e);
                        }
                        break;
                    }

                    ids = args[2].split(",");
                    cfName = args[3];
                    try {
                        dbCli.queryForList(cfName, ids);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in listing column family.", e));
                        log.error("Exception in listing column family.", e);
                    }
                    break;
                case CREATE:
                    cmd.validArgs(args);
                    fileName = args[2];
                    if (!new File(fileName).exists()) {
                        throw new IllegalArgumentException(String.format("File: %s does not exist.", fileName));
                    }
                    dbCli.initDbClient();
                    try {
                        dbCli.loadFileAndCreate(fileName);
                    } catch (Exception e) {
                        System.out.println(String.format("Exception %s%n in load file:%s into database.", e, fileName));
                        log.error("Exception in loading file{} into database.", fileName, e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid command");
            }
        } catch (Exception e) {
            System.err.println("Exception e=" + e);
            usage();
        } finally {
            stop();
        }
    }

    /**
     * Stop client and exit
     */
    private static void stop() {
        if (dbCli != null) {
            dbCli.stop();
        }
        System.exit(0);
    }

    private static void processListArgs(String[] args, DbCli dbCli) {
        if (args[args.length - 1].equalsIgnoreCase(LIST_LIMIT)
                || args[args.length - 1].equalsIgnoreCase(LIST_ACTIVE)
                || args[args.length - 1].matches(REGEX_NUMBERS)) {
            System.err.println("The Column Family Name is missing");
            throw new IllegalArgumentException("The Column Family Name is missing");
        }
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(LIST_ACTIVE)) {
                dbCli.setActiveOnly(true);
            }
            if (args[i].equalsIgnoreCase(LIST_LIMIT)) {
                dbCli.setTurnOnLimit(true);
                if (args[i + 1].matches(REGEX_NUMBERS)) {
                    dbCli.setListLimit(Integer.valueOf(args[i + 1]));
                    i++;
                }
            }
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

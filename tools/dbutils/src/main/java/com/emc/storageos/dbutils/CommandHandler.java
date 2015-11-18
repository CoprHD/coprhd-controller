/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbutils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.GlobalLock;
import com.google.common.base.Joiner;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;

public abstract class CommandHandler {
    public String cfName = null;

    public abstract void process(DBClient _client) throws Exception;

    public static int repairDb(String[] args) throws Exception {
        boolean isGeodb = false;
        boolean canResume = true;
        boolean crossVdc = false;
        for (int i = 1; i < args.length; i++) {
            if (args[1].compareToIgnoreCase("-db") == 0) {
                isGeodb = false;
            } else if (args[1].compareToIgnoreCase("-geodb") == 0) {
                isGeodb = true;
            } else if (args[1].compareToIgnoreCase("-new") == 0) {
                canResume = false;
            } else if (args[1].compareToIgnoreCase("-crossVdc") == 0) {
                crossVdc = true;
            } else {
                throw new IllegalArgumentException(String.format("Invalid argument at #%d: %s", i, args[i]));
            }
        }

        try (DbManagerOps dbManagerOps = new DbManagerOps(isGeodb ? Constants.GEODBSVC_NAME : Constants.DBSVC_NAME)) {
            dbManagerOps.startNodeRepair(canResume, crossVdc);
        }

        return 0;
    }
    
    public static class DependencyHandler extends CommandHandler {
        String id = null;

        public DependencyHandler(String args[]) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid command:need at least 2 arguments");
            }
            cfName = args[1];
            if (args.length > 2) {
                id = args[2];
            }
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.printDependencies(cfName, id == null ? null : URI.create(id));
        }

    }

    public static class CountHandler extends CommandHandler {
        private boolean isActiveOnly = true;

        public CountHandler(String args[]) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid command:need at least 2 arguments");
            }
            if (args.length == 3) {
                String isActiveOnlyStr = args[1];
                if (!isActiveOnlyStr.equalsIgnoreCase(Main.INACTIVE)) {
                    throw new IllegalArgumentException("Invalid command option: " + isActiveOnlyStr);
                }
                isActiveOnly = false;
                cfName = args[2];
            } else {
                cfName = args[1];
            }
        }

        @Override
        public void process(DBClient _client) {
            try {
                _client.getRowCount(cfName, isActiveOnly);
            } catch (Exception e) {
                System.out.println("Error querying from db: " + e);
            }
        }
    }

    public static class DeleteHandler extends CommandHandler {
        private static final Logger log = LoggerFactory.getLogger(DeleteHandler.class);

        boolean force = false;
        boolean fileOption = false;
        List<String> ids = null;

        public DeleteHandler(String[] args) {
            String userName = System.getProperty("user.name");

            if (!userName.equals("root")) {
                System.err.println("Only root user can run the 'delete' command");
                return;
            }

            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid delete command ");
            }

            ids = new ArrayList<>();
            int position = 1;
            if (args[position].equals("-force")) {
                // delete -force <cf> <id>
                force = true;
                position++;
            }

            cfName = args[position];

            int index = position + 1;

            if (index < args.length && args[index].equals("-file")) {
                // delete <cf> -file <file_path>
                index++;
                if (index == args.length) {
                    throw new IllegalArgumentException("Invalid file path ");
                }
                fileOption = true;
                BufferedReader br = null;
                try {
                    File idsFile = new File(args[index]);
                    br = new BufferedReader(new FileReader(idsFile));
                    String line = "";
                    while ((line = br.readLine()) != null) {
                        String trimLine = StringUtils.trim(line);
                        if (!StringUtils.isEmpty(trimLine)) {
                            ids.add(trimLine);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not read file " + args[index] + ": " + e);
                } finally {
                    if (br != null) {
                        try {
                            br.close();
                        } catch (IOException e) {
                            log.error("Failed to close the BufferedReader resource ");
                        }
                    }
                }
            } else {
                // delete <cf> param1 param2 ...
                while (++position < args.length) {
                    ids.add(args[position]);
                }
            }

            log.info("To delete {} from {}", Joiner.on(',').join(ids), cfName);
            log.info("force option = {} ", force);
            log.info("file option = {} ", fileOption);
        }

        @Override
        public void process(DBClient _client) throws Exception {
            for (String id : ids) {
                _client.delete(id, cfName, force);
            }
        }
    }

    public static class ListHandler extends CommandHandler {

        private static final String TYPE_EVENTS = "events";
        private static final String TYPE_STATS = "stats";
        private static final String TYPE_AUDITS = "audits";
        private static final String REGEX_NUMBERS = "\\d+";
        private static final String CRITERIAS_DELIMITER = "=";
        /*
         * The KEY of map is the field name of an object, VALUE is the given value wants to be matched.
         */
        private static Map<String, String> criterias = new HashMap<>();

        public ListHandler(String[] args, DBClient client) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid list command ");
            }

            if (args[1].equalsIgnoreCase(TYPE_EVENTS) ||
                    args[1].equalsIgnoreCase(TYPE_STATS) ||
                    args[1].equalsIgnoreCase(TYPE_AUDITS)) {
                if (args.length < 3) {
                    throw new IllegalArgumentException("The file name prefix is missing");
                }
                processTimeSeriesReq(args, client);
                return;
            }
            
            if (args[1].equalsIgnoreCase(Main.INACTIVE)
                    || args[1].equalsIgnoreCase(Main.LIST_LIMIT)
                    || args[1].equalsIgnoreCase(Main.MODIFICATION_TIME)
                    || args[1].equals(Main.FILTER)) {
                processListArgs(args, client);
            }
            cfName = args[args.length - 1];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.listRecords(cfName, criterias);
        }

        private static boolean isValidDateTime(String[] args) {
            Boolean status = Boolean.FALSE;
            String year = args[0];
            String month = args[1];
            String day = args[2];
            String hour = args[3];

            if (month != null && month.length() > 2) {
                return Boolean.FALSE;
            }
            if (day != null && day.length() > 2) {
                return Boolean.FALSE;
            }
            if (hour != null && (hour.length() > 2)) {
                return Boolean.FALSE;
            }

            try {

                Integer iYear = new Integer(year);
                Integer iMonth = new Integer(month);
                Integer iDay = new Integer(day);
                Integer iHour = new Integer(hour);

                if (iMonth > 12 || Calendar.getInstance().get(Calendar.YEAR) > iYear || iDay > 31 || iHour > 24 ||
                        Calendar.getInstance().get(Calendar.MONTH) > iMonth) {
                    return Boolean.FALSE;
                }
                Calendar cal = Calendar.getInstance();
                cal.set(iYear, iMonth, iDay);
                // @TO-DO Still need to add more validation for future days
                status = Boolean.TRUE;
            } catch (Exception e) {
                System.err.println(e.getMessage());
                status = Boolean.FALSE;
            }
            return status;
        }

        private static DateTime getDateTime(String[] args) {
            int year, month, day, hour;
            year = new Integer(args[0]);
            month = new Integer(args[1]);
            day = new Integer(args[2]);
            hour = new Integer(args[3]);
            DateTime dateTime = new DateTime(year, month, day, hour, 0, 0, 0, DateTimeZone.UTC);
            return dateTime;
        }

        private static void processTimeSeriesReq(String[] args, DBClient _client) {
            DateTime queryTimeWindow = new DateTime(DateTimeZone.UTC);
            // check if timestamp is specified or not
            if (args.length > 3) {
                String dateTime = args[3];
                String[] values = dateTime.split("/");
                if (values.length == 4 && isValidDateTime(values)) {
                    queryTimeWindow = getDateTime(values);
                } else {
                    System.err.println(" *** Invalid date time, should be in the format of YEAR/MONTH/DAY/HOUR, " +
                            "where hour is in 24 Hr format");
                    System.err.println(" Example :  2012/05/18/15");
                }
            }

            String type = args[1];
            String file = args[2];
            if (type.equalsIgnoreCase(TYPE_EVENTS)) {
                _client.queryForCustomDayEvents(queryTimeWindow, file);
            } else if (type.equalsIgnoreCase(TYPE_STATS)) {
                _client.queryForCustomDayStats(queryTimeWindow, file);
            } else if (type.equalsIgnoreCase(TYPE_AUDITS)) {
                _client.queryForCustomDayAudits(queryTimeWindow, file);
            } else {
                System.err.println(" ** NOT A VALID INPUT **");
            }
        }

        private static void processListArgs(String[] args, DBClient _client) {
            if (args[args.length - 1].equalsIgnoreCase(Main.LIST_LIMIT)
                    || args[args.length - 1].equalsIgnoreCase(Main.INACTIVE)
                    || args[args.length - 1].matches(REGEX_NUMBERS)
                    || args[args.length - 1].equalsIgnoreCase(Main.MODIFICATION_TIME)
                    || args[args.length - 1].equalsIgnoreCase(Main.FILTER)
                    || args[args.length - 1].contains(CRITERIAS_DELIMITER)) {
                System.err.println("The Column Family Name is missing");
                throw new IllegalArgumentException("The Column Family Name is missing");
            }
            for (int i = 1; i < args.length - 1; i++) {
                if (args[i].equalsIgnoreCase(Main.INACTIVE)) {
                    _client.setActiveOnly(false);
                }
                if (args[i].equalsIgnoreCase(Main.LIST_LIMIT)) {
                    _client.setTurnOnLimit(true);
                    if (args[i + 1].matches(REGEX_NUMBERS)) {
                        _client.setListLimit(Integer.valueOf(args[i + 1]));
                    }
                }
                if (args[i].equalsIgnoreCase(Main.MODIFICATION_TIME)) {
                    _client.setShowModificationTime(true);
                }
                if (args[i].equalsIgnoreCase(Main.FILTER)) {
                    if(!args[i+1].contains(CRITERIAS_DELIMITER)) {
                        String errMsg = "The filter criteria is not available, please follow the usage.";
                        throw new IllegalArgumentException(errMsg);
                    }
                    String[] pureCriteria = args[i+1].split(CRITERIAS_DELIMITER);
                    criterias.put(pureCriteria[0], pureCriteria[1]);
                }
            }
        }
    }

    public static class QueryHandler extends CommandHandler {
        String id = null;

        public QueryHandler(String[] args, DBClient client) {
            if (args.length < 3) {
                throw new IllegalArgumentException("Invalid query command ");
            }

            if (args[1].equalsIgnoreCase(Main.MODIFICATION_TIME)) {
                client.setShowModificationTime(true);
            }

            cfName = args[args.length - 2];
            id = args[args.length - 1];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.query(id, cfName);
        }
    }

    public static class RecordHandler extends CommandHandler {
        Calendar startTime;
        Calendar endTime;

        public RecordHandler(String[] args) {
            if (args.length < 4) {
                throw new IllegalArgumentException("Invalid command:need at least 4 arguments");
            }
            cfName = args[1];
            String startTimeStr = args[2];
            String endTimeStr = args[3];
            if (!cfName.equals("Events") && !cfName.equals("Stats") && !cfName.equals("AuditLogs")) {
                throw new IllegalArgumentException(String.format(
                        "Invalid command:the column family %s is not time series data", cfName));
            }

            startTime = Calendar.getInstance();
            endTime = Calendar.getInstance();

            if (!isValidTime(startTimeStr, endTimeStr)) {
                throw new IllegalArgumentException(
                        String.format("Invalid command:time error %s or %s", startTimeStr, endTimeStr));
            }
            System.out.println(String.format("start time is: %s", startTimeStr));
            System.out.println(String.format("end time is: %s", endTimeStr));
        }

        @Override
        public void process(DBClient _client) {
            _client.countTimeSeries(cfName, startTime, endTime);
        }

        private boolean isValidTime(String startTimeStr, String endTimeStr) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd/HH");
            dateFormat.setLenient(false);
            try {
                startTime.setTime(dateFormat.parse(startTimeStr));
                endTime.setTime(dateFormat.parse(endTimeStr));
                if (startTime.compareTo(endTime) > 0) {
                    return false;
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static class GlobalLockHandler extends CommandHandler {
        private static final String CMD_CREATE = "CREATE";
        private static final String CMD_READ = "READ";
        private static final String CMD_DELETE = "DELETE";

        String cmd = null;
        String _name = null;
        String _mode = GlobalLock.GL_Mode.GL_VdcShared_MODE.toString();
        String _owner = null;
        long _timeout = 0;

        Keyspace _keyspace = null;                   // geo keyspace
        ColumnFamily<String, String> _cf = null;     // global lock CF

        public GlobalLockHandler(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid command:need at least 2 arguments");
            }

            cmd = args[1];
            if (cmd.equalsIgnoreCase(CMD_CREATE)) {
                if (args.length < 4) {
                    throw new IllegalArgumentException("Invalid command:CREATE need 4 arguments");
                }
                _name = args[2];
                _owner = args[3];
                if (args.length > 4) {
                    _mode = args[4];
                    if (!_mode.equalsIgnoreCase(GlobalLock.GL_Mode.GL_NodeSvcShared_MODE.toString()) &&
                            !_mode.equalsIgnoreCase(GlobalLock.GL_Mode.GL_VdcShared_MODE.toString())) {
                        throw new IllegalArgumentException("Invalid command:unrecognized global lock mode");
                    }
                }
                if (args.length > 5) {
                    _timeout = Long.parseLong(args[5]);
                }
            } else if (cmd.equalsIgnoreCase(CMD_READ)
                    || cmd.equalsIgnoreCase(CMD_DELETE)) {
                _name = args[2];
            } else {
                throw new IllegalArgumentException("Invalid command:unrecognized sub commands");
            }
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _keyspace = _client.getGeoDbContext().getKeyspace();
            _cf = TypeMap.getGlobalLockType().getCf();
            MutationBatch m = _keyspace.prepareMutationBatch();

            if (cmd.equalsIgnoreCase(CMD_CREATE)) {
                try {
                    m.withRow(_cf, _name).putColumn(GlobalLock.GL_MODE_COLUMN, _mode.toString());
                    m.withRow(_cf, _name).putColumn(GlobalLock.GL_OWNER_COLUMN, _owner);
                    long curTimeMicros = System.currentTimeMillis();
                    long exprirationTime = (_timeout == 0) ? 0 : curTimeMicros + _timeout;
                    m.withRow(_cf, _name).putColumn(GlobalLock.GL_EXPIRATION_COLUMN, String.valueOf(exprirationTime));

                    m.setConsistencyLevel(ConsistencyLevel.CL_EACH_QUORUM);
                    m.execute();
                } catch (Exception e) {
                    System.out.println(String.format("Failed to create global lock %s due to unexpected exception.", _name));
                    throw e;
                }
                System.out.println(String.format("Succeed to create global lock %s.", _name));
            } else if (cmd.equalsIgnoreCase(CMD_READ)) {
                ColumnMap<String> columns = new OrderedColumnMap<String>();

                ColumnList<String> colList = _keyspace
                        .prepareQuery(_cf)
                        .setConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM)
                        .getKey(_name)
                        .execute()
                        .getResult();
                for (Column<String> c : colList) {
                    columns.add(c);
                }
                _mode = columns.getString(GlobalLock.GL_MODE_COLUMN, null);
                _owner = columns.getString(GlobalLock.GL_OWNER_COLUMN, null);

                String currExpiration = columns.getString(GlobalLock.GL_EXPIRATION_COLUMN, null);
                long curTimeMicros = System.currentTimeMillis();
                String expiredInfo = "";
                if (currExpiration != null) {
                    long expirationTime = Long.parseLong(currExpiration);
                    if (expirationTime == 0) {
                        expiredInfo = String.format("never expired");
                    } else if (curTimeMicros < expirationTime) {
                        expiredInfo = String.format("expired in %l milliseconds", expirationTime - curTimeMicros);
                    } else {
                        expiredInfo = String.format("expired");
                    }
                }

                if (_mode != null && _owner != null) {
                    System.out.println(String.format("Global lock %s info: mode:%s, owner:%s, %s",
                            _name, _mode, _owner, expiredInfo));
                } else {
                    System.out.println(String.format("Global lock %s does not exist.", _name));
                }
            } else if (cmd.equalsIgnoreCase(CMD_DELETE)) {
                try {
                    m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_MODE_COLUMN);
                    m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_OWNER_COLUMN);
                    m.withRow(_cf, _name).deleteColumn(GlobalLock.GL_EXPIRATION_COLUMN);
                    m.setConsistencyLevel(ConsistencyLevel.CL_EACH_QUORUM);
                    m.execute();
                } catch (Exception e) {
                    System.out.println(String.format("Failed to remove global lock %s due to unexpected exception.", _name));
                    throw e;
                }
                System.out.println(String.format("Succeed to remove global lock %s.", _name));
            }
        }
    }

    public static class DumpSchemaHandler extends CommandHandler {
        private String schemaVersion = null;
        private String dumpFilename = null;

        public DumpSchemaHandler(String[] args) {
            if (args.length < 3) {
                throw new IllegalArgumentException("Invalid query command ");
            }
            schemaVersion = args[1];
            dumpFilename = args[2];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.dumpSchema(schemaVersion, dumpFilename);
        }
    }

    public static class RecoverVdcHandler extends CommandHandler {
        String cmd = null;
        private final static String RecoverFileName = "VdcRecoverConfig.data";

        public RecoverVdcHandler(String[] args) {
            if (args.length != 2) {
                throw new IllegalArgumentException("Invalid recover command ");
            }
            cmd = args[1];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            if (cmd.equalsIgnoreCase(Main.RECOVER_DUMP)) {
                _client.dumpRecoverInfoToRecoverFile(RecoverFileName);
            } else if (cmd.equalsIgnoreCase(Main.RECOVER_LOAD)) {
                _client.recoverVdcConfigFromRecoverFile(RecoverFileName);
            } else {
                throw new IllegalArgumentException("Invalid recover command ");
            }

        }
    }

    public static class DumpKeyHandler extends CommandHandler {
        private String dumpFileName = null;

        public DumpKeyHandler(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid dump key command ");
            }
            dumpFileName = args[1];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.dumpSecretKey(dumpFileName);
        }
    }

    public static class RestoreKeyHandler extends CommandHandler {
        private String restoreFileName = null;

        public RestoreKeyHandler(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Invalid restore key command ");
            }
            restoreFileName = args[1];
        }

        @Override
        public void process(DBClient _client) throws Exception {
            _client.restoreSecretKey(restoreFileName);
        }
    }

    public static class GeoBlacklistHandler extends CommandHandler {
        private static final String RESET = "-reset";
        private static final String SET = "-set";
        private boolean reset = false;
        private boolean set = false;
        private String vdcShortId = null;

        public GeoBlacklistHandler(String args[]) {
            if (args.length == 3) {
                String subcmd = args[1];
                if (subcmd.equalsIgnoreCase(RESET)) {
                    reset = true;
                } else if (subcmd.equalsIgnoreCase(SET)) {
                    set = true;
                } else {
                    throw new IllegalArgumentException("Invalid command option: " + subcmd);
                }
                vdcShortId = args[2];
            } else {
                if (args.length > 1) {
                    throw new IllegalArgumentException("Invalid command: " + args[1]);
                }
            }
        }

        @Override
        public void process(DBClient _client) {
            try {
                if (reset) {
                    // reset blacklist for given vdc id
                    _client.resetGeoBlacklist(vdcShortId);
                } else if (set) {
                    _client.setGeoBlacklist(vdcShortId);
                } else {
                    // show black list only
                    Map<String, List<String>> blacklist = _client.getGeoBlacklist();
                    if (blacklist.size() == 0) {
                        System.out.println("Empty geodb blacklist");
                    }

                    for (String nodeIp : blacklist.keySet()) {
                        System.out.println("Node: " + nodeIp);
                        System.out.println("      " + blacklist.get(nodeIp));
                    }
                }
            } catch (Exception e) {
                System.out.println("Unexpected errors: " + e);
            }
        }
    }

    public static class CheckDBHandler extends CommandHandler {
        public CheckDBHandler(String[] args) {
            if (args.length != 1) {
                throw new IllegalArgumentException("Invalid command option. ");
            }
        }

        @Override
        public void process(DBClient _client) {
            _client.checkDB();
        }
    }

    public static class RebuildIndexHandler extends CommandHandler {
        private String rebuildIndexFileName;
        static final String KEY_ID = "id";
        static final String KEY_CFNAME = "cfName";
        static final String KEY_COMMENT_CHAR = DbCheckerFileWriter.COMMENT_CHAR;

        public RebuildIndexHandler(String[] args) {
            if (args.length == 2) {
                rebuildIndexFileName = args[1];
            } else {
                throw new IllegalArgumentException("Invalid command option. ");
            }
        }

        @Override
        public void process(DBClient _client) {
            if (rebuildIndexFileName == null) {
                System.out.println("rebuild Index file is null");
                return;
            }
            File rebuildFile = new File(rebuildIndexFileName);
            if(!rebuildFile.exists() || !rebuildFile.isFile()) {
                System.err.println("Rebuild file is not exist or is not a file");
                return;
            }
            int successCounter = 0;
            int failCounter = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(rebuildIndexFileName))) {
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.length() == 0 || line.startsWith(KEY_COMMENT_CHAR))
                        continue;
                    Map<String, String> rebuildMap = readToMap(line);
                    String id = rebuildMap.get(KEY_ID);
                    String cfName = rebuildMap.get(KEY_CFNAME);
                    if (_client.rebuildIndex(id, cfName)) {
                        successCounter++;
                    } else {
                        failCounter++;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error occured when reading the cleanup file.");
                e.printStackTrace();
            }
            if (successCounter > 0) {
                System.out.println(String.format("successfully rebuild %s indexes.", successCounter));
            }
            if (failCounter > 0) {
                System.out.println(String.format("failed rebuild %s indexes.", failCounter));
            }
        }

        private Map<String, String> readToMap(String input) {
            Map<String, String> cleanUpMap = new HashMap<> ();
            for(String property : input.split(",")) {
                int index = property.indexOf(":");
                if(index > 0) {
                    cleanUpMap.put(property.substring(0, index).trim(), property.substring(index+1).trim());
                }
            }
            return cleanUpMap;
        }
    }
}
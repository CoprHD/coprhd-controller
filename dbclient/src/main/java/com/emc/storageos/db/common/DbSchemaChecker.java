/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.common.DbMigrationCallbackChecker.MigrationCallbackDiff;
import com.emc.storageos.db.common.diff.DbSchemasDiff;
import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;

public class DbSchemaChecker {
    private final static Logger log = LoggerFactory.getLogger(DbSchemaChecker.class);

    private final static String BANNER = "<!--\n The following schema is auto generated\n"
            + "by %s against ViPR version %s, at %s\n"
            + "Please DO NOT modify the content.\n-->\n";

    static void usage() {
        log.info("dbchecker [-i ignored-pkg1:ignored-pkg2:...] [-l geo|all] schema-file" +
                " pkg1:pkg2");
        log.info("           -i: packages to ignore during schema comparison");
        log.info("           -l: none|geo|all, none or lock the geo/all db schemas so that" +
                " no changes can be made");
        log.info("           -b: base migration callback file");
        log.info("           -c: current migration callback file");
        log.info("           -v: db schema version");
    }

    /**
     * The local/geo db schema lock type we support.
     * ALL - Fully locked. No change should be made.
     * GEO - Geodb locked. Only local db schema change could be made.
     * NONE - No lock. Both local db/geodb schema change can be made.
     * The following geodb schema change could be refused by any lock type:
     * Add index on existing field.
     */
    private enum SchemaLockType {
        NONE, GEO, ALL
    }

    public static void main(String[] args) throws Exception {
        String schemaFile = null;
        String[] pkgs = null;
        String[] ignoredPkgs = null;
        String dbSchemaVersion = null;
        String baseCallbackFile = null;
        String currentCallbackFile = null;
        SchemaLockType schemaLock = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-i")) {
                ignoredPkgs = args[++i].split(":");
                if (ignoredPkgs.length == 0) {
                    usage();
                    throw new IllegalArgumentException("no ignored packages provided");
                }
                continue;
            }

            if (args[i].equals("-v")) {
                dbSchemaVersion = args[++i];
                continue;
            }

            if (args[i].equals("-l")) {
                String lock = null;
                try {
                    lock = args[++i].trim();
                    schemaLock = SchemaLockType.valueOf(lock.toUpperCase());
                    log.info("Schema lock:{}", schemaLock);
                } catch (IllegalArgumentException e) {
                    usage();
                    throw new IllegalArgumentException("Invalid schema lock: " + lock);
                }
                continue;
            }

            if (args[i].equals("-b")) {
                baseCallbackFile = args[++i];
                continue;
            }

            if (args[i].equals("-c")) {
                currentCallbackFile = args[++i];
                continue;
            }

            schemaFile = args[i++];
            pkgs = args[i].split(":");
        }

        if (baseCallbackFile == null || currentCallbackFile == null) {
            usage();
            throw new IllegalArgumentException("no migraton callback file provided");
        }
        if (schemaFile == null || pkgs.length == 0) {
            usage();
            throw new IllegalArgumentException("no schema file or packages provided");
        }

        DbMigrationCallbackChecker migrationCallbackChecker = new DbMigrationCallbackChecker(baseCallbackFile, currentCallbackFile);
        if (SchemaLockType.ALL.equals(schemaLock) && migrationCallbackChecker.hasDiff()) {
            Map<String, List<MigrationCallbackDiff>> versionedDiffs = migrationCallbackChecker.getDiff();
            dumpMigrationCallbackDiff(versionedDiffs);
            log.warn("All migration callback has been locked");
            System.exit(-1);
        }

        DbSchemaScanner scanner = new DbSchemaScanner(pkgs);
        scanner.setScannerInterceptor(new DbSchemaInterceptorImpl());
        scanner.scan();

        log.info("Check the integrity of DataObject classes in packages {}", pkgs);
        checkSourceSchema(pkgs);

        DbSchemas currentSchemas = scanner.getSchemas();
        if (currentSchemas.hasDuplicateField()) {
            Map<String, List<FieldInfo>> schemaDuplicateFields = currentSchemas.getDuplicateFields();
            dumpDuplicateColumns(schemaDuplicateFields);
            System.exit(-1);
        }

        log.info("Check db schemas of the packages: {}\nagainst schema file: {}", pkgs,
                schemaFile);

        try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
            DbSchemas spec = unmarshalSchemas(dbSchemaVersion, reader);

            DbSchemasDiff diff = new DbSchemasDiff(spec, currentSchemas, ignoredPkgs);
            if (diff.isChanged()) {
                log.info("schema diffs: {}", marshalSchemasDiff(diff));
                switch (schemaLock) {
                    case ALL:
                        log.error("All the db schemas have been locked");
                        System.exit(-1);
                        break;
                    case GEO:
                        if (genGeoDiffs(spec, scanner.getGeoSchemas()).isChanged()) {
                            log.error("The geo db schemas have been locked");
                            System.exit(-1);
                        }
                    case NONE:
                    default:
                        if (diff.isUpgradable()) {
                            log.warn("The db schemas are changed but upgradable");
                        } else {
                            log.error("The db schemas are changed and not upgradable");
                            System.exit(-1);
                        }
                }
            } else {
                log.info("The Db schemas are the SAME");
            }
        }
    }

    public static void checkSourceSchema(String[] pkgs) throws Exception {
        DataObjectScanner dataObjectScanner = new DataObjectScanner();
        dataObjectScanner.setPackages(pkgs);
        dataObjectScanner.init();

        try {
            TypeMap.check();
        } catch (Exception e) {
            log.error("The check on the TypeMap failed e:", e);
            throw e;
        }
    }

    private static void dumpMigrationCallbackDiff(
            Map<String, List<MigrationCallbackDiff>> versionedDiffs) {
        for (Map.Entry<String, List<MigrationCallbackDiff>> versionedDiff : versionedDiffs.entrySet()) {
            log.info("migration callback diffs under {}", versionedDiff.getKey());
            for (MigrationCallbackDiff diff : versionedDiff.getValue()) {
                log.info("    {}", diff);
            }
        }
    }

    public static DbSchemas genSchemas(String[] packages, DbSchemaScannerInterceptor
            scannerInterceptor) {
        DbSchemaScanner scanner = new DbSchemaScanner(packages);
        scanner.setScannerInterceptor(scannerInterceptor);
        scanner.scan();
        return scanner.getSchemas();
    }

    /**
     * Filter out all the non-geo db schemas from the spec and generate a diff
     * Note that some CFs might have been migrated from local db to geo db
     * So we need to grab a latest list of geo schemas from the current schema first.
     * 
     * @param spec the db schema spec generated from the baseline file
     * @param geoSchemas the latest list of geo schemas from the current DbSchemas object
     * @return
     */
    private static DbSchemasDiff genGeoDiffs(DbSchemas spec, List<DbSchema> geoSchemas) {
        // prepare a list of geo schema names
        List<String> geoSchemaNames = new ArrayList<>();
        for (DbSchema geoSchema : geoSchemas) {
            geoSchemaNames.add(geoSchema.getName());
        }

        List<DbSchema> specSchemaList = new ArrayList<>();
        for (DbSchema schema : spec.getSchemas()) {
            if (geoSchemaNames.contains(schema.getName())) {
                specSchemaList.add(schema);
            }
        }

        return new DbSchemasDiff(new DbSchemas(specSchemaList), new DbSchemas(geoSchemas));
    }

    private static void dumpDuplicateColumns(Map<String, List<FieldInfo>> schemaDuplicateFields) {
        for (Map.Entry<String, List<FieldInfo>> entry : schemaDuplicateFields.entrySet()) {
            log.warn("More than one fields are mapped to same column in data object {}", entry.getKey());
            for (FieldInfo fieldInfo : entry.getValue()) {
                log.warn("    Field name:{}", fieldInfo.getName());
            }
        }

        log.error("It is not allowed to map more than one object fields to single column");
    }

    public static String marshalSchemasDiff(DbSchemasDiff diff) {
        try
        {
            JAXBContext jc = JAXBContext.newInstance(DbSchemasDiff.class);
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            StringWriter sw = new StringWriter();
            m.marshal(diff, sw);

            return sw.toString();
        } catch (JAXBException e) {
            log.error("Failed to marshal DbSchemasDiff:", e);
        }
        return null;
    }

    /**
     * marshal DbSchemas instance into a String
     * 
     * @param schemas
     * @param dbSchemaVersion if specified, generate a human-readable String with schema
     *            version specified in the banner. If null, generate a compact String
     *            instead.
     * @return
     */
    public static String marshalSchemas(DbSchemas schemas, String dbSchemaVersion) {
        try {
            JAXBContext jc = JAXBContext.newInstance(DbSchemas.class);
            Marshaller m = jc.createMarshaller();
            if (dbSchemaVersion != null) {
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                m.setProperty("com.sun.xml.internal.bind.xmlHeaders", String.format(
                        BANNER, DbSchemaChecker.class.getName(), dbSchemaVersion,
                        (new Date()).toString()));
            }
            StringWriter sw = new StringWriter();
            m.marshal(schemas, sw);

            return sw.toString();
        } catch (JAXBException e) {
            log.error("Failed to marshal:", e);
        }
        return null;
    }

    public static DbSchemas unmarshalSchemas(final String version, final Reader reader) {
        DbSchemas schemas = null;
        try {
            JAXBContext jc = JAXBContext.newInstance(DbSchemas.class);
            Unmarshaller um = jc.createUnmarshaller();

            schemas = (DbSchemas) um.unmarshal(reader);
            log.info("{} drop unused schema if exists", version);
            removeUnusedSchemaIfExists(schemas, DbSchemaInterceptorImpl.getIgnoreCfList());

            if (DbSchemaFilter.needDoFilterFor(version)) {
                log.info("filter out the garbage fileds for {}", version);
                DbSchemaFilter.doFilter(schemas);
            }
        } catch (JAXBException e) {
            log.error("Failed to unmarshal DbSchemas:", e);
        }

        return schemas;
    }
    
    /**
     * drop schema from db is not allowed, but we have special cases to drop schema such as:
     * Data Service separation, we drop schema used by Data Service to perform cleanup,
     * during migration we convert xml-based schema stored in db to DbSchemas object as previous
     * schema, the dropped schema needs to be skipped before schema comparison in order to removed
     * schema , otherwise migration will fail because of unsupported schema change.
     * 
     * @param schemas
     * @param ignoreSchemaNames, the list of schema names which needs to be removed from schemas
     * @return
     */
    private static void removeUnusedSchemaIfExists(DbSchemas schemas, List<String> ignoreSchemaNames) {
        Iterator<DbSchema> it = schemas.getSchemas().iterator();
        while (it.hasNext()) {
            DbSchema schema = it.next();
            if (ignoreSchemaNames.contains(schema.getName())) {
                log.info("skip schema:{} since it's removed", schema.getName());
                it.remove();
            }
        }
    }

    /*
     * some fields were inserted into db schema unexpected because of
     * incorrect behavior of db schema generator, for detail please
     * refer to bug:CTRL-9876
     */
    private static class DbSchemaFilter {
        private static final int[] SCHEMA_VERSION_PARTS_WITH_GARBAGE_FILEDS = new int[] { 2, 2 };

        public static boolean needDoFilterFor(final String version) {
            String[] versionParts = StringUtils.split(version, ".");

            for (int i = 0; i < SCHEMA_VERSION_PARTS_WITH_GARBAGE_FILEDS.length; i++) {
                if (!NumberUtils.isDigits(versionParts[i])) {
                    return false;
                }
                if (Integer.parseInt(versionParts[i]) < SCHEMA_VERSION_PARTS_WITH_GARBAGE_FILEDS[i]) {
                    return true;
                } else if (Integer.parseInt(versionParts[i]) > SCHEMA_VERSION_PARTS_WITH_GARBAGE_FILEDS[i]) {
                    return false;
                }
            }
            return true;
        }

        public static void doFilter(DbSchemas dbSchemas) {
            DbSchemaScannerInterceptor interceptor = new DbSchemaInterceptorImpl();
            Iterator<DbSchema> itr = dbSchemas.getSchemas().iterator();
            while (itr.hasNext()) {
                DbSchema dbSchema = itr.next();
                if (interceptor.isClassIgnored(dbSchema.getName())) {
                    log.info("skip db schema:{}", dbSchema.getName());
                    itr.remove();
                } else {
                    filterSchemaFiled(interceptor, dbSchema);
                    filterSchemaClassAnnotaton(interceptor, dbSchema);
                }
            }
        }

        private static void filterSchemaFiled(DbSchemaScannerInterceptor interceptor, DbSchema dbSchema) {
            Iterator<FieldInfo> itr = dbSchema.getFields().iterator();
            while (itr.hasNext()) {
                if (interceptor.isFieldIgnored(dbSchema.getName(), itr.next().getName())) {
                    itr.remove();
                }
            }
        }

        private static void filterSchemaClassAnnotaton(DbSchemaScannerInterceptor interceptor, DbSchema dbSchema) {
            if (!hasClassAnnotation(dbSchema)) {
                return;
            }

            Iterator<AnnotationType> itr = dbSchema.getAnnotations().getAnnotations().iterator();
            while (itr.hasNext()) {
                AnnotationType annoType = itr.next();
                if (interceptor.isClassAnnotationIgnored(dbSchema.getName(), annoType.getName())) {
                    log.info("Class annotation {}:{} is ignored in schema due to interceptor", dbSchema.getName(), annoType.getName());
                    itr.remove();
                }
            }
        }

        private static boolean hasClassAnnotation(DbSchema dbSchema) {
            return dbSchema.getAnnotations().getAnnotations() != null && !dbSchema.getAnnotations().getAnnotations().isEmpty();
        }
    }
}

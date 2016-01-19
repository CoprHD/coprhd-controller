/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.*;

import com.emc.storageos.db.common.*;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.UpgradeFailureInfo;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.model.SchemaRecord;
import com.emc.storageos.db.client.model.UpgradeAllowed;
import com.emc.storageos.db.common.diff.DbSchemasDiff;
import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.AnnotationValue;
import com.emc.storageos.db.common.schema.FieldInfo;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.exceptions.FatalDatabaseException;
import com.emc.storageos.db.server.MigrationHandler;
import com.emc.storageos.db.client.upgrade.*;
import com.emc.storageos.db.client.upgrade.callbacks.GeoDbMigrationCallback;
import com.netflix.astyanax.Keyspace;

/**
 * Default implementation of migration handler
 */
public class MigrationHandlerImpl implements MigrationHandler {
    private static final Logger log = LoggerFactory.getLogger(MigrationHandler.class);
    private AlertsLogger alertLog = AlertsLogger.getAlertsLogger();

    private static final int WAIT_TIME_BEFORE_RETRY_MSEC = 5 * 1000; // 5 sec
    private static final String DB_MIGRATION_LOCK = "dbmigration";
    private static final int MAX_MIGRATION_RETRY = 10;

    private CoordinatorClient coordinator;
    private InternalDbClient dbClient;
    private DbSchemas currentSchema;
    private Service service;
    @SuppressWarnings("unused")
    private String[] pkgs;
    private String[] ignoredPkgs;
    private Map<String, List<BaseCustomMigrationCallback>> customMigrationCallbacks;
    private DbServiceStatusChecker statusChecker;
    private SchemaUtil schemaUtil;

    String targetVersion;
    String failedCallbackName;
    Exception lastException;

    /**
     * Package where model classes are defined
     * 
     * @param packages
     */
    public void setPackages(String... packages) {
        pkgs = packages;
        currentSchema = DbSchemaChecker.genSchemas(packages, new DbSchemaInterceptorImpl());
    }

    public void setPackagesAndChangeInjector(DbSchemaScannerInterceptor injector, String... packages) {
        pkgs = packages;
        currentSchema = DbSchemaChecker.genSchemas(packages, injector);
    }

    /**
     * The packages to be ignored in the comparing
     * 
     * @param pkgs
     */
    public void setIgnoredPackages(String... pkgs) {
        ignoredPkgs = pkgs;
    }

    /**
     * Set coordinator client
     * 
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Set db client
     * 
     * @param dbClient
     */
    public void setDbClient(DbClient dbClient) {
        if (dbClient instanceof InternalDbClient) {
            this.dbClient = (InternalDbClient) dbClient;
        } else {
            String errorMsg = "MigrationHandler only accept InternalDbClient instances";
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Set service info
     * 
     * @param service
     */
    public void setService(Service service) {
        this.service = service;
    }

    /**
     * set versioned custom migration callbacks
     * 
     * @param customMigrationCallbacks
     */
    public void setCustomMigrationCallbacks(Map<String, List<BaseCustomMigrationCallback>>
            customMigrationCallbacks) {
        this.customMigrationCallbacks = customMigrationCallbacks;
    }

    @Autowired
    public void setStatusChecker(DbServiceStatusChecker statusChecker) {
        this.statusChecker = statusChecker;
    }

    public void setSchemaUtil(SchemaUtil util) {
        schemaUtil = util;
    }

    /**
     *
     */
    @Override
    public boolean run() throws DatabaseException {
        if (schemaUtil.isStandby()) {
            // no migration on standby site
            log.info("Migration does not run on standby");
            return true;
        } 
        
        Date startTime = new Date();
        // set state to migration_init and wait for all nodes to reach this state
        setDbConfig(DbConfigConstants.MIGRATION_INIT);

        targetVersion = service.getVersion();
        statusChecker.setVersion(targetVersion);
        statusChecker.setServiceName(service.getName());
        // dbsvc will wait for all dbsvc, and geodbsvc waits for all geodbsvc.
        statusChecker.waitForAllNodesMigrationInit();

        if (schemaUtil.isGeoDbsvc()) {
            // no migration procedure for geosvc, just wait till migration is done on one of the
            // dbsvcs
            log.warn("Migration is not supported for Geodbsvc. Wait till migration is done");
            statusChecker.waitForMigrationDone();
            return true;
        } else {
            // We support adjusting num_tokens for dbsvc, have to wait for it to complete before continue.
            // geodbsvc is not supported to adjust num_tokens, yet, if it's enabled in UpgradeManager,
            // move this to common code path in both dbsvc and geodbsvc.
            statusChecker.waitForAllNodesNumTokenAdjusted();

            // for dbsvc, we have to wait till all geodbsvc becomes migration_init since we might
            // need to copy geo-replicated resources from local to geo db.
            statusChecker.waitForAllNodesMigrationInit(Constants.GEODBSVC_NAME);
        }
        
        InterProcessLock lock = null;
        String currentSchemaVersion = null;
        int retryCount = 0;
        while (retryCount < MAX_MIGRATION_RETRY) {
            log.debug("Migration handlers - Start. Trying to grab lock ...");
            try {
                // grab global lock for migration
                lock = getLock(DB_MIGRATION_LOCK);

                // make sure we haven't finished the migration on another node already
                MigrationStatus status = coordinator.getMigrationStatus();
                if (status != null) {
                    if (status == MigrationStatus.DONE) {
                        log.info("DB migration is done already. Skipping...");
                        if (null == getPersistedSchema(targetVersion)) {
                            persistSchema(targetVersion, DbSchemaChecker.marshalSchemas(currentSchema, null));
                        }
                        return true;
                    } else if (status == MigrationStatus.FAILED) {
                        log.error("DB migration is done already with status:{}. ", status);
                        return false;
                    }
                }
                schemaUtil.setMigrationStatus(MigrationStatus.RUNNING);

                // we expect currentSchemaVersion to be set
                currentSchemaVersion = coordinator.getCurrentDbSchemaVersion();
                if (currentSchemaVersion == null) {
                    throw new IllegalStateException("Schema version not set");
                }
                // figure out our source and target versions
                DbSchemas persistedSchema = getPersistedSchema(currentSchemaVersion);

                if (isSchemaMissed(persistedSchema, currentSchemaVersion, targetVersion)) {
                    throw new IllegalStateException("Schema definition not found for version "
                            + currentSchemaVersion);
                }

                if (isFreshInstall(persistedSchema, currentSchemaVersion, targetVersion)) {
                    log.info("saving schema of version {} to db", currentSchemaVersion);
                    persistedSchema = currentSchema;
                    persistSchema(currentSchemaVersion, DbSchemaChecker.marshalSchemas(
                            persistedSchema, null));
                }

                // check if we have a schema upgrade to deal with
                if (!currentSchemaVersion.equals(targetVersion)) {
                    DbSchemasDiff diff = new DbSchemasDiff(persistedSchema, currentSchema,
                            ignoredPkgs);
                    if (diff.isChanged()) {
                        // log the changes
                        dumpChanges(diff);

                        if (!diff.isUpgradable()) {
                            // we should never be here, but, if we are here, throw an IllegalStateException and stop
                            // To Do - dump the problematic diffs here
                            log.error("schema diff details: {}",
                                    DbSchemaChecker.marshalSchemasDiff(diff));
                            throw new IllegalStateException("schema not upgradable.");
                        }
                    }

                    log.info("Starting migration callbacks from {} to {}", currentSchemaVersion,
                            targetVersion);

                    // we need to check point the progress of these callbacks as they are run,
                    // so we can resume from where we left off in case of restarts/errors
                    String checkpoint = schemaUtil.getMigrationCheckpoint();
                    if (checkpoint != null) {
                        log.info("Migration checkpoint found for {}", checkpoint);
                    }
                    // run all migration callbacks
                    runMigrationCallbacks(diff, checkpoint);
                    log.info("Done migration callbacks");

                    persistSchema(targetVersion, DbSchemaChecker.marshalSchemas(currentSchema,
                            null));
                    // set current version in zk
                    schemaUtil.setCurrentVersion(targetVersion);
                    log.info("current schema version is updated to {}", targetVersion);
                    schemaUtil.dropUnusedCfsIfExists();
                }
                schemaUtil.setMigrationStatus(MigrationStatus.DONE);
                // Remove migration checkpoint after done
                schemaUtil.removeMigrationCheckpoint();
                removeMigrationFailInfoIfExist();
                log.debug("Migration handler - Done.");
                return true;
            } catch (Exception e) {
            	if (e instanceof MigrationCallbackException) {
            		markMigrationFailure(startTime, currentSchemaVersion, e);
            	} else if (isUnRetryableException(e)) {
                    markMigrationFailure(startTime, currentSchemaVersion, e);
                    return false;
                } else {
                    log.warn("Retryable exception during migration ", e);
                    retryCount++;
                    lastException = e;
                }
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Exception ignore) {
                        log.debug("lock release failed");
                    }
                }
            }
            sleepBeforeRetry();
        }  // while -- not done
        markMigrationFailure(startTime, currentSchemaVersion, lastException);
        return false;
    }

    private void removeMigrationFailInfoIfExist() {
        UpgradeFailureInfo failInfo = coordinator.queryRuntimeState(Constants.UPGRADE_FAILURE_INFO, UpgradeFailureInfo.class);
        if (failInfo != null) {
            log.info("remove upgrade fail information from zk.");
            coordinator.removeRuntimeState(Constants.UPGRADE_FAILURE_INFO);
        }
    }

    private void persistMigrationFailInfo(Date startTime, Exception e) {
        schemaUtil.setMigrationStatus(MigrationStatus.FAILED);
        UpgradeFailureInfo failure = new UpgradeFailureInfo();
        failure.setVersion(targetVersion);
        failure.setStartTime(startTime);
        if (e instanceof MigrationCallbackException) {
        	failure.setSuggestion(((MigrationCallbackException)e).getMsg());
        }
        failure.setMessage(String.format("Upgrade to %s failed:%s", targetVersion, e.getClass().getName()));
        List<String> callStack = new ArrayList<String>();
        for (StackTraceElement t : e.getStackTrace()){
            callStack.add(t.toString());
        }       
        failure.setCallStack(callStack);
        coordinator.persistRuntimeState(Constants.UPGRADE_FAILURE_INFO, failure);
    }
    
    private void markMigrationFailure(Date startTime, String currentSchemaVersion, Exception e) {
        persistMigrationFailInfo(startTime, e);
        
        String errMsg =
                String.format("DB schema migration from %s to %s failed due to an unexpected error.",
                        currentSchemaVersion, targetVersion);

        if (failedCallbackName != null) {
            errMsg += " (The failing callback is " + failedCallbackName + ").";
        }

        errMsg += " Please contract the EMC support team.";

        alertLog.error(errMsg);
        if (e != null) {
            log.error(e.getMessage(), e);
        }
    }
    
    private boolean isUnRetryableException(Exception e) {
        return e instanceof FatalDatabaseException ||
                e instanceof FatalCoordinatorException ||
                e instanceof IllegalArgumentException ||
                e instanceof IllegalStateException;
    }

    public void sleepBeforeRetry() {
        try {
            log.info("Waiting for {} sec before retrying ...", WAIT_TIME_BEFORE_RETRY_MSEC / 1000);
            Thread.sleep(WAIT_TIME_BEFORE_RETRY_MSEC);
        } catch (InterruptedException ex) {
            log.warn("Thread is interrupted during wait for retry", ex);
        }

    }

    private boolean isSchemaMissed(DbSchemas persistedSchema,
            String currentSchemaVersion, String targetVersion2) {
        return persistedSchema == null && !currentSchemaVersion.equals(targetVersion);
    }

    private boolean isFreshInstall(DbSchemas persistedSchema,
            String currentSchemaVersion, String targetVersion) {
        return persistedSchema == null && currentSchemaVersion.equals(targetVersion);
    }

    /**
     * Checks and registers db configuration information
     */
    private void setDbConfig(String name) {
        Configuration config = coordinator.queryConfiguration(coordinator.getSiteId(),
                coordinator.getVersionedDbConfigPath(service.getName(), service.getVersion()), service.getId());
        if (config != null) {
            if (config.getConfig(name) == null) {
                config.setConfig(name, Boolean.TRUE.toString());
                coordinator.persistServiceConfiguration(coordinator.getSiteId(), config);
            }
        } else {
            throw new IllegalStateException("unexpected error, configuration is null");
        }
    }

    private InterProcessLock getLock(String name) throws Exception {
        InterProcessLock lock = null;
        while (true) {
            try {
                lock = coordinator.getLock(name);
                lock.acquire();
                break; // got lock
            } catch (Exception e) {
                if (coordinator.isConnected()) {
                    throw e;
                }
            }
        }
        return lock;
    }

    private DbSchemas getPersistedSchema(String version) {
        SchemaRecord record = dbClient.querySchemaRecord(version);
        if (record == null) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new StringReader(record.getSchema()));
            return DbSchemaChecker.unmarshalSchemas(version, reader);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    log.error("Fail to close buffer reader", e);
                }
            }
        }
    }

    private void persistSchema(String version, String schema) {
        SchemaRecord record = new SchemaRecord();
        record.setVersion(version);
        record.setSchema(schema);
        dbClient.persistSchemaRecord(record);
    }

    /**
     * Figure out all migration callbacks and run from given checkpoint
     * 
     * @param diff
     * @param checkpoint
     * @throws MigrationCallbackException 
     */
    private void runMigrationCallbacks(DbSchemasDiff diff, String checkpoint) throws MigrationCallbackException {
        List<MigrationCallback> callbacks = new ArrayList<>();
        // TODO: we are putting class annotations at the first place since that's where
        // @Keyspace belongs, but we probably need some explicit ordering to make sure
        // that the geo resources gets migrated into geodb first.
        callbacks.addAll(generateDefaultMigrationCallbacks(diff.getNewClassAnnotations()));
        callbacks.addAll(generateDefaultMigrationCallbacks(diff.getNewFieldAnnotations()));

        // now, see if there is any extra ones we need to run from the specified source
        // version
        callbacks.addAll(generateCustomMigrationCallbacks());

        log.info("Total {} migration callbacks ", callbacks.size());
        DbClientContext geoContext = disableGeoAccess();
        boolean startProcessing = false;
        try {
            for (MigrationCallback callback : callbacks) {
                // ignore the callback if it is before given checkpoint
                if (!startProcessing && checkpoint != null) {
                    if (!callback.getName().equals(checkpoint)) {
                        log.info("Ignore migration callback: " + callback.getName());
                        continue;
                    } else {
                        // Start from next callback
                        startProcessing = true;
                        continue;
                    }
                }

                log.info("Invoking migration callback: " + callback.getName());
                try {
                    callback.process();
                } catch (MigrationCallbackException ex) {
                	throw ex;
                } catch (Exception e) {
                    throw new MigrationCallbackException(callback.getName(),null,null,"Please contract the EMC support team",e);
                }
                // Update checkpoint
                schemaUtil.setMigrationCheckpoint(callback.getName());
            }
        } finally {
            enableGeoAccess(geoContext);
        }
    }

    private void enableGeoAccess(DbClientContext geoContext) {
        log.info("enable geo access since migration callback done");
        this.dbClient.setGeoContext(geoContext);
    }

    /*
     * don't allow geo db migration callback.
     */
    private DbClientContext disableGeoAccess() {
        log.info("disable geo access temporary since we don't support geo db migration callback now");
        DbClientContext geoContext = this.dbClient.getGeoContext();
        this.dbClient.setGeoContext(new DbClientContext() {
            @Override
            public Keyspace getKeyspace() {
                log.error("doesn't support migration callback for Geo");
                for (StackTraceElement st : Thread.currentThread().getStackTrace()) {
                    log.error(st.getClassName() + ":" + st.getMethodName() + ", (" + st.getLineNumber() + ") \n");
                }
                throw new IllegalArgumentException("doesn't support migration callback for Geo");
            }
        });
        return geoContext;
    }

    /**
     * Determines the default migration callbacks for a class and field and returns a list of handlers
     * 
     * @param annotationTypes
     * @return
     */
    private List<BaseDefaultMigrationCallback> generateDefaultMigrationCallbacks(List<AnnotationType> annotationTypes) {
        List<BaseDefaultMigrationCallback> callbacks = new ArrayList<BaseDefaultMigrationCallback>();
        for (AnnotationType annoType : annotationTypes) {
            Class<? extends Annotation> annoClass = annoType.getAnnoClass();
            if (annoClass.isAnnotationPresent(UpgradeAllowed.class)) {
                UpgradeAllowed upgrAnno = annoClass.getAnnotation(UpgradeAllowed.class);
                Class<? extends BaseDefaultMigrationCallback> callback = upgrAnno.migrationCallback();
                // skip geo migration callback
                if (callback == GeoDbMigrationCallback.class) {
                    log.info("skip geo db migration callback:{} since we don't support it now", callback.getCanonicalName());
                    continue;
                }
                String className = annoType.getCfClass().getCanonicalName();
                String fieldName = annoType.getFieldName();
                String annotationType = annoType.getType();
                try {
                    BaseDefaultMigrationCallback callbackInst = callback.newInstance();
                    callbackInst.setName(String.format("%s:%s:%s:%s",
                            callback.getSimpleName(), className, fieldName, annotationType));
                    callbackInst.setCfClass(annoType.getCfClass());
                    callbackInst.setFieldName(annoType.getFieldName());
                    callbackInst.setAnnotation(annoType.getAnnotation());
                    callbackInst.setInternalDbClient(dbClient);
                    callbacks.add(callbackInst);
                } catch (InstantiationException e) {
                    log.error("Failed to generate default migration callback ", e);
                    throw DatabaseException.fatals.failedDuringUpgrade("Failed for new index " + annotationType + " on " + className + "."
                            + fieldName, e);
                } catch (IllegalAccessException e) {
                    log.error("Failed to generate default migration callback ", e);
                    throw DatabaseException.fatals.failedDuringUpgrade("Failed for new index " + annotationType + " on " + className + "."
                            + fieldName, e);
                }
            }
        }

        // Sort callbacks to determine execution order
        Collections.sort(callbacks, new Comparator<BaseDefaultMigrationCallback>() {
            @Override
            public int compare(BaseDefaultMigrationCallback obj1, BaseDefaultMigrationCallback obj2) {
                return obj1.getName().compareTo(obj2.getName());
            }
        });

        log.info("Get default migration callbacks in the following order {}", getCallbackNames(callbacks).toString());
        return callbacks;
    }

    private static class VersionComparitor implements Comparator<String> {

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        @Override
        public int compare(String o1, String o2) {
            if (o1.equals(o2)) {
                return o1.compareTo(o2);
            }

            String[] o1Parts = StringUtils.split(o1, ".");
            String[] o2Parts = StringUtils.split(o2, ".");

            if (o1Parts.length >= o2Parts.length) {
                return compareParts(Arrays.asList(o1Parts), Arrays.asList(o2Parts));
            } else {
                return -1 * compareParts(Arrays.asList(o2Parts), Arrays.asList(o1Parts));
            }

        }

        private int compareParts(Collection<String> list1, Collection<String> list2) {
            Iterator<String> list2Itr = list2.iterator();
            for (String part1 : list1) {
                String part2 = list2Itr.hasNext() ? list2Itr.next() : "0";
                int compare = 0;
                if (StringUtils.isNumeric(part1) && StringUtils.isNumeric(part2)) {
                    compare = (Integer.valueOf(part1)).compareTo(Integer.valueOf(part2));
                } else {
                    compare = part1.compareToIgnoreCase(part2);
                }
                if (compare != 0) {
                    return compare;
                }
            }
            return 0;
        }

    }

    /**
     * Determines the custom migration callbacks for the current version and returns a list of handlers
     * 
     * @return
     */
    private List<BaseCustomMigrationCallback> generateCustomMigrationCallbacks() {
        List<BaseCustomMigrationCallback> callbacks = new ArrayList<>();
        if (customMigrationCallbacks != null && !customMigrationCallbacks.isEmpty()) {
            List<String> versions = new ArrayList<String>(customMigrationCallbacks.keySet());
            VersionComparitor versionComparitor = new VersionComparitor();
            Collections.sort(versions, versionComparitor);

            String currentSchemaVersion = coordinator.getCurrentDbSchemaVersion();

            for (String version : versions) {
                if (versionComparitor.compare(version, currentSchemaVersion) >= 0) {
                    for (BaseCustomMigrationCallback customMigrationCallback : customMigrationCallbacks.get(version)) {
                        customMigrationCallback.setName(customMigrationCallback.getClass().getName());
                        customMigrationCallback.setDbClient(dbClient);
                        customMigrationCallback.setCoordinatorClient(coordinator);
                        callbacks.add(customMigrationCallback);
                    }
                }
            }
        }

        log.info("Get custom migration callbacks in the following order {}", getCallbackNames(callbacks).toString());
        return callbacks;
    }

    /**
     * Get a list of callback names
     * 
     * @param callbacks
     * @return
     */
    private <T extends MigrationCallback> List<String> getCallbackNames(List<T> callbacks) {
        List<String> callbackNames = new ArrayList<String>();
        for (T callback : callbacks) {
            callbackNames.add(callback.getName());
        }
        return callbackNames;
    }

    /**
     * Dump schema changes we are processing to the log
     * 
     * @param diff
     */
    public void dumpChanges(DbSchemasDiff diff) {
        log.info("Start dumping changes");

        for (AnnotationValue newValue : diff.getNewAnnotationValues()) {
            log.info("new annotation value for class {}, field {}," +
                    " annotation type {}: {}={}", new Object[] {
                    newValue.getCfClass().getSimpleName(),
                    newValue.getFieldName(),
                    newValue.getAnnoClass().getSimpleName(),
                    newValue.getName(),
                    newValue.getValue() });
        }

        for (FieldInfo newField : diff.getNewFields()) {
            log.info("new field for class {}: {}",
                    newField.getCfClass().getSimpleName(),
                    newField.getName());
        }

        for (AnnotationType newAnno : diff.getNewClassAnnotations()) {
            log.info("new class annotation for class {}: {}",
                    newAnno.getCfClass().getSimpleName(), newAnno.getType());
        }

        for (AnnotationType newAnno : diff.getNewFieldAnnotations()) {
            log.info("new field annotation for class {}, field {}: {}",
                    new Object[] { newAnno.getCfClass().getSimpleName(),
                            newAnno.getFieldName(), newAnno.getType() });
        }

        for (DbSchema schema : diff.getNewClasses()) {
            log.info("new CF: {}", schema.getType());
        }

        log.info("Finish dumping changes");
    }
}

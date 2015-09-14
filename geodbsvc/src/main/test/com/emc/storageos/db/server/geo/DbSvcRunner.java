/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.emc.storageos.db.server.DbService;
import com.emc.storageos.services.util.LoggingUtils;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.coordinator.service.Coordinator;

/**
 * A runner to fork new java process for geo or local dbsvc.
 * 
 * We could not run 2 Cassandra instances in single process, so we need the
 * runner to start both local & geo dbsvc in a separated java process for
 * dbclient testing
 * 
 * @author liuh6
 */
public class DbSvcRunner {
    static {
        LoggingUtils.configureIfNecessary("geodbtest-log4j.properties");
        initDefaultFiles();
    }

    private static final Logger log = LoggerFactory
            .getLogger(DbSvcRunner.class);

    protected static CoordinatorClientImpl coordinator;

    protected static String DBSVC_CONFIG = "dbtest-conf.xml";
    protected static String GEODBSVC_CONFIG = "geodbtest-conf.xml";

    protected static String COORDINATORSVC_CONFIG = "coordinatortest-conf.xml";
    protected static String DBSVC_MAIN = "com.emc.storageos.db.server.geo.DbSvcRunner";

    protected static String SVC_VERSION = "2.2"; // version defined in
                                                 // db-var.xml or geodb-var.xml
    protected static String GEOSVC_ID = "geodb-standalone"; // svc id defined in
                                                            // geodb-var.xml

    protected static String DBSVC_ID = "db-standalone";

    private static String GEODB_DIR = "geodbtest"; // db dir defined in
                                                   // geodb-test.yaml
    private static String LOCALDB_DIR = "dbtest"; // db dir defined in
                                                  // db-test.yaml
    private static String ZK_DIR = "/data/zk"; // zk data dir defined in

    private File dataDir;

    private String configFile;
    private String serviceName;

    private Process process;

    public DbSvcRunner(String configFile, String serviceName) {
        this.configFile = configFile;
        this.serviceName = serviceName;
        setupDataDir(serviceName);
    }

    /**
     * Start the runner within the current running process
     */
    public void startInProcess() {
        final String[] args = new String[] { getFullConfigFilePath(configFile) };
        log.info("Starting " + DBSVC_MAIN + " with config file : " + args[0]);
        File dir = new File(GEODB_DIR);
        if (dir.exists()) {
            cleanDirectory(dir);
        }
        Thread inProcRunnerThread = new Thread(new Runnable() {
            public void run() {
                main(args);
                log.info(DBSVC_MAIN + "Thread has stopped");
            }
        }, DBSVC_MAIN + "Thread");
        inProcRunnerThread.start();
    }

    /**
     * Start the runner
     */
    public void start() {
        startProcessThread(serviceName, DBSVC_MAIN, getFullConfigFilePath(configFile));
    }

    /**
     * Stop the runner
     */
    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }

    /**
     * Start coordinatorsvc in new thread of current process
     * 
     * @param args
     */
    public void startCoordinator() {
        File dir = new File(ZK_DIR);
        if (dir.exists()) {
            cleanDirectory(dir);
        }
        Thread coordThread = new Thread(new Runnable() {
            public void run() {
                try {
                    final String[] args = new String[] { "classpath:" + COORDINATORSVC_CONFIG };
                    log.info("Starting Coordinator with config file : {}", args[0]);
                    FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(args);
                    Coordinator coordinator = (Coordinator) ctx.getBean("coordinatorsvc");
                    coordinator.start();
                    log.info("CoordinatorThread has stopped");
                } catch (Exception ex) {
                    log.error("Coordinator startup error", ex);
                }
            }
        }, "CoordinatorThread");
        coordThread.start();
    }

    private String getFullConfigFilePath(String fileName) {
        String confFile = Thread.currentThread().getContextClassLoader()
                .getResource(fileName).getFile();
        return confFile;
    }

    /**
     * Start a thread to fork new java process
     * 
     * @param threadName
     * @param main
     * @param conf
     */
    private void startProcessThread(final String threadName, final String main,
            final String conf) {
        Thread procThread = new Thread(new Runnable() {
            public void run() {
                try {
                    startProcess(main, conf);
                } catch (Exception e) {
                    log.error("exception starting process " + threadName, e);
                }
            }
        }, threadName);

        procThread.start();
    }

    /**
     * Deletes given directory
     * 
     * @param dir
     */
    protected void cleanDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    /**
     * Setup db data directory
     * 
     * @param name
     */
    private void setupDataDir(String serviceName) {
        String fileName = LOCALDB_DIR;
        if (Constants.GEODBSVC_NAME.equals(serviceName)) {
            fileName = GEODB_DIR;
        }
        dataDir = new File(fileName);
        if (dataDir.exists() && dataDir.isDirectory()) {
            cleanDirectory(dataDir);
        }
        dataDir.mkdir();
    }

    /**
     * Fork a new java process to run dbsvc
     * 
     * @param main
     * @param conf
     * @return
     * @throws Exception
     */
    private int startProcess(String main, String conf) throws Exception {
        String classPath = System.getProperty("java.class.path");
        ProcessBuilder processBuilder = new ProcessBuilder("java",
                "-Djava.net.preferIPv4Stack=true", main, conf);
        processBuilder.environment().put("CLASSPATH", classPath);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(dataDir);
        log.info("Starting " + main + " with conf " + conf);
        process = processBuilder.start();

        Thread closeChildThread = new Thread() {
            public void run() {
                log.info("Stopping child process");
                process.destroy();
            }
        };

        Runtime.getRuntime().addShutdownHook(closeChildThread);
        doWaitFor(process);
        log.info("process " + main + " with conf " + conf + " stopping");
        return process.exitValue();
    }

    /**
     * Redirect stdout/stderr of given process to current log until process
     * exits.
     * 
     * @param process
     */
    private void doWaitFor(Process process) {
        startReadStreamThread(process.getInputStream());
        startReadStreamThread(process.getErrorStream());

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.warn("e=", e);
        }
    }

    /**
     * Start a new thread to read from process out/err stream and dump to logs
     * 
     * @param is
     */
    private void startReadStreamThread(final InputStream is) {
        new Thread(Thread.currentThread().getName()) {
            public void run() {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(is));
                try {
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        log.info("{}", line);
                    }
                } catch (IOException e) {
                    // log.error("e=", e);
                } finally {
                    try {
                        br.close();
                    } catch (IOException e) {
                        // log.error("e=", e);
                    }
                }
            }
        }.start();
    }

    /**
     * Check if service is started
     * 
     * @return
     */
    public boolean isStarted() {
        try {
            CoordinatorClient coordinator = getCoordinator();
            List<Service> service = coordinator.locateAllServices(serviceName,
                    SVC_VERSION, null, null);
            if (service.iterator().hasNext()) {
                Service svc = service.iterator().next();
                URI hostUri = svc.getEndpoint();
                log.info("Found " + svc.getName() + "; host = "
                        + hostUri.getHost() + "; port = " + hostUri.getPort());
                return true;
            }
        } catch (RetryableCoordinatorException e) {
            log.warn(
                    "no {} instance running. Coordinator exception message: {}",
                    serviceName, e.getMessage());
        } catch (Exception e) {
            log.error("service lookup failure", e);
        }

        return false;
    }

    /**
     * Wait until service started
     * 
     * @param timeout
     *            max wait time in seconds
     */
    public boolean waitUntilStarted(int timeout) {
        int cnt = 0;
        while (cnt < timeout) {
            if (isStarted()) {
                log.info("Dbsvc startup OK");
                return true;
            }
            sleep(4);
            cnt++;
        }
        return false;
    }

    public void sleep(int seconds) {
        try {
            Thread.sleep(1000 * seconds);
        } catch (InterruptedException ex) {
            // Ignore this exception.
        }

    }

    /**
     * Get CoordinatorClient instance
     * 
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    public CoordinatorClient getCoordinator() throws URISyntaxException,
            IOException {
        if (coordinator == null) {
            ZkConnection zkConn = new ZkConnection();
            List<URI> uris = new ArrayList<URI>();
            uris.add(new URI("coordinator://localhost:2181"));
            zkConn.setServer(uris);
            zkConn.setTimeoutMs(10000);
            zkConn.build();

            // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            // Junit test will be called in single thread by default, it's safe to ignore this violation
            coordinator = new CoordinatorClientImpl(); // NOSONAR ("squid:S2444")
            coordinator.setZkConnection(zkConn);
            coordinator.setSysSvcName("syssvc");
            coordinator.setSysSvcVersion("1");
            coordinator.setNodeCount(1);

            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("nodeaddrmap-var.xml");
            CoordinatorClientInetAddressMap inetAddressMap = (CoordinatorClientInetAddressMap) ctx.getBean("inetAddessLookupMap");
            if (inetAddressMap == null) {
                log.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
            }
            Map<String, DualInetAddress> controlNodes = inetAddressMap.getControllerNodeIPLookupMap();
            coordinator.setInetAddessLookupMap(inetAddressMap); // HARCODE FOR NOW

            DbVersionInfo dbVersionInfo = new DbVersionInfo();
            dbVersionInfo.setSchemaVersion(SVC_VERSION);
            coordinator.setDbVersionInfo(dbVersionInfo);
            coordinator.start();
        }

        return coordinator;
    }

    public static void main(String args[]) {
        try {
            String ctxFile = args[0];
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext("file:" + ctxFile);
            DbService dbsvc = (DbService) ctx.getBean("dbsvc");
            dbsvc.start();
            log.info("dbsvc is started");
            while (true) {
                ;
            }
        } catch (Exception ex) {
            log.error("Exception ", ex);
        }
    }

    protected static void initDefaultFiles() {
        File file = new File("/etc/config.defaults");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                log.error("Exception: ", e);
            }
        }
        File file2 = new File("/etc/ovfenv.properties");
        if (!file2.exists()) {
            try {
                file2.createNewFile();
            } catch (IOException e) {
                log.error("Exception: ", e);
            }
        }
    }

}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package plugin;

import controllers.util.CatalogAclListener;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.support.GenericXmlApplicationContext;
import play.Logger;
import play.Play;
import play.PlayPlugin;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.keystore.KeyStoreExporter;
import com.emc.storageos.security.validator.Validator;
import com.emc.vipr.client.ViPRCoreClient;

/**
 * Manages the connection into the Storage OS Framework
 */
public class StorageOsPlugin extends PlayPlugin {
    private static final String DEFAULT_CONTEXT_FILE = "dbclient-prod.xml";

    private static StorageOsPlugin instance = null;

    private String version;
    private GenericXmlApplicationContext context;

    private ZkConnection zkConnection;
    private CoordinatorClient coordinatorClient;
    private EncryptionProvider encryptionProvider;

    private AuthSvcEndPointLocator authSvcEndPointLocator;

    public static StorageOsPlugin getInstance() {
        return instance;
    }

    public StorageOsPlugin() {
        // Load the version from the client version
        String clientVersion = ViPRCoreClient.class.getPackage().getImplementationVersion();
        String version = StringUtils.defaultIfBlank(clientVersion, "dev");
        version = StringUtils.substringBefore(version, " ");
        this.version = version.replaceAll("[^A-Za-z0-9_\\-\\.]", "");
    }

    public String getVersion() {
        return version;
    }

    public static boolean isEnabled() {
        return "spring".equals(Play.configuration.getProperty("dbClient", "spring"));
    }

    public static String getContextFileName() {
        return Play.configuration.getProperty("dbClient.spring.context", DEFAULT_CONTEXT_FILE);
    }

    private String[] getContextFiles() {
        return new String[] { DEFAULT_CONTEXT_FILE, "portal-oss-conf.xml", "portal-emc-conf.xml" };
    }

    /**
     * Called at application start (and at each reloading) Time to start stateful things.
     */
    @Override
    public void onApplicationStart() {
        instance = this;// NOSONAR
                        // ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for field instance")
        if (!isEnabled()) {
            return;
        }

        try {
            Logger.info("Connecting to Coordinator Service");
            // To using Spring profile feature
            context = new GenericXmlApplicationContext();
            context.getEnvironment().setActiveProfiles(System.getProperty("buildType"));
            context.load(getContextFiles());
            context.refresh();

            Logger.info("Connected to Coordinator Service");

            zkConnection = getBean("zkconn", ZkConnection.class);
            coordinatorClient = getBean("coordinator", CoordinatorClient.class);
            encryptionProvider = getBean("encryptionProvider", EncryptionProvider.class);

            authSvcEndPointLocator = getBean("authSvcEndpointLocator", AuthSvcEndPointLocator.class);

            Validator.setAuthSvcEndPointLocator(authSvcEndPointLocator);
            Validator.setCoordinator(coordinatorClient);
            // need reference to local-security-conf.xml to load this
            Validator.setStorageOSUserRepository(null);

            coordinatorClient.start();
            encryptionProvider.start();
            Logger.info("Started ViPR connection, version: %s", version);

            KeyStoreExporter keystoreExporter = getBean("keystoreExporter", KeyStoreExporter.class);
            keystoreExporter.export();

            // register node listener for catalog acl change
            coordinatorClient.addNodeListener(new CatalogAclListener());
            Logger.info("added CatalogAclListener");
        } catch (Exception e) {
            Logger.error(e, "Error initializing ViPR Connection");
            shutdown();
        }
    }

    private <T> T getBean(String name, Class<T> type) {
        return (T) context.getBean(name, type);
    }

    private void shutdown() {
        Logger.error("Shutting down");
        Runtime.getRuntime().halt(-1);
    }

    @Override
    public void onApplicationStop() {
        if (!isEnabled()) {
            return;
        }
        zkConnection.disconnect();
        Logger.info("Stopped StorageOS Connection");

        context.destroy();
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public EncryptionProvider getEncryptionProvider() {
        return encryptionProvider;
    }

    public AuthSvcEndPointLocator getAuthSvcEndPointLocator() {
        return authSvcEndPointLocator;
    }
}

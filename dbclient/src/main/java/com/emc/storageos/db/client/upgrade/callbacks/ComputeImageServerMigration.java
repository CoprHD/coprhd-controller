/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class ComputeImageServerMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory
            .getLogger(ComputeImageServerMigration.class);
    private EncryptionProvider encryptionProvider;

    private static final String IMAGE_SERVER_ADDRESS = "image_server_address";
    private static final String IMAGE_SERVER_USERNAME = "image_server_username";
    private static final String IMAGE_SERVER_TFTPBOOT_DIR = "image_server_tftpboot_directory";
    private static final String IMAGE_SERVER_OS_NETWORK_IP = "image_server_os_network_ip";
    private static final String IMAGE_SERVER_HTTP_PORT = "image_server_http_port";
    private static final String IMAGE_SERVER_IMAGEDIR = "image_server_image_directory";
    private static final String IMAGE_SERVER_ENC_PWD = "image_server_encpassword";

    @Override
    public void process() throws MigrationCallbackException {
        try {
            // Retrieve data from zk db using coordinator client
            Configuration config1 = coordinatorClient.queryConfiguration(
                    PropertyInfoExt.TARGET_PROPERTY,
                    PropertyInfoExt.TARGET_PROPERTY_ID);

            log.info("imageServerIP:" + config1.getConfig(IMAGE_SERVER_ADDRESS));
            PropertyInfo p = coordinatorClient.getPropertyInfo();
            EncryptionProviderImpl encryptionProvider1 = new EncryptionProviderImpl();
            encryptionProvider1.setCoordinator(coordinatorClient);
            encryptionProvider1.start();
            this.encryptionProvider = encryptionProvider1;

            if (!StringUtils.isBlank(p.getProperty("image_server_address"))) {

                ComputeImageServer imageServer = new ComputeImageServer();
                imageServer.setId(URIUtil.createId(ComputeImageServer.class));
                imageServer.setImageServerIp(p
                        .getProperty(IMAGE_SERVER_ADDRESS));
                imageServer.setLabel(p
                        .getProperty(IMAGE_SERVER_ADDRESS));
                imageServer.setImageServerUser(p
                        .getProperty(IMAGE_SERVER_USERNAME));
                imageServer.setTftpBootDir(p
                        .getProperty(IMAGE_SERVER_TFTPBOOT_DIR));
                imageServer.setImageServerSecondIp(p
                        .getProperty(IMAGE_SERVER_OS_NETWORK_IP));
                imageServer.setImageServerHttpPort(p
                        .getProperty(IMAGE_SERVER_HTTP_PORT));
                imageServer.setImageDir(p
                        .getProperty(IMAGE_SERVER_IMAGEDIR));
                String encryptedPassword = p.getProperty(IMAGE_SERVER_ENC_PWD);
                try {
                    imageServer.setImageServerPassword(encryptionProvider.decrypt(Base64.decodeBase64(encryptedPassword)));
                } catch (Exception e) {
                    log.error("Can't decrypt image server password :" + e.getLocalizedMessage());
                    e.printStackTrace();
                    log.error("Failed to save image server details into database during migration");
                    throw e;
                }
                associateComputeImages(imageServer);
                dbClient.createObject(imageServer);
                log.info("Saved imageServer info into cassandra db");

                // Associate all existing Compute Systems to this image server
                List<URI> computeSystemURIs = dbClient.queryByType(
                        ComputeSystem.class, true);
                Iterator<ComputeSystem> computeSystemListIterator = dbClient
                        .queryIterativeObjects(ComputeSystem.class,
                                computeSystemURIs);
                while (computeSystemListIterator.hasNext()) {
                    ComputeSystem computeSystem = computeSystemListIterator
                            .next();
                    computeSystem.setComputeImageServer(imageServer.getId());
                    dbClient.persistObject(computeSystem);

                }
                // Delete imageserverConf data from ZK db.
                Configuration config = coordinatorClient.queryConfiguration(
                        PropertyInfoExt.TARGET_PROPERTY,
                        PropertyInfoExt.TARGET_PROPERTY_ID);
                config.removeConfig(IMAGE_SERVER_ADDRESS);
                config.removeConfig(IMAGE_SERVER_USERNAME);
                config.removeConfig(IMAGE_SERVER_ENC_PWD);
                config.removeConfig(IMAGE_SERVER_TFTPBOOT_DIR);
                config.removeConfig(IMAGE_SERVER_HTTP_PORT);
                config.removeConfig(IMAGE_SERVER_OS_NETWORK_IP);
                config.removeConfig(IMAGE_SERVER_IMAGEDIR);
                coordinatorClient.persistServiceConfiguration(config);

            } else {
                log.info("No image server configuration found in Zookeeper db");
            }

        } catch (Exception e) {
            log.error("Exception occured while migrating compute image server information");
            log.error(e.getMessage(), e);
        }

    }

    private void associateComputeImages(ComputeImageServer imageServer) {
        boolean hasAvailableImages = false;
        List<URI> computeImageURIs = dbClient.queryByType(
                ComputeImage.class, true);
        Iterator<ComputeImage> computeImageListIterator = dbClient
                .queryIterativeObjects(ComputeImage.class,
                        computeImageURIs);
        while (computeImageListIterator.hasNext()) {
            ComputeImage computeImage = computeImageListIterator
                    .next();
            if (computeImage.getComputeImageStatus().equals(ComputeImageStatus.NOT_AVAILABLE.name())) {
                if (imageServer.getFailedComputeImages() == null) {
                    imageServer.setFailedComputeImages(new StringSet());
                }
                imageServer.getFailedComputeImages().add(computeImage.getId().toString());

            } else if (computeImage.getComputeImageStatus().equals(ComputeImageStatus.AVAILABLE.name())) {
                if (imageServer.getComputeImages() == null) {
                    imageServer.setComputeImages(new StringSet());
                }
                imageServer.getComputeImages().add(computeImage.getId().toString());
                hasAvailableImages = true;
            }

        }
        if (hasAvailableImages) {
            imageServer.setComputeImageServerStatus(ComputeImageServer.ComputeImageServerStatus.AVAILABLE.name());
        } else {
            imageServer.setComputeImageServerStatus(ComputeImageServer.ComputeImageServerStatus.NOT_AVAILABLE.name());
        }
    }

}

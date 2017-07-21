/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.plugins.common.Constants;

public abstract class AbstractMigrationServiceApiImpl implements MigrationServiceApi {

    // A logger reference.
    private static final Logger logger = LoggerFactory.getLogger(AbstractMigrationServiceApiImpl.class);

    protected DbClient dbClient;

    private CoordinatorClient coordinator;

    // Coordinator getter/setter
    public void setCoordinator(CoordinatorClient locator) {
        this.coordinator = locator;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    // Db client getter/setter
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public AbstractMigrationServiceApiImpl() {
        super();
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup controller for
     * EXTERNALDEVICE.
     *
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        T controller;
        try {
            controller = coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
        } catch (RetryableCoordinatorException rex) {
            controller = coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, Constants.EXTERNALDEVICE, clazz.getSimpleName());
        }
        return controller;
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup default controller
     * for EXTERNALDEVICE tag.
     *
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param externalDevice
     *            hardware tag for external devices
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw, String externalDevice) {
        return coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, externalDevice, clazz.getSimpleName());
    }
}

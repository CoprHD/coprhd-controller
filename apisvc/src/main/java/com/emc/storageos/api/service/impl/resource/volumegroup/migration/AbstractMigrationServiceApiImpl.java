/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.plugins.common.Constants;


public abstract class AbstractMigrationServiceApiImpl implements MigrationServiceApi {
	
	    // A logger reference.
	    private static final Logger logger = LoggerFactory
	            .getLogger(AbstractMigrationServiceApiImpl.class);
	    
        public AbstractMigrationServiceApiImpl() {
            super();
        }

        protected final static String CONTROLLER_SVC = "controllersvc";
	    protected final static String CONTROLLER_SVC_VER = "1";
	    
	    @Autowired
	    private PermissionsHelper _permissionsHelper;

	    @Autowired
	    protected DependencyChecker _dependencyChecker;
	
	    protected DbClient _dbClient;

	    private CoordinatorClient _coordinator;

	    // Permissions helper getter/setter
	    public void setPermissionsHelper(PermissionsHelper permissionsHelper) {
	        _permissionsHelper = permissionsHelper;
	    }

	    public PermissionsHelper getPermissionsHelper() {
	        return _permissionsHelper;
	    }

	    // Dependency checker getter/setter
	    public void setDependencyChecker(DependencyChecker dependencyChecker) {
	        _dependencyChecker = dependencyChecker;
	    }

	    public DependencyChecker getDependencyChecker() {
	        return _dependencyChecker;
	    }

	    // Coordinator getter/setter
	    public void setCoordinator(CoordinatorClient locator) {
	        _coordinator = locator;
	    }

	    public CoordinatorClient getCoordinator() {
	        return _coordinator;
	    }

	    // Db client getter/setter
	    public void setDbClient(DbClient dbClient) {
	        _dbClient = dbClient;
	    }

	    public DbClient getDbClient() {
	        return _dbClient;
	    }	  

	    /**
	     * Map of implementing class instances; used for iterating through them for
	     * connectivity purposes.
	     */
	    static private Map<String, AbstractMigrationServiceApiImpl> s_migrationImplementations = new HashMap<String, AbstractMigrationServiceApiImpl>();

	    /**
	     * Constructor used to keep track of the various implementations of this class.
	     * In particular, we are interested in "migrationType" implementations
	     * 
	     * @param migrationType
	     *           
	     */
	    public AbstractMigrationServiceApiImpl(String migrationType) {
	        if (migrationType != null) {
	            s_migrationImplementations.put(migrationType, this);
	        }
	    }

	    static protected Map<String, AbstractMigrationServiceApiImpl> getMigrationImplementations() {
	        return s_migrationImplementations;
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
	            controller = _coordinator.locateService(
	                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
	        } catch (RetryableCoordinatorException rex) {
	            controller = _coordinator.locateService(
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
	        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, externalDevice, clazz.getSimpleName());
	    }
}

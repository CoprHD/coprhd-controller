/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow.Method;

public class AbstractMountAdapter implements HostMountAdapter {
    private Logger log;

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    protected ModelClient modelClient;

    protected DbClient dbClient;

    protected CoordinatorClient coordinator;

    public ModelClient getModelClient() {
        return modelClient;
    }

    @Override
    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    @Override
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    protected <T extends DataObject> T findModelByLabel(List<T> models, String label) {
        for (T model : models) {
            if (StringUtils.equals(label, model.getLabel())) {
                return model;
            }
        }
        return null;
    }

    protected Host findHostByLabel(List<Host> models, String label) {
        for (Host model : models) {
            if (StringUtils.equals(label, model.getLabel())) {
                return model;
            }
        }
        return null;
    }

    /**
     * Gets a model object by ID.
     * 
     * @param modelClass
     *            the model class.
     * @param id
     *            the ID of the model object.
     * @return the model.
     */
    protected <T extends DataObject> T get(Class<T> modelClass, URI id) {
        return modelClient.of(modelClass).findById(id);
    }

    protected synchronized Logger getLog() {
        if (log == null) {
            log = LoggerFactory.getLogger(getClass());
        }
        return log;
    }

    protected void error(String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().error(String.format(message, args));
        } else {
            getLog().error(message);
        }
    }

    protected void warn(Throwable t, String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().warn(String.format(message, args), t);
        } else {
            getLog().warn(message, t);
        }
    }

    protected void warn(String message, Object... args) {
        if (args != null && args.length > 0) {
            getLog().warn(String.format(message, args));
        } else {
            getLog().warn(message);
        }
    }

    protected void info(String message, Object... args) {
        if (getLog().isInfoEnabled()) {
            if (args != null && args.length > 0) {
                getLog().info(String.format(message, args));
            } else {
                getLog().info(message);
            }
        }
    }

    protected void debug(String message, Object... args) {
        if (getLog().isDebugEnabled()) {
            if (args != null && args.length > 0) {
                getLog().debug(String.format(message, args));
            } else {
                getLog().debug(message);
            }
        }
    }

    @Override
    public String getErrorMessage(Throwable t) {
        Throwable rootCause = getRootCause(t);
        if (rootCause instanceof UnknownHostException) {
            return "Unknown host: " + rootCause.getMessage();
        } else if (rootCause instanceof ConnectException) {
            return "Error connecting: " + rootCause.getMessage();
        } else if (rootCause instanceof NoRouteToHostException) {
            return "No route to host: " + rootCause.getMessage();
        } else if (rootCause instanceof SSLException) {
            return "SSL error: " + rootCause.getMessage();
        }
        return getClosestErrorMessage(t);
    }

    protected String getClosestErrorMessage(Throwable originalThrowable) {
        String message = null;
        Throwable t = originalThrowable;
        while ((t != null) && (message == null)) {
            message = t.getMessage();
            t = t.getCause() != t ? t.getCause() : null;
        }
        if (message == null) {
            message = originalThrowable.getClass().getName();
        }
        return message;
    }

    protected Throwable getRootCause(Throwable t) {
        Throwable rootCause = t;
        while ((rootCause.getCause() != null) && (rootCause.getCause() != rootCause)) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz
     *            controller interface
     * @param hw
     *            hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return coordinator.locateService(
                clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    @Override
    public void doMount(HostDeviceInputOutput args) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doUnmount(HostDeviceInputOutput args) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public Method createDirectoryMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method addtoFSTabMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method mountDeviceMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method verifyMountPointMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method unmountDeviceMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method removeFromFSTabMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method deleteDirectoryMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method setMountTagMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Method removeMountTagMethod(HostDeviceInputOutput args) {
        // TODO Auto-generated method stub
        return null;
    }
}

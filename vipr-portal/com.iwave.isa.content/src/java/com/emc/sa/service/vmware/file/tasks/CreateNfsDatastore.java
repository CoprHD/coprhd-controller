/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import static com.emc.sa.service.vmware.VMwareUtils.isAlreadyExists;
import static com.emc.sa.service.vmware.VMwareUtils.isPermissionDenied;
import static com.emc.sa.service.vmware.VMwareUtils.isPlatformConfigFault;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.service.vmware.VMwareUtils;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.AlreadyExists;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

/**
 * Create an NFS Datastore in VCenter
 */
public class CreateNfsDatastore extends ExecutionTask<Datastore> {
    private static final int CREATE_RETRIES = 34;

    private final HostStorageAPI hostStorageAPI;
    private final HostSystem host;
    private final String datastoreName;
    private final String fileserver;
    private final String mountPath;

    public CreateNfsDatastore(HostSystem host, String fileserver, String mountPath, String datastoreName) {
        this.host = host;
        this.hostStorageAPI = new HostStorageAPI(this.host);
        this.datastoreName = datastoreName;
        this.fileserver = fileserver;
        this.mountPath = mountPath;
        provideDetailArgs(datastoreName, host.getName(), fileserver, mountPath);
    }

    @Override
    public Datastore executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        long starttime = System.nanoTime();
        try {
            return createNfsDatastore();
        } catch (VMWareException e) {
            throw handleException(fileserver + mountPath, e);
        } finally {
            info("elapsed time: %d seconds", (System.nanoTime() - starttime) / 1000 / 1000 / 1000);
        }
    }

    private Datastore createNfsDatastore() {
        RuntimeException lastException = new RuntimeException("Unable to create NFS Datastore.");
        for (int retry = 1; retry <= CREATE_RETRIES + 1; retry++) {
            info("Attempt #%d to create the NFS Datastore...", retry);
            try {
                return hostStorageAPI.createNfsDatastore(datastoreName, fileserver, mountPath);
            } catch (VMWareException e) {
                lastException = e;
                handleCreationAttemptException(retry, e);
            }
        }
        info("Retries exhausted.");
        throw lastException;
    }

    private void handleCreationAttemptException(int retry, VMWareException e) {
        String errorMessage = e.getMessage();

        // if this is a platform config fault we can get some more info about its actual message
        if (isPlatformConfigFault(e)) {
            errorMessage = "Fault Message: " + getFaultMessage(e);
        }

        info(e, "Attempt #%d to create the NFS Datastore failed. %d retries remaining. [%s]", retry, CREATE_RETRIES - retry + 1,
                errorMessage);

        // if the error is the 'permission denied' error, we want to try again
        // any other error will be viewed as a show-stopper
        if (isPermissionDenied(e)) {
            delayRetry(retry);
        }
        else {
            throw e;
        }
    }

    /**
     * dalays the current thread. Logs any errors.
     * The delay is 1000 millis * the given multiplier.
     * Used within a loop, the iterator index can be used to give
     * progressively longer delays.
     */
    private void delayRetry(int multiplier) {
        debug("Sleeping before next retry...");
        try {
            Thread.sleep(1000 * multiplier);
        } catch (InterruptedException e) {
            warn(e, "Thread sleeping failed.\nContinuing with the next retry.");
        }
    }

    /**
     * An attempt was made to create the Datastore on a particular
     * file server interface and it failed. If it's an error we
     * were expecting might have occurred, we can throw a new
     * exception with some more relevant information.
     * Otherwise we need to raise this as a legitimate exception.
     */
    private Exception handleException(String mountPath, Exception e) {
        if (isPlatformConfigFault(e)) {
            return handleKnownException(e, getFaultMessage(e));
        }
        else if (isAlreadyExists(e)) {
            final String datastore = ((AlreadyExists) e.getCause()).getName();
            return handleKnownException(e, getMessage("CreateNfsDatastore.exception.exportMapped", datastore, getFaultMessage(e)));
        }
        else {
            return e;
        }
    }

    private RuntimeException handleKnownException(Exception e, String detailMessage, String... args) {
        final String errorMessage = getMessage("CreateNfsDatastore.exception.boiler", mountPath,
                String.format(detailMessage, (Object[]) args));
        error(e, errorMessage);
        return stateException(errorMessage, e);
    }

    private String getFaultMessage(Exception e) {
        if (e.getCause() instanceof MethodFault) {
            return VMwareUtils.getFaultMessage((MethodFault) e.getCause());
        }
        else {
            return null;
        }
    }

}

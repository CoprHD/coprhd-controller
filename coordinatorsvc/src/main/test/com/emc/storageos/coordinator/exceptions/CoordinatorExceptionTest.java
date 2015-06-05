/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.exceptions;

import java.net.URI;

import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.mappers.BaseServiceCodeExceptionTest;

public class CoordinatorExceptionTest extends BaseServiceCodeExceptionTest {

    private static final String WORKPOOL_NAME = "workpool";
    private static final String FULL_PATH = "/service/full/path";
    private static final String SCHEMA = "blah";
    private static final String ENDPOINT_KEY = "endpointKey";
    private static final String TAG = "tag";
    private static final String VERSION_1 = "1";
    private static final String DBSVC = "dbsvc";
    private static final String DBCONFIG = "dbconfig";
    private static final String LOCK_NAME = "testlock";

    @Test
    public void errorConnectingService() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .errorConnectingCoordinatorService(e);
        assertCoordinatorError("Error connecting coordinator service", exception);
    }

    @Test
    public void unableToPersistConfig() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToPersistTheConfiguration(e);
        assertCoordinatorError("Unable to persist the configuration", exception);
    }

    @Test
    public void unableToRemove() {
        final Exception e = createException();
        final URI uri = knownId;
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToRemoveConfiguration(uri.toString(), e);
        assertCoordinatorError("Unable to remove the configuration " + uri, exception);
    }

    @Test
    public void unableToList() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToListAllConfigurationForKind(DBCONFIG, e);
        assertCoordinatorError("Unable to list all the configurations for " + DBCONFIG,
                exception);
    }

    @Test
    public void unableToFindConfig() {
        final Exception e = createException();
        final URI uri = knownId;
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToFindConfigurationForKind(DBCONFIG, uri.toString(), e);
        assertCoordinatorError("Unable to find the configuration for kind:" + DBCONFIG
                + " and id:" + uri, exception);
    }

    @Test
    public void unableToLocateService() {
        final CoordinatorException exception = CoordinatorException.retryables
                .unableToLocateService(DBSVC, VERSION_1, TAG, ENDPOINT_KEY);
        assertCoordinatorSvcNotFound("Unable to locate service with name: " + DBSVC + ", version: "
                + VERSION_1 + ", tag: " + TAG + " and end point key: " + ENDPOINT_KEY, exception);
    }

    @Test
    public void unableToLocateServiceEndPoint() {
        final CoordinatorException exception = CoordinatorException.retryables
                .unableToLocateServiceNoEndpoint(DBSVC, VERSION_1, TAG, ENDPOINT_KEY);
        assertCoordinatorSvcNotFound("Unable to locate service with name: " + DBSVC + ", version: "
                + VERSION_1 + ", tag: " + TAG + " and end point key: " + ENDPOINT_KEY
                + ". No service with the specified end point", exception);
    }

    @Test
    public void unsupportedEndpoint() {
        final CoordinatorException exception = CoordinatorException.retryables
                .unsupportedEndPointSchema(SCHEMA);
        assertCoordinatorSvcNotFound("Unsupported end point schema. Expected rmi but found "
                + SCHEMA, exception);
    }

    @Test
    public void cannotFindNode() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.retryables
                .cannotFindNode(FULL_PATH, e);
        assertCoordinatorSvcNotFound("Coordinator client cannot find the node with path "
                + FULL_PATH, exception);
    }

    @Test
    public void errorFindingNode() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.retryables
                .errorWhileFindingNode(FULL_PATH, e);
        assertCoordinatorSvcNotFound("Error occurred while finding the node with path "
                + FULL_PATH, exception);
    }

    @Test
    public void cannotLocateService() {
        final CoordinatorException exception = CoordinatorException.retryables
                .cannotLocateService(FULL_PATH);
        assertCoordinatorSvcNotFound("The coordinator cannot locate any service with path "
                + FULL_PATH, exception);
    }

    @Test
    public void unableToGetPool() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToGetWorkPool(WORKPOOL_NAME, e);
        assertCoordinatorError("Unable to get work pool " + WORKPOOL_NAME, exception);
    }

    @Test
    public void unableToGetLock() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToGetLock(LOCK_NAME, e);
        assertCoordinatorError("Unable to get lock " + LOCK_NAME, exception);
    }

    @Test
    public void unableToGetPersistentLock() {
        final Exception e = createException();
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToGetPersistentLock(LOCK_NAME, e);
        assertCoordinatorError("Unable to get persistent lock " + LOCK_NAME, exception);
    }

    @Test
    public void unableToDecodeData() {
        final CoordinatorException exception = CoordinatorException.fatals
                .unableToDecodeDataFromCoordinator(null);
        assertCoordinatorError("Unable to decode the data from the coordinator", exception);
    }
}

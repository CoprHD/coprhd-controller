/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.exceptions;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.DBSVC_CONNECTION_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.DBSVC_DESERIALIZATION_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.DBSVC_ENTITY_NOT_FOUND;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.DBSVC_SERIALIZATION_ERROR;
import static com.sun.jersey.api.client.ClientResponse.Status.INTERNAL_SERVER_ERROR;
import static com.sun.jersey.api.client.ClientResponse.Status.SERVICE_UNAVAILABLE;

import java.beans.IntrospectionException;
import java.net.URI;

import org.junit.Test;

import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.svcs.errorhandling.mappers.BaseServiceCodeExceptionTest;

public class DatabaseExceptionTest extends BaseServiceCodeExceptionTest {

    @Test
    public void entityInactive() {
        final URI id = knownId;
        final DatabaseException exception = DatabaseException.fatals.unableToFindEntity(id);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_ENTITY_NOT_FOUND, "Unable to find entity " + id + " in database", exception);
    }

    @Test
    public void unsupportedType() {
        final DatabaseException exception =
                DatabaseException.fatals.serializationFailedUnsupportedType("column");
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Unsupported type", exception);
    }

    @Test
    public void connectionException() {
        final DatabaseException exception = DatabaseException.retryables.connectionFailed(new DriverException(EXCEPTION_MESSAGE));
        assertInternalException(SERVICE_UNAVAILABLE, DBSVC_CONNECTION_ERROR, "Database connection failed. Please check the database services and network connectivity on all nodes", exception);
    }

    @Test
    public void generalSerializeException() {
        final Exception e = createException();
        final DatabaseException exception = DatabaseException.fatals.serializationFailedClass(DatabaseExceptionTest.class, e);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Failed to serialize " + DatabaseExceptionTest.class
                + " object", exception);
    }

    @Test
    public void introspectionException() {
        final DatabaseException exception = DatabaseException.fatals.serializationFailedInitializingBeanInfo(DatabaseExceptionTest.class,
                new IntrospectionException(EXCEPTION_MESSAGE));
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Unexpected exception getting bean info", exception);
    }

    @Test
    public void indexGreaterMax() {
        final int index = 1;
        final DatabaseException exception = FatalDatabaseException.fatals.serializationFailedIndexGreaterThanMax("prop", index, 7);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR,
                "Serialization index greater than max expected: " + index, exception);
    }

    @Test
    public void unexpectedIndex() {
        final DatabaseException exception = DatabaseException.fatals
                .deserializationFailedUnexpectedIndex(DatabaseExceptionTest.class, 1, 7);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_DESERIALIZATION_ERROR, "Unexpected serialization index", exception);
    }

    @Test
    public void unexpectedType() {
        final DatabaseException exception = DatabaseException.fatals.deserializationFailedUnsupportedType(DatabaseExceptionTest.class,
                "prop", int.class);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_DESERIALIZATION_ERROR, "Unexpected type int", exception);
    }

    @Test
    public void inconsistentState() {
        final DatabaseException exception = DatabaseException.fatals
                .serializationFailedInconsistentPropertyMap(DatabaseExceptionTest.class);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Inconsistent state for PropertyMap while serializing "
                + DatabaseExceptionTest.class, exception);
    }

    @Test
    public void notImplemented() {
        final DatabaseException exception =
                FatalDatabaseException.fatals.serializationFailedNotImplementedForType(DatabaseExceptionTest.class, "prop", int.class);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Not implemented for type int", exception);
    }

    @Test
    public void length() {
        final DatabaseException exception =
                DatabaseException.fatals.serializationFailedFieldLengthTooLong(DatabaseExceptionTest.class, "prop", 777);
        assertInternalException(INTERNAL_SERVER_ERROR, DBSVC_SERIALIZATION_ERROR, "Length more than 64k", exception);
    }
}

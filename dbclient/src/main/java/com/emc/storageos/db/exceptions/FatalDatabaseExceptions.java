/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.exceptions;

import java.beans.IntrospectionException;
import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 500
 * <p/>
 * Remember to add the English message associated to the method in FatalDatabaseExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ Error+Handling#ErrorHandling-DevelopersGuide
 */
@MessageBundle
public interface FatalDatabaseExceptions {
    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public FatalDatabaseException unableToFindEntity(final URI value);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public FatalDatabaseException unableToFindClass(final String value);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public FatalDatabaseException unableToFindEntity(final URI value, Throwable cause);

    @DeclareServiceCode(ServiceCode.DBSVC_ENTITY_NOT_FOUND)
    public FatalDatabaseException cannotCreateSecretKeyForUser(URI id);

    // Failed to serialize {0} object
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedClass(Class<?> clazz, Throwable cause);

    // Failed to serialize {0}
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedId(URI id, Exception e);

    // Inconsistent state for PropertyMap while serializing {0}
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedInconsistentPropertyMap(Class<?> clazz);

    // Not implemented for type {2}
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedNotImplementedForType(Class<?> clazz, String property, Class<?> type);

    // Unsupported type
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedUnsupportedType(Object name);

    // Length more than 64k
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedFieldLengthTooLong(Class<?> clazz, String property, int length);

    // Serialization index greater than max expected: {1}
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedIndexGreaterThanMax(String property,
            int index, int maxProperties);

    // SerializationIndex reused. index {1} field {0}
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedIndexReused(String property, int index);

    // Unexpected exception getting bean info
    @DeclareServiceCode(ServiceCode.DBSVC_SERIALIZATION_ERROR)
    public FatalDatabaseException serializationFailedInitializingBeanInfo(Class<?> clazz,
            IntrospectionException ex);

    // Failed to deserialize {0} object
    @DeclareServiceCode(ServiceCode.DBSVC_DESERIALIZATION_ERROR)
    public FatalDatabaseException deserializationFailed(Class<?> clazz, Throwable cause);

    // Unexpected serialization index
    @DeclareServiceCode(ServiceCode.DBSVC_DESERIALIZATION_ERROR)
    public FatalDatabaseException deserializationFailedUnexpectedIndex(Class<?> clazz, int index, int max);

    // Unexpected type {2}
    @DeclareServiceCode(ServiceCode.DBSVC_DESERIALIZATION_ERROR)
    public FatalDatabaseException deserializationFailedUnsupportedType(Class<?> clazz, String property, Class<?> type);

    // Failed to deserialize encrypted property {0}
    @DeclareServiceCode(ServiceCode.DBSVC_DESERIALIZATION_ERROR)
    public FatalDatabaseException deserializationFailedEncryptedProperty(String property, Throwable cause);

    // Failed to deserialize property {0}
    @DeclareServiceCode(ServiceCode.DBSVC_DESERIALIZATION_ERROR)
    public FatalDatabaseException deserializationFailedProperty(String property, Throwable cause);

    // Unexpected purge error
    @DeclareServiceCode(ServiceCode.DBSVC_PURGE_ERROR)
    public FatalDatabaseException purgeFailed(Throwable cause);

    // Failed to query database
    @DeclareServiceCode(ServiceCode.DBSVC_QUERY_ERROR)
    public FatalDatabaseException queryFailed(Throwable cause);

    // Error while trying to get the value for property {1} on object of type {0}
    @DeclareServiceCode(ServiceCode.DBSVC_ERROR)
    public FatalDatabaseException failedToReadPropertyValue(Class<?> clazz, String property, Throwable cause);

    // Error during db upgrade
    @DeclareServiceCode(ServiceCode.DBSVC_ERROR)
    public FatalDatabaseException failedDuringUpgrade(String error, Throwable cause);

    @DeclareServiceCode(ServiceCode.DBSVC_ERROR)
    public FatalDatabaseException nullIdProvided();

    @DeclareServiceCode(ServiceCode.DBSVC_ANNOTATION_ERROR)
    public FatalDatabaseException invalidAnnotation(String annotationName, String errMsg);

    @DeclareServiceCode(ServiceCode.DBSVC_GEO_UPDATE_ERROR)
    public FatalDatabaseException disallowedGeoUpdate(String clazzName, String fieldName, String geoVersion, String expectVersion);
    
    @DeclareServiceCode(ServiceCode.DBSVC_FIELD_LENGTH_ERROR)
    public FatalDatabaseException fieldLengthTooShort(String clazzName, URI id, String methodName, int length, int minLength);
}

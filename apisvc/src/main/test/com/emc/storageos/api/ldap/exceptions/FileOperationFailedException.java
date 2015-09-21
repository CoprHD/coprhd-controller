/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.ldap.exceptions;

/**
 * Signals a failure while creating or deleting or modifying
 * a file ore directory with given in a given path.
 * Used for the in memory ldap server.
 */
public class FileOperationFailedException extends Exception {
    private final static String _errorMessage = "Cannot %s %s %s.";

    /**
     * Constructs a new exception with detailed error message.
     *
     * @param operation that specifies whether that failed attempt was
     *                  to create or delete or modify the given file or
     *                  directory.
     * @param entity represents a file or directory that is failed for
     *               the above operation.
     * @param name an absolute name of the of the file or directory that is
     *             failed for the above operation.
     */
    public FileOperationFailedException(String operation, String entity, String name) {
        super(String.format(_errorMessage, operation, entity, name));
    }

    /**
     * Constructs a new exception with detailed error message and cause.
     *
     * @param operation that specifies whether that failed attempt was
     *                  to create or delete or modify the given file or
     *                  directory.
     * @param entity represents a file or directory that is failed for
     *               the above operation.
     * @param name an absolute name of the of the file or directory that is
     *             failed for the above operation.
     * @param cause the cause that is saved for the retrieval for the future use.
     */
    public FileOperationFailedException(String operation, String entity, String name, Throwable cause) {
        super(String.format(_errorMessage, operation, entity, name), cause);
    }
}

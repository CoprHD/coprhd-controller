/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.ldap.exceptions;

/**
 * Signals that an attempt to open the file or directory denoted by a
 * specified pathname has failed. Used for the in memory ldap server.
 */
public class DirectoryOrFileNotFoundException extends Exception {
    private final static String _errorMessage = "%s %s cannot be found.";

    /**
     * Constructs a new exception with detailed error message.
     *
     * @param entity that represents an item that failed to open.
     *               It can file or directory.
     * @param name an absolute name of the of the file or directory that is
     *             not found.
     */
    public DirectoryOrFileNotFoundException(String entity, String name) {
        super(String.format(_errorMessage, entity, name));
    }

    /**
     * Constructs a new exception with detailed error message and cause
     *
     * @param entity that represents an item that failed to open is a file or
     *               directory.
     * @param name an absolute name of the of the file or directory that is
     *             not found.
     * @param cause the cause that is saved for the retrieval for the future use.
     */
    public DirectoryOrFileNotFoundException(String entity, String name, Throwable cause) {
        super(String.format(_errorMessage, entity, name), cause);
    }
}

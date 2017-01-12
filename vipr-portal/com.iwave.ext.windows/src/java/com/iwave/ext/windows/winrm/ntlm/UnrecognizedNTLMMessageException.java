/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

/**
 * Local type of exception so that we know something went wrong internally.
 *
 */
public class UnrecognizedNTLMMessageException extends Exception {

    /**
     * Default seialVersionUID.
     */
    private static final long serialVersionUID = 7365795815387049823L;

    /**
     * Constructor.
     * 
     * @param string
     *            the error message
     */
    public UnrecognizedNTLMMessageException(String string) {
        super(string);
    }
}

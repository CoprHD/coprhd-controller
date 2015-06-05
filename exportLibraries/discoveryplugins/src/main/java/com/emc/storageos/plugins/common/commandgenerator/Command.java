/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.plugins.common.commandgenerator;

import java.lang.reflect.InvocationTargetException;

/**
 * 
 * All Command Objects should implement this interface. Multiple Command Objects
 * like SMICommandObject,SNMP CommandObject. Each command Object has all the
 * necessary components encapsulated within itself. Every Command Object exposes
 * a method Execute.Client can invoke the execution of this command Object using
 * the exposed Method Execute.Command Objects will get created for each Domain
 * Logic operation.
 * 
 * Default CommandObject Implementation is CommandImpl, Other plugins can use
 * it, or if they need their own, they can still write their custom command
 */
public interface Command {
    /**
     * All Command Objects should implement this interface.
     * 
     * @return Object.
     * @throws InvocationTargetException.
     * @throws IllegalAccessException.
     * @throws IllegalArgumentException.
     */
    Object execute() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException;

    /**
     * Debug purpose, need the arguments to find out what happened in SMI Calls
     * 
     * @return Object[]
     */
    Object[] retreiveArguments();
    
    /**
     * Each command Object knows its index in existing List of Commands
     * @return int
     */
    int getCommandIndex();
}

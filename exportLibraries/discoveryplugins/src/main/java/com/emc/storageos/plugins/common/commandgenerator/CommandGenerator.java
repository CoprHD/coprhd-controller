/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.commandgenerator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Util;
import com.emc.storageos.plugins.common.domainmodel.Argument;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * CommandGenerator generates one or multiple Command Objects and returns a List
 * to the client.The number of command Objects depends on the size of the value
 * list returned by any of the previous operations, and currently used by this
 * operation to execute. Example : Say detection as scenario, for SMI plugin we
 * use SMI detection Say 1st operation returns 32 port instances. Then the next
 * operation needs to run 32 distinct CIM operations in case of SMI to get the
 * results back. Hence in this case 32 command objects will get created.
 * 
 */
public class CommandGenerator {
    /**
     * Logger.
     */
    private static final Logger _logger = LoggerFactory.getLogger(CommandGenerator.class);
    private Util _util;

    public void setutil(Util util) {
        this._util = util;
    }

    public Util getutil() {
        return _util;
    }

    /**
     * Get the Max size of the value List. If any of the argument is of type
     * reference, then get the size of the value list from keyMap data
     * structure. This size determines how many Commands will get created for
     * this operation.
     * 
     * To-Do: Add try-catch block here
     * 
     * @param operation
     *            Operation.
     * @param keyMap
     *            HashMap<String, List<Object>>.
     * @return int.
     * @throws SMIPluginException.
     */
    protected int getMaxLength(final Operation operation, final Map<String, Object> keyMap)
            throws BaseCollectionException {
        final List<Object> argobjects = operation.getArguments();
        int size = 1;
        StringBuilder sb = new StringBuilder();
        sb.append("Commands Objects to get Generated for Path : ");

        if (null != operation.getExecutionCycles() && keyMap.containsKey(operation.getExecutionCycles())) {
            sb.append(operation.getExecutionCycles());
            List<Object> objectpaths = (List<Object>) keyMap.get(operation.getExecutionCycles());
            size = objectpaths.size();
        } else {
            for (Object argobj : argobjects) {
                Argument arg = (Argument) argobj;
                if (arg.getMethod().contains("Reference")) {
                    String objectpath = (String) arg.getValue();
                    if (keyMap.containsKey(objectpath)) {
                        sb.append(objectpath);
                        Object resultObj = keyMap.get(objectpath);
                        if (resultObj instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<Object> obj = (List<Object>) keyMap.get(objectpath);
                            size = obj.size();
                        }
                        break;
                    }
                }
            }
        }
        sb.append(" is -> " + size);
        _logger.debug(sb.toString());
        sb = null;
        return size;
    }

    /**
     * creates a Command Object.
     * 
     * @param operation
     *            Operation.
     * 
     * @param componentMap
     *            HashMap<String, List<Object>>
     * @return CommandInterface : ICOmmand.
     * @throws SMIPluginException
     *             ex.
     * @throws InvocationTargetException
     *             ex.
     * @throws IllegalAccessException
     *             ex.
     * @throws IllegalArgumentException
     *             ex.
     */
    protected Command createCommandObject(
            final Operation operation, final Map<String, Object> componentMap, int index)
            throws BaseCollectionException, IllegalAccessException,
            InvocationTargetException {
        final CommandImpl commandobj = new CommandImpl();
        commandobj.setInputArgs(_util.returnInputArgs(operation, componentMap, index));
        final Object instance = _util.returnInstanceToRun(operation, componentMap, index);
        commandobj.setMethod(_util.getMethod(operation, operation.getMethod(), instance,
                Util.ENDPOINTS.OPERATION.toString()));
        commandobj.setInstance(instance);
        commandobj.setCommandIndex(index);
        _logger.debug("Command Object created.");
        return commandobj;
    }

    /**
     * Return Command Objects based on the operation passed in.
     * 
     * @param operation
     *            : Domain Logic operation.
     * 
     * @return List<CommandInterface>
     * @throws SMIPluginException
     *             ex.
     * @throws InvocationTargetException
     *             ex.
     * @throws IllegalAccessException
     *             ex.
     * @throws IllegalArgumentException
     *             ex.
     */
    public List<Command> returnCommandObjects(
            Operation operation, Map<String, Object> keyMap)
            throws BaseCollectionException, IllegalAccessException,
            InvocationTargetException {
        final List<Command> commandList = new ArrayList<Command>();
        final int maxCommands = getMaxLength(operation, keyMap);
        for (int commandIndex = 0; commandIndex < maxCommands; commandIndex++) {
            final Command command = createCommandObject(operation, keyMap, commandIndex);
            if (null != command) {
                commandList.add(command);
            }
        }
        return commandList;
    }
}

/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import com.emc.vipr.client.util.AbstractItemProcessor;
import com.emc.vipr.model.sys.logging.LogMessage;

/**
 * Item processor for LogMessages. This allows for one-by-one handling of LogMessages returned from the Logs API without
 * storing all in memory.
 * <p>
 * For example:
 * 
 * <pre>
 * ViPRSystemClient client = ...
 * // dump logs to standard out without holding any in memory
 * client.logs().search().items(new LogMessageProcessor() {
 *     public void processItem(LogMessage item) throws Exception {
 *         System.out.println(item.getSeverity() + " " + item.getTime() + " " + item.getMessage());
 *     }
 * });
 * </pre>
 */
public abstract class LogMessageProcessor extends AbstractItemProcessor<LogMessage> {

}

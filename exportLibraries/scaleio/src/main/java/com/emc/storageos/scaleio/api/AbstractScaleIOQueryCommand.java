/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract class implements processing of the CLI command output.
 * Classes that derive from this should at least implement getOutputPatternSpecification()
 * and processMatch(). These functions will be used in the output processing.
 * 
 * The expectation is that the overridden routines will be written so
 * that the type T result object can be filled in.
 * 
 * @param <T>
 */
abstract public class AbstractScaleIOQueryCommand<T> extends ScaleIOResultsCommand<T> {
    private static final Logger log = LoggerFactory.getLogger(AbstractScaleIOQueryCommand.class);
    private static final Pattern SIO_LOGIN_COMMAND_PATTERN =
            Pattern.compile("scli --login --username\\s+.*?\\s+--password\\s+.*?\\s+(.*)");

    abstract ParsePattern[] getOutputPatternSpecification();

    abstract void beforeProcessing();

    abstract void processMatch(ParsePattern spec, List<String> capturedStrings);

    @Override
    public void parseOutput() {
        long start = System.currentTimeMillis();

        beforeProcessing();

        String output = getOutput().getStdout();
        if (log.isDebugEnabled()) {
            String command = sanitizeCommand(getCommand());
            String args = Joiner.on(',').join(getArguments());
            log.debug(String.format("Command: %s Args: %s%nResult: %s", command, args, output));
        }

        for (String line : Splitter.on(CharMatcher.anyOf("\n\r")).split(output)) {
            if (line == null || line.isEmpty() || line.matches("\\s+")) {
                continue;
            }
            processLineAgainstSpec(line);
        }
        long total = System.currentTimeMillis() - start;
        log.info(String.format("Total time taken %f seconds for %s%n", (double) total / (double) 1000,
                this.getClass().getSimpleName()));
    }

    private String sanitizeCommand(String command) {
        String sanitized = command;
        if (command.contains("--login")) {
            // This is to prevent the username password from being logged
            Matcher commandParser = SIO_LOGIN_COMMAND_PATTERN.matcher(command);
            if (commandParser.matches()) {
                sanitized =
                        String.format("scli --login --username XXXXXXX --password XXXXXXX %s", commandParser.group(1));
            }
        }
        return sanitized;
    }

    private void processLineAgainstSpec(String line) {
        for (ParsePattern spec : getOutputPatternSpecification()) {
            List<String> capturedStrings = spec.isMatch(line);
            if (capturedStrings != null) {
                processMatch(spec, capturedStrings);
                break;
            }
        }
    }
}

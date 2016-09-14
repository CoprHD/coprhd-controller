package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

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
abstract public class AbstractIBMSVCQueryCommand<T> extends IBMSVCResultsCommand<T> {
    
    private static final Logger _log = LoggerFactory.getLogger(AbstractIBMSVCQueryCommand.class);

    abstract ParsePattern[] getOutputPatternSpecification();

    abstract void beforeProcessing();

    abstract void processMatch(ParsePattern spec, List<String> capturedStrings);

    @Override
    public void parseOutput() {
        long start = System.currentTimeMillis();

        beforeProcessing();

        String output = getOutput().getStdout();
        if (_log.isDebugEnabled()) {
            String command = getCommand();
            String args = Joiner.on(',').join(getArguments());
            _log.debug(String.format("Command: %s Args: %s\nResult: %s", command, args, output));
        }

        for (String line : Splitter.on(CharMatcher.anyOf("\n\r")).split(output)) {
            if (line == null || line.isEmpty() || line.matches("\\s+")) {
                continue;
            }
            processLineAgainstSpec(line);
        }
        long total = System.currentTimeMillis() - start;
        _log.info(String.format("Total time taken %f seconds for %s\n", (double) total / (double) 1000,
                this.getClass().getSimpleName()));
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

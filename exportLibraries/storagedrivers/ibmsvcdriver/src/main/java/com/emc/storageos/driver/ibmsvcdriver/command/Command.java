package com.emc.storageos.driver.ibmsvcdriver.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;

/**
 * Utility class for executing a command. This handles building the command line and resolving
 * variable references contained within. Variables are of the form <code>${variable}</code>, which
 * will be replaced in {@link #resolveCommandLine()}.
 * 
 */
public class Command {
    
    /** The command logger. */
    protected final Logger _log = LoggerFactory.getLogger(getClass());
    /** The implementation that handles the actual execution of the command. */
    private CommandExecutor commandExecutor;
    /** The resolved command line. */
    private String commandLine;
    /** The resolved command line values. */
    private List<String> commandLineValues;
    /** The command output. */
    private CommandOutput output;

    /** The base command string. */
    private String command;
    /** The list of arguments. */
    private List<String> arguments = new ArrayList<String>();
    /** The map of variable values. */
    private Map<String, String> variables = new HashMap<String, String>();
    /** Flag to indicate the command must be run as root. */
    private boolean runAsRoot = false;
    
    public Command() {
    }

    public Command(String command, String... args) {
        setCommand(command);
        addArguments(args);
    }

    public boolean isRunAsRoot() {
        return runAsRoot;
    }

    public void setRunAsRoot(boolean runAsRoot) {
        this.runAsRoot = runAsRoot;
    }

    /**
     * Gets the command executor.
     * 
     * @return the command executor.
     */
    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    /**
     * Sets the command executor.
     * 
     * @param commandExecutor the command executor.
     */
    public void setCommandExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Gets the command-line. This is only available after {@link #resolveCommandLine()} is called
     * during command execution.
     * 
     * @return the command-line.
     */
    public String getCommandLine() {
        return commandLine;
    }

    /**
     * Gets the command-line values. This is only available after {@link #resolveCommandLine()} is
     * called during command execution.
     * 
     * @return the command-line values.
     */
    public List<String> getCommandLineValues() {
        return commandLineValues;
    }

    /**
     * Gets the command-line that's safe for logging purposes.
     * 
     * @return the loggable command line.
     */
    protected String getLoggableCommandLine() {
        return commandLine;
    }

    /**
     * Gets the output of the command.
     * 
     * @return the command output.
     */
    public CommandOutput getOutput() {
        return output;
    }

    /**
     * Gets the base command to be executed.
     * 
     * @return the command to be executed.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Sets the base command to be executed.
     * 
     * @param command the base command.
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Gets the arguments for the command.
     * 
     * @return the arguments.
     */
    protected List<String> getArguments() {
        return arguments;
    }

    /**
     * Adds an argument to the command.
     * 
     * @param arg the argument to add.
     * @return this for chaining.
     */
    public Command addArgument(String arg) {
        arguments.add(arg);
        return this;
    }

    /**
     * Adds multiple arguments to the command.
     * 
     * @param args the arguments to add.
     * @return this for chaining.
     */
    public Command addArguments(String... args) {
        for (String arg : args) {
            addArgument(arg);
        }
        return this;
    }

    /**
     * Removes an argument from the command.
     * 
     * @param arg the argument to remove.
     * @return this for chaining.
     */
    public Command removeArgument(String arg) {
        arguments.remove(arg);
        return this;
    }

    /**
     * Determines if the command has the given argument.
     * 
     * @param arg the argument.
     * @return true if the command contains the given argument.
     */
    public boolean hasArgument(String arg) {
        return arguments.contains(arg);
    }

    /**
     * Adds a variable to the list of arguments.
     * 
     * @param name the variable name.
     * @return this for chaining.
     */
    public Command addVariable(String name) {
        return addArgument("${" + name + "}");
    }

    /**
     * Removes a variable from the list of arguments and removes its value.
     * 
     * @param name the variable name.
     * @return this for chaining.
     */
    public Command removeVariable(String name) {
        removeArgument("${" + name + "}");
        removeVariableValue(name);
        return this;
    }

    /**
     * Determines if the command contains the given variable.
     * 
     * @param name the variable name.
     * @return true if the command contains the given variable.
     */
    public boolean hasVariable(String name) {
        return hasArgument("${" + name + "}");
    }

    /**
     * Gets the variable value.
     * 
     * @param name the variable name.
     * @return the variable value.
     */
    public String getVariableValue(String name) {
        return variables.get(name);
    }

    /**
     * Sets a variable's value.
     * 
     * @param name the name of the variable.
     * @param value the variable value.
     */
    public void setVariableValue(String name, String value) {
        variables.put(name, value);
    }

    /**
     * Removes the specified variable value.
     * 
     * @param name the variable name.
     */
    public void removeVariableValue(String name) {
        variables.remove(name);
    }

    /**
     * Determines if a variable's value is set.
     * 
     * @param name the name of the variable.
     * @return the variable value.
     */
    public boolean hasVariableValue(String name) {
        return variables.containsKey(name);
    }

    /**
     * Requires that the specified variable values are set.
     * 
     * @param names the names of the variable values.
     * 
     * @throws CommandException if any of the required variable values are not set.
     */
    protected void requireVariableValues(String... names) {
        StringBuilder missing = new StringBuilder();
        for (String name : names) {
            if (!hasVariableValue(name)) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(name);
            }
        }

        if (missing.length() > 0) {
            throw new CommandException("Missing required value(s): " + missing);
        }
    }

    /**
     * Removes any of the specified variables is they have no value set.
     * 
     * @param names the names of the variables.
     */
    protected void removeUnsetVariables(String... names) {
        for (String name : names) {
            if (!hasVariableValue(name)) {
                removeVariable(name);
            }
        }
    }

    /**
     * Requires that the specified arguments are present.
     * 
     * @param names the names of the arguments.
     * 
     * @throws CommandException if any of the required arguments are not present.
     */
    protected void requireArguments(String... names) {
        StringBuilder missing = new StringBuilder();
        for (String name : names) {
            if (!hasArgument(name)) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(name);
            }
        }

        if (missing.length() > 0) {
            throw new CommandException("Missing required argument(s): " + missing);
        }
    }

    /**
     * Quotes the string and escapes any quotes within.
     * 
     * @param str the string to quote.
     * @return the quoted string.
     * 
     * @see #escapeQuotes(String)
     */
    public String quoteString(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        if (StringUtils.isNotBlank(str)) {
            sb.append(escapeQuotes(str));
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Escapes the quotes in the given string.
     * 
     * @param str the string to escape.
     * @return the escaped string.
     */
    public String escapeQuotes(String str) {
        return StringUtils.replace(str, "\"", "\\\"");
    }

    /**
     * Evaluates the given string for variable references.
     * 
     * @param str the string.
     * @return the evaluated string.
     */
    protected String evaluate(String str) {
        StrSubstitutor substitutor = new StrSubstitutor(variables);
        return substitutor.replace(str);
    }

    /**
     * Resolves the given variable value to a string.
     * 
     * @param name the name of the variable to resolve.
     * @return the string value.
     * 
     * @see #getVariableValue(String)
     */
    protected String resolveValue(String name) {
        return getVariableValue(name);
    }

    /**
     * Hook method for validating the command line arguments.
     * 
     * @throws CommandException if the command line is not valid.
     */
    protected void validateCommandLine() throws CommandException {
    }

    /**
     * Builds the command line and resolves all variable references.
     */
    protected void resolveCommandLine() {
        commandLineValues = new ArrayList<String>();
        commandLineValues.add(evaluate(getCommand()));
        for (String argument : getArguments()) {
            commandLineValues.add(evaluate(argument));
        }
        commandLine = StringUtils.join(commandLineValues, " ");
    }

    /**
     * Gets the resolved command line.
     * 
     * @return the resolved command line.
     */
    public String getResolvedCommandLine() {
        validateCommandLine();
        resolveCommandLine();
        return commandLine;
    }

    /**
     * Executes the resolved command line.
     */
    protected void executeCommandLine() throws CommandException {
        if (_log.isDebugEnabled()) {
            _log.debug("Executing: " + getLoggableCommandLine());
        }
        output = commandExecutor.executeCommand(this);
        if (_log.isTraceEnabled()) {
            if (output.getExitValue() != 0) {
                _log.trace("ExitValue: " + output.getExitValue());
            }
            if (StringUtils.isNotEmpty(output.getStdout())) {
                _log.trace("Stdout: " + output.getStdout());
            }
            if (StringUtils.isNotEmpty(output.getStderr())) {
                _log.trace("Stderr: " + output.getStderr());
            }
        }
    }

    /**
     * Processes the command output.
     * 
     * @throws CommandException if an error occurs.
     */
    protected void processOutput() throws CommandException {
    }

    /**
     * Processes the command output when an error occurs (non-zero exit value).
     * 
     * @throws CommandException if an error occurs.
     */
    protected void processError() throws CommandException {
        String errorMessage = getErrorMessage();
        throw new CommandException(errorMessage, output);
    }

    /**
     * Get an error message from the CommandOutput object
     */
    protected String getErrorMessage() {
        String errorMessage = StringUtils.trimToNull(output.getStderr());
        if (errorMessage == null) {
            errorMessage = StringUtils.trimToNull(output.getStdout());
        }
        return errorMessage;
    }

    /**
     * Executes the command.
     */
    public void execute() throws CommandException {
        if (commandExecutor == null) {
            throw new CommandException("commandExecutor is not set");
        }
        validateCommandLine();
        resolveCommandLine();
        executeCommandLine();

        if (output.getExitValue() == 0) {
            processOutput();
        }
        else {
            processError();
        }
    }

    /**
     * Determines if the string is contained in either STDOUT or STDERR, case-sensitive.
     * 
     * @param find the string to look for.
     * @return true if the string is found in either output stream (case-sensitive).
     */
    protected boolean containsInOutput(String find) {
        return containsInOutput(find, true);
    }

    /**
     * Determines if the string is contained in either STDOUT or STDERR, case-insensitive.
     * 
     * @param find the string to look for.
     * @return true if the string is found in either output stream (case-insensitive).
     */
    protected boolean containsInOutputIgnoreCase(String find) {
        return containsInOutput(find, false);
    }

    private boolean containsInOutput(String find, boolean caseSensitive) {
        if (output != null) {
            String stdout = output.getStdout();
            String stderr = output.getStderr();

            if (caseSensitive) {
                return StringUtils.contains(stdout, find) || StringUtils.contains(stderr, find);
            }
            else {
                return StringUtils.containsIgnoreCase(stdout, find) || StringUtils.containsIgnoreCase(stderr, find);
            }
        }
        return false;
    }
}

/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.command.CommandExecutor;
import com.iwave.ext.command.CommandOutput;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * This is a command executor that can execute multiple commands within a shell session.
 * 
 * @author jonnymiller
 */
public class ShellCommandExecutor implements CommandExecutor {
    private static final Logger LOG = Logger.getLogger(ShellCommandExecutor.class);
    private static final Pattern PROMPT = Pattern.compile("[$#>] ");
    private static final int TIMEOUT = 600;
    private static final String ENTER = "\r";
    private static final String EXIT = "exit";

    /** The connection information. */
    private SSHConnection connection;
    /** The timeout for commands. */
    private int timeoutInSeconds = TIMEOUT;
    /** The current session initial prompt. */
    private String initialPrompt;
    /** The pattern for the command prompt. */
    private Pattern promptPattern;

    /** The shell encoding (defaults to UTF-8). */
    private String encoding = "UTF-8";
    /** The SSH session. */
    private Session session;
    /** The shell. */
    private ChannelShell shell;
    /** The shell configurator (optional). */
    private ShellConfigurator shellConfigurator;
    /** Stdin writer. */
    private Writer stdin;
    /** The consumer of stdout. */
    private CharStreamConsumer stdout;
    /** Tracks the position of stdout between command executions. */
    private int stdoutPos;

    public ShellCommandExecutor() {
    }

    public ShellCommandExecutor(String host, String username, String password) {
        this(host, 22, username, password);
    }

    public ShellCommandExecutor(String host, int port, String username, String password) {
        this.connection = new SSHConnection(host, port, username, password);
    }

    public ShellCommandExecutor(SSHConnection connection) {
        this.connection = connection;
    }

    /**
     * Gets the SSH connection information.
     * 
     * @return the connection information.
     */
    public SSHConnection getConnection() {
        return connection;
    }

    /**
     * Sets the SSH connection information.
     * 
     * @param connection the connection information.
     */
    public void setConnection(SSHConnection connection) {
        this.connection = connection;
    }

    public ShellConfigurator getShellConfigurator() {
        return shellConfigurator;
    }

    public void setShellConfigurator(ShellConfigurator shellConfigurator) {
        this.shellConfigurator = shellConfigurator;
    }

    public int getTimeoutInSeconds() {
        return timeoutInSeconds;
    }

    public void setTimeoutInSeconds(int timeout) {
        this.timeoutInSeconds = timeout;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Gets the prompt that was matched initially after login.
     * 
     * @return the initial prompt.
     */
    public String getInitialPrompt() {
        return initialPrompt;
    }

    /**
     * Gets the exact prompt pattern.
     * 
     * @return the exact prompt pattern.
     */
    public Pattern getPromptPattern() {
        return promptPattern;
    }

    /**
     * Sets the prompt pattern. This will be set after {@link #connect()}, and may be overridden
     * then if required.
     * 
     * @param promptPattern the exact prompt pattern.
     */
    public void setPromptPattern(Pattern promptPattern) {
        this.promptPattern = promptPattern;
    }

    /**
     * Determines if this is connected.
     * 
     * @return true if this is connected.
     */
    public boolean isConnected() {
        return (shell != null) && !shell.isClosed();
    }

    /**
     * Connects to the system with the default prompt pattern (<code>[$#&gt;] </code>).
     * 
     * @throws SSHException
     */
    public void connect() throws SSHException {
        connect(PROMPT);
    }

    /**
     * Connects to the system using the specified initial prompt pattern. The actual prompt matched
     * will be available via {@link #getInitialPrompt()}, and the specific prompt pattern may be
     * changed after using {@link #setPromptPattern(Pattern)}.
     * 
     * @throws SSHException if an error occurs.
     */
    public void connect(Pattern prompt) throws SSHException {
        if (!isConnected()) {
            try {
                debug("Connecting to %s:%s as %s", connection.getHost(), connection.getPort(),
                        connection.getUsername());

                // Create a new SSH session
                session = new JSch().getSession(connection.getUsername(), connection.getHost(),
                        connection.getPort());
                session.setUserInfo(new SSHUserInfo(connection.getPassword()));
                session.connect();

                debug("Opening shell channel, encoding: %s", encoding);

                // Open a shell and setup the input/output
                shell = (ChannelShell) session.openChannel("shell");
                stdin = new BufferedWriter(
                        new OutputStreamWriter(shell.getOutputStream(), encoding));
                stdout = new CharStreamConsumer(shell.getInputStream(), encoding);
                stdout.setLogger(Logger.getLogger(getClass().getName() + ".stdout"));
                configureShell(shell);
                shell.connect();

                waitForInitialPrompt(prompt);
            } catch (JSchException e) {
                forceQuit();
                throw new SSHException(e);
            } catch (IOException e) {
                forceQuit();
                throw new SSHException(e);
            }
        }
    }

    /**
     * Configures the shell before connect.
     * 
     * @param shell the shell to configure.
     */
    protected void configureShell(ChannelShell shell) {
        if (shellConfigurator != null) {
            shellConfigurator.configureShell(shell);
        }
    }

    /**
     * Sends a shell command.
     * 
     * @param command the command to send.
     * 
     * @throws IOException if an I/O error occurs.
     */
    protected void send(String command) throws IOException {
        debug("Sending: %s", command);
        stdin.write(command);
        stdin.write(ENTER);
        stdin.flush();
    }

    /**
     * Gets the output for the currently running command.
     * 
     * @return the current command output.
     */
    protected String getCurrentCommandOutput() {
        return StringUtils.substring(getCurrentStandardOutContents(), stdoutPos);
    }

    /**
     * Gets the current standard out contents (from the entire shell session).
     * 
     * @return the current standard out contents.
     */
    protected String getCurrentStandardOutContents() {
        return stdout.toString();
    }

    /**
     * Waits for the given pattern to appear in the output.
     * 
     * @param pattern the pattern.
     * @return the matched value.
     * 
     * @throws IOException
     */
    private String waitFor(Pattern pattern) throws IOException {
        debug("Waiting for: %s", pattern.pattern());
        String output = getCurrentCommandOutput();
        int lastLength = output.length();
        long lastChange = System.currentTimeMillis();

        try {
            Matcher m = pattern.matcher(output);
            while (!m.find()) {
                boolean noChange = (output.length() == lastLength);
                if (noChange) {
                    long sinceLastChange = System.currentTimeMillis() - lastChange;
                    if (sinceLastChange > (timeoutInSeconds * 1000)) {
                        debug("Timeout with output: %s", output);
                        throw new IOException("Timeout waiting for: " + m.pattern().pattern());
                    }
                }
                else {
                    lastChange = System.currentTimeMillis();
                }

                Thread.sleep(20);

                output = getCurrentCommandOutput();
                lastLength = output.length();
                m.reset(output);
            }
            String match = m.group();
            debug("Found: '%s'", match);
            return match;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * Waits for the first prompt.
     * 
     * @throws IOException
     */
    private void waitForInitialPrompt(Pattern initialPromptPattern) throws IOException {
        String prompt = waitFor(initialPromptPattern);

        String output = getCurrentStandardOutContents();
        int index = StringUtils.indexOf(output, prompt) + prompt.length();

        // Find the initial prompt for this session
        initialPrompt = StringUtils.substring(output, 0, index);
        if (StringUtils.contains(initialPrompt, '\n')) {
            initialPrompt = StringUtils.substringAfterLast(initialPrompt, "\n");
        }

        debug("Found initial prompt: '%s'", initialPrompt);
        promptPattern = Pattern.compile(Pattern.quote(initialPrompt));

        stdoutPos = index;
    }

    /**
     * Disconnects from the remote host.
     * 
     * @throws SSHException if an error occurs.
     */
    public void disconnect() throws SSHException {
        if (isConnected()) {
            try {
                send(EXIT);
            } catch (IOException e) {
                throw new SSHException(e);
            } finally {
                forceQuit();
            }
            debug("Disconnected");
        }
    }

    /**
     * Forcibly quits the session.
     */
    private void forceQuit() {
        if (stdout != null) {
            stdout.close();
        }

        try {
            if (shell != null) {
                shell.disconnect();
            }
        } catch (RuntimeException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            shell = null;
        }

        try {
            if (session != null) {
                session.disconnect();
            }
        } catch (RuntimeException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            session = null;
        }
    }

    /**
     * Executes a command on the remote system.
     */
    @Override
    public CommandOutput executeCommand(Command command) throws CommandException {
        if (!isConnected()) {
            connect();
        }
        String cli = command.getCommandLine();
        return sendCommand(cli);
    }

    /**
     * Sends a command to the remote host. This returns a CommandOutput, but the exit value is
     * always 0 since it is being handled by the remote shell.
     * 
     * @param command the command to send.
     * @return the command output.
     */
    protected CommandOutput sendCommand(String command) {
        try {
            send(command);
            String matched = waitFor(promptPattern);

            String stdout = getCurrentCommandOutput();
            stdoutPos += StringUtils.length(stdout);

            // Strip the command from the start of the output (ignoring any inserted line breaks)
            stdout = IWaveStringUtils.removeStartIgnoringWhiteSpace(stdout, command);
            if (StringUtils.startsWith(stdout, "\r\n")) {
                stdout = StringUtils.removeStart(stdout, "\r\n");
            }
            else if (StringUtils.startsWith(stdout, "\r") || StringUtils.startsWith(stdout, "\n")) {
                stdout = StringUtils.substring(stdout, 1);
            }
            // Strip the prompt from the end of the output
            stdout = StringUtils.removeEnd(stdout, matched);

            return new CommandOutput(stdout, null, 0);
        } catch (Exception e) {
            CommandException ce = new CommandException(e);
            ce.setOutput(tryGetCommandOutput());
            throw ce;
        }
    }

    private CommandOutput tryGetCommandOutput() {
        try {
            String stdout = getCurrentCommandOutput();
            return new CommandOutput(stdout, null, 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public List<CommandOutput> executeCommands(Command... commands) throws CommandException {
        return executeCommands(Arrays.asList(commands));
    }

    public List<CommandOutput> executeCommands(List<? extends Command> commands)
            throws CommandException {
        List<CommandOutput> results = Lists.newArrayList();
        for (Command command : commands) {
            command.setCommandExecutor(this);
            command.execute();
            results.add(command.getOutput());
        }
        return results;
    }

    protected void info(String message, Object... args) {
        if (LOG.isInfoEnabled()) {
            if (args.length > 0) {
                LOG.info(String.format(message, args));
            }
            else {
                LOG.info(message);
            }
        }
    }

    protected void debug(String message, Object... args) {
        if (LOG.isDebugEnabled()) {
            if (args.length > 0) {
                LOG.debug(String.format(message, args));
            }
            else {
                LOG.debug(message);
            }
        }
    }

    /**
     * Interface for configuring the shell before connect.
     */
    public static interface ShellConfigurator {
        public void configureShell(ChannelShell shell);
    }

    /**
     * Shell configurator for setting a VT100 terminal.
     */
    public static class VT100ShellConfigurator implements ShellConfigurator {
        private int columns;
        private int rows;

        public VT100ShellConfigurator(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        @Override
        public void configureShell(ChannelShell shell) {
            shell.setPtyType("vt100", columns, rows, 0, 0);
        }
    }
}

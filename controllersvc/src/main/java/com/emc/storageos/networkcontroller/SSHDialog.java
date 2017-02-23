/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;

public class SSHDialog {
    private static final Logger _log = LoggerFactory.getLogger(SSHDialog.class);
    SSHSession session;
    protected Integer defaultTimeout;   // default timeout in milliseconds
    InputStreamReader insr;
    OutputStreamWriter oswr;
    protected String devname = "__unknown__device__";
    
    // Identifies and exception in the response.
    private static final String EXCEPTION_REGEX = "raise Exception\\('.*'\\)";

    public SSHDialog(SSHSession session, Integer defaultTimeout) {
        this.session = session;
        if (defaultTimeout == null) {
            defaultTimeout = 60000;
        }
        this.defaultTimeout = defaultTimeout;
        this.insr = new InputStreamReader(session.ins);
        this.oswr = new OutputStreamWriter(session.outs);
    }

    private SSHPrompt checkForPrompt(String buf, SSHPrompt[] prompts) throws NetworkDeviceControllerException {
        // First check for an exception, which will be thrown if found.
        checkForException(buf);
        
        // If no exceptions, now look for the expected prompt(s).
        for (SSHPrompt p : prompts) {
            String regex = p.getRegex();
            if (regex.contains("<<devname>>")) {
                regex = regex.replace("<<devname>>", "\\Q" + devname + "\\E");
            }
            regex = "(?sm).*" + regex;
            // Only check the last few lines of the buffer for the prompt as
            // it is always at the end and checking the full buffer can be costly
            String substring = buf.substring(Math.max(0, (buf.length() - 1024)));
            _log.debug("Checking prompts in " + substring);
            if (substring.matches(regex)) {
                return p;
            }
        }
        return SSHPrompt.NOMATCH;
    }
    
    /**
     * Checks the passed response content for an embedded exception.
     * 
     * Content will have the following for an exception:
     * 
     *     "raise Exception('A very specific bad thing happened')"
     * 
     * @param buf The response content.
     * 
     * @throws NetworkDeviceControllerException When an exception is found.
     */
    private void checkForException(String buf) throws NetworkDeviceControllerException {
        Pattern p = Pattern.compile(getResponseExceptionRegex());     
        Matcher m = p.matcher(buf);
        if (m.find()) {
            String match = buf.substring(m.start(), m.end());
            String message = match.substring(match.indexOf("'")+1, match.lastIndexOf("'"));
            _log.error("Found exception in response {}:{}", match, message);
            throw NetworkDeviceControllerException.exceptions.exceptionInResponse(message);
        }
    }

    /**
     * Wait for the occurrence of a prompt.
     * 
     * @param prompts -- List of possible prompts.
     * @param timeout -- Timeout in milliseconds; if null defaultTimeout is used.
     * @param delayMatchCheck -- wait to check for prompt match until no input is ready
     * @param buf -- OUTPUT parameter returning the entire captured String.
     * @return the prompt found
     */
    public SSHPrompt waitFor(SSHPrompt[] prompts, Integer timeout, StringBuilder buf, boolean delayMatchCheck)
            throws NetworkDeviceControllerException {
        if (timeout == null) {
            timeout = defaultTimeout;
        }
        buf.setLength(0);
        char[] input = new char[32670];
        int nread = 0;
        long start = 0;
        long lastInputTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - lastInputTime < timeout && nread != -1) {
            try {
                Thread.sleep(10);
                if (insr.ready()) {
                    nread = insr.read(input);
                    _log.debug("insr is ready and the buffer will be appended by " + String.valueOf(input));
                    if (nread != -1) {
                        lastInputTime = System.currentTimeMillis();
                        buf.append(input, 0, nread);
                        if (delayMatchCheck) {
                            Thread.sleep(10);
                        }
                        start = System.currentTimeMillis();
                        _log.debug("Checking for prompts in new input: " + String.valueOf(input));
                        SSHPrompt px = checkForPrompt(buf.toString(), prompts);
                        _log.debug("Checking for prompts in new input only took " + (System.currentTimeMillis() - start));
                        if (px != SSHPrompt.NOMATCH) {
                            _log.debug("Prompt found " + px);
                            return px;
                        }
                    } else {
                        _log.debug("Reached EOF. Will check the full buffer for prompts");
                        start = System.currentTimeMillis();
                        SSHPrompt px = checkForPrompt(buf.toString(), prompts);
                        _log.debug("Checking for prompts in the full buffer took " + (System.currentTimeMillis() - start));
                        if (px != SSHPrompt.NOMATCH) {
                            return px;
                        }
                    }
                }

            } catch (IOException ex) {
                _log.error(ex.getLocalizedMessage());
            } catch (InterruptedException ex) {
                _log.error(ex.getLocalizedMessage());
            }
        }

        SSHPrompt prompt = checkForPrompt(buf.toString(), prompts);
        if (prompt == SSHPrompt.NOMATCH) {
            StringBuffer expectedPrompts = new StringBuffer("Expected one of these prompts, but not found: ");
            for (SSHPrompt chkPrompt : prompts) {
                expectedPrompts.append(chkPrompt.toString()).append("(" + chkPrompt.getRegex() + "), ");
            }
            throw NetworkDeviceControllerException.exceptions.timeoutWaitingOnPrompt(expectedPrompts.toString());
        }
        return prompt;
    }

    protected String[] getLines(StringBuilder buf) {
        String[] lines = buf.toString().split("[\n\r]+");
        return lines;
    }

    /**
     * Send string and then wait for a prompt in the supplied set.
     * All data received is in buf.
     * 
     * @param send
     * @param timeout
     * @param prompts - An array of MDS prompts. The first one encountered will be returned.
     * @param buf - Output: StringBuilder containing characters received (including prompt)
     * @return
     */
    public SSHPrompt sendWaitFor(String send, Integer timeout, SSHPrompt[] prompts, StringBuilder buf)
            throws NetworkDeviceControllerException {
        _log.debug(MessageFormat.format("Host: {0}, Port: {1} - sendWaitFor: {2}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), send }));

        SSHPrompt prompt = null;
        try {
            oswr.append(send);
            oswr.flush();

            prompt = waitFor(prompts, timeout, buf, false);
        } catch (Exception ex) {
            _log.error("Exception sending string: {},  recevied: {}", send, buf);
            throw new NetworkDeviceControllerException(ex);
        }

        _log.debug(MessageFormat.format("Host: {0}, Port: {1} - sendWaitFor: {2} - Received data: {3}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), send, buf }));

        return prompt;
    }

    /**
     * Send a string without waiting for a reply.
     * 
     * @param send String
     * @throws NetworkDeviceControllerException
     */
    public void send(String send) {
        try {
            oswr.append(send);
            oswr.flush();
            _log.debug("Sent: " + send);
        } catch (IOException ex) {
            String msg = "Exception sending string: " + send + " " + ex.getLocalizedMessage();
            _log.error(msg);
        }
    }

    /**
     * Function that overloads {@link #match(String, String[], String[], int)} to make <code>flags</code> an optional parameter.
     * 
     * @param buf - Input string buffer.
     * @param regexs - Array of regular expressions to be considered. First one matching
     *            is returned.
     * @param groups - Returns the regex groups for the matching regular expressions.
     *            Therefore the length of the groups array passed in must be able to accommodate the
     *            regex with the largest number of groups defined. (0 .. n-1)
     * @return the index of the regex that matched (0 .. n-1)
     */
    public static int match(String buf, String[] regexs, String[] groups) {
        return match(buf, regexs, groups, 0);
    }

    /**
     * Returns the index of the first regex that matches the buffer. If none match,
     * returns -1. Any regex group outputs are returned in groups. The matching text is
     * removed from the buffer.
     * 
     * @param buf - Input string buffer.
     * @param regexs - Array of regular expressions to be considered. First one matching
     *            is returned.
     * @param groups - Returns the regex groups for the matching regular expressions.
     *            Therefore the length of the groups array passed in must be able to accommodate the
     *            regex with the largest number of groups defined. (0 .. n-1)
     * @param flags
     *            Match flags, a bit mask that may include {@link Pattern#CASE_INSENSITIVE}, {@link Pattern#MULTILINE}, etc.
     * @return the index of the regex that matched (0 .. n-1)
     */
    public static int match(String buf, String[] regexs, String[] groups, int flags) {
        int index = 0;
        for (String regex : regexs) {
            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(buf);
            if (m.matches()) {
                int ngroups = m.groupCount();
                for (int j = 1; j <= ngroups; j++) {
                    groups[j - 1] = m.group(j);
                }
                return index;       // return the index of the regex that matched
            }
            index++;
        }
        return -1;          // none of the regular expressions matched
    }

    public SSHSession getSession() {
        return session;
    }
    
    /**
     * Returns a regular expression that can be used to parse the response and
     * identify whether or not an exception occurred handling the request.
     * 
     * @return The regular expression to use when parsing the response for an exception.
     */
    protected String getResponseExceptionRegex() {
        return EXCEPTION_REGEX;
    }
}

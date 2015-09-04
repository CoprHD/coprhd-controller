/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.StringBuilder;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.Strings;

public class Exec {
    private static final Logger _log = LoggerFactory.getLogger(Exec.class);

    public static final int SIGNAL_OFFSET = 128;
    public static final long DEFAULT_CMD_TIMEOUT = 10 * 1000;

    private static final int _SLEEP_MS = 100;
    private static final int _EXCEPTION_EXIT_VALUE = 0xffff;

    private enum Termination {
        _NORMAL, _SIGNAL, _TIMEOUT, _EXCEPTION
    };

    /***
     * An immutable result object for Exec.exec(...)
     *
     */
    public final static class Result {
        private final String[] _cmd;
        private final long _timeout;
        private final int _exitValue;
        private final String _stdOutput;
        private final String _stdError;
        private final Termination _termination;

        Result(final String[] cmd, final long timeout,
                final int exitValue, final String stdOutput, final String stdError,
                final Termination termination) {
            _cmd = cmd;
            _timeout = timeout;
            _exitValue = exitValue;
            _stdOutput = stdOutput;
            _stdError = stdError;
            _termination = termination;
        }

        public String[] getCmd() {
            return _cmd;
        }

        /**
         * 
         * @return exit status or signal number + SIGNAL_OFFSET
         * 
         *         This returns java.;ang.Process.exitValue(), which appears to be either
         *         the exit code or the number of the signal number that killed the process
         *         plus SIGNAL_OFFSET (128).
         * 
         *         Thus, exitVakues above 128 are ambiguous.
         * 
         */
        public int getExitValue() {
            return _exitValue;
        }

        /**
         * @return String with the process stdout
         */
        public String getStdOutput() {
            return _stdOutput;
        }

        /***
         * @return String with the process stderr
         */
        public String getStdError() {
            return _stdError;
        }

        public boolean exitedNormally() {
            return _termination == Termination._NORMAL;
        }

        public boolean timedOut() {
            return _termination == Termination._TIMEOUT;
        }

        public boolean signalled() {
            return _termination == Termination._SIGNAL;
        }

        public boolean execFailed() {
            return _termination == Termination._EXCEPTION;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("cmd=");
            if (_cmd != null) {
                for (int i = 0; i < _cmd.length; i++) {
                    builder.append(_cmd[i]).append(" ");
                }
            } else {
                builder.append("null");
            }
            builder.append(" timeout=").append(_timeout).append(" ms");
            builder.append(" terminated=").append(_termination);
            builder.append(" status=").append(_exitValue);
            builder.append(" stdout=").append(Strings.repr(_stdOutput));
            builder.append(" stderr=").append(Strings.repr(_stdError));
            return builder.toString();
        }
    }

    /***
     * Sudo an external command providing exitStatus, stdOutput and stdError with root privilege.
     * 
     * @param args - A command and command arguments
     * @param timeout - Maximum execution time in ms
     * 
     * @return Exec.Result object
     */
    public static Result sudo(long timeout, String... args) {
        String userName = System.getProperty("user.name");
        if (userName.equals("root")) {
            // Root user does not need SUDO.
            return exec(timeout, args);
        }
        List<String> tmpList = new ArrayList(Arrays.asList(args));
        tmpList.add(0, "sudo");
        String[] newArray = tmpList.toArray(new String[tmpList.size()]);
        return exec(timeout, newArray);
    }

    /***
     * Exec an external command providing exitStatus, stdOutput and stdError.
     * 
     * @param args - A command and command arguments
     * @param timeout - Maximum execution time in ms
     * 
     * @return Exec.Result object
     */
    public static Result exec(long timeout, String... args) {
        List<String> cmdList = new ArrayList(Arrays.asList(args));

        final String[] cmd = cmdList.toArray(new String[cmdList.size()]);

        _log.debug("exec(): timeout=" + timeout + " cmd=" + Strings.repr(cmd));
        final long endTime = (timeout <= 0) ? 0 : System.currentTimeMillis() + timeout;

        InputStreamReader stdOutputStream = null;
        InputStreamReader stdErrorStream = null;
        StringBuilder stdOutput = new StringBuilder();
        StringBuilder stdError = new StringBuilder();

        try {

            boolean destroyed = false;
            Process p = new ProcessBuilder(cmd).start();
            stdOutputStream = new InputStreamReader(p.getInputStream());
            stdErrorStream = new InputStreamReader(p.getErrorStream());

            while (true) {
                if (!destroyed && System.currentTimeMillis() > endTime) {
                    _log.error("exec(): Timeout. Destroying the process.");
                    destroyed = true;
                    p.destroy();
                }

                boolean done = exited(p);
                int n1 = tryRead(stdErrorStream, stdError);
                int n2 = tryRead(stdOutputStream, stdOutput);

                if (n1 == 0 && n2 == 0 && (done || destroyed)) {
                    break;
                } else {
                    _log.debug("Done=" + done);
                    _log.debug("Destroyed=" + destroyed);
                    _log.debug("n1=" + n1);
                    _log.debug("n2=" + n2);
                    _log.debug("exec(): Sleep.");
                    sleep(_SLEEP_MS);
                }
            }

            final int exitValue = p.exitValue();
            Result result = new Result(cmd, timeout,
                    p.exitValue(), stdOutput.toString(), stdError.toString(),
                    (destroyed && exitValue != 0) ? Termination._TIMEOUT : Termination._NORMAL);

            _log.debug("exec(): " + result);
            return result;
        } catch (Exception e) {
            Result result = new Result(cmd, timeout,
                    _EXCEPTION_EXIT_VALUE, stdOutput.toString(), stdError.toString(),
                    Termination._EXCEPTION);
            _log.error("exec(): " + result + " (" + e + ")");
            return result;
        } finally {
            tryClose(stdOutputStream);
            tryClose(stdErrorStream);
        }
    }

    private static int tryRead(InputStreamReader in, StringBuilder s) throws IOException {
        final char[] charBuf = new char[0x1000];
        if (in.ready()) {
            final int charsRead = in.read(charBuf, 0, charBuf.length);
            if (charsRead > 0) {
                s.append(charBuf, 0, charsRead);
            }
            return charsRead;
        } else {
            return 0;
        }
    }

    private static boolean exited(Process p) {
        try {
            int exitValue = p.exitValue();
            _log.debug("exit=", exitValue);
            return true;
        } catch (IllegalThreadStateException e) {
            return false;
        }
    }

    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            ;
        }
    }

    private static void tryClose(InputStreamReader in) {
        try {
            if (in != null)
                in.close();
        } catch (Exception e) {
            ; // Ignore
        }
    }

}

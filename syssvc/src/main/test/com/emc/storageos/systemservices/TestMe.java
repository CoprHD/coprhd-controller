/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestMe {
    public void testname(String cmd) throws Exception {
        // String cmd = "ls -l"; // this is the command to execute in the Unix
        // shell
        // create a process for the shell
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
        pb.redirectErrorStream(true); // use this to capture messages sent to
                                      // stderr
        Process shell = pb.start();
        InputStream shellIn = shell.getInputStream(); // this captures the
                                                      // output from the command
        BufferedReader instream = new BufferedReader(new InputStreamReader(shell.getInputStream()));
        int shellExitStatus = shell.waitFor(); // wait for the shell to finish
                                               // and get the return code
        // at this point you can process the output issued by the command
        // for instance, this reads the output and writes it to System.out:
        System.out.println("Exit Status " + shellExitStatus);
        int c;
        String line;
        while ((line = instream.readLine()) != null) {
            System.out.print(line);
        }
        // close the stream
        try {
            shellIn.close();
        } catch (IOException ignoreMe) {
        }
    }

    public static void main(String[] args) {
        TestMe test = new TestMe();
        try {
            test.testname(args[0]);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}

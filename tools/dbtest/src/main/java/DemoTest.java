/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

public class DemoTest extends TestCase {
    @Override
    public void setup() throws Exception {
        System.out.println("tc setup");

    }

    @Override
    public void execute() throws Exception {
        System.out.println("tc running");
        Thread.sleep(20);
    }

    public static void main(String[] args) throws Exception {
        DemoTest test = new DemoTest();
        Runner runner = new Runner(test, 10, 100);
        runner.run();
    }
}

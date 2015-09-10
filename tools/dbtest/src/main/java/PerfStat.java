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

import java.util.List;

public class PerfStat {
    public int nSucc = 0;
    public int nFail = 0;
    public long totalOpTime;

    long opStartTime;

    public void startOp() {
        opStartTime = System.currentTimeMillis();
    }

    public void succeedOp() {
        nSucc++;
        long opTime = System.currentTimeMillis() - opStartTime;
        totalOpTime += opTime;
    }

    public void failOp() {
        nFail++;
    }

    public static void mergeAndShowResult(List<PerfStat> stats) {

    }
}

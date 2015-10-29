#!/usr/bin/python

import sys, os
from datetime import timedelta, datetime, tzinfo
import subprocess
'''
The util tool to cleanup pending tasks which remind in pending status 
for long time
'''
if __name__ == "__main__":
    if len(sys.argv) < 3:
        print "Usage:\nThis script is to clean up the pending tasks that out of date."
        print "Need to specify Data File and Expired Time(Time Unit: Hour) as following:"
        print "delete_pendingTask.py TaskDumpFile 24"
        print "(You can issue 'dbutils list Task > TaskDumpFile' to dump all task records into file)"
        sys.exit(1)
    name = sys.argv[1]
    hours = int(sys.argv[2])
    if hours < 6:
        print "hours must be equal or greater than 6 hours"
        sys.exit(1)
    print "Analyze pending task over %s hours from file: %s" % (hours, name)
    f = open(name, "r")
 
    # parse createTime from task 
    def readCreationTime():
        line = f.readline()
        while len(line) > 0:
            if "creationTime = " in line:
                year = int(line.split(",YEAR=")[1].split(",")[0])
                month = int(line.split(",MONTH=")[1].split(",")[0])
                day = int(line.split(",DAY_OF_MONTH=")[1].split(",")[0])
                hour = int(line.split(",HOUR_OF_DAY=")[1].split(",")[0])
                minute = int(line.split(",MINUTE=")[1].split(",")[0])
                second = int(line.split(",SECOND=")[1].split(",")[0])
                return datetime(year, month + 1, day, hour, minute, second)
            else:
                line = f.readline()

    # parse pending field from task
    def getPendingStatus():
        line = f.readline()
        while len(line) > 0:
            if "pending = " in line:
                return line.split("pending = ")[1].strip()
            else:
                line = f.readline()

    longPendingTaskIds = []
    expiredTime = datetime.now() - timedelta(hours=hours)
    line = f.readline()
    while len(line) > 0:
        if "id: " in line:
            taskId = line.split()[1]
            ct = readCreationTime()
            isPending = getPendingStatus()
            # only delete pending task over than expiredTime
            if isPending == "true" and ct < expiredTime:
                print "Found taks id=", taskId
                longPendingTaskIds.append(taskId)

        line = f.readline()
    f.close()
    print "Total pending tasks over %s hours found: %s." % (hours, len(longPendingTaskIds))
    BATCHSIZE = 100

    # delete task in BATCHSIZE
    for i in range(0, len(longPendingTaskIds), BATCHSIZE):
        cmd = "/opt/storageos/bin/dbutils delete Task %s " % (" ".join(longPendingTaskIds[i:i + BATCHSIZE]))
        print cmd
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
        (output, err) = process.communicate()
        exit_code = process.wait()
        if exit_code != 0:
            print "failed to execute %s , %s" % (cmd, err)
            print "please check database status"
            sys.exit(-1)
    print "Delete the pending tasks successfully."

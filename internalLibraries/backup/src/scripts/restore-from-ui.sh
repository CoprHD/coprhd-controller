#!/bin/bash

# This is a wrapper script, it uses nohup 
# to void to terminate the restore process
# when syssvc is stopped
#
# This accepts following arguments:
# $1=backup directory
# $2=root password
# $3=is geo from scratch (a flag)
# $4=log file name

DIR=/opt/storageos/bin

nohup ${DIR}/restore-internal.sh "$1" "$2" "$3" "$4"& 
exit 0

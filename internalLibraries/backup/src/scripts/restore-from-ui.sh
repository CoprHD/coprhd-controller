#!/bin/bash
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

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

check_password() {                                                              
    local password="$1"
    echo "${password}" | sudo -S ls /tmp &>/dev/null

    if [ $? -ne 0 ]; then
        echo "Invalid root password"
        exit 1 
    fi
}

check_password "$2"

nohup ${DIR}/restore-internal.sh "$1" "$2" "$3" "$4"& 

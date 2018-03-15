#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#

# 
# Script to help manage hosts outside of ViPR.
# Used to perform various operations.
#
# Usage: ./hosthelper.sh verify_mount_point <OS_TYPE> <MOUNT_POINT> <MOUNT_POINT_SIZE> <WWN> <MOUNT_LETTER
#
#set -x

## Convenience method for deleting a mask outside of ViPR (including the storage group)

verify_mount_point() {
    # Parameters: mount point, mount point size
    # If checking if the mount point does not exist, then parameter $3 should be "gone"
    OS_TYPE=$1
    MOUNT_POINT=$2
    MOUNT_POINT_SIZE=$3
    VOLUME_WWN=$4
    VERIFY_SIZE=$5

    DIFF=$((100 * 1024))
    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/dev/null
    
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -host ${OS_TYPE} -method get_mount_point -params "${MOUNT_POINT},${VOLUME_WWN}" > ${TMPFILE1} 2> ${TMPFILE2}
    if [ $? -ne 0 ]
    then
        if [ "$6" = "gone" ]
        then
	        echo "PASSED: Verified mount point ${MOUNT_POINT} doesn't exist."
	        exit 0;
    	fi
    	echo -e "\e[91mERROR\e[0m::expected mount point - ${MOUNT_POINT} doesn't exist.";
    	exit 1;
    
    else 
        if [ "$6" = "gone" ]
	then
	       echo -e "\e[91mERROR\e[0m: Expected mount point ${MOUNT_POINT} to be gone, but it was found";
	       exit 1;
        fi
        if [ "$VERIFY_SIZE" = true -a "$OS_TYPE" = "hpux" ]; then
            result=$(awk 'NF{s=$0}END{print s}' ${TMPFILE1})
            actualFilesystemSize=$(echo $result | cut -d " " -f 2)
            expectedFilesystemSize=$(($MOUNT_POINT_SIZE*1024*1024))
            if [ $actualFilesystemSize -gt $(($expectedFilesystemSize - $DIFF)) -a $actualFilesystemSize -lt $(($expectedFilesystemSize + $DIFF)) ]; then
               echo "SUCCESS: Mount point '$2' exists and is the correct size of '$actualFilesystemSize'"
               exit 0;
            else
               echo "FAILURE: Mount point '$2' exists but the filesystem size is '$actualFilesystemSize', which is not the expected size of '$expectedFilesystemSize'"
               exit 1;
            fi
        else
           echo "PASSED: MOunt point '$2' exists";
           exit 0;
        fi
    fi
    
}

# Check to see if this is a verification of mount point
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
tools_file="${DIR}/tools.yml"
tools_jar="${DIR}/ArrayTools.jar"

if [ "$1" = "verify_mount_point" ]; then
    shift
    verify_mount_point $*
else
    echo "Usage: $0 [verify_mount_point] {params}"
fi

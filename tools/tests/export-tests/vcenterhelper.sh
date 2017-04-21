#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#

# 
# Script to help manage storage system outside of ViPR.
# Used to perform various operations.
#
# Usage: ./vplexhelper.sh verify_export <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#        ./vplexhelper.sh add_volume_to_mask <DEVICE_ID> <NAME_PATTERN>
#        ./vplexhelper.sh remove_volume_from_mask <DEVICE_ID> <NAME_PATTERN>
#        ./vplexhelper.sh delete_volume <DEVICE_ID>
#        ./vplexhelper.sh add_initiator_to_mask <PWWN> <NAME_PATTERN>
#        ./vplexhelper.sh remove_initiator_from_mask <PWWN> <NAME_PATTERN>
#
#set -x

TMPFILE1=/tmp/verify-${RANDOM}
TMPFILE2=$TMPFILE1-error

verify_datastore() {
    # Parameters: Datacenter, Datastore
    DATACENTER=$1
    DATASTORE=$2
	
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -vcenter -method get_datastore -params "${DATACENTER},${DATASTORE}" > ${TMPFILE1} 2> ${TMPFILE2}
    grep -n ${DATASTORE} ${TMPFILE1} > /dev/null
    # 0 if line selected, 1 if no line selected
    foundIt=$?
    if [ -s $TMPFILE1 ]; then
        if [ $foundIt -ne 0 ]; then
            if [ "$2" = "gone" ]; then
                echo "PASSED: Verified Datastore with pattern ${DATASTORE} doesn't exists."
                exit 0;
            fi
            echo -e "\e[91mERROR\e[0m: Expected Datastore ${DATASTORE}, but could not find it"
            exit 1;
        else
            if [ "$2" = "gone" ]; then
                echo -e "\e[91mERROR\e[0m: Expected Datastore ${DATASTORE} to be gone, but it was found"
                exit 1;
            fi
        fi
    else
        echo -e "\e[91mERROR\e[0m: empty or invalid response from vcenter"
        if [ -s $TMPFILE2 ]; then
            cat $TMPFILE2
        fi
        exit 1;
    fi    
}

# Check to see if this is an operational request or a verification of export request
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
tools_file="${DIR}/tools.yml"
tools_jar="${DIR}/ArrayTools.jar"

if [ "$1" = "verify_datastore" ]; then
    shift
    verify_datastore $1 $2 $*
else
    echo "Usage: $0 [verify_datastore] {params}"
fi

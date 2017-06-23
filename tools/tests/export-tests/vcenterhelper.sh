#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#

# 
# Script to help manage vcenter outside of ViPR.
# Used to perform various operations.
#
# Usage: ./vcenterhelper.sh verify_datastore <DATACENTER> <DATASTORE> [verify the datastore exists]
#        ./vcenterhelper.sh verify_datastore <DATACENTER> <DATASTORE> gone [verify the datastore does not exist]
#
#set -x

TMPFILE1=/tmp/verify-${RANDOM}
TMPFILE2=$TMPFILE1-error

verify_datastore() {
    # Parameters: Datacenter, Datastore, VCenter Host
    DATACENTER=$1
    DATASTORE=$2
    HOST=$3
	
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -vcenter -method get_datastore -params "${DATACENTER},${DATASTORE},${HOST}" > ${TMPFILE1} 2> ${TMPFILE2}
    grep -n ${DATASTORE} ${TMPFILE1} > /dev/null
    # 0 if line selected, 1 if no line selected
    foundIt=$?
    if [ -s $TMPFILE1 ]; then
        if [ $foundIt -ne 0 ]; then
            if [ "$4" = "gone" ]; then
                echo "PASSED: Verified Datastore with pattern ${DATASTORE} doesn't exists."
                exit 0;
            fi
            echo -e "\e[91mERROR\e[0m: Expected Datastore ${DATASTORE}, but could not find it"
            exit 1;
        else
            if [ "$4" = "gone" ]; then
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

verify_datastore_capacity() {
    # Parameters: Datacenter, Datastore, VCenter Host, Capacity (desired capacity in bytes)
    DATACENTER=$1
    DATASTORE=$2
    HOST=$3
    CAPACITY=$4

    ((CAPACITY=CAPACITY*1000000000))
    
    ((UPPER_BOUND=CAPACITY + 500000000))
    ((LOWER_BOUND=CAPACITY - 500000000))
    
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -vcenter -method get_datastore -params "${DATACENTER},${DATASTORE},${HOST}" > ${TMPFILE1} 2> ${TMPFILE2}
    grep -n ${DATASTORE} ${TMPFILE1} > /dev/null
    # 0 if line selected, 1 if no line selected
    foundIt=$?
    if [ -s $TMPFILE1 ]; then
        if [ $foundIt -ne 0 ]; then
            echo -e "\e[91mERROR\e[0m: Expected Datastore ${DATASTORE}, but could not find it"
            exit 1;
        else
            grep -n datastoreCapacity ${TMPFILE1} > /dev/null
            if [ $? -eq 0 ]; then
                result=$(grep datastoreCapacity ${TMPFILE1} | cut -d ':' -f 2)
                if [ $result -le $UPPER_BOUND -a $result -ge $LOWER_BOUND ]; then
                    echo "PASSED: Verified Datastore ${DATASTORE} has the desired capacity ${CAPACITY}."
                    exit 0;
                else
                    echo -e "\e[91mERROR\e[0m: Found Datastore ${DATASTORE} with capacity ${result}, which does not match desired capacity ${CAPACITY}"
                    exit -1;
                fi
            else
                echo -e "\e[91mERROR\e[0m: Found Datastore ${DATASTORE}, but could not determine its capacity"
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

verify_datastore_lun_count() {
    # Parameters: Datacenter, Datastore, VCenter Host, Count (desired LUN count)
    DATACENTER=$1
    DATASTORE=$2
    HOST=$3
    COUNT=$4

    java -Dproperty.file=${tools_file} -jar ${tools_jar} -vcenter -method get_datastore -params "${DATACENTER},${DATASTORE},${HOST}" > ${TMPFILE1} 2> ${TMPFILE2}
    grep -n ${DATASTORE} ${TMPFILE1} > /dev/null
    # 0 if line selected, 1 if no line selected
    foundIt=$?
    if [ -s $TMPFILE1 ]; then
        if [ $foundIt -ne 0 ]; then
            echo -e "\e[91mERROR\e[0m: Expected Datastore ${DATASTORE}, but could not find it"
            exit 1;
        else
            grep -n datastoreLuns ${TMPFILE1} > /dev/null
            if [ $? -eq 0 ]; then
                 result=$(grep datastoreLuns ${TMPFILE1} | tr -cd , | wc -c)
                 result=`expr $result + 1`
                 if [ "$result" -eq "$COUNT" ]; then
                    echo "PASSED: Verified Datastore ${DATASTORE} has the desired numbered of LUNs ${COUNT}."
                    exit 0;
                else
                    echo -e "\e[91mERROR\e[0m: Found Datastore ${DATASTORE} with LUN count ${result}, which does not match desired count ${COUNT}"
                    exit -1;
                fi
            else
                echo -e "\e[91mERROR\e[0m: Found Datastore ${DATASTORE}, but could not determine its LUN count"
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
    verify_datastore $1 $2 $3
elif [ "$1" = "verify_datastore_capacity" ]; then
    shift
    verify_datastore_capacity $1 $2 $3 $4
elif [ "$1" = "verify_datastore_lun_count" ]; then
    shift
    verify_datastore_lun_count $1 $2 $3 $4
else
    echo "Usage: $0 [verify_datastore | verify_datastore_capacity | verify_datastore_lun_count] {params}"
fi

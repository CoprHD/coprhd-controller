#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# 
# Quick verification script
#
# Usage: ./xiohelper.sh <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#
#set -x

add_volume_to_mask() {
    serial_number=$1
    device_id=$2
    pattern=$3
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays xio -method remove_volume_from_mask -params ${device_id},${pattern} > ${TMPFILE1} 2> ${TMPFILE2}

    echo "Added volume ${device_id} to initiator group ${pattern}"
}

remove_volume_from_mask() {
    serial_number=$1
    device_id=$2
    pattern=$3
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays xio -method add_volume_to_mask -params ${device_id},${pattern} > ${TMPFILE1} 2> ${TMPFILE2}
    echo "Removed volume ${device_id} from initiator group ${pattern}"
}

delete_volume() {
    echo "Not yet implemented for XIO"
}

verify_export() {
    # First parameter is the serial number
    SID=$1
    shift

    # next parameters: Initiator group Name, Number of Initiators, Number of Luns
    # If checking if the Initiator group does not exist, then parameter $2 should be "gone"
    IG_PATTERN=$1
    NUM_INITIATORS=$2
    NUM_LUNS=$3
    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/dev/null

    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays xio -method get_initiator_group -params ${IG_PATTERN} > ${TMPFILE1} 2> ${TMPFILE2}
    grep -n ${IG_PATTERN} ${TMPFILE1} > /dev/null
    if [ $? -ne 0 ]
	then
	if [ "$2" = "gone" ]
	    then
	    echo "PASSED: Verified MaskingView with pattern ${IG_PATTERN} doesn't exist."
	    exit 0;
	fi
	echo "ERROR: Expected MaskingView ${IG_PATTERN}, but could not find it";
	exit 1;
    else
	if [ "$2" = "gone" ]
	    then
	    echo "ERROR: Expected MaskingView ${IG_PATTERN} to be gone, but it was found"
	    exit 1;
	fi
    fi

    num_inits=`grep -Po '(?<="numberOfInitiators":")[^"]*' ${TMPFILE1}`
    num_luns=`grep -Po '(?<="numberOfVolumes":")[^"]*' ${TMPFILE1}`
    echo "num_inits is ${num_inits}"
    echo "num_luns is ${num_luns}"
    failed=false

    if [ ${num_inits} -ne ${NUM_INITIATORS} ]
	then
	echo "FAILED: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
	failed=true
    fi

    if [ ${num_luns} -ne ${NUM_LUNS} ]
	then
	echo "FAILED: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
	failed=true
    fi

    if [ "${failed}" = "true" ]
	then
	exit 1;
    fi

    echo "PASSED: Initiator group '$1' contained $2 initiators and $3 luns"
    exit 0;
}

# Check to see if this is an operational request or a verification of export request
dir=`pwd`
tools_file="${dir}/tools/tests/export-tests/tools.yml"
tools_jar="${dir}/tools/tests/export-tests/ArrayTools.jar"
if [ "$1" = "add_volume_to_mask" ]; then
    shift
    add_volume_to_mask $1 $2 $3
elif [ "$1" = "remove_volume_from_mask" ]; then
    shift
    remove_volume_from_mask $1 $2 $3
elif [ "$1" = "delete_volume" ]; then
    shift
    delete_volume $1 $2
else
    verify_export $*
fi
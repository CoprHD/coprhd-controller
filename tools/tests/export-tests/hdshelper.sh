#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#

#
# Script to help manage HDS storage system outside of ViPR.
# Used to perform various operations.
#
# Usage: ./hdshelper.sh verify_export <HOST_STORAGE_DOMAIN (hds1 hsd2 ...)> <INITIATOR (initiator1 initiator2 ...)> <SYS_NATIVE_ID> <SYS_MODEL> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#        ./hdshelper.sh get_hlus <HOST_STORAGE_DOMAIN (hds1 hsd2 ...)> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh create_volume <NAME> <SIZE> <IS_THIN_VOL> <POOL_ID> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh delete_volume <DEVICE_ID> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh create_mask <HOST_NAME> <HOST_OS> <HOST_TYPE> <MODE_OPTION> <INITIATOR...> <VOLUME:HLU...> <TARGET_PORT...> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh delete_mask <HOST_STORAGE_DOMAIN...> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh add_volume_to_mask <HOST_STORAGE_DOMAIN...> <DEVICE_ID:HLU...> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh remove_volume_from_mask <HOST_STORAGE_DOMAIN...> <DEVICE_ID...> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh add_initiator_to_mask <HOST_STORAGE_DOMAIN...> <INITIATOR...> <SYS_NATIVE_ID> <SYS_MODEL>
#        ./hdshelper.sh remove_initiator_from_mask <HOST_STORAGE_DOMAIN...> <INITIATOR...> <SYS_NATIVE_ID> <SYS_MODEL>

#set -x

TMPFILE1=/tmp/verify-${RANDOM}
TMPFILE2=$TMPFILE1-error
echo Temp files - $TMPFILE1 $TMPFILE2
HOST_STORAGE_DOMAIN="hostStorageDomain:"
NUMBER_OF_INITIATORS="numberOfInitiators:"
NUMBER_OF_LUNS="numberOfLUNs:"
USED_HLUS="usedHLUs:"

verify_export() {
    # Parameters: Storage View Name Name, Number of Initiators, Number of Luns, HLUs
    # If checking if the Storage View does not exist, then parameter $2 should be "gone"
    HSDS=$1
    INITIATORS=$2
	SYSID=$3
    NUM_INITIATORS=$5
    NUM_LUNS=$6
    HLUS=$7

	IDENTIFIER=$INITIATORS
	if [ "$HSDS" != "" ]; then
        IDENTIFIER=$HSDS
    fi

    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method get_mask_info -params "$HSDS,$INITIATORS,$SYSID" > ${TMPFILE1} 2> ${TMPFILE2}
    if [ -s $TMPFILE1 ]; then
        grep -n "${HOST_STORAGE_DOMAIN}" ${TMPFILE1} > /dev/null
        if [ $? -ne 0 ]; then
            echo -e "\e[91mERROR\e[0m: Cannot verify export on array. Check ${TMPFILE1} and ${TMPFILE2} for detail"
            exit 1;
        fi

        grep -n "${IDENTIFIER}" ${TMPFILE1} > /dev/null
        if [ $? -ne 0 ]; then
            if [ "$5" = "gone" ]; then
                echo "PASSED: Verified HDSs/initiators ${INITIATORS} doesn't exist."
                exit 0;
            fi
            echo -e "\e[91mERROR\e[0m: Expected HDSs/initiators ${INITIATORS}, but could not find";
            exit 1;
        else
            if [ "$6" = "gone" ]; then
                echo -e "\e[91mERROR\e[0m: Expected initiators ${INITIATORS} to be gone, but found in HSD"
                exit 1;
            fi
        fi
    else
        echo -e "\e[91mERROR\e[0m: Empty or invalid response from ${array_type}"
        if [ -s $TMPFILE2 ]; then
            cat $TMPFILE2
        fi
        exit 1;
    fi

    num_inits=`grep ${NUMBER_OF_INITIATORS} ${TMPFILE1} | awk -F: '{print $2}'`
    num_luns=`grep ${NUMBER_OF_LUNS} ${TMPFILE1} | awk -F: '{print $2}'`
    failed=false

    if [ ${num_inits} -ne ${NUM_INITIATORS} ]
	then
	echo -e "\e[91mERROR\e[0m: Initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
	failed=true
    fi

    if [ ${num_luns} -ne ${NUM_LUNS} ]
	then
	echo -e "\e[91mERROR\e[0m: LUNs: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
	failed=true
    fi

    if [ -n "${HLUS}" ]
    then
	    hlus=`grep ${USED_HLUS} ${TMPFILE1} | awk -F: '{print $2}'`

        hlu_arr=(${HLUS//,/, })
        hlus=${hlus:1:-1}
        if [ "${hlus[*]}" != "${hlu_arr[*]}" ]
        then
            echo -e "\e[91mERROR\e[0m: HLUs: Expected: ${hlu_arr[*]} Retrieved: ${hlus[*]}";
            failed=true
        fi
    fi

    if [ "${failed}" = "true" ]
	then
	exit 1;
    fi

    if [ -n "${HLUS}" ]
    then
        echo "PASSED: HDSs/initiators '$IDENTIFIER' contained ${NUM_INITIATORS} initiators and ${NUM_LUNS} luns with hlus ${HLUS}"
    else
        echo "PASSED: HSDs/initiators '$IDENTIFIER' contained ${NUM_INITIATORS} initiators and ${NUM_LUNS} luns"
    fi

    exit 0;
}

get_hlus() {
    HSDS=$1
    hlus=-1

    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method get_mask_info -params "$HSDS,$2" > ${TMPFILE1} 2> ${TMPFILE2}
    if [ -s $TMPFILE1 ]; then
        grep -n "${HOST_STORAGE_DOMAIN}" ${TMPFILE1} > /dev/null
        if [ $? -ne 0 ]; then
            echo -e "\e[91mERROR\e[0m: Cannot verify export on array. Check ${TMPFILE1} and ${TMPFILE2} for detail"
            exit 1;
        fi

        grep -n "${HSDS}" ${TMPFILE1} > /dev/null
        if [ $? -eq 0 ]; then
            hlus=`grep ${USED_HLUS} ${TMPFILE1} | awk -F: '{print $2}'`
        fi
    else
        echo -e "\e[91mERROR\e[0m: Empty or invalid response from ${array_type}"
        if [ -s $TMPFILE2 ]; then
            cat $TMPFILE2
        fi
        exit 1;
    fi

    echo $hlus
}

create_volume() {
    volume_name=$1
    size=$2
	thin_vol=$3
    pool=$4
	sys=$5
	model=$6

    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method create_volume -params "${volume_name},${size},${thin_vol},${pool},${sys},${model}" > ${TMPFILE1} 2> ${TMPFILE2}
    echo `tail -1 ${TMPFILE1}`
}

delete_volume() {
    device_id=$1
	thin_vol=$2
	sys=$3
	model=$4
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method delete_volume -params "${device_id},${thin_vol},${sys},${model}" > ${TMPFILE1} 2> ${TMPFILE2}
}

create_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method create_mask -params "$1,$2,$3,$4,$5,$6,$7,$8,$9" > ${TMPFILE1} 2> ${TMPFILE2}
    cat ${TMPFILE1}
}

## Convenience method for deleting a host outside of ViPR
delete_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method delete_mask -params "$1,$2,$3"
    echo "Deleted HSDs $1"
}

add_volume_to_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method add_volume_to_mask -params "$1,$2,$3,$4" > ${TMPFILE1} 2> ${TMPFILE2}
    cat ${TMPFILE1}
    echo "Added volume $2 to HSDs $1"
}

remove_volume_from_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method remove_volume_from_mask -params "$1,$2,$3,$4"
    echo "Removed volume $2 from HSDs $1"
}

add_initiator_to_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method add_initiator_to_mask -params "$1,$2,$3,$4"
}

remove_initiator_from_mask() {
    java -Dproperty.file=${tools_file} -jar ${tools_jar} -arrays ${array_type} -method remove_initiator_from_mask -params "$1,$2,$3,$4"
}

# Check to see if this is an operational request or a verification of export request
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
tools_file="${DIR}/tools.yml"
tools_jar="${DIR}/ArrayTools.jar"
array_type="hds"

if [ -f ${tools_file} ]; then
    is_hds=$(grep "hds" ${tools_file} || echo "")
    if [ "$is_hds" == "" ]; then
        echo "Array type (hds) not found in ${tools_file}"
        exit 1
    fi
else
    echo "${tools_file} not found"
    exit 1
fi

if [ ! -f ${tools_jar} ]; then
    echo "${tools_jar} not found"
    exit 1
fi

if [[ $# -lt 4 ]]; then
    echo "Missing operation/params"
    echo "Usage: $0 [verify_export | get_hlus | create_volume | delete_volume | create_mask | delete_mask | add_volume_to_mask | remove_volume_from_mask | add_initiator_to_mask | remove_initiator_from_mask] {params}"
    exit 1
fi

if [ "$1" = "verify_export" ]; then
    shift
    verify_export "$1" "$2" "$3" "$4" "$5" "$6" "$7"
elif [ "$1" = "get_hlus" ]; then
    shift
    get_hlus "$1" "$2" "$3"
elif [ "$1" = "create_volume" ]; then
    shift
    create_volume "$1" "$2" "$3" "$4" "$5" "$6"
elif [ "$1" = "delete_volume" ]; then
    shift
    delete_volume "$1" "$2" "$3" "$4"
elif [ "$1" = "create_mask" ]; then
    shift
    create_mask "$1" "$2" "$3" "$4" "$5" "$6" "$7" "$8" "$9"
elif [ "$1" = "delete_mask" ]; then
    shift
    delete_mask "$1" "$2" "$3"
elif [ "$1" = "add_volume_to_mask" ]; then
    shift
    add_volume_to_mask "$1" "$2" "$3" "$4"
elif [ "$1" = "remove_volume_from_mask" ]; then
    shift
    remove_volume_from_mask "$1" "$2" "$3" "$4"
elif [ "$1" = "add_initiator_to_mask" ]; then
    shift
    add_initiator_to_mask "$1" "$2" "$3" "$4"
elif [ "$1" = "remove_initiator_from_mask" ]; then
    shift
    remove_initiator_from_mask "$1" "$2" "$3" "$4"
else
    echo "Unsupported operation $1"
    echo "Usage: $0 [verify_export | get_hlus | create_volume | delete_volume | create_mask | delete_mask | add_volume_to_mask | remove_volume_from_mask | add_initiator_to_mask | remove_initiator_from_mask] {params}"
fi
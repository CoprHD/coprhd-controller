#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# 
# Script to help manage storage system outside of ViPR.
# Used to perform various operations.
#
# Usage: ./navihelper.sh verify-export <SERIAL_NUMBER> <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#        ./navihelper.sh add_volume_to_mask <SERIAL_NUMBER> <DEVICE_ID> <NAME_PATTERN>
#        ./navihelper.sh remove_volume_from_mask <SERIAL_NUMBER> <DEVICE_ID> <NAME_PATTERN>
#        ./navihelper.sh delete_volume <SERIAL_NUMBER> <DEVICE_ID>
#        ./navihelper.sh add_initiator_to_mask <SERIAL_NUMBER> <PWWN> <NAME_PATTERN>
#        ./navihelper.sh remove_initiator_from_mask <SERIAL_NUMBER> <PWWN> <NAME_PATTERN>
#
#set -x

# Get the storage group full name, given a pattern
get_sg_name() {
    VNX_SP_IP=$1
    SG_PATTERN=$2

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`
    echo ${sgname}
}

add_volume_to_mask() {
    SID=$1
    VNX_SP_IP=$2
    device_id=$3
    SG_PATTERN=$4

    # Get the full name of the storage group
    sgname=`get_sg_name ${VNX_SP_IP} ${SG_PATTERN}`

    # Find out how many luns there are in the mask now.
    num_luns=`get_number_of_luns_in_mask ${VNX_SP_IP} ${sgname}`
    
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -addhlu -gname ${sgname} -alu ${device_id} -hlu 250 > /tmp/navisechelper.out

    # Ensure the provider is updated
    verify_export_via_provider ${SID} ${VNX_SP_IP} ${sgname} none `expr ${num_luns} + 1`
}

remove_volume_from_mask() {
    SID=$1
    VNX_SP_IP=$2
    device_id=$(echo $3 | sed 's/^0*//')
    SG_PATTERN=$4

    # Get the full name of the storage group
    sgname=`get_sg_name ${VNX_SP_IP} ${SG_PATTERN}`

    # Find out how many luns there are in the mask now.
    num_luns=`get_number_of_luns_in_mask ${VNX_SP_IP} ${sgname}`

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    # Find the volume's device ID, get its HLU
    sgline=`grep -n ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $1}'`

    sgblocksize=`tail -n +${sgline} /tmp/verify.txt | grep -n Shareable | head -1 | awk -F: '{print $1}'`
    sgblocksize=`expr ${sgblocksize} + 1`
    sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
    head -${sgblockstart} /tmp/verify.txt | tail -${sgblocksize} > /tmp/verify2.txt
    hlunumber=`grep ${device_id} /tmp/verify2.txt | awk '{print $1}'`

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -removehlu -o -gname ${sgname} -hlu ${hlunumber} > /tmp/navisechelper.out

    # Ensure the provider is updated
    verify_export_via_provider ${SID} ${VNX_SP_IP} ${sgname} none `expr ${num_luns} - 1`
}

add_initiator_to_mask() {
    SID=$1
    VNX_SP_IP=$2
    pwwn=$(echo $3 | awk '{print substr($0,1,2),":",substr($0,3,2),":",substr($0,5,2),":",substr($0,7,2),":",substr($0,9,2),":",substr($0,11,2),":",substr($0,13,2),":",substr($0,15,2)}' | sed 's/ //g')
    SG_PATTERN=$4

    # Get the full name of the storage group
    sgname=`get_sg_name ${VNX_SP_IP} ${SG_PATTERN}`

    # Find out how many luns there are in the mask now.
    num_inits=`get_number_of_initiators_in_mask ${VNX_SP_IP} ${sgname}`

    # register a fake host (maybe add a check to see if it's already there?)
    # This requires that the first number of the WWN is "1"
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -setpath -gname ${sgname} -hbauid $(echo $pwwn | sed 's/^1/2/g'):${pwwn} -sp a -spport 0 -arraycommpath 1 -failovermode 4 -host dutest_fakehost -ip 11.22.33.44 -o > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to add the initiator to the mask, trying port 1"
	/opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -setpath -gname ${sgname} -hbauid $(echo $pwwn | sed 's/^1/2/g'):${pwwn} -sp a -spport 1 -arraycommpath 1 -failovermode 4 -host dutest_fakehost -ip 11.22.33.44 -o > /tmp/navisechelper.out
	if [ $? -ne 0 ]; then
	    echo "Failed to add the initiator to the mask"
	fi
    fi

    # Ensure the provider is updated
    verify_export_via_provider ${SID} ${VNX_SP_IP} ${sgname} `expr ${num_inits} + 1` none
}


remove_initiator_from_mask() {
    SID=$1
    VNX_SP_IP=$2
    pwwn=$(echo $3 | awk '{print substr($0,1,2),":",substr($0,3,2),":",substr($0,5,2),":",substr($0,7,2),":",substr($0,9,2),":",substr($0,11,2),":",substr($0,13,2),":",substr($0,15,2)}' | sed 's/ //g')
    SG_PATTERN=$4

    # Get the full name of the storage group
    sgname=`get_sg_name ${VNX_SP_IP} ${SG_PATTERN}`

    # Find out how many initiators there are in the mask now.
    num_inits=`get_number_of_initiators_in_mask ${VNX_SP_IP} ${sgname}`

    # remove the initiators(s)
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -disconnecthost -gname ${sgname} -o -host dutest_fakehost > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to remove the initiator from the mask."
    fi

    # Ensure the provider is updated
    verify_export_via_provider ${SID} ${VNX_SP_IP} ${sgname} `expr ${num_inits} - 1` none
}

create_storage_group() {
    SID=$1
    VNX_SP_IP=$2
    device_id=$3
    sgname=$5

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -create -gname ${sgname} > /tmp/navisechelper.out

    # Add the volume
    add_volume_to_mask ${SID} ${VNX_SP_IP} ${device_id} ${sgname}

    # Add the initiator
    add_initiator_to_mask ${SID} ${VNX_SP_IP} $4 ${sgname}
}

delete_storage_group() {
    VNX_SP_IP=$1
    SG_PATTERN=$2
    HOSTREMOVE=${SG_PATTERN: :-4}

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`

    # Delete the host
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -disconnecthost -gname ${sgname} -o -host ${HOSTREMOVE} > /tmp/navisechelper.out
    # Ignore failures by this command

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -destroy -o -gname ${sgname} > /tmp/navisechelper.out
    set +x
}

delete_volume() {
    VNX_SP_IP=$1
    device_id=$(echo $2 | sed 's/^0*//')
    printf 'y\ny\n' | /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP lun -destroy -l ${device_id} > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to delete the LUN"
    fi
}

verify_export() {
    VNX_SP_IP=$1
    INITIATOR_PATTERN=$2
    SG_PATTERN=$3
    NUM_INITIATORS=$4
    NUM_LUNS=$5

    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/dev/null

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > ${TMPFILE1}

    grep -n ${SG_PATTERN} ${TMPFILE1} > /dev/null
    if [ $? -ne 0 ]
	then
	if [ "${NUM_INITIATORS}" = "gone" ]
	    then
	    echo "PASSED: Verified storage group with pattern ${SG_PATTERN} doesn't exist."
	    exit 0;
	fi
	echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN}, but could not find it";
	exit 1;
    else
	if [ "${NUM_INITIATORS}" = "gone" ]
	    then
	    echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN} to be gone, but it was found"
	    exit 1;
	fi
    fi

    hits=`grep -n ${SG_PATTERN} ${TMPFILE1} | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo -e "\e[91mERROR\e[0m: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgline=`grep -n ${SG_PATTERN} ${TMPFILE1} | awk -F: '{print $1}'`

    sgblocksize=`tail -n +${sgline} ${TMPFILE1} | grep -n Shareable | head -1 | awk -F: '{print $1}'`
    sgblocksize=`expr ${sgblocksize} + 1`
    sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
    head -${sgblockstart} ${TMPFILE1} | tail -${sgblocksize} > /tmp/verify2.txt
    num_inits=`grep -e "SP [AB]" /tmp/verify2.txt | awk '{print $1}' | sort -u | wc -l | awk '{print $1}'`
    # The verify2.txt file should have something like this:
    #
    # HLU/ALU Pairs:
    #
    #  HLU Number     ALU Number
    #  ----------     ----------
    #    1695            1695
    #
    # We are trying to match the line with HLU and ALU numbers
    num_luns=`perl -nle 'print if (m#\s+\d+\s+\d+#)' /tmp/verify2.txt | wc -l`
    failed=false

    if [ "${num_inits}" != "${NUM_INITIATORS}" ]
	then
	echo -e "\e[91mERROR\e[0m: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
	echo "Export group dump:"
	cat /tmp/verify2.txt
	failed=true
    fi

    if [ "${num_luns}" != "${NUM_LUNS}" ]
	then
	echo -e "\e[91mERROR\e[0m: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
	echo "Export group dump:"
	cat /tmp/verify2.txt
	failed=true
    fi

    if [ "${failed}" = "true" ]
	then
	exit 1;
    fi

    echo "PASSED: Verified storage group starting with pattern ${SG_PATTERN} contained ${num_inits} initiators and ${num_luns} luns"
    exit 0;
}

verify_export_prechecks() {
    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/tmp/verify2-${RANDOM}
    TMPERROR=/dev/null

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > ${TMPFILE1} 2> ${TMPERROR}

    grep -n ${SG_PATTERN} ${TMPFILE1} > /dev/null
    if [ $? -ne 0 ]; then
	echo -e "\e[91mERROR\e[0m: Expected MaskingView ${SG_PATTERN}, but could not find it";
	exit 1;
    fi

    sgline=`grep -n ${SG_PATTERN} ${TMPFILE1} | awk -F: '{print $1}'`

    sgblocksize=`tail -n +${sgline} ${TMPFILE1} | grep -n Shareable | head -1 | awk -F: '{print $1}'`
    sgblocksize=`expr ${sgblocksize} + 1`
    sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
    head -${sgblockstart} ${TMPFILE1} | tail -${sgblocksize} > ${TMPFILE2}
}

get_number_of_luns_in_mask() {
    # First parameter is the Symm ID
    VNX_SP_IP=$1
    SG_PATTERN=$2

    verify_export_prechecks

    num_luns=`perl -nle 'print if (m#\s+\d+\s+\d+#)' ${TMPFILE2} | wc -l`
    echo ${num_luns}
    exit 0;
}

get_number_of_initiators_in_mask() {
    VNX_SP_ID=$1
    SG_PATTERN=$2

    verify_export_prechecks

    num_inits=`grep -e "SP [AB]" ${TMPFILE2} | awk '{print $1}' | sort -u | wc -l | awk '{print $1}'`
    echo ${num_inits}
    exit 0;
}

# This method will use the array tool to check the mask on the provider
verify_export_via_provider() {
    # First parameter is the Symm ID
    SID=$1
    VNX_SP_IP=$2
    SG_PATTERN=$3
    NUM_INITIATORS=$4
    NUM_LUNS=$5
    TMPFILE1=/tmp/verify-${RANDOM}
    MAX_WAIT_SECONDS=`expr 20 \* 60`
    SLEEP_INTERVAL_SECONDS=10

    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    tools_jar="${DIR}/preExistingConfig.jar"

    if [ ! -f ${DIR}/preExistingConfig.properties ]; then
	echo "Missing preExistingConfg.properties.  dutests should generate this for you"
	exit 1
    fi
    
    if [ "none" != "${NUM_INITIATORS}" -a "none" != "${NUM_LUNS}" ]; then
	echo "Invalid parameters sent to verify_export_via_provider...."
	exit 1
    fi

    numinits="none"
    numluns="none"
    waited=0

    while [ "${numinits}" != "${NUM_INITIATORS}" -o "${numluns}" != "${NUM_LUNS}" ]
    do
      if [ "${numinits}" != "none" -o "${numluns}" != "none" ]; then
	  sleep ${SLEEP_INTERVAL_SECONDS}
	  waited=`expr ${waited} + ${SLEEP_INTERVAL_SECONDS}`

	  if [ ${waited} -ge ${MAX_WAIT_SECONDS} ]; then
	      echo "Waited, but never found provider to have the right number of luns and volumes"
	      exit 1;
	  fi
      fi

      # Gather results from the external tool
      java -Dlogback.configurationFile=./logback.xml -jar ${tools_jar} show-view $SID $SG_PATTERN > ${TMPFILE1}

      numinits=`grep -i "Total Number of Initiators for View" ${TMPFILE1} | awk '{print $NF}'`
      numluns=`grep -i "Total Number of Volumes For View" ${TMPFILE1} | awk '{print $NF}'`
      echo "Found ${numinits} initiators and ${numluns} volumes in mask ${SG_PATTERN}"

      if [ "${NUM_INITIATORS}" = "none" ]; then
	  numinits="none"
      fi

      if [ "${NUM_LUNS}" = "none" ]; then
	  numluns="none"
      fi
    done
}

# Check to see if this is an operational request or a verification of export request
if [ "$1" = "add_volume_to_mask" ]; then
    shift
    add_volume_to_mask $1 $2 $3 $4
elif [ "$1" = "remove_volume_from_mask" ]; then
    shift
    remove_volume_from_mask $1 $2 $3 $4
elif [ "$1" = "add_initiator_to_mask" ]; then
    shift
    add_initiator_to_mask $1 $2 $3 $4
elif [ "$1" = "remove_initiator_from_mask" ]; then
    shift
    remove_initiator_from_mask $1 $2 $3 $4
elif [ "$1" = "delete_volume" ]; then
    shift
    delete_volume $1 $2
elif [ "$1" = "delete_mask" ]; then
    shift
    delete_storage_group $1 $2
elif [ "$1" = "create_export_mask" ]; then
    shift
    create_storage_group $1 $2 $3 $4 $5
elif [ "$1" = "verify_export" ]; then
    shift
    verify_export $*
elif [ "$1" = "verify_export_via_provider" ]; then
    shift
    verify_export_via_provider $*
else
    # Backward compatibility with vnxexport scripts
    verify_export $*
fi


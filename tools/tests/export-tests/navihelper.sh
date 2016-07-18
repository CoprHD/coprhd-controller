#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# 
# Quick verification script
#
# Usage: ./navihelper.sh <ServiceProcessor IP> <Initiator Pattern> <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#
# set -x

## Convenience method for deleting a mask outside of ViPR
delete_mask() {
    serial_number=$1
    sid=${serial_number: -3}
    pattern=$2

    echo "delete_mask for VNX not supported yet"
    return -1

    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} delete view -name ${pattern}_${sid}
    if [ $? -ne 0 ]; then
	echo "no mask found."
    fi

    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -v | grep ${pattern}_${sid}
    if [ $? -ne 0 ]; then
	echo "SG not found for ${pattern}_${sid}"
    else
	sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${pattern} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

        # Remove the volume from the storage group it is in
	dev_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage show ${sg_long_id} | grep Devices | awk -F: '{print $2}'`
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name ${sg_long_id} remove dev ${dev_id}
	
	# Put it back into the optimized SG?

	# Delete storage group
	/opt/emc/SYMCLI/bin/symaccess -sid 612 delete -force -name ${sg_long_id} -type storage
    fi
}

add_volume_to_mask() {
    VNX_SP_IP=$1
    device_id=$2
    SG_PATTERN=$3

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`
    
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -addhlu -gname ${sgname} -alu ${device_id} -hlu 250 > /tmp/navisechelper.out
}

remove_volume_from_mask() {
    VNX_SP_IP=$1
    device_id=$(echo $2 | sed 's/^0*//')
    SG_PATTERN=$3

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`
    
    # Find the volume's device ID, get its HLU
    sgline=`grep -n ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $1}'`

    sgblocksize=`tail -n +${sgline} /tmp/verify.txt | grep -n Shareable | head -1 | awk -F: '{print $1}'`
    sgblocksize=`expr ${sgblocksize} + 1`
    sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
    head -${sgblockstart} /tmp/verify.txt | tail -${sgblocksize} > /tmp/verify2.txt
    hlunumber=`grep ${device_id} /tmp/verify2.txt | awk '{print $1}'`

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -removehlu -o -gname ${sgname} -hlu ${hlunumber} > /tmp/navisechelper.out
}

add_initiator_to_mask() {
    VNX_SP_IP=$1
    pwwn=$(echo $2 | awk '{print substr($0,1,2),":",substr($0,3,2),":",substr($0,5,2),":",substr($0,7,2),":",substr($0,9,2),":",substr($0,11,2),":",substr($0,13,2),":",substr($0,15,2)}' | sed 's/ //g')
    SG_PATTERN=$3

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`

    # register a fake host (maybe add a check to see if it's already there?)
    # This requires that the first number of the WWN is "1"
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -setpath -gname ${sgname} -hbauid $(echo $pwwn | sed 's/^1/2/g'):${pwwn} -sp a -spport 0 -arraycommpath 1 -failovermode 4 -host dutest_fakehost -ip 11.22.33.44 -o > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to add the initiator to the mask."
    fi
}

remove_initiator_from_mask() {
    VNX_SP_IP=$1
    pwwn=$(echo $2 | awk '{print substr($0,1,2),":",substr($0,3,2),":",substr($0,5,2),":",substr($0,7,2),":",substr($0,9,2),":",substr($0,11,2),":",substr($0,13,2),":",substr($0,15,2)}' | sed 's/ //g')
    SG_PATTERN=$3

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`

    # register a fake host (maybe add a check to see if it's already there?)
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -disconnecthost -gname ${sgname} -o -host dutest_fakehost > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to remove the initiator from the mask."
    fi
}

create_storage_group() {
    VNX_SP_IP=$1
    device_id=$(echo $2 | sed 's/^0*//')
    pwwn=$(echo $3 | awk '{print substr($0,1,2),":",substr($0,3,2),":",substr($0,5,2),":",substr($0,7,2),":",substr($0,9,2),":",substr($0,11,2),":",substr($0,13,2),":",substr($0,15,2)}' | sed 's/ //g')
    sgname=$4

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -create -gname ${sgname} > /tmp/navisechelper.out

    # Add the volume
    add_volume_to_mask ${VNX_SP_IP} ${device_id} ${sgname}

    # Add the initiator
    add_initiator_to_mask ${VNX_SP_IP} $3 ${sgname}
}

delete_storage_group() {
    VNX_SP_IP=$1
    SG_PATTERN=$2

    set -x
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgname=`grep ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $2}' | awk '{print $1}'`

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -destroy -o -gname ${sgname} > /tmp/navisechelper.out
    set +x
}

delete_volume() {
    VNX_SP_IP=$1
    device_id=$(echo $2 | sed 's/^0*//')
    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP -o lun -destroy -l ${device_id} > /tmp/navisechelper.out
    if [ $? -ne 0 ]; then
	echo "Failed to delete the LUN"
    fi
}

verify_export() {
    VNX_SP_IP=$1
    INITIATOR_PATTERN=$2

    shift 2

    SG_PATTERN=$1
    NUM_INITIATORS=$2
    NUM_LUNS=$3

    /opt/Navisphere/bin/naviseccli -User bourne -Password bourne -Scope 0 -Address $VNX_SP_IP storagegroup -list > /tmp/verify.txt

    grep -n ${SG_PATTERN} /tmp/verify.txt > /dev/null
    if [ $? -ne 0 ]
	then
	if [ "$2" = "gone" ]
	    then
	    echo "PASSED: Verified storage group with pattern ${SG_PATTERN} doesn't exist."
	    exit 0;
	fi
	echo "ERROR: Expected storage group ${SG_PATTERN}, but could not find it";
	exit 1;
    else
	if [ "$2" = "gone" ]
	    then
	    echo "ERROR: Expected storage group ${SG_PATTERN} to be gone, but it was found"
	    exit 1;
	fi
    fi

    hits=`grep -n ${SG_PATTERN} /tmp/verify.txt | wc -l`
    if [ ${hits} -gt 1 ]
	then
	echo "ERROR: Expected storage group ${SG_PATTERN}, but found more than one that fit that pattern!"
	exit 1;
    fi

    sgline=`grep -n ${SG_PATTERN} /tmp/verify.txt | awk -F: '{print $1}'`

    sgblocksize=`tail -n +${sgline} /tmp/verify.txt | grep -n Shareable | head -1 | awk -F: '{print $1}'`
    sgblocksize=`expr ${sgblocksize} + 1`
    sgblockstart=`expr ${sgline} + ${sgblocksize} - 1`
    head -${sgblockstart} /tmp/verify.txt | tail -${sgblocksize} > /tmp/verify2.txt
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

    if [ ${num_inits} -ne ${NUM_INITIATORS} ]
	then
	echo "ERROR: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
	echo "Export group dump:"
	cat /tmp/verify2.txt
	failed=true
    fi

    if [ ${num_luns} -ne ${NUM_LUNS} ]
	then
	echo "ERROR: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
	echo "Export group dump:"
	cat /tmp/verify2.txt
	failed=true
    fi

    if [ "${failed}" = "true" ]
	then
	exit 1;
    fi

    echo "PASSED: Verified storage group starting with pattern $1 contained $2 initiators and $3 luns"
    exit 0;
}

# Check to see if this is an operational request or a verification of export request
if [ "$1" = "add_volume_to_mask" ]; then
    shift
    add_volume_to_mask $1 $2 $3
elif [ "$1" = "remove_volume_from_mask" ]; then
    shift
    remove_volume_from_mask $1 $2 $3
elif [ "$1" = "add_initiator_to_mask" ]; then
    shift
    add_initiator_to_mask $1 $2 $3
elif [ "$1" = "remove_initiator_from_mask" ]; then
    shift
    remove_initiator_from_mask $1 $2 $3
elif [ "$1" = "delete_volume" ]; then
    shift
    delete_volume $1 $2
elif [ "$1" = "delete_mask" ]; then
    shift
    delete_mask $1 $2
elif [ "$1" = "create_export_mask" ]; then
    shift
    create_storage_group $1 $2 $3 $4
elif [ "$1" = "delete_export_mask" ]; then
    shift
    delete_storage_group $1 $2
elif [ "$1" = "verify_export" ]; then
    shift
    verify_export $*
else
    # Backward compatibility with vnxexport scripts
    verify_export $*
fi


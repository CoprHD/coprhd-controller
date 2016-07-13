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

## Convenience method for deleting a mask outside of ViPR (including the storage group)
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
    serial_number=$1
    sid=${serial_number: -3}
    device_id=$2
    pattern=$3

    echo "add_volume_to_mask for VNX not supported yet"
    return -1

    # Find out where the volume ended up, thanks to ViPR (formalize this!)
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -dev ${device_id}
    if [ $? -ne 0 ]; then
	echo "Volume ${serial_number}_${device_id} was not found in another SG, skipping removal step"
    else
	sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -dev ${device_id} | grep ViPR_Optimized | cut -c1-31`
	sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

        # Remove the volume from the storage group it is in
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name $sg_long_id remove dev ${device_id}
    fi

    # Add it to the storage group ViPR knows about
    # TODO: I've seen storage groups sneak in over tests...make sure only storage group is found
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage | grep ${pattern}_${sid}_SG | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

    # Add the volume into the storage group we specify with the pattern
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name $sg_long_id add dev ${device_id}
}

remove_volume_from_mask() {
    serial_number=$1
    sid=${serial_number: -3}
    device_id=$2
    pattern=$3

    echo "remove_volume_from_mask for VNX not supported yet"
    return -1

    # Add it to the storage group ViPR knows about
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage | grep ${pattern}_${sid}_SG | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name $sg_long_id remove dev ${device_id}
}

add_initiator_to_mask() {
    serial_number=$1
    sid=${serial_number: -3}
    pwwn=$2
    pattern=$3
    echo "add_initiator_to_mask for VNX not supported yet"
    return -1

    # Find the initiator group that contains the pattern sent in
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type initiator | grep ${pattern}_${sid}_IG
    if [ $? -ne 0 ]; then
	echo "Initiator group ${pattern}_${sid}_IG was not found.  Not able to add to it."
    else
	# dd the initiator to the IG, which in turn adds it to the visibility of the mask
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type initiator -name ${pattern}_${sid}_IG add -wwn ${pwwn}
    fi
}

remove_initiator_from_mask() {
    serial_number=$1
    sid=${serial_number: -3}
    pwwn=$2
    pattern=$3
    echo "remove_initiator_from_mask for VNX not supported yet"
    return -1

    # Find the initiator group that contains the pattern sent in
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type initiator | grep ${pattern}_${sid}_IG
    if [ $? -ne 0 ]; then
	echo "Initiator group ${pattern}_${sid}_IG was not found.  Not able to add to it."
    else
	# dd the initiator to the IG, which in turn adds it to the visibility of the mask
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type initiator -name ${pattern}_${sid}_IG remove -wwn ${pwwn}
    fi
}

delete_volume() {
    echo "Delete volume for VNX not yet supported";
    sleep 30
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
	    exit;
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
    num_inits=`grep "$INITIATOR_PATTERN" /tmp/verify2.txt | awk '{print $1}' | sort -u | wc -l | awk '{print $1}'`
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
else
    verify_export $*
fi


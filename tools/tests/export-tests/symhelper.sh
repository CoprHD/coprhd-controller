#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

#
# Script to help manage storage system outside of ViPR.
# Used to perform various operations.
#
# Usage: ./symhelper.sh verify-export <SERIAL_NUMBER> <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#        ./symhelper.sh add_volume_to_mask <SERIAL_NUMBER> <DEVICE_ID> <NAME_PATTERN>
#        ./symhelper.sh remove_volume_from_mask <SERIAL_NUMBER> <DEVICE_ID> <NAME_PATTERN>
#        ./symhelper.sh delete_volume <SERIAL_NUMBER> <DEVICE_ID>
#        ./symhelper.sh add_initiator_to_mask <SERIAL_NUMBER> <PWWN> <NAME_PATTERN>
#        ./symhelper.sh remove_initiator_from_mask <SERIAL_NUMBER> <PWWN> <NAME_PATTERN>
#
#set -x

# Required for remote execution of SYMCLI commands.
# If you are debugging, export this variable on your command line or add to your .bashrc
export SYMCLI_CONNECT=SYMAPI_SERVER

## Convenience method for deleting a mask outside of ViPR (including the storage group)
delete_mask() {
    serial_number=$1
    pattern=$2

    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} delete view -name ${pattern} -noprompt
    if [ $? -ne 0 ]; then
	echo "no mask found."
    fi

    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -v | grep ${pattern}
    if [ $? -ne 0 ]; then
	echo "SG not found for ${pattern}"
    else
	sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${pattern} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

        # Remove the volume from the storage group it is in
	dev_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage show ${sg_long_id} | grep Devices | awk -F: '{print $2}'`
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -force -name ${sg_long_id} remove dev ${dev_id}

	# Put it back into the optimized SG?

	# Delete storage group
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} delete -force -name ${sg_long_id} -type storage -noprompt
    fi
}

add_volume_to_mask() {
    serial_number=$1
    device_id=$2
    pattern=$3

    # Find out how many luns there are in the mask now.
    num_luns=`get_number_of_luns_in_mask ${serial_number} ${pattern}`

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
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage | grep ${pattern}_SG | tail -1 | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

    # Add the volume into the storage group we specify with the pattern
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name $sg_long_id add dev ${device_id}

    # Ensure the provider is updated
    verify_export_via_provider ${serial_number} ${pattern} none `expr ${num_luns} + 1`
}

remove_volume_from_mask() {
    serial_number=$1
    device_id=$2
    pattern=$3

    # Find out how many luns there are in the mask now.
    num_luns=`get_number_of_luns_in_mask ${serial_number} ${pattern}`

    # Add it to the storage group ViPR knows about
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage | grep ${pattern}_SG | tail -1 | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name $sg_long_id remove dev ${device_id}

    # Ensure the provider is updated
    verify_export_via_provider ${serial_number} ${pattern} none `expr ${num_luns} - 1`
}

add_initiator_to_mask() {
    serial_number=$1
    pwwn=$2
    pattern=$3

    # Find out how many inits there are in the mask now.
    num_inits=`get_number_of_initiators_in_mask ${serial_number} ${pattern}`

    # Find the initiator group that contains the pattern sent in
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type initiator | grep ${pattern}_IG
    if [ $? -ne 0 ]; then
	echo "Initiator group ${pattern}_IG was not found.  Not able to add to it."
    else
	# dd the initiator to the IG, which in turn adds it to the visibility of the mask
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type initiator -name ${pattern}_IG add -wwn ${pwwn} 
    fi

    # Ensure the provider is updated
    verify_export_via_provider ${serial_number} ${pattern} `expr ${num_inits} + 1` none
}

remove_initiator_from_mask() {
    serial_number=$1
    pwwn=$2
    pattern=$3

    # Find out how many inits there are in the mask now.
    num_inits=`get_number_of_initiators_in_mask ${serial_number} ${pattern}`

    # Find the initiator group that contains the pattern sent in
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type initiator | grep ${pattern}_IG
    if [ $? -ne 0 ]; then
	echo "Initiator group ${pattern}_IG was not found.  Not able to add to it."
    else
	# dd the initiator to the IG, which in turn adds it to the visibility of the mask
	/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type initiator -name ${pattern}_IG remove -wwn ${pwwn}
    fi

    # Ensure the provider is updated
    verify_export_via_provider ${serial_number} ${pattern} `expr ${num_inits} - 1` none
}

delete_volume() {
    serial_number=$1
    devid=$2
    /opt/emc/SYMCLI/bin/symdev -sid ${serial_number} not_ready ${devid} -noprompt
    
    # Assume VMAX3 if there is an Optimized group...
    /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -v | grep "ViPR_Optimized"
    if [ $? -eq 0 ]; then
        # Check if the volume is in there...
        OPTIMIZEDSG=`/opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} list -type storage -devs ${devid} -v \
            | grep "Storage Group Name" | grep "Optimized"  | awk -F:  '{ print $2 }' | sed -E 's/\s//'`
        if [ ! -z ${OPTIMIZEDSG// } ]; then
            /opt/emc/SYMCLI/bin/symaccess -sid ${serial_number} -type storage -name ${OPTIMIZEDSG} remove dev ${devid}
        fi
        /opt/emc/SYMCLI/bin/symdev -sid ${serial_number} free -all -devs ${devid} -noprompt
    else
        /opt/emc/SYMCLI/bin/symconfigure -sid ${serial_number} -cmd "unmap dev ${devid};" commit -noprompt
        /opt/emc/SYMCLI/bin/symdev -sid ${serial_number} -dev ${devid} unbind -noprompt
    fi
    /opt/emc/SYMCLI/bin/symconfigure -sid ${serial_number} -cmd "delete dev ${devid};" commit -noprompt
    exit 0;
}

verify_export_prechecks() {
    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/dev/null

    SYM=${SID:-000198700406}
    /opt/emc/SYMCLI/bin/symaccess -sid ${SYM} list view -name ${SG_PATTERN} -detail > ${TMPFILE1} 2> ${TMPFILE2}

    grep -n ${SG_PATTERN} ${TMPFILE1} > /dev/null
    if [ $? -ne 0 ]; then
	echo -e "\e[91mERROR\e[0m: Expected MaskingView ${SG_PATTERN}, but could not find it";
	exit 1;
    fi
}

get_number_of_luns_in_mask() {
    # First parameter is the Symm ID
    SID=$1
    SG_PATTERN=$2
    NUM_INITIATORS=$3
    NUM_LUNS=$4

    verify_export_prechecks

    num_luns=`perl -nle 'print $1 if(m#(\S+)\s+\S+\s+Not Visible\s+#);' ${TMPFILE1} | sort -u | wc -l`
    echo ${num_luns}
    exit 0;
}

get_number_of_initiators_in_mask() {
    # First parameter is the Symm ID
    SID=$1
    SG_PATTERN=$2
    NUM_INITIATORS=$3
    NUM_LUNS=$4

    verify_export_prechecks

    num_inits=`grep "WWN.*:" ${TMPFILE1} | wc -l`
    echo ${num_inits}
    exit 0;
}

verify_export() {
    # First parameter is the Symm ID
    SID=$1
    shift
    # Subsequent parameters: MaskingView Name, Number of Initiators, Number of Luns, HLUs for Luns
    # If checking if the MaskingView does not exist, then parameter $2 should be "gone"
    SG_PATTERN=$1
    NUM_INITIATORS=$2
    NUM_LUNS=$3
    HLUS=$4
    TMPFILE1=/tmp/verify-${RANDOM}
    TMPFILE2=/dev/null

    SYM=${SID:-000198700406}
    /opt/emc/SYMCLI/bin/symaccess -sid ${SYM} list view -name ${SG_PATTERN} -detail > ${TMPFILE1} 2> ${TMPFILE2}

    grep -n ${SG_PATTERN} ${TMPFILE1} > /dev/null
    if [ $? -ne 0 ]
	then
	if [ "$2" = "gone" ]
	    then
	    echo "PASSED: Verified MaskingView with pattern ${SG_PATTERN} doesn't exist."
	    exit 0;
	fi
	echo -e "\e[91mERROR\e[0m: Expected MaskingView ${SG_PATTERN}, but could not find it";
	exit 1;
    else
	if [ "$2" = "gone" ]
	    then
	    echo -e "\e[91mERROR\e[0m: Expected MaskingView ${SG_PATTERN} to be gone, but it was found"
	    exit 1;
	fi
    fi

    num_inits=`grep "WWN.*:" ${TMPFILE1} | wc -l`
    num_luns=`perl -nle 'print $1 if(m#(\S+)\s+\S+\s+Not Visible\s+#);' ${TMPFILE1} | sort -u | wc -l`
    hlus=( `perl -nle 'print $1 if(m#\S+\s+\S+\s+Not Visible\s+(\S+)+\s#);' ${TMPFILE1} | sort -u` )
    failed=false

    if [ "${num_inits}" != "${NUM_INITIATORS}" ]; then
	echo -e "\e[91mERROR\e[0m: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
	echo -e "\e[91mERROR\e[0m: Masking view dump:"
	grep "Masking View Name" ${TMPFILE1}
	grep "Group Name" ${TMPFILE1}
	grep "WWN.*:" ${TMPFILE1}
	grep "Not Visible" ${TMPFILE1} | sort -u
	failed=true
    fi

    if [ "${num_luns}" != "${NUM_LUNS}" ]; then
	echo -e "\e[91mERROR\e[0m: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
	echo -e "\e[91mERROR\e[0m: Masking view dump:"
	grep "Masking View Name" ${TMPFILE1}
	grep "Group Name" ${TMPFILE1}
	grep "WWN" ${TMPFILE1}
	grep "Not Visible" ${TMPFILE1} | sort -u
	failed=true
    fi

    if [ -n "${HLUS}" ]; then
        hlu_arr=(${HLUS//,/ })
        if [  "${hlus[*]}" != "${hlu_arr[*]}" ]; then
            echo -e "\e[91mERROR\e[0m: Export group HLUs: Expected: ${hlu_arr[*]} Retrieved: ${hlus[*]}";
            echo -e "\e[91mERROR\e[0m: Masking view dump:"
            grep "Masking View Name" ${TMPFILE1}
            grep "Group Name" ${TMPFILE1}
            grep "WWN" ${TMPFILE1}
            grep "Not Visible" ${TMPFILE1} | sort -u
            failed=true
        fi
    fi

    if [ "${failed}" = "true" ]; then
	exit 1;
    fi

    if [ -n "${HLUS}" ]; then
        echo "PASSED: MaskingView '$1' contained $2 initiators and $3 luns with HLUs $4"
    else
        echo "PASSED: MaskingView '$1' contained $2 initiators and $3 luns"
    fi
    exit 0;
}


# This method will use the array tool to check the mask on the provider
verify_export_via_provider() {
    # First parameter is the Symm ID
    SID=$1
    MAX_WAIT_SECONDS=`expr 20 \* 60`
    SLEEP_INTERVAL_SECONDS=10
    shift
    # Subsequent parameters: MaskingView Name, Number of Initiators, Number of Luns
    # If checking if the MaskingView does not exist, then parameter $2 should be "gone"
    SG_PATTERN=$1
    NUM_INITIATORS=$2
    NUM_LUNS=$3
    TMPFILE1=/tmp/verify-${RANDOM}

    # Clean up on exit
    trap "{ rm -f $TMPFILE1 ; }" EXIT

    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    tools_jar="${DIR}/preExistingConfig.jar"

    if [ ! -f preExistingConfig.properties ]; then
	echo "Missing preExistingConfg.properties.  dutests should generate this for you"
	exit 1
    fi

    if [ "${NUM_INITIATORS}" = "none" -a "${NUM_LUNS}" = "none" ]; then
        echo "Invalid parameters passed to verify_export_via_provider"
        exit 1
    fi

    if [ "${NUM_INITIATORS}" = "none" ]; then
	  numinits="0"
    fi
    if [ "${NUM_LUNS}" = "none" ]; then
	  numluns="0"
    fi

    waited=0
    while :
    do
      # Gather results from the external tool
      java -Dlogback.configurationFile=./logback.xml -jar ${tools_jar} show-view $SID $SG_PATTERN $NUM_INITIATORS $NUM_LUNS > ${TMPFILE1}

      numviews=`grep -i "Total Found  Views Count" ${TMPFILE1} | awk '{print $NF}'`
      numinits=`grep -i "Total Number of Initiators for View" ${TMPFILE1} | awk '{print $NF}'`
      numluns=`grep -i "Total Number of Volumes For View" ${TMPFILE1} | awk '{print $NF}'`

      # Fix empty values
      if [ -z ${numinits// } ]; then
        numinits="0"
      fi
      if [ -z ${numluns// } ]; then
        numluns="0"
      fi

      echo "Found views ${numviews}"
      echo "Found ${numinits} initiators and ${numluns} volumes in mask ${SG_PATTERN}"

      if [ "${NUM_INITIATORS}" = "gone" -a "$numviews" = "0" ]; then
      # Verified that the mask is gone
      exit 0
      fi

      if [ "${NUM_INITIATORS}" = "exists" -a "$numviews" = "1" ]; then
      # Verified that the mask exists, disregarding number of inits/luns
      exit 0
      fi

      # Check if caller does not care about inits or luns
      if [ "${NUM_INITIATORS}" = "none" ]; then
        numinits="${NUM_INITIATORS}"
      fi
      if [ "${NUM_LUNS}" = "none" ]; then
        numluns="${NUM_LUNS}"
      fi

      if [ "${NUM_INITIATORS}" = "$numinits" -a "${NUM_LUNS}" = "$numluns" ]; then
      # Verified the expected number of initiators and luns
      exit 0
      fi

      sleep ${SLEEP_INTERVAL_SECONDS}
	  waited=`expr ${waited} + ${SLEEP_INTERVAL_SECONDS}`

	  if [ ${waited} -ge ${MAX_WAIT_SECONDS} ]; then
	      echo "Waited, but never found provider to have the right number of luns and volumes"
	      exit 1;
	  fi
    done
}

create_export_mask() {
    SID=$1
    CSG=$2
    PWWN=$3
    NAME="${4}_${SID: -3}"
    # Test if we were passed pwwn's or an existing IG name (starting with "host")
    if [[ $PWWN =~ ^host ]]; then
        IG="${NAME}_CIG"
        echo "=== symaccess -sid ${SID} create -type initiator -name ${IG}"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} create -type initiator -name ${IG}
        echo "=== symaccess -sid ${SID} -type initiator -name ${IG} set consistent_lun on"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type initiator -name ${IG} set consistent_lun on
        echo "=== symaccess -sid ${SID} -type initiator -name ${IG} add -ig ${PWWN}"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type initiator -name ${IG} add -ig ${PWWN}

        # Replace CIG or IG with PG
        PG=`echo ${PWWN} | sed -E "s/[CIG]+$/PG/"`
        echo "Generated PG name ${PG}"
    else
        IG="${NAME}_IG"
        echo "=== symaccess -sid ${SID} create -type initiator -name ${IG} -wwn ${PWWN}"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} create -type initiator -name ${IG} -wwn ${PWWN}
    fi

    # Test if we were passed devIds or a CSG/SG name
    if [[ $CSG =~ ^host ]]; then
        echo "Hijacking existing CSG/SG ${CSG}"

        # Replace CSG or SG with PG
        PG=`echo ${CSG} | sed -E "s/[CSG]+$/PG/"`
        echo "Generated PG name ${PG}"
    else
        dev_id=$CSG
        CSG="${NAME}_SG"

        echo "Creating new ${CSG}"

        # Remove from any existing group, i.e. Optimized
        optimized=`symaccess -sid ${SID} list -type storage -v | grep Optimized | awk '{ print $5 }'`
        if [[ ! -z "${optimized// }" ]]; then
            current_sg=`symaccess -sid ${SID} list -type storage -dev $dev_id -v | grep "Storage Group Name" | tail -n1 | awk '{ print $5 }'`
            echo "=== /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type storage -name ${current_sg} remove devs $dev_id"
            /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type storage -name ${current_sg} remove devs $dev_id
        fi

        echo "=== symaccess -sid ${SID} create -type storage -name ${CSG} devs $dev_id"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} create -type storage -name ${CSG} devs $dev_id
    fi

    echo "Hijacking port group ${PG}"

    echo "=== symaccess -sid ${SID} create view -name ${NAME} -sg $CSG -pg ${PG} -ig ${IG}"
    /opt/emc/SYMCLI/bin/symaccess -sid ${SID} create view -name ${NAME} -sg $CSG -pg ${PG} -ig ${IG}

    # Verify that the mask has been created on the provider
    verify_export_via_provider $SID $NAME exists
}

delete_export_mask() {
    SID=$1
    NAME="${2}_${SID: -3}"
    SG=$3
    IG=$4

    echo "=== symaccess -sid ${SID} delete view -name ${NAME} -unmap -noprompt"
    /opt/emc/SYMCLI/bin/symaccess -sid ${SID} delete view -name ${NAME} -unmap -noprompt

    if [[ "$SG" != "noop" ]]; then
        echo "Deleting storage group ${SG}"
        dev_id=`symaccess -sid ${SID} show ${SG} -type storage | grep Devices | awk '{ print $3 }'`
        echo "=== symaccess -sid ${SID} delete -type storage -name ${SG} -force -noprompt"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} delete -type storage -name ${SG} -force -noprompt

        optimized=`symaccess -sid ${SID} list -type storage -v | grep Optimized | awk '{ print $5 }'`
        if [[ ! -z "${optimized// }" ]]; then
            echo "=== /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type storage -name ${optimized} add devs $dev_id"
            /opt/emc/SYMCLI/bin/symaccess -sid ${SID} -type storage -name ${optimized} add devs $dev_i
        fi
    else
        echo "=== Skipping storage group deletion because 'noop' was passed"
    fi

    if [[ "$IG" != "noop" ]]; then
        echo "Deleting initiator group ${IG}"
        echo "=== symaccess -sid ${SID} delete -type initiator -name ${IG} -force -noprompt"
        /opt/emc/SYMCLI/bin/symaccess -sid ${SID} delete -type initiator -name ${IG} -force -noprompt
    else
        echo "=== Skipping initiator group deletion because 'noop' was passed"
    fi

    # Verify that the mask has been deleted on the provider
    verify_export_via_provider $SID $NAME gone
}

cleanup_rdfg() {
    SID=$1
    PROJECT=$2

    SYMCLI=/opt/emc/SYMCLI/bin

    # Get RDF group number
    RDFG=`/opt/storageos/bin/dbutils list RemoteDirectorGroup | grep -A10 "label = $PROJECT" | grep "sourceGroupId =" | tail -n1 | awk -F" " '{print $NF}'`

    # Determine device pairs present in the group
    PAIRS=`$SYMCLI/symrdf -sid $SID -rdfg $RDFG list -offline 2> /dev/null | grep -E "^([0-9A-F]+\s){2}" | awk -F' ' '{print $1,$2}' 2> /dev/null`
    IFS=$'\n'
    TMP_DEVICE_FILE="/tmp/devices-${RANDOM}"
    cat /dev/null > $TMP_DEVICE_FILE
    for p in $PAIRS; do
        echo $p >> $TMP_DEVICE_FILE
    done

    if [ `cat $TMP_DEVICE_FILE | wc -l` -eq 0 ]
    then
        echo "=== RDFG $SID:$RDFG is empty.  No cleanup required."
        exit
    fi

    echo "=== Removing RDF device pairs in $TMP_DEVICE_FILE from $SID:$RDFG"

    # Suspend
    $SYMCLI/symrdf -sid $SID -rdfg $RDFG -file $TMP_DEVICE_FILE suspend -force -noprompt > /dev/null
    SUSPEND_OP=$?
    # Disable
    $SYMCLI/symrdf -sid $SID -rdfg $RDFG -file $TMP_DEVICE_FILE disable -force -noprompt > /dev/null
    DISABLE_OP=$?
    # Delete
    $SYMCLI/symrdf -sid $SID -rdfg $RDFG -file $TMP_DEVICE_FILE deletepair -noprompt > /dev/null
    DELETE_OP=$?

    echo "=== Finished RDFG cleanup (${SUSPEND_OP}, ${DISABLE_OP}, ${DELETE_OP})"
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
elif [ "$1" = "verify_export" ]; then
    shift
    verify_export $*
elif [ "$1" = "verify_export_via_provider" ]; then
    shift
    verify_export_via_provider $*
elif [ "$1" = "create_export_mask" ]; then
    shift
    create_export_mask $*
elif [ "$1" = "delete_export_mask" ]; then
    shift
    delete_export_mask $*
elif [ "$1" = "cleanup_rdfg" ]; then
    shift
    cleanup_rdfg $*
else
    # Backward compatibility with vmaxexport scripts
    verify_export $*
fi


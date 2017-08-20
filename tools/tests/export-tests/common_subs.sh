#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#
# Common subroutines for export tests
# ===================================
#

# Reset the simulator
# You need plow-through access to the simulator via ssh, which is usually granted
# thanks to the AIO settings and the cisco simulator.
# For an example of /root/reset.sh, look at lglw1045.
reset_simulator() {
    if [ "${SIM}" = "1" ]; then
	/usr/bin/sshpass -p ${HW_SIMULATOR_DEFAULT_PASSWORD} ssh -q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null root@${HW_SIMULATOR_IP} /root/reset.sh
    else
	echo "No simulator set, not resetting simulator"
    fi
}

# Method to retrieve all of the tools needed that do not get stored directly in the git repo
retrieve_tooling() {
    if [ "${TOOLING_AT_JAR_NAME}" = "" ]
    then
	echo "ERROR: No tooling variables are defined.  Please update to the latest sanity.conf that contains these entries"
	exit;
    fi
    retrieve_arraytools
    retrieve_preexistingconfig
}

# Generic method for determining the latest version of a jar, then retrieve it
retrieve_jar() {
    TOOLING_JAR_NAME=$1
    TOOLING_REPO_IP=$2
    TOOLING_REPO_PW=$3
    TOOLING_REPO_DIR=$4
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    if [ ! -f "${DIR}/${TOOLING_JAR_NAME}.jar" ]; then
	jar_version=`sshpass -p ${TOOLING_REPO_PW} ssh -o StrictHostKeyChecking=no root@${TOOLING_REPO_IP} "ls -t ${TOOLING_REPO_DIR} | grep -v jar | head -1"`
	if [ $? -ne 0 ]; then
	    echo "ERROR: Could not reach repo for ${TOOLING_JAR_NAME}.jar, and it is required for execution."
	    exit;
	fi

	sshpass -p ${TOOLING_REPO_PW} scp root@${TOOLING_REPO_IP}:${TOOLING_REPO_DIR}/${jar_version}/${TOOLING_JAR_NAME}-${jar_version}.jar ${TOOLING_JAR_NAME}.jar
	if [ $? -ne 0 -o ! -f ${TOOLING_JAR_NAME}.jar ]; then
	    echo "ERROR: Could not get ${TOOLING_JAR_NAME}.jar, and it is required for execution."
	    exit;
	fi

	echo "Downloaded latest ${TOOLING_JAR_NAME} (version ${jar_version}) from repository"
    fi	
}

# Retrieve the ArrayTools.jar tooling
retrieve_arraytools() {
    retrieve_jar ${TOOLING_AT_JAR_NAME} ${TOOLING_AT_REPO_IP} ${TOOLING_AT_REPO_PW} ${TOOLING_AT_REPO_DIR}
}

# Retrieve the preExistingConfig.jar tooling
retrieve_preexistingconfig() {
    retrieve_jar ${TOOLING_PEC_JAR_NAME} ${TOOLING_PEC_REPO_IP} ${TOOLING_PEC_REPO_PW} ${TOOLING_PEC_REPO_DIR}
}

# A method that reports on the status of the test, along with other 
# important information:
#
# What is helpful in one line:
# 1. storage system under test
# 2. simulator or not
# 3. test number
# 4. failure scenario within that test (null in this suite)
# 5. git branch
# 6. git commit SHA
# 7. IP address
# 8. date/time stamp
# 9. test status
#
# A huge plus, but wouldn't be available on a single line is:
# 1. output from a failed test case
# 2. the controller/apisvc logs for the test case
#
# But maybe we crawl before we run.
report_results() {
    if [ "${REPORT}" != "1" ]; then
	return;
    fi

    testname=${1}
    failure_scenario=${2}
    branch=`git rev-parse --abbrev-ref HEAD`
    sha=`git rev-parse HEAD`
    ss=${SS}

    if [ "${SS}" = "vplex" ]; then
	    ss="${SS} ${VPLEX_MODE}"
    fi
    if [ "${SS}" = "srdf" ]; then
        ss="${SS} ${SRDF_MODE}"
    fi

    simulator="Hardware"
    if [ "${SIM}" = "1" ]; then
	simulator="Simulator"
    fi
    status="PASSED"
    if [ ${TRIP_VERIFY_FAIL_COUNT} -gt 0 ]; then
	status="FAILED"
    fi
    datetime=`date +"%Y-%m-%d.%H:%M:%S"`

    result="${ss},${simulator},${testname},${failure_scenario},${branch},${sha},${ipaddr},${datetime},<a href=\"${GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR}/${TEST_OUTPUT_FILE}\">${status}</a>"
    mkdir -p /root/reliability
    echo ${result} > /tmp/report-result.txt
    echo ${result} >> /root/reliability/results-local-set.db

    echo -e "${result}\n$(cat ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE})" > ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}

    cat /tmp/report-result.txt | sshpass -p $SYSADMIN_PASSWORD ssh -o StrictHostKeyChecking=no root@${GLOBAL_RESULTS_IP} "cat >> ${GLOBAL_RESULTS_PATH}/${RESULTS_SET_FILE}" > /dev/null 2> /dev/null
    cat ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE} | sshpass -p $SYSADMIN_PASSWORD ssh -o StrictHostKeyChecking=no root@${GLOBAL_RESULTS_IP} "cat > ${GLOBAL_RESULTS_PATH}/${GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR}/${TEST_OUTPUT_FILE} ; chmod 777 ${GLOBAL_RESULTS_PATH}/${GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR}/${TEST_OUTPUT_FILE}" > /dev/null 2> /dev/null
}

# Helper method to increment the failure counts
incr_fail_count() {
    VERIFY_FAIL_COUNT=`expr $VERIFY_FAIL_COUNT + 1`
    TRIP_VERIFY_FAIL_COUNT=`expr $TRIP_VERIFY_FAIL_COUNT + 1`
}

# Reset the trip counters on a distinct test case
reset_counts() {
    TRIP_VERIFY_COUNT=0
    TRIP_VERIFY_FAIL_COUNT=0
}

verify_export() {
    export_name=$1
    host_name=$2
    shift 2

    masking_view_name=`get_masking_view_name ${export_name} ${host_name} false`
    arrayhelper verify_export ${SERIAL_NUMBER} "${masking_view_name}" $*
    VERIFY_COUNT=`expr $VERIFY_COUNT + 1`
}

# Determine the mask name, given the storage system and other info
get_masking_view_name() {
    no_host_name=0
    export_name=$1
    host_name=$2
    get_vipr_name=$3

    if [ "$host_name" = "-x-" ]; then
        # The host_name parameter is special, indicating no hostname, so
        # set it as an empty string
        host_name=""
        no_host_name=1
    fi

    cluster_name_if_any="_"
    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        cluster_name_if_any=""
        if [ "$no_host_name" -eq 1 ]; then
            # No hostname is applicable, so this means that the cluster name is the
            # last part of the MaskingView name. So, it doesn't need to end with '_'
            cluster_name_if_any="${CLUSTER}"
        fi
    fi

    if [ "$SS" = "vmax2" -o "$SS" = "vmax3" -o "$SS" = "vnx" -o "$SS" = "srdf" ]; then
	masking_view_name="${cluster_name_if_any}${host_name}_${SERIAL_NUMBER: -3}"
    elif [ "$SS" = "xio" ]; then
        masking_view_name=$host_name
    elif [ "$SS" = "unity" ]; then
        if [ "${get_vipr_name}" != "true" ]; then
	    if [ "$host_name" = "$HOST1" ]; then
                masking_view_name="$H1ID"
	    elif [ "$host_name" = "$HOST2" ]; then
                masking_view_name="$H2ID"
	    elif [ "$host_name" = "$HOST3" ]; then
                masking_view_name="$H3ID"
	    fi
	else
	    masking_view_name="${cluster_name_if_any}${host_name}_${SERIAL_NUMBER: -3}"
	fi
    elif [ "$SS" = "vplex" ]; then
        # TODO figure out how to account for vplex cluster (V1_ or V2_)
        # also, the 3-char serial number suffix needs to be based on actual vplex cluster id,
        # not just splitting the string and praying...
        serialNumSplitOnColon=(${SERIAL_NUMBER//:/ })
        masking_view_name="V1_${CLUSTER}_${host_name}_${serialNumSplitOnColon[0]: -3}"
    fi

    if [ "$host_name" = "-exact-" ]; then
        masking_view_name=$export_name
    fi

    echo ${masking_view_name}
}

# Extra gut-check.  Make sure we didn't just grab a different mask off the array.
# Run this during test_0 to make sure we're not getting off on the wrong foot.
# Even better would be to delete that mask, export_group, and try again.
verify_maskname() {
    export_name=$1
    host_name=$2

    masking_view_name=`get_masking_view_name ${export_name} ${host_name} true`

    maskname=$(/opt/storageos/bin/dbutils list ExportMask | grep maskName | grep ${masking_view_name} | awk -e ' { print $3; }')

    if [ "${maskname}" = "" ]; then
	echo -e "\e[91mERROR\e[0m: Mask was not found with the name we expected.  This is likely because there is another mask using the same WWNs from a previous run of the test on this VM."
	echo -e "\e[91mERROR\e[0m: Recommended action: delete mask manually from array and rerun test"
	echo -e "\e[91mERROR\e[0m: OR: rerun -setup after setting environment variable: \"export WWN=<some value between 1-9,A-F>\""
	echo "Masks found: "
	/opt/storageos/bin/dbutils list ExportMask | grep maskName | grep host1export

	# We normally don't bail out of the suite, but this is a pretty serious issue that would prevent you from running further.
	cleanup
	finish
    fi
}

# Array helper method that supports the following operations:
# 1. add_volume_to_mask
# 2. remove_volume_from_mask
# 3. delete_volume
#
# The goal is to do minimal processing here and dispatch to the respective array type's helper to do the work.
#
arrayhelper() {
    operation=$1
    serial_number=$2

    case $operation in
    add_volume_to_mask)
	device_id=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_volume_mask_operation $operation $serial_number $device_id "$masking_view_name"
	;;
    remove_volume_from_mask)
	device_id=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_volume_mask_operation $operation $serial_number $device_id "$masking_view_name"
	;;
    add_initiator_to_mask)
	pwwn=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_initiator_mask_operation $operation $serial_number $pwwn "$masking_view_name"
	;;
    remove_initiator_from_mask)
	pwwn=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_initiator_mask_operation $operation $serial_number $pwwn "$masking_view_name"
	;;
    create_export_mask)
        device_id=$3
	pwwn=$4
	name=$5
	arrayhelper_create_export_mask_operation $operation $serial_number $device_id $pwwn $name
	;;
    delete_volume)
	device_id=$3
	arrayhelper_delete_volume $operation $serial_number $device_id
	;;
	delete_export_mask)
    masking_view_name=$3
    sg_name=$4
    ig_name=$5
	arrayhelper_delete_export_mask $operation $serial_number $masking_view_name $sg_name $ig_name
	;;
    delete_mask)
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_delete_mask $operation $serial_number $masking_view_name
	;;
    verify_export)
	masking_view_name=$3
	shift 3
	arrayhelper_verify_export $operation $serial_number "$masking_view_name" $*
	VERIFY_EXPORT_STATUS=$?
	;;
    *)
        echo -e "\e[91mERROR\e[0m: Invalid operation $operation specified to arrayhelper."
	cleanup
	finish -1
	;;
    esac
}

# Call the appropriate storage array helper script to perform masking operations
# outside of the controller.
#
arrayhelper_create_export_mask_operation() {
    operation=$1
    serial_number=$2
    device_id=$3
    pwwn=$4
    maskname=$5

    case $SS in
    vnx)
         runcmd navihelper.sh $operation $serial_number $array_ip $device_id $pwwn $maskname
	 ;;
	vmax2|vmax3)
	    runcmd symhelper.sh $operation $serial_number $device_id $pwwn $maskname
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to perform masking operations
# outside of the controller.
#
arrayhelper_volume_mask_operation() {
    operation=$1
    serial_number=$2
    device_id=$3
    pattern=$4

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $device_id $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $serial_number $array_ip $device_id $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $device_id $pattern
	 ;;
    unity)
         runcmd vnxehelper.sh $operation $device_id "$pattern"
         ;;
    vplex)
         runcmd vplexhelper.sh $operation $device_id $pattern
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to perform masking operations
# outside of the controller.
#
arrayhelper_initiator_mask_operation() {
    operation=$1
    serial_number=$2
    pwwn=$3
    pattern=$4

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $pwwn $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $serial_number $array_ip $pwwn $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $pwwn $pattern
	 ;;
    unity)
         runcmd vnxehelper.sh $operation "$pwwn" "$pattern"
         ;;
    vplex)
         runcmd vplexhelper.sh $operation $pwwn $pattern
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to perform delete volume
# outside of the controller.
#
arrayhelper_delete_volume() {
    operation=$1
    serial_number=$2
    device_id=$3

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $device_id
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $device_id
	 ;;
    xio)
         runcmd xiohelper.sh $operation $device_id
	 ;;
    unity)
         runcmd vnxehelper.sh $operation $device_id
         ;;
    vplex)
         runcmd vplexhelper.sh $operation $device_id
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to perform delete mask
# outside of the controller.
#
arrayhelper_delete_export_mask() {
    operation=$1
    serial_number=$2
    masking_view_name=$3
    sg_name=$4
    ig_name=$5

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $masking_view_name $sg_name $ig_name
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to perform delete mask
# outside of the controller.
#
arrayhelper_delete_mask() {
    operation=$1
    serial_number=$2
    pattern=$3

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $pattern
	 ;;
    unity)
         runcmd vnxehelper.sh $operation "$pattern"
         ;;
    vplex)
         runcmd vplexhelper.sh $operation $pattern
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# Call the appropriate storage array helper script to verify export
#
arrayhelper_verify_export() {
    operation=$1
    serial_number=$2
    masking_view_name=$3
    shift 3
    return_status=0

    case $SS in
    vmax2|vmax3|srdf)
         runcmd symhelper.sh $operation $serial_number $masking_view_name $*
         return_status=$?
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $macaddr $masking_view_name $*
         return_status=$?
	 ;;
    xio)
         runcmd xiohelper.sh $operation $masking_view_name $*
         return_status=$?
	 ;;
    unity)
         runcmd vnxehelper.sh $operation "$masking_view_name" $*
         return_status=$?
         ;;
    vplex)
         runcmd vplexhelper.sh $operation $masking_view_name $*
         return_status=$?
	 ;;
    *)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
    return $return_status
}

# We need a way to get all of the zones that could be associated with this host
# that makes sense for the beginning of a test case.
verify_no_zones() {
    fabricid=$1
    host=$2

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    recho "zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host}"
    zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host} | grep ${host} > /dev/null
    if [ $? -eq 0 ]; then
	echo -e "\e[91mERROR\e[0m: Found zones on the switch associated with host ${host}."
    fi
}

# Load FCZoneReference zone names from the database.  To be run after an export operation
#
load_zones() {
    host=$1

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    zones=`/opt/storageos/bin/dbutils list FCZoneReference | grep zoneName | grep ${HOST1} | awk -F= '{print $2}'`
    if [ $? -ne 0 ]; then
	echo -e "\e[91mERROR\e[0m: Could not determine the zones that were created"
    fi
    if [ ${DUTEST_DEBUG} -eq 1 ]; then
	secho "load_zones: " $zones
    fi
}

# Filter a zone out of the zones list used for verification
# args $1=wwn of initiator in zone(s) to remove
# Note: if the initiator is zoned to multiple ports, this will remove all zones of the initiator
# Also, it takes off the first two bytes of the WWN (because they aren't in the zone name.) 
filter_zone() {
    filteredzones=""
    wwn=$(echo $1 | sed -e s/://g | sed -e s/^[0-9a-f][0-9a-f][0-9a-f][0-9a-f]// )
    echo filter_zone wwn = $wwn
    for zone in ${zones}
    do
        matchzone=$(echo $zone | grep $wwn)
	if [ "$matchzone" = "" ]; then
	    filteredzones="$zone $filteredzones"
	fi
    done
    zones=${filteredzones}
    echo "Filtered zones: "  $zones
}

# Verify the zones exist (or don't exist)
verify_zones() {
    fabricid=$1
    check=$2

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    for zone in ${zones}
    do
      recho "zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name ${zone}"
      zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name ${zone} | grep ${zone} > /dev/null
      if [ $? -ne 0 -a "${check}" = "exists" ]; then
	  echo -e "\e[91mERROR\e[0m: Expected to find zone ${zone}, but did not."
      elif [ $? -eq 0 -a "${check}" = "gone" ]; then
	  echo -e "\e[91mERROR\e[0m: Expected to not find zone ${zone}, but it is there."
      fi
    done
}

# Gets the zone name corresponding to a host and initiator
get_zone_name() {
    host=$1
    initiator=$2

    # Remove the ':' characters
    initiator=${initiator//:}

    zone=`/opt/storageos/bin/dbutils list FCZoneReference | grep zoneName | grep ${host} | grep ${initiator:4} | awk -F= '{print $2}'`
    if [[ -z $zone ]]; then
        # Try to find the zone based on the initiator only - might be a pre-existing zone
        zone=`/opt/storageos/bin/dbutils list FCZoneReference | grep zoneName | grep ${initiator:4} | awk -F= '{print $2}'`
    fi  
    echo $zone
}

# Verify that a given zone exists or has been removed.
# Increments the fail count if check fails and does not return a
# return code.
verify_zone() {
    zone=$1
    fabricid=$2
    check=$3  
    
    if [ "${SIM}" = "1" ]; then
        network=fabric-sim
        # Remove the FABRIC_ prefix for sim fabric
        fabricid=${fabricid:5} 
    else
        network=$BROCADE_NETWORK
        # Remove the FABRIC_ prefix for non sim fabric
        fabricid=${fabricid:7}
    fi
    
    recho "zone list $network --fabricid ${fabricid} --zone_name ${zone}"
    zone list $network --fabricid ${fabricid} --zone_name ${zone} | grep ${zone} > /dev/null
    returncode=$?
    if [ $returncode -ne 0 -a "${check}" = "exists" ]; then
        echo -e "\e[91mERROR\e[0m: Expected to find zone ${zone}, but did not."
        incr_fail_count           
    elif [ $returncode -eq 0 -a "${check}" = "gone" ]; then
        echo -e "\e[91mERROR\e[0m: Expected to not find zone ${zone}, but it is there."
        incr_fail_count           
    fi    
}

# Determines if a zone is newly created.  A newly created zone is one
# where the specified host name from the test occurrence is contained 
# within the zone name.
newly_created_zone_for_host() {
    zone=$1
    host=$2
    if [[ "$zone" == *${host}* ]]; then
        return 0
    fi    
    return 1
}    

# Cleans zones and zone references ($1=fabricId, $2=host)
clean_zones() {
    fabricid=$1

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    load_zones $2
    delete_zones ${HOST1}
    zoneUris=$(/opt/storageos/bin/dbutils list FCZoneReference | awk  -e \
"
/^id: / { uri=\$2; }
/zoneName/ { name = \$3; print uri, name; }
" | grep host1 | awk -e ' { print $1; }')
    if [ "${zoneUris}" != "" ]; then
	for uri in $zoneUris
	do
	  runcmd /opt/storageos/bin/dbutils delete FCZoneReference $uri
	done
    fi
}

# Deletes the zones returned by load_zones, then any remaining zones
delete_zones() {
    host=$1

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    zonesdel=0
    for zone in ${zones}
    do
      if [ ${DUTEST_DEBUG} -eq 1 ]; then
	  secho "deleteing zone ${zone}"
      fi

      # Delete zones that were returned by load_zones
      runcmd zone delete $BROCADE_NETWORK --fabric ${fabricid} --zones ${zone}
      zonesdel=1
      if [ $? -ne 0 ]; then
	  secho "zones not deleted"
      fi
    done

    if [ ${zonesdel} -eq 1 ]; then
	if [ ${DUTEST_DEBUG} -eq 1 ]; then
	    echo "sactivating fabric ${fabricid}"
	fi
	runcmd zone activate $BROCADE_NETWORK --fabricid ${fabricid} | tail -1 > /dev/null
	if [ $? -ne 0 ]; then
	    secho "fabric not activated"
	fi
    fi

    zonesdel=0

    recho "zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host}"
    fabriczones=`zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host} | grep ${host}`
    if [ $? -eq 0 ]; then
	for zonename in ${fabriczones}
	do
	  runcmd zone delete $BROCADE_NETWORK --fabric ${fabricid} --zones ${zonename}
	  zonesdel=1
	done	    
    fi

    if [ ${zonesdel} -eq 1 ]; then
	if [ ${DUTEST_DEBUG} -eq 1 ]; then
	    echo "sactivating fabric ${fabricid}"
	fi
	runcmd zone activate $BROCADE_NETWORK --fabricid ${fabricid} | tail -1 > /dev/null
	if [ $? -ne 0 ]; then
	    secho "fabric not activated"
	fi
    fi
}

finish() {
    code=${1}
    if [ $VERIFY_FAIL_COUNT -ne 0 ]; then
        exit $VERIFY_FAIL_COUNT
    fi
    if [ "${code}" != "" ]; then
	exit ${code}
    fi
    exit 0
}

drawstars() {
    repeatchar=`expr $1 + 2`
    while [ ${repeatchar} -gt 0 ]
    do 
       echo -n "*" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
       repeatchar=`expr ${repeatchar} - 1`
    done
    echo "*" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
}

echot() {
    numchar=`echo $* | wc -c`
    echo "" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    drawstars $numchar
    echo "* $* *" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    drawstars $numchar
}

# General echo output
secho()
{
    echo -e "*** $*" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
}

# General echo output for things that are run that will suspend
recho()
{
    datetime=`date +"%Y-%m-%d %H:%M:%S"`
    echo -e "=== $datetime - $*" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
}

# Helper method to increment the failure counts
incr_fail_count() {
    VERIFY_FAIL_COUNT=`expr $VERIFY_FAIL_COUNT + 1`
    TRIP_VERIFY_FAIL_COUNT=`expr $TRIP_VERIFY_FAIL_COUNT + 1`
}

# A method to run a command that exits on failure.
run() {
    cmd=$*
    datetime=`date +"%Y-%m-%d %H:%M:%S"`
    echo === $datetime - $cmd | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    rm -f ${CMD_OUTPUT}
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
	$cmd &> ${CMD_OUTPUT}
    else
	$cmd 2>&1
    fi
    if [ $? -ne 0 ]; then
	if [ -f ${CMD_OUTPUT} ]; then
	    cat ${CMD_OUTPUT}
	fi
	echo There was a failure | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
	cleanup
	finish -1
    fi
}

# A method to run a command that continues on failure.
runcmd() {
    cmd=$*
    datetime=`date +"%Y-%m-%d %H:%M:%S"`
    echo === $datetime - $cmd | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    rm -f ${CMD_OUTPUT}
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
	"$@" &> ${CMD_OUTPUT}
    else
	"$@" 2>&1
    fi
    if [ $? -ne 0 ]; then
	if [ -f ${CMD_OUTPUT} ]; then
	    cat ${CMD_OUTPUT} | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
	fi
	echo There was a failure | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
	incr_fail_count
	return 1
    fi
}

# Utility for running a command that is expected to pause.
# It will fill in variables needed for resume and follow operations
#
runcmd_suspend() {
    testname=$1
    shift
    cmd=$*
    # Print the command that will be run
    recho $*

    # Actually run the cmd, put stdout to the resultcmd variable.  the rest goes to errors.txt
    resultcmd=`$* 2> /tmp/errors.txt`

    if [ $? -ne 0 ]; then
	echo "Command was expected to suspend, however it failed outright"
	incr_fail_count
	report_results ${testname}
	return
    fi

    cat /tmp/errors.txt | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    echo ${resultcmd}

    # Parse results and store results in variables to be used later
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}
}

# Utility for resuming the most recently suspended workflow and following its task
resume_follow_task() {
    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task
    runcmd task follow $task
}

#counterpart for run
#executes a command that is expected to fail
fail(){
    if [ "${1}" = "-with_error" ]; then
      witherror=${2}
      shift 2
      # TODO When the cmd fails, we can check if the failure output contains this expected error message
    fi
    cmd=$*
    datetime=`date +"%Y-%m-%d %H:%M:%S"`
    echo === $datetime - $cmd | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
        $cmd &> ${CMD_OUTPUT}
    else
        $cmd 2>&1
    fi

    status=$?
    if [ $status -eq 0 ] ; then
        echo '**********************************************************************' | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
        echo -e "$cmd succeeded, which \e[91mshould not have happened\e[0m" | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
        cat ${CMD_OUTPUT} | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
        echo '**********************************************************************' | tee -a ${LOCAL_RESULTS_PATH}/${TEST_OUTPUT_FILE}
        incr_fail_count;
    else
        secho "$cmd failed, which \e[32mis the expected ouput\e[0m"
    fi
}

pwwn()
{
    # Note, for VNX, the array tooling relies on the first number being "1", please don't change that for dutests on VNX.  Thanks!
    WWN=${WWN:-0}
    idx=$1
    echo 1${WWN}:${macaddr}:${idx}
}

nwwn()
{
    WWN=${WWN:-0}
    idx=$1
    echo 2${WWN}:${macaddr}:${idx}
}

setup_yaml() {
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    tools_file="${DIR}/tools.yml"
    if [ -f "$tools_file" ]; then
	echo "stale $tools_file found. Deleting it."
	rm $tools_file
    fi

    # create the yml file to be used for tooling
    touch $tools_file

    if [ "${VCENTER_IP}" != "" ]; then
	# Append the vcenter attributes 
	printf 'vcenter:\n  - ip: %s:%s\n    username: %s\n    password: %s\n' "${VCENTER_IP}" "${VCENTER_PORT}" "${VCENTER_USERNAME}" "${VCENTER_PASSWORD}" >> $tools_file
    else
	echo "WARNING: VCENTER_IP not set, vcenter verification operations will not work!"
    fi

    if [ "${WINDOWS_HOST_IP}" != "" ]; then
	# Append Windows host attributes
	printf 'hosts:\n  windows:\n  - ip: %s:%s:false\n    username: %s\n    password: %s\n' "${WINDOWS_HOST_IP}" "${WINDOWS_HOST_PORT}" "${WINDOWS_HOST_USERNAME}" "${WINDOWS_HOST_PASSWORD}" >> $tools_file
    else
	echo "WARNING: WINDOWS_HOST_IP not set.  host verification operations will not work!"
    fi

    if [ "${HPUX_HOST_IP}" != "" ]; then
	# Append HP-UX host attributes
	printf '  hpux:\n  - ip: %s:%s\n    username: %s\n    password: %s\n' "${HPUX_HOST_IP}" "${HPUX_HOST_PORT}" "${HPUX_HOST_USERNAME}" "${HPUX_HOST_PASSWORD}" >> $tools_file
    else
	echo "WARNING: HPUX_HOST_IP not set.  host verification operations will not work!"
    fi

    if [ "${LINUX_HOST_IP}" != "" ]; then
	# Append Linux host attributes
	printf '  linux:\n  - ip: %s:%s\n    username: %s\n    password: %s\n' "${LINUX_HOST_IP}" "${LINUX_HOST_PORT}" "${LINUX_HOST_USERNAME}" "${LINUX_HOST_PASSWORD}" >> $tools_file
    else
	echo "WARNING: LINUX_HOST_IP not set.  host verification operations will not work!"
    fi

    if [ "${SS}" = "hds" ]; then
        echo "Creating ${tools_file}"
        printf 'array:\n  %s:\n  - ip: %s:%s\n    username: %s\n    password: %s\n    usessl: false' "${SS}" "${HDS_PROVIDER_IP}" "${HDS_PROVIDER_PORT}" "${HDS_PROVIDER_USER}" "${HDS_PROVIDER_PASSWD}" >> $tools_file
        return
    fi

    if [ "$SS" = "xio" -o "$SS" = "vplex" -o "$SS" = "unity" ]; then
    	if [ "${SS}" = "unity" ]; then
            echo "Creating ${tools_file}"
            printf 'array:\n  %s:\n  - ip: %s:%s\n    username: %s\n    password: %s' "${SS}" "$UNITY_IP" "$UNITY_PORT" "$UNITY_USER" "$UNITY_PW" >> $tools_file
            return
    	fi

    	if [ "${storage_password}" = "" ]; then
	    echo "storage_password is not set.  Cannot make a valid tools.yml file without a storage_password"
	    exit;
    	fi

    	sstype=${SS:0:3}
    	if [ "${SS}" = "xio" ]; then
	    sstype="xtremio"
    	fi

    	storage_type=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $1}'`
    	storage_name=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $2}'`
    	storage_version=`storagedevice show ${storage_name} | grep firmware_version | awk '{print $2}' | cut -d '"' -f2`
    	storage_ip=`storagedevice show ${storage_name} | grep smis_provider_ip | awk '{print $2}' | cut -d '"' -f2`
    	storage_port=`storagedevice show ${storage_name} | grep smis_port_number | awk '{print $2}' | cut -d ',' -f1`
    	storage_user=`storagedevice show ${storage_name} | grep smis_user_name | awk '{print $2}' | cut -d '"' -f2`
    	##update tools.yml file with the array details
    	printf 'array:\n  %s:\n  - ip: %s:%s\n    id: %s\n    username: %s\n    password: %s\n    version: %s' "$storage_type" "$storage_ip" "$storage_port" "$SERIAL_NUMBER" "$storage_user" "$storage_password" "$storage_version" >> $tools_file
    fi
}

setup_provider() {
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    tools_file="preExistingConfig.properties"
    if [ -f "$tools_file" ]; then
	echo "stale $tools_file found. Deleting it."
	rm $tools_file
    fi

    if [ "${storage_password}" = "" ]; then
	echo "storage_password is not set.  Cannot make a valid ${tools_file} file without a storage_password"
	exit;
    fi

    sstype=${SS}
    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" -o "${SS}" = "srdf" ]; then
	sstype="vmax"
    fi

    # create the yml file to be used for array tooling
    touch $tools_file
    storage_type=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $1}'`
    storage_name=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $2}' | head -n 1`
    storage_version=`storagedevice show ${storage_name} | grep firmware_version | awk '{print $2}' | cut -d '"' -f2`
    storage_ip=`storagedevice show ${storage_name} | grep smis_provider_ip | awk '{print $2}' | cut -d '"' -f2`
    storage_port=`storagedevice show ${storage_name} | grep smis_port_number | awk '{print $2}' | cut -d ',' -f1`
    storage_user=`storagedevice show ${storage_name} | grep smis_user_name | awk '{print $2}' | cut -d '"' -f2`
    ##update provider properties file with the array details
    printf 'provider.ip=%s\nprovider.cisco_ip=1.1.1.1\nprovider.username=%s\nprovider.password=%s\nprovider.port=%s\n' "$storage_ip" "$storage_user" "$storage_password" "$storage_port" >> $tools_file
}

login() {
    echo "Tenant is ${TENANT}";
    security login $SYSADMIN $SYSADMIN_PASSWORD
}

# call this to generate a random WWN for exports.
# VNX (especially) does not like multiple initiator registrations on the same
# WWN to different hostnames, which only our test scripts tend to do.
# Give two arguments if you want the first and last pair to be something specific
# to help with debugging/diagnostics
randwwn() {
   if [ "$1" = "" ]
   then
      PRE="87"
   else
      PRE=$1
   fi

   if [ "$2" = "" ]
   then
      POST="32"
   else
      POST=$2
   fi

   I2=`date +"%N" | cut -c5-6`
   I3=`date +"%N" | cut -c5-6`
   I4=`date +"%N" | cut -c5-6`
   I5=`date +"%N" | cut -c5-6`
   I6=`date +"%N" | cut -c5-6`
   I7=`date +"%N" | cut -c5-6`

   echo "${PRE}:${I2}:${I3}:${I4}:${I5}:${I6}:${I7}:${POST}"   
}

getwwn() {
    if [ "$SS" = "unity" ]; then
        PWWN=`randwwn`
        NWWN=`randwwn`
        PWWN=${NWWN}:${PWWN}
    else
        PWWN=`randwwn | sed 's/://g'`
    fi

    echo $PWWN
}

cleanup() {
    if [ "${DO_CLEANUP}" = "1" ]; then
	for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
	do
	  runcmd export_group delete ${id} > /dev/null
	  echo "Deleted export group: ${id}"
	done
	runcmd volume delete --project $PROJECT --wait
    fi
    echo There were $VERIFY_COUNT export verifications
    echo There were $VERIFY_FAIL_COUNT export verification failures
}

# Clean up any exports or volumes from previous runs, but not the volumes you need to run tests
cleanup_previous_run_artifacts() {
    project list --tenant emcworld  > /dev/null 2> /dev/null
    if [ $? -eq 1 ]; then
	return;
    fi

    export_group list $PROJECT | grep YES > /dev/null 2> /dev/null
    if [ $? -eq 0 ]; then
	for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
	do
	    echo "Deleting old export group: ${id}"
	    runcmd export_group delete ${id} > /dev/null
	done
    fi

    volume list ${PROJECT} | grep YES | grep "hijack\|fake" > /dev/null 2> /dev/null
    if [ $? -eq 0 ]; then
	for id in `volume list ${PROJECT} | grep YES | grep hijack | awk '{print $7}'`
	do
	    echo "Deleting old volume: ${id}"
	    runcmd volume delete ${id} --wait > /dev/null
	done
    fi
}

verify_mount_point() {
    ostype=$1
    mountpoint=$2
    size=$3
    wwn=$4
    
    runcmd hosthelper.sh verify_mount_point $ostype $mountpoint $size $wwn $*
    return_status=$?
    return $return_status
}

verify_datastore() {
    runcmd vcenterhelper.sh verify_datastore $*
    return_status=$?
    return $return_status
}

verify_datastore_capacity() {
    runcmd vcenterhelper.sh verify_datastore_capacity $*
    return_status=$?
    return $return_status
}

verify_datastore_lun_count() {
    datacenter=$1
    datastore=$2
    host=$3
    count=$4

    runcmd vcenterhelper.sh verify_datastore_lun_count $datacenter $datastore $host $count
    return_status=$?
    return $return_status
}

add_tag() {
    resource_type=$1
    resource_id=$2
    tag=$3
    runcmd tag --resource_type $resource_type --id $resource_id $tag
    return $?
}

remove_tag() {
    resource_type=$1
    resource_id=$2
    tag=$3
    runcmd tag --remove --resource_type $resource_type --id $resource_id $tag
    return $?
}

# Print functions in this file that start with test_
print_test_names() {
    for test_name in `grep -E "^test_.+\(\)" $0 | sed -E "s/\(.*$//"`
    do
      echo "  $test_name"
    done
}

set_artificial_failure() {
    if [ "$1" = "none" ]; then
        # Reset the failure injection occurence counter
        run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure_counter_reset "true"
    else
        # Start incrementing the failure occurence counter for this injection point
        run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure_counter_reset "false"                                                                                                                                             
    fi
        
    run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure "$1"
}

snap_db() {
    slot=$1
    column_families=$2
    escape_seq=$3

    base_filter="| sed -r '/6[0]{29}[A-Z0-9]{2}=/s/\=-?[0-9][0-9]?[0-9]?/=XX/g' | sed -r 's/vdc1=-?[0-9][0-9]?[0-9]?/vdc1=XX/g' | grep -v \"status = OpStatusMap\" | grep -v \"lastDiscoveryRunTime = \" | grep -v \"allocatedCapacity = \" | grep -v \"capacity = \" | grep -v \"provisionedCapacity = \" | grep -v \"successDiscoveryTime = \" | grep -v \"storageDevice = URI: null\" | grep -v \"StringSet \[\]\" | grep -v \"varray = URI: null\" | grep -v \"Description:\" | grep -v \"Additional\" | grep -v -e '^$' | grep -v \"Rollback encountered problems\" | grep -v \"clustername = null\" | grep -v \"cluster = URI: null\" | grep -v \"vcenterDataCenter = \" | grep -v \"compositionType = \" | grep -v \"metaMemberCount = \" | grep -v \"metaMemberSize = \" $escape_seq"
    
    secho "snapping column families [set $slot]: ${column_families}"

    IFS=' ' read -ra cfs_array <<< "$column_families"
    for cf in "${cfs_array[@]}"; do
       execute="/opt/storageos/bin/dbutils list -sortByURI ${cf} $base_filter > results/${item}/${cf}-${slot}.txt"
       eval $execute
    done
}
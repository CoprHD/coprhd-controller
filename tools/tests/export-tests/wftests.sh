#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
#
# Rollback and WF Validation Tests
# ==========================
#
# This test suite attempts to find inconsistencies in the ViPR database after certain operations are performed
# that would necessitate manual cleanup and/or cause future operations to fail.
#
#
# Tooling
# ======
#  This test suite requires the ability to scan certain objects in the database and be able to verify those objects
# revert to their expected/original state after a specific operation completes.  A failure is when the DB is in an
# inconsistent state.
#
#  This test suite requires the ability to cause failures in workflows at certain locations and to compare the
# database after rollback, or after failure.  (We expect there to be changes in the database after failure and 
# before rollback.
#
#  The product itself will attempt to get the database back to a known state, regardless of its ability to clean-up
# the array/switch/RP resources so the operation can be tried again.  At worst, the user would need to clean up
# the array resource before retrying, but that is an easier service operation than cleaning the ViPR database.
#
# set -x

Usage()
{
    echo 'Usage: wftests.sh <sanity conf file path> [setuphw|setupsim|delete] [vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity]  [test1 test2 ...]'
    echo ' [setuphw|setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    echo ' [delete]: deletes export and volume resources on the array'
    exit 2
}

# Extra debug output
DUTEST_DEBUG=${DUTEST_DEBUG:-0}

SANITY_CONFIG_FILE=""
: ${USE_CLUSTERED_HOSTS=1}

# ============================================================
# Check if there is a sanity configuration file specified
# on the command line. In, which case, we should use that
# ============================================================
if [ "$1"x != "x" ]; then
   if [ -f "$1" ]; then
      SANITY_CONFIG_FILE=$1
      echo Using sanity configuration file $SANITY_CONFIG_FILE
      shift
      source $SANITY_CONFIG_FILE
   fi
fi

# Determine the mask name, given the storage system and other info
get_masking_view_name() {
    no_host_name=0
    export_name=$1
    host_name=$2

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

    if [ "$SS" = "vmax2" -o "$SS" = "vmax3" -o "$SS" = "vnx" ]; then
	masking_view_name="${cluster_name_if_any}${host_name}_${SERIAL_NUMBER: -3}"
    elif [ "$SS" = "xio" ]; then
        masking_view_name=$host_name
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

VERIFY_EXPORT_COUNT=0
VERIFY_EXPORT_FAIL_COUNT=0
verify_export() {
    export_name=$1
    host_name=$2
    shift 2

    masking_view_name=`get_masking_view_name ${export_name} ${host_name}`

    arrayhelper verify_export ${SERIAL_NUMBER} ${masking_view_name} $*
    if [ $? -ne "0" ]; then
	if [ -f ${CMD_OUTPUT} ]; then
	    cat ${CMD_OUTPUT}
	fi
	echo There was a failure
	VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
	cleanup
	finish
    fi
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
}

# Extra gut-check.  Make sure we didn't just grab a different mask off the array.
# Run this during test_0 to make sure we're not getting off on the wrong foot.
# Even better would be to delete that mask, export_group, and try again.
verify_maskname() {
    export_name=$1
    host_name=$2

    masking_view_name=`get_masking_view_name ${export_name} ${host_name}`

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
	arrayhelper_volume_mask_operation $operation $serial_number $device_id $masking_view_name
	;;
    remove_volume_from_mask)
	device_id=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_volume_mask_operation $operation $serial_number $device_id $masking_view_name
	;;
    add_initiator_to_mask)
	pwwn=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_initiator_mask_operation $operation $serial_number $pwwn $masking_view_name
	;;
    remove_initiator_from_mask)
	pwwn=$3
        pattern=$4
	masking_view_name=`get_masking_view_name no-op ${pattern}`
	arrayhelper_initiator_mask_operation $operation $serial_number $pwwn $masking_view_name
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
	arrayhelper_verify_export $operation $serial_number $masking_view_name $*
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
    default)
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
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $device_id $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $serial_number $array_ip $device_id $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $device_id $pattern
	 ;;
    vplex)
         runcmd vplexhelper.sh $operation $device_id $pattern
	 ;;
    default)
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
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $pwwn $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $serial_number $array_ip $pwwn $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $pwwn $pattern
	 ;;
    vplex)
         runcmd vplexhelper.sh $operation $pwwn $pattern
	 ;;
    default)
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
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $device_id
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $device_id
	 ;;
    xio)
         runcmd xiohelper.sh $operation $device_id
	 ;;
    vplex)
         runcmd vplexhelper.sh $operation $device_id
	 ;;
    default)
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
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $masking_view_name $sg_name $ig_name
	 ;;
    default)
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
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $pattern
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $pattern
	 ;;
    xio)
         runcmd xiohelper.sh $operation $pattern
	 ;;
    vplex)
         runcmd vplexhelper.sh $operation $pattern
	 ;;
    default)
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

    case $SS in
    vmax2|vmax3)
         runcmd symhelper.sh $operation $serial_number $masking_view_name $*
	 ;;
    vnx)
         runcmd navihelper.sh $operation $array_ip $macaddr $masking_view_name $*
	 ;;
    xio)
         runcmd xiohelper.sh $operation $masking_view_name $*
	 ;;
    vplex)
         runcmd vplexhelper.sh $operation $masking_view_name $*
	 ;;
    default)
         echo -e "\e[91mERROR\e[0m: Invalid platform specified in storage_type: $storage_type"
	 cleanup
	 finish -1
	 ;;
    esac
}

# We need a way to get all of the zones that could be associated with this host
# that makes sense for the beginning of a test case.
verify_no_zones() {
    fabricid=$1
    host=$2

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    echo "=== zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host}"
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

# Verify the zones exist (or don't exist)
verify_zones() {
    fabricid=$1
    check=$2

    if [ "${ZONE_CHECK}" = "0" ]; then
	return
    fi

    for zone in ${zones}
    do
      echo "=== zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name ${zone}"
      zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name ${zone} | grep ${zone} > /dev/null
      if [ $? -ne 0 -a "${check}" = "exists" ]; then
	  echo -e "\e[91mERROR\e[0m: Expected to find zone ${zone}, but did not."
      elif [ $? -eq 0 -a "${check}" = "gone" ]; then
	  echo -e "\e[91mERROR\e[0m: Expected to not find zone ${zone}, but it is there."
      fi
    done
}

# Cleans zones and zone referencese ($1=fabricId, $2=host)
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

    echo "=== zone list $BROCADE_NETWORK --fabricid ${fabricid} --zone_name filter:${host}"
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

dbupdate() {
    runcmd dbupdate.sh $*
}

finish() {
    code=${1}
    if [ $VERIFY_EXPORT_FAIL_COUNT -ne 0 ]; then
        exit $VERIFY_EXPORT_FAIL_COUNT
    fi
    if [ "${code}" != "" ]; then
	exit ${code}
    fi
    exit 0
}

# The token file name will have a suffix which is this shell's PID
# It will allow to run the sanity in parallel
export BOURNE_TOKEN_FILE="/tmp/token$$.txt"
BOURNE_SAVED_TOKEN_FILE="/tmp/token_saved.txt"

PATH=$(dirname $0):$(dirname $0)/..:/bin:/usr/bin

BOURNE_IPS=${1:-$BOURNE_IPADDR}
IFS=',' read -ra BOURNE_IP_ARRAY <<< "$BOURNE_IPS"
BOURNE_IP=${BOURNE_IP_ARRAY[0]}
IP_INDEX=0

macaddr=`/sbin/ifconfig eth0 | /usr/bin/awk '/HWaddr/ { print $5 }'`
if [ "$macaddr" = "" ] ; then
    macaddr=`/sbin/ifconfig en0 | /usr/bin/awk '/ether/ { print $2 }'`
fi
seed=`date "+%H%M%S%N"`
ipaddr=`/sbin/ifconfig eth0 | /usr/bin/perl -nle 'print $1 if(m#inet addr:(.*?)\s+#);' | tr '.' '-'`
export BOURNE_API_SYNC_TIMEOUT=700
BOURNE_IP=${BOURNE_IP:-"localhost"}

#
# Zone configuration
#
NH=nh
NH2=nh2
# By default, we'll use this network.  Some arrays may use another and redefine it in their setup
FC_ZONE_A=FABRIC_losam082-fabric

if [ "$BOURNE_IP" = "localhost" ]; then
    SHORTENED_HOST="ip-$ipaddr"
fi
SHORTENED_HOST=${SHORTENED_HOST:=`echo $BOURNE_IP | awk -F. '{ print $1 }'`}
: ${TENANT=emcworld}
: ${PROJECT=project}

#
# cos configuration
#
VPOOL_BASE=vpool
VPOOL_FAST=${VPOOL_BASE}-fast

BASENUM=${BASENUM:=$RANDOM}
VOLNAME=dutestexp${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
HOST1=host1export${BASENUM}
HOST2=host2export${BASENUM}
CLUSTER=cl${BASENUM}

# Allow for a way to easily use different hardware
if [ -f "./myhardware.conf" ]; then
    echo Using ./myhardware.conf
    source ./myhardware.conf
fi

drawstars() {
    repeatchar=`expr $1 + 2`
    while [ ${repeatchar} -gt 0 ]
    do 
       echo -n "*"
       repeatchar=`expr ${repeatchar} - 1`
    done
    echo "*"
}

echot() {
    numchar=`echo $* | wc -c`
    echo ""
    drawstars $numchar
    echo "* $* *"
    drawstars $numchar
}

# General echo output
secho()
{
    echo -e "*** $*"
}

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}

# A method to run a command that exits on failure.
run() {
    cmd=$*
    echo === $cmd
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
	echo There was a failure
	cleanup
	finish -1
    fi
}

# A method to run a command that continues on failure.
runcmd() {
    cmd=$*
    echo === $cmd
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
	echo There was a failure
	VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
    fi
}

#counterpart for run
#executes a command that is expected to fail
fail(){
    cmd=$*
    echo === $cmd
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
	$cmd &> ${CMD_OUTPUT}
    else
	$cmd 2>&1
    fi

    status=$?
    if [ $status -eq 0 ] ; then
        echo '**********************************************************************'
        echo -e "$cmd succeeded, which \e[91mshould not have happened\e[0m"
	cat ${CMD_OUTPUT}
        echo '**********************************************************************'
	VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
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

    if [ "${storage_password}" = "" ]; then
	echo "storage_password is not set.  Cannot make a valid tools.yml file without a storage_password"
	exit;
    fi

    sstype=${SS:0:3}
    if [ "${SS}" = "xio" ]; then
	sstype="xtremio"
    fi

    # create the yml file to be used for array tooling
    touch $tools_file
    storage_type=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $1}'`
    storage_name=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $2}'`
    storage_version=`storagedevice show ${storage_name} | grep firmware_version | awk '{print $2}' | cut -d '"' -f2`
    storage_ip=`storagedevice show ${storage_name} | grep smis_provider_ip | awk '{print $2}' | cut -d '"' -f2`
    storage_port=`storagedevice show ${storage_name} | grep smis_port_number | awk '{print $2}' | cut -d ',' -f1`
    storage_user=`storagedevice show ${storage_name} | grep smis_user_name | awk '{print $2}' | cut -d '"' -f2`
    ##update tools.yml file with the array details
    printf 'array:\n  %s:\n  - ip: %s:%s\n    id: %s\n    username: %s\n    password: %s\n    version: %s' "$storage_type" "$storage_ip" "$storage_port" "$SERIAL_NUMBER" "$storage_user" "$storage_password" "$storage_version" >> $tools_file
}

setup_provider() {
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    tools_file="${DIR}/preExistingConfig.properties"
    if [ -f "$tools_file" ]; then
	echo "stale $tools_file found. Deleting it."
	rm $tools_file
    fi

    if [ "${storage_password}" = "" ]; then
	echo "storage_password is not set.  Cannot make a valid ${toos_file} file without a storage_password"
	exit;
    fi

    sstype=${SS}
    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]; then
	sstype="vmax"
    fi

    # create the yml file to be used for array tooling
    touch $tools_file
    storage_type=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $1}'`
    storage_name=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $2}'`
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

prerun_setup() {
    # Convenience, clean up known artifacts
    cleanup_previous_run_artifacts

    # Check if we have the most recent version of preExistingConfig.jar
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    TOOLS_MD5="${DIR}/preExistingConfig.md5"
    TOOLS_JAR="${DIR}/preExistingConfig.jar"
    TMP_MD5=/tmp/preExistingConfig.md5
    MD5=`cat ${TOOLS_MD5} | awk '{print $1}'`
    echo "${MD5} ${TOOLS_JAR}" > ${TMP_MD5}
    if [ -f "${TOOLS_JAR}" ]; then
       md5sum -c ${TMP_MD5}
       [ $? -ne 0 ] && echo "WARNING: There may be a newer version of ${TOOLS_JAR} available."
    fi

    project list --tenant emcworld > /dev/null 2> /dev/null
    if [ $? -eq 0 ]; then
	echo "Seeing if there's an existing base of volumes"
	BASENUM=`volume list ${PROJECT} | grep YES | head -1 | awk '{print $1}' | awk -Fp '{print $2}' | awk -F- '{print $1}'`
    else
	BASENUM=""
    fi

    if [ "${BASENUM}" != "" ]
    then
       echo "Volumes were found!  Base number is: ${BASENUM}"
       VOLNAME=dutestexp${BASENUM}
       EXPORT_GROUP_NAME=export${BASENUM}
       HOST1=host1export${BASENUM}
       HOST2=host2export${BASENUM}
       CLUSTER=cl${BASENUM}

       sstype=${SS:0:3}
       if [ "${SS}" = "xio" ]; then
	   sstype="xtremio"
       fi

       # figure out what type of array we're running against
       storage_type=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $1}'`
       echo "Found storage type is: $storage_type"
       SERIAL_NUMBER=`storagedevice list | grep COMPLETE | grep ${sstype} | awk '{print $2}' | awk -F+ '{print $2}'`
       echo "Serial number is: $SERIAL_NUMBER"
       if [ "${storage_type}" = "xtremio" ]
       then
	    storage_password=${XTREMIO_3X_PASSWD}
       fi

    fi

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ];
    then
	ZONE_CHECK=0
	echo "Shutting off zone check for simulator environment"
    fi

    if [ "${SS}" = "vnx" ]
    then
	array_ip=${VNXB_IP}
	if [ "${SIM}" = "1" ]; then
	    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
	else
	    FC_ZONE_A=FABRIC_vplex154nbr2
	fi
    elif [ "${SS}" = "vmax2" ]
    then
        FC_ZONE_A=FABRIC_VPlex_LGL6220_FID_30-10:00:00:27:f8:58:f6:c1
    fi

    # All export operations orchestration go through the same entry-points
    exportCreateOrchStep=ExportWorkflowEntryPoints.exportGroupCreate
    exportAddVolumesOrchStep=ExportWorkflowEntryPoints.exportAddVolumes
    exportRemoveVolumesOrchStep=ExportWorkflowEntryPoints.exportRemoveVolumes
    exportAddInitiatorsOrchStep=ExportWorkflowEntryPoints.exportAddInitiators
    exportRemoveInitiatorsOrchStep=ExportWorkflowEntryPoints.exportRemoveInitiators
    exportDeleteOrchStep=ExportWorkflowEntryPoints.exportGroupDelete

    # The actual steps that the orchestration generates varies depending on the device type
    if [ "${SS}" != "vplex" ]; then
	exportCreateDeviceStep=MaskingWorkflowEntryPoints.doExportGroupCreate
	exportAddVolumesDeviceStep=MaskingWorkflowEntryPoints.doExportGroupAddVolumes
	exportRemoveVolumesDeviceStep=MaskingWorkflowEntryPoints.doExportGroupRemoveVolumes
	exportAddInitiatorsDeviceStep=MaskingWorkflowEntryPoints.doExportGroupAddInitiators
	exportRemoveInitiatorsDeviceStep=MaskingWorkflowEntryPoints.doExportGroupRemoveInitiators
	exportDeleteDeviceStep=MaskingWorkflowEntryPoints.doExportGroupDelete
    else
	# VPLEX-specific entrypoints
	exportCreateDeviceStep=VPlexDeviceController.createStorageView
	exportAddVolumesDeviceStep=ExportWorkflowEntryPoints.exportAddVolumes
	exportRemoveVolumesDeviceStep=ExportWorkflowEntryPoints.exportRemoveVolumes
	exportAddInitiatorsDeviceStep=ExportWorkflowEntryPoints.exportAddInitiators
	exportRemoveInitiatorsDeviceStep=ExportWorkflowEntryPoints.exportRemoveInitiators
	exportDeleteDeviceStep=VPlexDeviceController.deleteStorageView
    fi
}

# get the device ID of a created volume
get_device_id() {
    label=$1

    if [ "$SS" = "xio" -o "$SS" = "vplex" ]; then
        volume show ${label} | grep device_label | awk '{print $2}' | cut -d '"' -f2
    else
	volume show ${label} | grep native_id | awk '{print $2}' | cut -c2-6
    fi
}

# Reset all of the system properties so settings are back to normal
reset_system_props() {
    set_suspend_on_class_method "none"
    set_suspend_on_error false
    set_artificial_failure "none"
    set_validation_check true
    set_validation_refresh true
}

# Clean zones from previous tests, verify no zones are on the switch
prerun_tests() {
    clean_zones ${FC_ZONE_A:7} ${HOST1}
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

vnx_sim_setup() {
    VNX_PROVIDER_NAME=VNX-PROVIDER-SIM
    VNX_SMIS_IP=$SIMULATOR_SMIS_IP
    VNX_SMIS_PORT=5988
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VNX_SMIS_SSL=false
    VNXB_NATIVEGUID=$SIMULATOR_VNX_NATIVEGUID
}

vnx_setup() {
    SMISPASS=0
    # do this only once
    echo "Setting up SMIS for VNX"
    storage_password=$SMIS_PASSWD

    if [ "${SIM}" = "1" ]; then
	vnx_sim_setup
    fi

    run smisprovider create ${VNX_PROVIDER_NAME} ${VNX_SMIS_IP} ${VNX_SMIS_PORT} ${SMIS_USER} "$SMIS_PASSWD" ${VNX_SMIS_SSL}
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VNXB_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VNXB_NATIVEGUID} | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    do
	run storagedevice deregister CLARIION+${id}
	run storagedevice delete CLARIION+${id}
    done

    run storagepool update $VNXB_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VNXB_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VNXB_NATIVEGUID --nhadd $NH --type block
    # Can no longer do this since network discovers where the ports are
    if [ "${SIM}" = "1" ]; then
	run storageport update $VNXB_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
    fi
    storageport $VNXB_NATIVEGUID list --v | grep FABRIC

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${VNXB_NATIVEGUID}
}

unity_setup()
{
    discoveredsystem create $UNITY_DEV unity $UNITY_IP $UNITY_PORT $UNITY_USER $UNITY_PW --serialno=$UNITY_SN
    storagedevice list

    storagepool update $UNITY_NATIVEGUID --type block --volume_type THIN_AND_THICK
    run transportzone add ${SRDF_VMAXA_VSAN} $UNITY_INIT_PWWN1
    run transportzone add ${SRDF_VMAXA_VSAN} $UNITY_INIT_PWWN2
    run storagedevice discover_all

    setup_varray

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${UNITY_NATIVEGUID}
}

vmax3_sim_setup() {
    VMAX_PROVIDER_NAME=VMAX2-PROVIDER-SIM
    VMAX_SMIS_IP=$SIMULATOR_SMIS_IP
    VMAX_SMIS_PORT=5889
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VMAX_SMIS_SSL=true
    VMAX_NATIVEGUID=$SIMULATOR_VMAX_NATIVEGUID
    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
}

vmax2_setup() {
    SMISPASS=0

    if [ "${SIM}" = "1" ]; then
	vmax3_sim_setup
    fi
 
    # do this only once
    echo "Setting up SMIS for VMAX2"
    storage_password=$SMIS_PASSWD

    run smisprovider create $VMAX_PROVIDER_NAME $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VMAX_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VMAX_NATIVEGUID} | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    do
	run storagedevice deregister SYMMETRIX+${id}
	run storagedevice delete SYMMETRIX+${id}
    done

    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block
    if [ "${SIM}" = "1" ]; then
       run storageport update $VMAX_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
    fi

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
}

vmax3_sim_setup() {
    VMAX_PROVIDER_NAME=VMAX3-PROVIDER-SIM
    VMAX_SMIS_IP=$SIMULATOR_SMIS_IP
    VMAX_SMIS_PORT=7009
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VMAX_SMIS_SSL=true
    VMAX_NATIVEGUID=$SIMULATOR_VMAX_NATIVEGUID
    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
}

vmax3_setup() {
    SMISPASS=0

    if [ "${SIM}" = "1" ]; then
	vmax3_sim_setup
    fi
 
   # do this only once
    echo "Setting up SMIS for VMAX3"
    storage_password=$SMIS_PASSWD

    run smisprovider create $VMAX_PROVIDER_NAME $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VMAX_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VMAX_NATIVEGUID} | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    do
	run storagedevice deregister SYMMETRIX+${id}
	run storagedevice delete SYMMETRIX+${id}
    done

    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block
    run storageport update $VMAX_NATIVEGUID FC --tzone $NH/$FC_ZONE_A

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
}

vplex_sim_setup() {
    secho "Setting up VPLEX environment connected to simulators on: ${VPLEX_SIM_IP}"

    # Discover the Brocade SAN switch.
    secho "Configuring MDS/Cisco Simulator using SSH on: $VPLEX_SIM_MDS_IP"
    FABRIC_SIMULATOR=fabric-sim
    run networksystem create $FABRIC_SIMULATOR  mds --devip $VPLEX_SIM_MDS_IP --devport 22 --username $VPLEX_SIM_MDS_USER --password $VPLEX_SIM_MDS_PW

    # Discover the storage systems 
    secho "Discovering back-end storage arrays using ECOM/SMIS simulator on: $VPLEX_SIM_SMIS_IP..."
    run smisprovider create $VPLEX_SIM_SMIS_DEV_NAME $VPLEX_SIM_SMIS_IP $VPLEX_VMAX_SMIS_SIM_PORT $VPLEX_SIM_SMIS_USER "$VPLEX_SIM_SMIS_PASSWD" false

    secho "Discovering VPLEX using simulator on: ${VPLEX_SIM_IP}..."
    run storageprovider create $VPLEX_SIM_DEV_NAME $VPLEX_SIM_IP 443 $VPLEX_SIM_USER "$VPLEX_SIM_PASSWD" vplex
    run storagedevice discover_all

    VPLEX_GUID=$VPLEX_SIM_VPLEX_GUID
    CLUSTER1NET_NAME=$CLUSTER1NET_SIM_NAME
    CLUSTER2NET_NAME=$CLUSTER2NET_SIM_NAME

    # Setup the varrays. $NH contains VPLEX cluster-1 and $NH2 contains VPLEX cluster-2.
    secho "Setting up the virtual arrays nh and nh2"
    VPLEX_VARRAY1=$NH
    VPLEX_VARRAY2=$NH2
    FC_ZONE_A=${CLUSTER1NET_NAME}
    FC_ZONE_B=${CLUSTER2NET_NAME}
    run neighborhood create $VPLEX_VARRAY1
    run transportzone assign $FC_ZONE_A $VPLEX_VARRAY1
    run transportzone create $FC_ZONE_A $VPLEX_VARRAY1 --type FC
    secho "Setting up the VPLEX cluster-2 virtual array $VPLEX_VARRAY2"
    run neighborhood create $VPLEX_VARRAY2
    run transportzone assign $FC_ZONE_B $VPLEX_VARRAY2
    run transportzone create $FC_ZONE_B $VPLEX_VARRAY2 --type FC
    # Assign both networks to both transport zones
    run transportzone assign $FC_ZONE_A $VPLEX_VARRAY2
    run transportzone assign $FC_ZONE_B $VPLEX_VARRAY1

    secho "Setting up the VPLEX cluster-1 virtual array $VPLEX_VARRAY1"
    run storageport update $VPLEX_GUID FC --group director-1-1-A --addvarrays $NH
    run storageport update $VPLEX_GUID FC --group director-1-1-B --addvarrays $NH
    run storageport update $VPLEX_GUID FC --group director-1-2-A --addvarrays $VPLEX_VARRAY1
    run storageport update $VPLEX_GUID FC --group director-1-2-B --addvarrays $VPLEX_VARRAY1
    # The arrays are assigned to individual varrays as well.
    run storageport update $VPLEX_SIM_VMAX1_NATIVEGUID FC --addvarrays $NH
    run storageport update $VPLEX_SIM_VMAX2_NATIVEGUID FC --addvarrays $NH
    run storageport update $VPLEX_SIM_VMAX3_NATIVEGUID FC --addvarrays $NH

    run storageport update $VPLEX_GUID FC --group director-2-1-A --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-1-B --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-2-A --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-2-B --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_SIM_VMAX4_NATIVEGUID FC --addvarrays $NH2
    run storageport update $VPLEX_SIM_VMAX5_NATIVEGUID FC --addvarrays $NH2
    #run storageport update $VPLEX_VMAX_NATIVEGUID FC --addvarrays $VPLEX_VARRAY2

    common_setup

    SERIAL_NUMBER=$VPLEX_GUID

    case "$VPLEX_MODE" in 
        local)
            secho "Setting up the virtual pool for local VPLEX provisioning"
            run cos create block $VPOOL_BASE true                            \
                             --description 'vpool-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX1_NATIVEGUID
            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX2_NATIVEGUID
            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX3_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for local VPLEX provisioning and migration (source)"
            run cos create block ${VPOOL_BASE}_migration_src false                            \
                             --description 'vpool-for-vplex-local-volumes-src'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block ${VPOOL_BASE}_migration_src --storage $VPLEX_SIM_VMAX1_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for local VPLEX provisioning and migration (target)"
            run cos create block ${VPOOL_BASE}_migration_tgt false                           \
                             --description 'vpool-for-vplex-local-volumes-tgt'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block ${VPOOL_BASE}_migration_tgt --storage $VPLEX_SIM_VMAX2_NATIVEGUID
        ;;
        distributed)
            secho "Setting up the virtual pool for distributed VPLEX provisioning"
            run cos create block $VPOOL_BASE true                                \
                             --description 'vpool-for-vplex-distributed-volumes'    \
                             --protocols FC                                         \
                             --numpaths 2                                           \
                             --provisionType 'Thin'                                 \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true

            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX4_NATIVEGUID
            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX5_NATIVEGUID
        ;;
        *)
            secho "Invalid VPLEX_MODE: $VPLEX_MODE (should be 'local' or 'distributed')"
            Usage
        ;;
    esac
}

vplex_setup() {
    storage_password=${VPLEX_PASSWD}
    if [ "${SIM}" = "1" ]; then
	vplex_sim_setup
	return
    fi

    isNetworkDiscovered=$(networksystem list | grep $BROCADE_NETWORK | wc -l)
    if [ $isNetworkDiscovered -eq 0 ]; then
        secho "Discovering Brocade SAN Switch ..."
        run networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false
        sleep 30
    fi

    secho "Discovering VPLEX Storage Assets"
    storageprovider show $VPLEX_DEV_NAME &> /dev/null && return $?
    run smisprovider create $VPLEX_VMAX_SMIS_DEV_NAME $VPLEX_VMAX_SMIS_IP 5989 $VPLEX_SMIS_USER "$VPLEX_SMIS_PASSWD" true
    run smisprovider create $VPLEX_VNX1_SMIS_DEV_NAME $VPLEX_VNX1_SMIS_IP 5989 $VPLEX_SMIS_USER "$VPLEX_SMIS_PASSWD" true
    run smisprovider create $VPLEX_VNX2_SMIS_DEV_NAME $VPLEX_VNX2_SMIS_IP 5989 $VPLEX_SMIS_USER "$VPLEX_SMIS_PASSWD" true
    run storageprovider create $VPLEX_DEV_NAME $VPLEX_IP 443 $VPLEX_USER "$VPLEX_PASSWD" vplex
    run storagedevice discover_all

    VPLEX_VARRAY1=$NH
    FC_ZONE_A=${CLUSTER1NET_NAME}
    secho "Setting up the VPLEX cluster-1 virtual array $VPLEX_VARRAY1"
    run neighborhood create $VPLEX_VARRAY1
    run transportzone assign $FC_ZONE_A $VPLEX_VARRAY1
    run transportzone create $FC_ZONE_A $VPLEX_VARRAY1 --type FC
    run storageport update $VPLEX_GUID FC --group director-1-1-A --addvarrays $VPLEX_VARRAY1
    run storageport update $VPLEX_GUID FC --group director-1-1-B --addvarrays $VPLEX_VARRAY1
    run storageport update $VPLEX_VNX1_NATIVEGUID FC --addvarrays $VPLEX_VARRAY1
    run storageport update $VPLEX_VNX2_NATIVEGUID FC --addvarrays $VPLEX_VARRAY1
    
    VPLEX_VARRAY2=$NH2
    FC_ZONE_B=${CLUSTER2NET_NAME}
    secho "Setting up the VPLEX cluster-2 virtual array $VPLEX_VARRAY2"
    run neighborhood create $VPLEX_VARRAY2
    run transportzone assign $FC_ZONE_B $VPLEX_VARRAY2
    run transportzone create $FC_ZONE_B $VPLEX_VARRAY2 --type FC
    run storageport update $VPLEX_GUID FC --group director-2-1-A --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-1-B --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_VMAX_NATIVEGUID FC --addvarrays $VPLEX_VARRAY2
    
    common_setup

    SERIAL_NUMBER=$VPLEX_GUID

    case "$VPLEX_MODE" in 
        local)
            secho "Setting up the virtual pool for local VPLEX provisioning"
            run cos create block $VPOOL_BASE true                            \
                             --description 'vpool-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_BASE --storage $VPLEX_VNX1_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for local VPLEX provisioning and migration (source)"
            run cos create block ${VPOOL_BASE}_migration_src false                            \
                             --description 'vpool-for-vplex-local-volumes-src'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block ${VPOOL_BASE}_migration_src --storage $VPLEX_VNX1_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for local VPLEX provisioning and migration (target)"
            run cos create block ${VPOOL_BASE}_migration_tgt false                           \
                             --description 'vpool-for-vplex-local-volumes-tgt'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block ${VPOOL_BASE}_migration_tgt --storage $VPLEX_VNX2_NATIVEGUID
        ;;
        distributed)
            secho "Setting up the virtual pool for distributed VPLEX provisioning"
            run cos create block $VPOOL_BASE true                                \
                             --description 'vpool-for-vplex-distributed-volumes'    \
                             --protocols FC                                         \
                             --numpaths 2                                           \
                             --provisionType 'Thin'                                 \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true

	    # having issues predicting the storage that gets used, so commenting this out for now.
            #run cos update block $VPOOL_BASE --storage $VPLEX_VNX1_NATIVEGUID
            run cos update block $VPOOL_BASE --storage $VPLEX_VMAX_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for distributed VPLEX provisioning and migration (source)"
            run cos create block ${VPOOL_BASE}_migration_src false                            \
                             --description 'vpool-for-vplex-distributed-volumes-src'    \
                             --protocols FC                                         \
                             --numpaths 2                                           \
                             --provisionType 'Thin'                                 \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true

            run cos update block ${VPOOL_BASE}_migration_src --storage $VPLEX_VNX1_NATIVEGUID
            #run cos update block ${VPOOL_BASE}_migration_src --storage $VPLEX_VMAX_NATIVEGUID

	    # Migration vpool test
            secho "Setting up the virtual pool for distributed VPLEX provisioning and migration (target)"
            run cos create block ${VPOOL_BASE}_migration_tgt false                            \
                             --description 'vpool-for-vplex-distributed-volumes-tgt'    \
                             --protocols FC                                         \
                             --numpaths 2                                           \
                             --provisionType 'Thin'                                 \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true

            run cos update block ${VPOOL_BASE}_migration_tgt --storage $VPLEX_VNX2_NATIVEGUID
            run cos update block ${VPOOL_BASE}_migration_tgt --storage $VPLEX_VMAX_NATIVEGUID
        ;;
        *)
            secho "Invalid VPLEX_MODE: $VPLEX_MODE (should be 'local' or 'distributed')"
            Usage
        ;;
    esac
}

xio_setup() {
    # do this only once
    echo "Setting up XtremIO"
    XTREMIO_NATIVEGUID=XTREMIO+$XTREMIO_3X_SN
    storage_password=$XTREMIO_3X_PASSWD
    run storageprovider create XIO-PROVIDER $XTREMIO_3X_IP 443 $XTREMIO_3X_USER "$XTREMIO_3X_PASSWD" xtremio
    run storagedevice discover_all --ignore_error

    run storagepool update $XTREMIO_NATIVEGUID --type block --volume_type THIN_ONLY

    setup_varray

    run storagepool update $XTREMIO_NATIVEGUID --nhadd $NH --type block
    if [ "${SIM}" = "1" ]; then
	run storageport update $XTREMIO_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
    fi

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${XTREMIO_NATIVEGUID}
}

host_setup() {
    run project create $PROJECT --tenant $TENANT 
    secho "Project $PROJECT created."
    secho "Setup ACLs on neighborhood for $TENANT"
    run neighborhood allow $NH $TENANT

    run transportzone add $NH/${FC_ZONE_A} $H1PI1
    run transportzone add $NH/${FC_ZONE_A} $H1PI2
    run transportzone add $NH/${FC_ZONE_A} $H2PI1
    run transportzone add $NH/${FC_ZONE_A} $H2PI2
    run transportzone add $NH/${FC_ZONE_A} $H3PI1
    run transportzone add $NH/${FC_ZONE_A} $H3PI2

    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        run cluster create --project ${PROJECT} ${CLUSTER} ${TENANT}

        run hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        run initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        run initiator create ${HOST1} FC $H1PI2 --node $H1NI2

        run hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        run initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        run initiator create ${HOST2} FC $H2PI2 --node $H2NI2
    else
        run hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0
        run initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        run initiator create ${HOST1} FC $H1PI2 --node $H1NI2

        run hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0
        run initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        run initiator create ${HOST2} FC $H2PI2 --node $H2NI2
    fi
}

vcenter_setup() {
    secho "Setup virtual center..."
    runcmd vcenter create vcenter1 ${TENANT} ${VCENTER_SIMULATOR_IP} ${VCENTER_SIMULATOR_PORT} ${VCENTER_SIMULATOR_USERNAME} ${VCENTER_SIMULATOR_PASSWORD}

    # TODO need discovery to run
    sleep 30

    # Add initiators to network (grab these from your simulator's /data/simulators/vmware/add_ports.sh script)
    for i in {40..90..2}
    do 
	FOO=`printf "199900000000%04x\n" $i | awk '{print toupper($0)}' | sed 's/../&:/g;s/:$//'`;
	run transportzone add $NH/${FC_ZONE_A} $FOO
    done
    
    for i in {41..90..2}
    do 
	FOO=`printf "199900000000%04x\n" $i |awk '{print toupper($0)}' | sed 's/../&:/g;s/:$//'`;
	run transportzone add $NH/${FC_ZONE_A} $FOO
    done
}

common_setup() {
    host_setup;
    vcenter_setup;
}

setup_varray() {
    run neighborhood create $NH
    if [ "${SIM}" = "1" ]; then
	run transportzone create $FC_ZONE_A $NH --type FC
    else
	run transportzone assign ${FC_ZONE_A} ${NH}
    fi
}

setup() {
    storage_type=$1;

    syssvc $SANITY_CONFIG_FILE localhost setup
    security add_authn_provider ldap ldap://${LOCAL_LDAP_SERVER_IP} cn=manager,dc=viprsanity,dc=com secret ou=ViPR,dc=viprsanity,dc=com uid=%U CN Local_Ldap_Provider VIPRSANITY.COM ldapViPR* SUBTREE --group_object_classes groupOfNames,groupOfUniqueNames,posixGroup,organizationalRole --group_member_attributes member,uniqueMember,memberUid,roleOccupant
    tenant create $TENANT VIPRSANITY.COM OU VIPRSANITY.COM
    echo "Tenant $TENANT created."
    # Increase allocation percentage
    syssvc $SANITY_CONFIG_FILE localhost set_prop controller_max_thin_pool_subscription_percentage 600
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check true

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]; then
        which symhelper.sh
        if [ $? -ne 0 ]; then
            echo Could not find symhelper.sh path. Please add the directory where the script exists to the path
            locate symhelper.sh
            exit 1
        fi

        if [ ! -f /usr/emc/API/symapi/config/netcnfg ]; then
            echo SYMAPI does not seem to be installed on the system. Please install before running test suite.
            exit 1
        fi

        export SYMCLI_CONNECT=SYMAPI_SERVER
        symapi_entry=`grep SYMAPI_SERVER /usr/emc/API/symapi/config/netcnfg | wc -l`
        if [ $symapi_entry -ne 0 ]; then
            sed -e "/SYMAPI_SERVER/d" -i /usr/emc/API/symapi/config/netcnfg
        fi    

	if [ "${SS}" = "vmax2" ]; then
	    VMAX_SMIS_IP=${VMAX2_DUTEST_SMIS_IP}
	fi

	if [ "${SIM}" != "1" ]; then
            echo "SYMAPI_SERVER - TCPIP  $VMAX_SMIS_IP - 2707 ANY" >> /usr/emc/API/symapi/config/netcnfg
            echo "Added entry into /usr/emc/API/symapi/config/netcnfg"

            echo "Verifying SYMAPI connection to $VMAX_SMIS_IP ..."
            symapi_verify="/opt/emc/SYMCLI/bin/symcfg list"
            echo $symapi_verify
            result=`$symapi_verify`
            if [ $? -ne 0 ]; then
		echo "SYMAPI verification failed: $result"
		echo "Check the setup on $VMAX_SMIS_IP. See if the SYAMPI service is running"
		exit 1
            fi
            echo $result
	fi
    fi

    if [ "${SIM}" != "1" ]; then
	run networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false
    fi

    ${SS}_setup

    run cos allow $VPOOL_BASE block $TENANT
    sleep 30
    reset_system_props
    run volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 2
}

set_suspend_on_error() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop workflow_suspend_on_error $1
}

set_validation_check() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check $1
}

set_validation_refresh() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop refresh_provider_on_validation $1
}

set_suspend_on_class_method() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop workflow_suspend_on_class_method "$1"
}

set_artificial_failure() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure "$1"
}

# Verify no masks
#
# Makes sure there are no masks on the array before running tests
#
verify_nomasks() {
    echo "Verifying no masks exist on storage array"
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}2 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
}

# Export Test 0
#
# Test existing functionality of export and verifies there's good volumes, exports are clean, and tests are ready to run.
#
test_0() {
    echot "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    verify_export ${expname}1 ${HOST1} gone
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    # Paranoia check, verify if maybe we picked up an old mask.  Exit if we did.
    verify_maskname  ${expname}1 ${HOST1}
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

snap_db() {
    slot=$1
    shift
    column_families=$*
    
    secho "snapping column families [set $slot]: ${column_families}"

    for cf in ${column_families}
    do
      # Run list, but normalize the HLU numbers since the simulators can't handle that yet.
      /opt/storageos/bin/dbutils list ${cf} | sed -r 's/vdc1=-?[0-9][0-9]?[0-9]?/vdc1=XX/g' | grep -v "status = OpStatusMap"  > results/${item}/${cf}-${slot}.txt
    done
}      

validate_db() {
    slot_1=${1}
    shift
    slot_2=${1}
    shift
    column_families=$*

    for cf in ${column_families}
    do
      runcmd diff results/${item}/${cf}-${slot_1}.txt results/${item}/${cf}-${slot_2}.txt
    done
}

# Test 1
#
# Test creating a volume and verify the DB is in an expected state after it fails due to injected failure at end of workflow
#
# 1. Save off state of DB (1)
# 2. Perform volume create operation that will fail at the end of execution (and other locations)
# 3. Save off state of DB (2)
# 4. Compare state (1) and (2)
# 5. Retry operation without failure injection
# 6. Delete volume
# 7. Save off state of DB (3)
# 8. Compare state (2) and (3)
#
test_1() {
    echot "Test 1 Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004_final_step_in_workflow_complete:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004_final_step_in_workflow_complete:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                                    failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                                    failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation \
                                    failure_010_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_after_operation"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_createVolume \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015"

    for failure in ${failure_injections}
    do
      secho "Running Test 1 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="Volume ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 ${cfs}

      # Create the volume
      fail volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB

      # Let the async jobs calm down
      sleep 5

      # Perform any DB validation in here
      snap_db 2 ${cfs}

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none
      runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB

      # Remove the volume
      runcmd volume delete ${PROJECT}/${volname} --wait
      
      # Perform any DB validation in here
      snap_db 3 ${cfs}

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}
    done
}

add_host_to_cluster() {
    host=$1
    cluster=$2
    args="addHostTo $cluster $host"
    echo "Adding host $host to $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

remove_host_from_cluster() {
    host=$1
    cluster=$2
    args="removeHostFrom $cluster $host"
    echo "Removing host $host from $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

discover_vcenter() {
    vcenter=$1
    echo "Discovering vCenter $vcenter"
    vcenter discover $vcenter
    sleep 60
}

approve_pending_event() {
    EVENT_ID=$(events list emcworld | grep pending | awk '{print $1}')

    if [ -z "$EVENT_ID" ]
    then
      echo "No event found. Test failure."
      exit;
    fi

    echo "Approving event $EVENT_ID"
    events approve $EVENT_ID
    sleep 30
}

vcenter_event_test() {
    echot "vCenter Event Test: Export to cluster, move host into cluster, rediscover vCenter, approve event"
    expname=${EXPORT_GROUP_NAME}t2
    item=${RANDOM}
    cfs="ExportGroup ExportMask"
    mkdir -p results/${item}

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --clusters "emcworld/cluster-1"

    # move hosts21 into cluster-1
    remove_host_from_cluster "host21" "cluster-2"
    add_host_to_cluster "host21" "cluster-1"
    discover_vcenter "vcenter1"

    approve_pending_event

    # Remove the shared export
    runcmd export_group delete ${PROJECT}/${expname}1

    # Snap the DB again
    snap_db 2 ${cfs}

    # Validate nothing was left behind
    validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone

    # Set vCenter back to previous state
    remove_host_from_cluster "host21" "cluster-1"
    add_host_to_cluster "host21" "cluster-2"
    discover_vcenter "vcenter1"
}


# Basic export test for vcenter cluster
#
#
#
#
test_2() {
    echot "Test 2 Export VCenter Cluster test"
    expname=${EXPORT_GROUP_NAME}t2
    item=${RANDOM}
    cfs="ExportGroup ExportMask"
    mkdir -p results/${item}

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --clusters "emcworld/cluster-1"

    # Remove the shared export
    runcmd export_group delete ${PROJECT}/${expname}1

    # Snap the DB again
    snap_db 2 ${cfs}

    # Validate nothing was left behind
    validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone
}

# Test 3
#
# Test creating multiple volumes where one volume creation fails.
#
# 1. Save off state of DB (1)
# 2. Perform multiple volume create operation that will partially fail, causing a rollback.
# 3. Save off state of DB (2)
# 4. Compare state (1) and (2)
# 5. Retry operation without failure injection
# 6. Delete volumes
# 7. Save off state of DB (3)
# 8. Compare state (2) and (3)
#
test_3() {
    echot "Test 3 Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation&5 \
                                    failure_010_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_after_operation&5"
    fi

    if [ "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyElementFromStoragePool"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015"

    for failure in ${failure_injections}
    do
      secho "Running Test 3 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="Volume ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      project=${PROJECT}-${item}

      # Create the project
      project create ${project} --tenant $TENANT 
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 ${cfs}

      # Create the volume
      fail volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8

      # Let the async jobs calm down
      sleep 5

      # Perform any DB validation in here
      snap_db 2 ${cfs}

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none
      runcmd volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8

      # Remove the volume
      runcmd volume delete --project ${project} --wait

      # Delete the project
      project delete ${project}

      # Perform any DB validation in here
      snap_db 3 ${cfs}

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}
    done
}

# Test 4
#
# Test exporting the volumes while injecting several different failures that cause the job to not get
# done, or not get done effectively enough.
#
# 1. Save off state of DB (1)
# 2. Perform volume export operation that will fail at the end of execution (and other locations)
# 3. Save off state of DB (2)
# 4. Compare state (1) and (2)
# 5. Retry operation without failure injection
# 6. Unexport volume
# 7. Save off state of DB (3)
# 8. Compare state (2) and (3)
#
test_4() {
    echot "Test 4 Begins"
    expname=${EXPORT_GROUP_NAME}t0

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015"

    for failure in ${failure_injections}
    do
      secho "Running Test 1 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the export that doesn't exist
      snap_db 1 ${cfs}

      # Create the export
      fail export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Perform any DB validation in here
      snap_db 2 ${cfs}

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Remove the export
      runcmd export_group delete $PROJECT/${expname}1
      
      # Perform any DB validation in here
      snap_db 3 ${cfs}

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}
    done
}

# Test 5
#
# Test unexporting the volumes while injecting several different failures that cause the job to not get
# done, or not get done effectively enough.
#
# 0. Export a volume to a host
# 1. Save off state of DB (1)
# 2. Perform volume unexport operation that will fail at the end of execution (and other locations)
# 3. Save off state of DB (2)
# 4. Compare state (1) and (2)
# 5. Retry operation without failure injection
# 7. Save off state of DB (3)
# 8. Compare state (2) and (3)
#
test_5() {
    echot "Test 5 Begins"
    expname=${EXPORT_GROUP_NAME}t5

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015"

    for failure in ${failure_injections}
    do
      secho "Running Test 5 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Check the state of the export that it doesn't exist
      snap_db 1 ${cfs}

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Delete the export
      fail export_group delete $PROJECT/${expname}1

      # Don't validate in here.  It's a delete operation and it's allowed to leave things behind, as long as the retry cleans it up.

      # Rerun the command
      set_artificial_failure none
      runcmd export_group delete $PROJECT/${expname}1

      # Perform any DB validation in here
      snap_db 2 ${cfs}

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}
    done
}

# Test 6
#
# Test adding a volumes while injecting several different failures that cause the job to not get
# done, or not get done effectively enough.
#
# 1. Save off state of DB (1)
# 2. Export a volume to a host
# 3. Save off state of DB (2)
# 4. Perform add volume operation that will fail at the end of execution (and other locations)
# 5. Save off state of DB (3)
# 6. Compare state (2) and (3)
# 7. Retry operation without failure injection
# 8. Save off state of DB (4)
# 9. Compare state (1) and (4)
#
test_6() {
    echot "Test 6 Begins"
    expname=${EXPORT_GROUP_NAME}t6

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_004:failure_017"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004"

    for failure in ${failure_injections}
    do
      secho "Running Test 6 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Snap the state before the export group was created
      snap_db 1 ${cfs}

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Snap the DB state with the export group created
      snap_db 2 ${cfs}

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Delete the export
      fail export_group update ${PROJECT}/${expname}1 --addVol ${PROJECT}/${VOLNAME}-2

      # Validate nothing was left behind
      snap_db 3 ${cfs}
      validate_db 2 3 ${cfs}

      # rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --addVol ${PROJECT}/${VOLNAME}-2

      # Delete the export group
      runcmd export_group delete ${PROJECT}/${expname}1

      # Validate the DB is back to its original state
      snap_db 4 ${cfs}
      validate_db 1 4 ${cfs}
    done
}

# Test 7
#
# Test adding an initiator while injecting several different failures that cause the job to not get
# done, or not get done effectively enough.
#
# 1. Save off state of DB (1)
# 2. Export a volume to an initiator
# 3. Save off state of DB (2)
# 4. Perform add initiator operation that will fail at the end of execution (and other locations)
# 5. Save off state of DB (3)
# 6. Compare state (2) and (3)
# 7. Retry operation without failure injection
# 8. Save off state of DB (4)
# 9. Compare state (1) and (4)
#
test_7() {
    echot "Test 7 Begins"
    expname=${EXPORT_GROUP_NAME}t7

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_004:failure_016"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004"

    for failure in ${failure_injections}
    do
      secho "Running Test 7 with failure scenario: ${failure}..."
      item=${RANDOM}
      cfs="ExportGroup ExportMask"
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Check the state of the export that it doesn't exist
      snap_db 1 ${cfs}

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Exclusive --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1}/${H1PI1}"

      # Snsp the DB so we can validate after failures later
      snap_db 2 ${cfs}

      # Strip out colons for array helper command
      h1pi2=`echo ${H1PI2} | sed 's/://g'`

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Attempt to add an initiator
      fail export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}

      # Perform any DB validation in here
      snap_db 3 ${cfs}

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}

      # Rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}

      # Delete the export
      runcmd export_group delete ${PROJECT}/${expname}1

      # Verify the DB is back to the original state
      snap_db 4 ${cfs}
      validate_db 1 4 ${cfs}
    done
}

cleanup() {
    if [ "${docleanup}" = "1" ]; then
	for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
	do
	  runcmd export_group delete ${id} > /dev/null
	  echo "Deleted export group: ${id}"
	done
	runcmd volume delete --project $PROJECT --wait
    fi
    echo There were $VERIFY_EXPORT_FAIL_COUNT failures
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
	    runcmd export_group delete ${id} > /dev/null2> /dev/null
	done
    fi

    volume list ${PROJECT} | grep YES | grep hijack > /dev/null2> /dev/null
    if [ $? -eq 0 ]; then
	for id in `volume list ${PROJECT} | grep YES | grep hijack | awk '{print $7}'`
	do
	    echo "Deleting old volume: ${id}"
	    runcmd volume delete ${id} --wait > /dev/null
	done
    fi
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

# ============================================================
# -    M A I N
# ============================================================

H1PI1=`pwwn 00`
H1NI1=`nwwn 00`
H1PI2=`pwwn 01`
H1NI2=`nwwn 01`

H2PI1=`pwwn 02`
H2NI1=`nwwn 02`
H2PI2=`pwwn 03`
H2NI2=`nwwn 03`

H3PI1=`pwwn 04`
H3NI1=`nwwn 04`
H3PI2=`pwwn 05`
H3NI2=`nwwn 05`

# Delete and setup are optional
if [ "$1" = "delete" ]
then
    login
    cleanup
    finish
fi

setup=0;

SS=${1}
shift

case $SS in
    vmax2|vmax3|vnx|xio|unity)
    ;;
    vplex)
        # set local or distributed mode
        VPLEX_MODE=${1}
        shift
        echo "VPLEX_MODE is $VPLEX_MODE"
        [[ ! "local distributed" =~ "$VPLEX_MODE" ]] && Usage
        export VPLEX_MODE
        SERIAL_NUMBER=$VPLEX_GUID
    ;;
    *)
    Usage
    ;;
esac

# By default, check zones
ZONE_CHECK=${ZONE_CHECK:-1}
if [ "${1}" = "setuphw" -o "${1}" = "setup" -o "${1}" = "-setuphw" -o "${1}" = "-setup" ]
then
    echo "Setting up testing based on real hardware"
    setup=1;
    shift 1;
elif [ "${1}" = "setupsim" -o "${1}" = "-setupsim" ]; then
    if [ "$SS" = "xio" -o "$SS" = "vmax3" -o "$SS" = "vmax2" -o "$SS" = "vnx" -o "$SS" = "vplex" ]; then
	echo "Setting up testing based on simulators"
	SIM=1;
	ZONE_CHECK=0;
	setup=1;
	shift 1;
    else
	echo "Simulator-based testing of this suite is not supported on ${SS} due to lack of CLI/arraytools support to ${SS} provider/simulator"
	exit 1
    fi
fi

login

if [ "$1" = "regression" ]
then
    test_0;
    shift 2;
fi

# setup required by all runs, even ones where setup was already done.
prerun_setup;

if [ ${setup} -eq 1 ]
then
    setup
    if [ "$SS" = "xio" -o "$SS" = "vplex" ]; then
	setup_yaml;
    fi
    if [ "$SS" = "vmax2" -o "$SS" = "vmax3" -o "$SS" = "vnx" ]; then
	setup_provider;
    fi
fi

docleanup=0;
if [ "$1" = "-cleanup" ]
then
    docleanup=1;
    shift
fi

test_start=0
test_end=25

# If there's a last parameter, take that
# as the name of the test to run
# To start your suite on a specific test-case, just type the name of the first test case with a "+" after, such as:
# ./dutest.sh sanity.conf vplex local test_7+
if [ "$1" != "" -a "${1:(-1)}" != "+"  ]
then
   echo Request to run $*
   for t in $*
   do
      echo Run $t
      reset_system_props
      prerun_tests
      $t
      reset_system_props
   done
else
   if [ "${1:(-1)}" = "+" ]
   then
      num=`echo $1 | sed 's/test_//g' | sed 's/+//g'`
   else
      num=${test_start}
   fi
   # Passing tests:
   while [ ${num} -le ${test_end} ]; 
   do
     reset_system_props
     prerun_tests
     test_${num}
     reset_system_props
     num=`expr ${num} + 1`
   done
fi

echo There were $VERIFY_EXPORT_COUNT export verifications
echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures
echo `date`
echo `git status | grep 'On branch'`

if [ "${docleanup}" = "1" ]; then
    cleanup;
fi

finish;



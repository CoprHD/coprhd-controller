#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
#
# DU Validation Tests
# ===================
#
# This test suite will ensure that the controller catches inconsistencies in export masks on the array that would cause data unavailability otherwise.
# It does this a few different ways, but the most prevalent is by suspending after orchestration steps are created, changing the mask manually (using
# array helper scripts) outside of the controller, then resuming the workflow created by the orchestration.  It will also test to make sure rollback
# acts properly, as well as making sure that if the export mask already contained the requested resources, that the controller passes.
#
# Requirements for VMAX:
# ----------------------
# - SYMAPI should be installed, which is included in the SMI-S install. Install tars can be found on 
#   Download the tar file for Linux, untar, run seinstall -install
# - The provider host should allow for NOSECURE SYMAPI REMOTE access. See https://asdwiki.isus.emc.com:8443/pages/viewpage.action?pageId=28778911 for more information.
#
# Requirements for XtremIO and VPLEX:
# -----------------------------------
# - XIO and VPLEX testing requires the ArrayTools.jar file.  For now, see Bill, Tej, or Nathan for this file.
# - These platforms will create a tools.yml file that the jar file will use based on variables in sanity.conf
#
#set -x

Usage()
{
    echo 'Usage: dutests.sh <sanity conf file path> [setuphw|setupsim|delete] [vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity]  [test1 test2 ...]'
    echo ' [setuphw|setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    echo ' [delete]: Will exports and volumes'
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
    vmax2|vmax3)
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
    *)
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

    echo "Seeing if there's an existing base of volumes"
    BASENUM=`volume list ${PROJECT} | grep YES | head -1 | awk '{print $1}' | awk -Fp '{print $2}' | awk -F- '{print $1}'`
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
	FC_ZONE_A=FABRIC_vplex154nbr2
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
    
    set_validation_check true
    rm /tmp/verify*
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

vnx_setup() {
    SMISPASS=0
    # do this only once
    echo "Setting up SMIS for VNX"
    storage_password=$SMIS_PASSWD

    run smisprovider create VNX-PROVIDER $VNX_SMIS_IP $VNX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VNX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    run storagepool update $VNXB_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VNXB_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VNXB_NATIVEGUID --nhadd $NH --type block
    # Can no longer do this since network discovers where the ports are
    #run storageport update $VNXB_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
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
    run discoveredsystem create $UNITY_DEV unity $UNITY_IP $UNITY_PORT $UNITY_USER $UNITY_PW --serialno=$UNITY_SN

    run storagepool update $UNITY_NATIVEGUID --type block --volume_type THIN_AND_THICK
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

vmax2_setup() {
    SMISPASS=0
    # do this only once
    echo "Setting up SMIS for VMAX2"
    storage_password=$SMIS_PASSWD

    run smisprovider create VMAX2-PROVIDER $VMAX2_DUTEST_SMIS_IP $VMAX2_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX2_SMIS_SSL
    run storagedevice discover_all --ignore_error

    run storagepool update $VMAX2_DUTEST_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX2_DUTEST_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VMAX2_DUTEST_NATIVEGUID --nhadd $NH --type block
    if [ "${SIM}" = "1" ]; then
       run storageport update $VMAX2_DUTEST_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
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

    run cos update block $VPOOL_BASE --storage ${VMAX2_DUTEST_NATIVEGUID}
}

vmax3_setup() {
    SMISPASS=0
    # do this only once
    echo "Setting up SMIS for VMAX3"
    storage_password=$SMIS_PASSWD

    run smisprovider create VMAX-PROVIDER $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block
    if [ "${SIM}" = 0 ]; then
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
            run cos update block $VPOOL_BASE --storage $VPLEX_VNX2_NATIVEGUID

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

            run cos update block $VPOOL_BASE --storage $VPLEX_VNX1_NATIVEGUID
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
            run cos update block ${VPOOL_BASE}_migration_src --storage $VPLEX_VMAX_NATIVEGUID

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

common_setup() {
    run project create $PROJECT --tenant $TENANT 
    echo "Project $PROJECT created."
    echo "Setup ACLs on neighborhood for $TENANT"
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

    if [ "${SIM}" != "1" ]; then
        isNetworkDiscovered=$(networksystem list | grep $BROCADE_NETWORK | wc -l)
        if [ $isNetworkDiscovered -eq 0 ]; then
            secho "Discovering Brocade SAN Switch ..."
            run networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false
        fi
    fi

    ${SS}_setup

    run cos allow $VPOOL_BASE block $TENANT
    sleep 30
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

# Suspend/Resume base test 1
#
# This tests top-level workflow suspension.  It's the simplest form of suspend/resume for a workflow.
#
test_1() {
    echot "Test 1 DU Check Begins"
    expname=${EXPORT_GROUP_NAME}t1

    # Turn on suspend of export before orchestration
    set_suspend_on_class_method ${exportCreateOrchStep}

    # Verify there is no mask
    verify_export ${expname}1 ${HOST1} gone

    # Run the export group command
    echo === export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    resultcmd=`export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task
    
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export before orchestration
    set_suspend_on_class_method ${exportDeleteOrchStep}

    # Run the export group command
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Suspend/Resume base test 2
#
# This tests child workflow suspension.  This is more complicated to control.
#
test_2() {
    echot "Test 2 DU Check Begins"
    expname=${EXPORT_GROUP_NAME}t2

    verify_export ${expname}1 ${HOST1} gone

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportCreateDeviceStep}

    # Run the export group command
    echo === export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    resultcmd=`export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    echo $resultcmd

    echo $resultcmd | grep "suspended" > /dev/null
    if [ $? -ne 0 ]; then
	echo "export group command did not suspend";
	cleanup
	finish 2
    fi

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task
    
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    echo $resultcmd

    echo $resultcmd | grep "suspended" > /dev/null
    if [ $? -ne 0 ]; then
	echo "export group command did not suspend";
	cleanup
	finish 2
    fi

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 3
#
# Summary: Delete Export Group: Tests a volume sneaking into a masking view outside of ViPR.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR asked to delete the export group, but is paused after orchestration
# 3. Customer creates a volume outside of ViPR and adds the volume to the mask.
# 4. Delete of export group workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_3() {
    echot "Test 3: Export Group Delete doesn't delete Export Mask when extra volumes are in it"
    expname=${EXPORT_GROUP_NAME}t3

    if [ "$SS" = "xio" ]; then
        echo "For XtremIO, we do not delete initiators for export mask delete. So skipping this test for XIO."
        return
    fi


    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Create the volume and inventory-only delete it so we can use it later.
    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Run the export group command TODO: Do this more elegantly
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 3
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group delete task to verify it FAILS because of the additional volume"
    fail task follow $task

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Try the export operation again
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Export Test 4
#
# Summary: Delete Export Group: Tests an initiator (host) sneaking into a masking view outside of ViPR.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR asked to delete the export group, but is paused after orchestration
# 3. Customer adds initiators to the mask outside of ViPR
# 4. Delete export group workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_4() {
    echot "Test 4: Export Group Delete doesn't delete Export Mask when extra initiators are in it"
    expname=${EXPORT_GROUP_NAME}t4

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 2

    if [ "$SS" != "xio" ]; then
        PWWN=`echo ${H2PI1} | sed 's/://g'`

        # Add another initiator to the mask (done differently per array type)
        arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
        # Verify the mask has the new initiator in it
        verify_export ${expname}1 ${HOST1} 3 2

        # Run the export group command.  Expect it to fail with validation
	    fail export_group delete $PROJECT/${expname}1
        
        # Run the export group command.  Expect it to fail with validation
        fail export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"

        # Verify the mask wasn't touched
        verify_export ${expname}1 ${HOST1} 3 2

        # Now remove the initiator from the export mask
        arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

        # Verify the mask is back to normal
        verify_export ${expname}1 ${HOST1} 2 2
    fi

    # Reset test: test lower levels (original test_4 test)
    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
        echo "export group command failed outright"
        cleanup
        finish 4
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    PWWN=`randwwn | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 3 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group delete task to verify it FAILS because of the additional initiator"
    fail task follow $task

    # Verify the mask wasn't touched
    verify_export ${expname}1 ${HOST1} 3 2

    # Now remove the initiator from the export mask
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 2

    # Reset the test, now try to add initiators from other hosts we know about
    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
        echo "export group command failed outright"
        cleanup
        finish 4
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    PWWN=`echo ${H2PI1} | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 3 2

    # Resume the workflow
    runcmd workflow resume $workflow

    if [ "$SS" != "xio" ] 
    then
        # Follow the task.  It should fail because of Poka Yoke validation
        echo "*** Following the export_group delete task to verify it FAILS because of the additional initiator"
        fail task follow $task
        # Verify the mask wasn't touched
        verify_export ${expname}1 ${HOST1} 3 2
        # Now remove the initiator from the export mask
        arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

        # Verify the mask is back to normal
        verify_export ${expname}1 ${HOST1} 2 2

        # Turn off suspend of export after orchestration
        set_suspend_on_class_method "none"
        
        runcmd export_group delete $PROJECT/${expname}1
    else
        # For XIO, extra initiator of different host but same cluster results in delete initiator call
        sleep 60
        verify_export ${expname}1 ${HOST1} 1 2
        # Now remove the volumes from the storage group (masking view)
        device_id=`get_device_id ${PROJECT}/${VOLNAME}-1`
        arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
        device_id=`get_device_id ${PROJECT}/${VOLNAME}-2`
        arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}	
        # Now delete the export mask
        arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}        
    fi

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Export Test 5
#
# Summary: Remove Volume: Tests an initiator (host) sneaking into a masking view outside of ViPR.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 2 volumes, 1 host export.
# 2. ViPR asked to remove the volume from the export group, but is paused after orchestration
# 3. Customer adds initiators to the export mask outside of ViPR
# 4. Removal of volume workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_5() {
    echot "Test 5: Remove Volume doesn't remove the volume when extra initiators are in the mask"
    expname=${EXPORT_GROUP_NAME}t5

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 2

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 5
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    PWWN=`randwwn | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 3 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group delete task to verify it FAILS because of the additional initiator"
    fail task follow $task

    # Now remove the initiator from the export mask
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 2

    if [ "$SS" != "xio" ]; then    
        # Run the export group command TODO: Do this more elegantly
        echo === export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
        resultcmd=`export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"`

        if [ $? -ne 0 ]; then
	    echo "export group command failed outright"
       	    cleanup
	    finish 4
        fi

        # Show the result of the export group command for now (show the task and WF IDs)
        echo $resultcmd

        # Parse results (add checks here!  encapsulate!)
        taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
        answersarray=($taskworkflow)
        task=${answersarray[0]}
        workflow=${answersarray[1]}

        PWWN=`echo ${H2PI1} | sed 's/://g'`
    
        # Add another initiator to the mask (done differently per array type)
        arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
        # Verify the mask has the new initiator in it
        verify_export ${expname}1 ${HOST1} 3 2

        # Resume the workflow
        runcmd workflow resume $workflow

        # Follow the task.  It should fail because of Poka Yoke validation
        echo "*** Following the export_group remove volume task to verify it FAILS because of the additional initiator"
        fail task follow $task

        # Verify the mask wasn't touched
        verify_export ${expname}1 ${HOST1} 3 2

        # Now remove the initiator from the export mask
        arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

        # Verify the mask is back to normal
        verify_export ${expname}1 ${HOST1} 2 2
    fi

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Remove the volume (make sure the mask is OK now)
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"

    # Make sure it worked this time
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure the mask is gone
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 6
#
# Summary: Remove Initiator: Tests a volume sneaking into a masking view outside of ViPR.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR asked to remove an initiator from the export group, but is paused after orchestration
# 3. Customer creates a volume outside of ViPR and adds the volume to the mask.
# 4. Remove initiator workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_6() {
    echot "Test 6: Remove Initiator doesn't remove initiator when extra volumes are seen by it"
    expname=${EXPORT_GROUP_NAME}t6

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    known_device_id=`get_device_id ${PROJECT}/${VOLNAME}-2`

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${known_device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Run the export group command.  Expect it to fail with validation
    fail export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${known_device_id} ${HOST1}

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    resultcmd=`export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 6
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${known_device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group update task to verify it FAILS because of the additional volume"
    fail task follow $task

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${known_device_id} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Reset the test and run with a volume outside of ViPR
    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    # Inventory-only delete of the volume
    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Run the export group command.  Expect it to fail with validation
    fail export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    resultcmd=`export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 6
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group update task to verify it FAILS because of the additional volume"
    fail task follow $task

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Try the operation again
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 1 1

    # Try the export operation again
    runcmd export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Validation Test 7
#
# Summary: Add Volume: Volume added outside of ViPR, then inside ViPR
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.
# 3. Customer adds volume manually to export mask.
# 4. ViPR asked to add volume to host export.
# 5. Verify volume added
# 6. Delete export group
# 7. Verify we were able to delete the mask
#
test_7() {
    echot "Test 7: Add volume: don't remove volume when added outside of ViPR during rollback"
    expname=${EXPORT_GROUP_NAME}t7

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Create another volume, but don't export it through ViPR (yet)
    volname=du-hijack-volume-${RANDOM}

    runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Now add that volume using ViPR
    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${volname}

    # Verify the mask is "normal" after that command
    verify_export ${expname}1 ${HOST1} 2 2

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Delete the volume we created
    runcmd volume delete ${PROJECT}/${volname} --wait
}

# Validation Test 8
#
# Summary: Add Initiator: Initiator added outside of ViPR, then inside ViPR
#
# Note: Mostly tests orchestration's good decision making.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new initiator, doesn't not export it.
# 3. Customer adds initiator manually to export mask.
# 4. ViPR asked to add initiator to host export.
# 5. Verify initiator added
# 6. Delete export group
# 7. Verify we were able to delete the mask
#
test_8() {
    echot "Test 8: Add initiator: allow adding initiator that was added outside ViPR"
    expname=${EXPORT_GROUP_NAME}_${RANDOM}_t8

    # Make sure we start clean; no masking view on the array
    verify_export ${expname} ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname} $NH --type Exclusive --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1}/${H1PI1}"

    # Verify the mask has been created
    verify_export ${expname} ${HOST1} 1 1

    # Strip out colons for array helper command
    h1pi2=`echo ${H1PI2} | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${h1pi2} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname} ${HOST1} 2 1

    # Now add that volume using ViPR
    runcmd export_group update $PROJECT/${expname} --addInits ${HOST1}/${H1PI2}

    # Verify the mask is "normal" after that command
    verify_export ${expname} ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}

    # Make sure it really did kill off the mask
    verify_export ${expname} ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 9
#
# Summary: Add Volume: add volume outside ViPR, suspending in lowest level step.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.
# 3. ViPR update export group to add volume, but suspends when adding volume to mask
# 3. Customer adds volume manually to export mask.
# 4. ViPR resumes export group add volume
# 5. Operation should passively pass (realize it was already done and return success)
# 6. Verify rollback does not remove the volume from the host export.
#
test_9() {
    echot "Test 9: Add volume: add volume outside ViPR, lowest-level step resume"
    expname=${EXPORT_GROUP_NAME}t9

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 9
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Create another volume, but don't export it through ViPR (yet)
    volname="${VOLNAME}-2"

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.
    echo "*** Following the export_group update task to verify it PASSES passively because the volume is already there"
    runcmd task follow $task

    # Verify the mask is "normal" after that command
    verify_export ${expname}1 ${HOST1} 2 2

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 10
#
# Summary: Add Volume: add/remove volume outside ViPR, makes sure ViPR adds/removes the volume via orchestration.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.
# 3. Customer adds volume manually to export mask.
# 4. ViPR update export group to add the same volume
# 5. Operation should succeed and ViPR should manage the volume's export
# 6. Perform same operation for removing a volume outside of ViPR
#
test_10() {
    echot "Test 10: Add/Remove volume: add/remove volume outside ViPR, add/remove volume to mask in ViPR passes"
    expname=${EXPORT_GROUP_NAME}t10

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Create another volume, but don't export it through ViPR (yet)
    volname="${VOLNAME}-2"

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Run the export group command to add the volume into the mask
    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Remove the volume from the mask
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 1

    # Run the export group command to remove the volume from the mask
    runcmd export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 11
#
# Summary: Add Volume: add volume outside ViPR, failure during add volume step (very early, preferably).  Verify rollback doesn't remove it.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.
# 3. Customer adds volume manually to export mask.
# 4. ViPR update export group to add volume, but a failure occurs during the add volume to mask step
# 5. Operation should fail and mask should be untouched
#
test_11() {
    echot "Test 11: Add volume: add volume outside ViPR, add volume to mask fails early"
    expname=${EXPORT_GROUP_NAME}t11

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Find information about volume 2 so we can do stuff to it outside of ViPR
    volname="${VOLNAME}-2"

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 11
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Invoke failure in the step desired
    set_artificial_failure failure_001_early_in_add_volume_to_mask

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.
    echo "*** Following the export_group update task to verify it FAILS due to the invoked failure"
    fail task follow $task

    # Verify the mask still has the new volume in it (this will fail if rollback removed it)
    verify_export ${expname}1 ${HOST1} 2 2

    # Remove the volume from the mask
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume removed
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 12
#
# Summary: Create a volume in ViPR.  Delete the volume outside of ViPR and create another one with the same device ID.  Try to delete from ViPR
#
# Basic Use Case for single host, single volume
# 1. ViPR creates a new volume
# 2. Customer deletes volume outside of ViPR
# 3. Customer create new volume with same device ID, same size
# 4. ViPR attempt various operations, fails due to validation
# 6. ViPR inventory-only delete volume
# 7. Ingest volume?
#
test_12() {
    echot "Test 12: Volume gets reclaimed outside of ViPR"
    expname=${EXPORT_GROUP_NAME}t12
    volname=${HOST1}-dutest-oktodelete-t12-${RANDOM}

    # Check to make sure we're running VPLEX only
    if [ "${SS}" != "vplex" ]; then
        echo "test_12 only runs on VPLEX.  Bypassing for ${SS}."
        return
    fi

    # Create a new volume that ViPR knows about
    runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    volume_uri=`volume show ${PROJECT}/${volname} | grep ":Volume:" | grep id | awk -F\" '{print $4}'`

    # Now change the WWN in the database of that volume to emulate a delete-and-recreate on the array
    dbupdate Volume wwn ${volume_uri} 60000970000FFFFFFFF2533030314233

    # Now try to delete the volume, it should fail
    fail volume delete ${PROJECT}/${volname} --wait

    # Now try to expand the volume, it should fail
    fail volume expand ${PROJECT}/${volname} 2GB

    # Now try to create a snapshot off of the volume, it should fail
    if [ "$SS" = "vplex" ]; then
        echo "Skipping snapshot create test for Vplex because AbstractSnapshotOperations has no knowledge of Vplex volume"
    else
        fail blocksnapshot create ${PROJECT}/${volname} ${volname}-snap1
    fi

    # Inventory-only delete the volume
    volume delete ${PROJECT}/${volname} --vipronly

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}
}

# DU Prevention Validation Test 13
#
# Summary: add volume to mask fails after volume added, rollback can remove volume it added, even if extra initiator is in mask
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.
# 3. Set add volume to mask step to suspend
# 4. Set the add volume to mask step to fail after adding volume to mask
# 5. ViPR request to update export group to add the volume
# 6. export group update will suspend.
# 7. Add initiator to the mask.
# 8. Resume export group update task.  It should fail and rollback
# 9. Rollback should be allowed to remove the volume it added, regardless of the extra initiator.
#
test_13() {
    echot "Test 13: Test rollback of add volume, verify it can remove its own volumes on rollback when initiator sneaks into mask"
    expname=${EXPORT_GROUP_NAME}t13

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}
    set_artificial_failure failure_002_late_in_add_volume_to_mask

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 13
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    PWWN=`randwwn | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 3 1

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.
    echo "*** Following the export_group update task to verify it FAILS due to the invoked failure"
    fail task follow $task

    # Verify rollback was able to remove the volume it added
    verify_export ${expname}1 ${HOST1} 3 1

    # Remove initiator from the mask (done differently per array type)
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the initiator was removed
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 14
#
# Summary: add initiator to mask fails after initiator added, rollback allowed to remove it even though another volume in the mask
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new volume, doesn't not export it.  Inventory-only delete it.
# 3. Set add initiator to mask step to suspend
# 4. Set the add initiator to mask step to fail after adding initiator to mask
# 5. ViPR request to update export group to add the initiator
# 6. export group update will suspend.
# 7. Add external volume to the mask.
# 8. Resume export group update task.  It should fail and rollback
# 9. Rollback should remove the initiator in the mask that it added even if there's an existing volume in the mask.
#
test_14() {
    echot "Test 14: Test rollback of add initiator, verify it does not remove initiators when volume sneaks into mask"
    expname=${EXPORT_GROUP_NAME}t14-${RANDOM}

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Remove one of the initiator so we can add it back.
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 1 1

    # Create another volume that we will inventory-only delete
    volname="${HOST1}-dutest-oktodelete-t14-${RANDOM}"
    runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`
    
    # Inventory-only delete the volume so it's not under ViPR management
    runcmd volume delete ${PROJECT}/${volname} --vipronly

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddInitiatorsDeviceStep}
    set_artificial_failure failure_003_late_in_add_initiator_to_mask

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}
    resultcmd=`export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 14
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 1 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group update task to verify it FAILS because of the additional volume"
    fail task follow $task

    # Verify that ViPR rollback removed only the initiator that was previously added
    verify_export ${expname}1 ${HOST1} 1 2

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Verify the volume was removed
    verify_export ${expname}1 ${HOST1} 1 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Validation Test 15
#
# Summary: Add Initiator: Initiator added outside of ViPR, then inside ViPR, but fail after addInitiator, rollback shouldn't remove initiator we didn't really add.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR creates a new initiator, doesn't not export it.
# 3. Set export_group update to suspend after orchestration, but before adding initiator to mask
# 4. Set a failure to occur after initiator add passively succeeded (because initiator was already in the mask)
# 5. ViPR update export group to add initiator, suspends
# 6. add initiator to mask outside of ViPR
# 7. Resume workflow, it should passively succeed the initiator add step, then fail a future step, then rollback (and not remove the initiator)
# 8. Verify initiator still exists in mask
# 9. Remove suspensions/error injections
# 10. Rerun export_group update, verify the mask is still the same.
# 11. Delete export group
# 12. Verify we were able to delete the mask
#
test_15() {
    echot "Test 15: Add initiator: Make sure rollback doesn't remove initiator it didn't add"
    expname=${EXPORT_GROUP_NAME}t15

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Exclusive --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1}/${H1PI1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 1 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddInitiatorsDeviceStep}
    set_artificial_failure failure_003_late_in_add_initiator_to_mask

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI2}
    resultcmd=`export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI2}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 15
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Strip out colons for array helper command
    h1pi2=`echo ${H1PI2} | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${h1pi2} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 2 1

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of a failure invocation.
    echo "*** Following the export_group delete task to verify it FAILS because of the additional volume"
    fail task follow $task

    # Verify the mask still has the new initiator in it (this will fail if rollback removed it)
    verify_export ${expname}1 ${HOST1} 2 1

    # shut off suspensions/failures
    set_suspend_on_class_method "none"
    set_artificial_failure none

    # Now add that initiator into the mask, this time it should pass
    runcmd export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI2}

    # Verify the mask is "normal" after that command
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Validation Test 16
#
# Summary: VNX Only: Make sure we cannot steal host/initiators from other storage groups
#
# Basic Use Case for single host, single volume
# 0. Test for VNX, do not run otherwise
# 1. Set suspend on creating export mask
# 2. Export group create of one volume to the host, it will suspend
# 3. Create a volume and inventory-only delete it
# 4. Create the storage group (different name) with the unmanaged volume with host
# 5. Resume export group create and follow
# 6. Task should fail because it would steal the host from the storage group that exists
# 7. Remove the manually-created storage group
# 8. Delete the unmanaged volume
# 9. Remove the suspend of the creating export mask
# 10. Run export group create, verify mask outside of ViPR
# 11. Run export group delete, verify mask is gone outside of ViPR
#
test_16() {
    echot "Test 16: VNX Only: Make sure we cannot steal host/initiators from other storage groups"
    expname=${EXPORT_GROUP_NAME}t16

    # Check to make sure we're running VNX only
    if [ "${SS}" != "vnx" ]; then
	echo "test_16 only runs on VNX.  Bypassing for ${SS}."
	return
    fi

    SGNAME=${HOST1}-du-steal

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${SGNAME} -exact- gone

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportCreateDeviceStep}

    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`
    
    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Run the export group command TODO: Do this more elegantly
    echo === export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    resultcmd=`export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 16
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Strip out colons for array helper command
    h1pi2=`echo ${H1PI2} | sed 's/://g'`

    # 4. Create the storage group (different name) with the unmanaged volume with host
    arrayhelper create_export_mask ${SERIAL_NUMBER} ${device_id} ${h1pi2} ${SGNAME}

    # Verify the storage group was created
    verify_export ${SGNAME} -exact- 1 1

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because the storage group will be there with that init already
    echo "*** Following the export_group create task to verify it FAILS because of the existing SG"
    fail task follow $task

    # Delete the mask we created
    runcmd navihelper.sh remove_initiator_from_mask $serial_number $array_ip $h1pi2 $SGNAME
    runcmd navihelper.sh remove_volume_from_mask $serial_number $array_ip $device_id $SGNAME
    runcmd navihelper.sh delete_mask $array_ip ${SGNAME}
    verify_export ${SGNAME} -exact- gone

    # Delete the volume we created
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # shut off suspensions/failures
    reset_system_props

    # sleep for 2 mins for the provider to get updated with the deleted SG
    sleep 120

    # Run export group create again
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask was created
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 17
#
# Summary: Delete Export Group: Tests kill switch that allows service to shut off validation
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR asked to delete the export group, but is paused after orchestration
# 3. Customer creates a volume outside of ViPR and adds the volume to the mask.
# 4. Delete of export group workflow is resumed
# 5. Verify the operation succeeds because validation is turned off
#
test_17() {
    echot "Test 17: Tests kill switch to disable export validation checks"
    expname=${EXPORT_GROUP_NAME}t3

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    set_validation_check false

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask was created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Create the volume and inventory-only delete it so we can use it later.
    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Run the export group command TODO: Do this more elegantly
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 17
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should pass because validation is run but not enforced
    echo "*** Following the export_group delete task to verify it PASSES because validation is disabled"
    runcmd task follow $task

    if [ "${SS}" = "xio" ]; then
        # XIO will still protect the lun mapping due to the additional volume, leaving it behind
	verify_export ${expname}1 ${HOST1} 2 1
	# Delete the lun mapping and mask
	arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
	arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}
    fi

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} gone

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Export Test 18
#
# Summary: Remove Volume: Tests an volume sneaking into a masking view outside of ViPR.  Should not fail.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 2 volumes, 1 host export.
# 2. ViPR asked to remove the volume from the export group, but is paused after orchestration
# 3. Customer adds a different volume to the export mask outside of ViPR
# 4. Removal of volume workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_18() {
    echot "Test 18: Remove Volume allows removal of the volume when other volume sneaks into the mask"
    expname=${EXPORT_GROUP_NAME}t18

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 2

    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 18
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Add an unrelated initiator to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 2 3

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    echo "*** Following the export_group update task to verify it succeeds despite unrelated volume"
    runcmd task follow $task

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 2

    # Now remove the initiator from the export mask
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure the mask is gone
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 19
#
# Summary: Remove Initiator: Tests to make sure unrelated initiator don't prevent removal of initiator
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. ViPR asked to remove an initiator from the export group, but is paused after orchestration
# 3. Customer creates a volume outside of ViPR and adds the volume to the mask.
# 4. Remove initiator workflow is resumed
# 5. Verify the operation fails.
# 6. Remove the volume from the mask outside of ViPR.
# 7. Attempt operation again, succeeds.
#
test_19() {
    echot "Test 19: Remove Initiator removes initiator when extra initiators are seen by it"
    expname=${EXPORT_GROUP_NAME}t19

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    resultcmd=`export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 19
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    PWWN=`randwwn | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 3 1

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should succeed
    echo "*** Following the export_group update task to verify it passes despite extra initiator"
    runcmd task follow $task

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Remove initiator from the mask (done differently per array type)
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 1 1

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Try the export operation again
    runcmd export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 20
#
# Summary: (VMAX) Remove Volume: Tests to make sure a volume shared by an out-of-management MV is not removed.
#
# Basic Use Case for single host, two volumes
# 1. ViPR creates 2 volumes, 1 host export
# 2. ViPR asked to remove vol-1 from the export group, but is paused after orchestration
# 3. Customer creates a new MV outside of ViPR and adds both volumes to the mask
# 4. Remove volume workflow is resumed
# 5. Verify the operation fails
# 6. Delete the mask from outside of ViPR
# 7. Attempt operation again, succeeds
test_20() {
    echot "Test 20: (VMAX) Remove volume removes volume when SG is shared by out-of-management masking view"
    expname=${EXPORT_GROUP_NAME}t20

    # Check to make sure we're running VMAX only
    if [ "${SS: 0:-1}" != "vmax" ]; then
	echo "test_20 only runs on VMAX.  Bypassing for ${SS}."
	return
    fi

    HIJACK_MV=hijack-test20-${RANDOM}

    # Make sure we start clean; no masking views on the array
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HIJACK_MV} gone

    # Create the mask with 2 volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 2

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2
    resultcmd=`export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 20
    fi

	# Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Create another mask and add the volumes
    HOST1_CSG="${HOST1}_${SERIAL_NUMBER: -3}_CSG"
    PWWN=`echo ${H2PI1} | sed 's/://g'`
    arrayhelper create_export_mask ${SERIAL_NUMBER} ${HOST1_CSG} ${PWWN} ${HIJACK_MV}

    # Verify the new mask has both volumes in it
    verify_export ${expname}1 ${HIJACK_MV} 1 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail
    echo "*** Following the export_group update task to verify it fails"
    fail task follow $task

    # Verify the masks are unchanged
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HIJACK_MV} 1 2

    # Delete the masking view
    arrayhelper delete_export_mask ${SERIAL_NUMBER} ${HIJACK_MV} "noop" "${HIJACK_MV}_${SERIAL_NUMBER: -3}_IG"

    # Verify the out-of-management mask
    run verify_export ${expname}1 ${HIJACK_MV} gone

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Run the export group command to remove the volume from the mask again
    runcmd export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2

    # Verify the volume was removed
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 21
#
# Summary: (VMAX) Remove Initiator: Tests to make sure an initiator shared by an out-of-management MV is not removed.
#
# Basic Use Case for single host, two volumes
# 1. ViPR creates 2 volumes, 1 host export
# 2. ViPR asked to remove initiator from the export group, but is paused after orchestration
# 3. Customer creates a new MV outside of ViPR with shared initiators
# 4. Remove initiator workflow is resumed
# 5. Verify the operation fails
# 6. Delete the mask from outside of ViPR
# 7. Attempt operation again, succeeds
test_21() {
    echot "Test 21: (VMAX) Remove initiator removes initiator when IG is shared by out-of-management masking view"
    expname=${EXPORT_GROUP_NAME}t20
    HIJACK_MV=hijack_test21_${RANDOM}

    # Check to make sure we're running VMAX only
    if [ "${SS: 0:-1}" != "vmax" ]; then
	echo "test_21 only runs on VMAX.  Bypassing for ${SS}."
	return
    fi

    # Make sure we start clean; no masking views on the array
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HIJACK_MV} gone

    # Create the mask with 2 volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command TODO: Do this more elegantly
    echo === export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    resultcmd=`export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
	cleanup
	finish 21
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Get the device ID of 2nd volume
    device_id=`get_device_id ${PROJECT}/${VOLNAME}-2`

    HOST1_CSG="${HOST1}_${SERIAL_NUMBER: -3}_CSG"
    PWWN=`echo ${H2PI1} | sed 's/://g'`
    HOST1_IG="${HOST1}_${SERIAL_NUMBER: -3}_IG"
    arrayhelper create_export_mask ${SERIAL_NUMBER} ${device_id} ${HOST1_IG} ${HIJACK_MV}

    # Verify the new mask has both volumes in it
    verify_export ${expname}1 ${HIJACK_MV} 2 1

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail
    echo "*** Following the export_group update task to verify it fails"
    fail task follow $task

    # Verify the masks are unchanged
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HIJACK_MV} 2 1

    # Delete the masking view (it will have a cascaded IG)
    arrayhelper delete_export_mask ${SERIAL_NUMBER} ${HIJACK_MV} "${HIJACK_MV}_${SERIAL_NUMBER: -3}_SG" "${HIJACK_MV}_${SERIAL_NUMBER: -3}_CIG"

    # Verify the out-of-management mask
    verify_export ${expname}1 ${HIJACK_MV} gone

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Run the export group command to remove the initiator from the mask again
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Verify the initiator was removed
    verify_export ${expname}1 ${HOST1} 1 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# Suspend/Resume of Migration test volumes
#
# This tests top-level workflow suspension for the migration workflow with volumes
#
test_22() {
    echot "Test 22 suspend resume migration with volume"
    expname=${EXPORT_GROUP_NAME}t22

    # Bailing out for non-VPLEX
    if [ "${SS}" != "vplex" ]; then
	echo "This test is testing migration, so it is only valid for VPLEX."
	return
    fi

    if [ "${SIM}" = "1" ]; then
        # validation check off for this one.  vplex sim failing volume validation
	runcmd syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false
    fi

    # Create a new vplex volume that we can migrate
    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE}_migration_src 1GB --count 1

    # Run change vpool, but suspend will happen

    # Run the export group command
    echo === volume change_cos ${PROJECT}/${HIJACK} ${VPOOL_BASE}_migration_tgt --suspend
    resultcmd=`volume change_cos ${PROJECT}/${HIJACK} ${VPOOL_BASE}_migration_tgt --suspend`

    if [ $? -ne 0 ]; then
	echo "volume change_cos command failed outright"
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    # Delete the volume we created
    runcmd volume delete ${PROJECT}/${HIJACK} --wait
}

# Suspend/Resume of Migration test CG
#
# This tests top-level workflow suspension for the migration workflow with CGs
#
test_23() {
    echot "Test 23 (VPLEX) Suspend resume migration with CGs"
    expname=${EXPORT_GROUP_NAME}t23

    # validation check off for this one.  vplex sim failing volume validation
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false

    # Bailing out for non-VPLEX
    if [ "${SS}" != "vplex" ]; then
	echo "This test is testing migration, so it is only valid for VPLEX."
        return
    fi

    if [ "${VPLEX_MODE}" = "distributed" ]; then
	echo "This test is reserved for vplex local as distributed mode uses the same code path"
	return
    fi

    if [ "${SIM}" = "1" ]; then
        # validation check off for this one.  vplex sim failing volume validation
	runcmd syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false
    fi

    randval=${RANDOM}

    # Create a new CG
    CGNAME=du-test23-cg-${randval}
    runcmd blockconsistencygroup create $PROJECT ${CGNAME}

    # Create a new vplex volume that we can migrate
    HIJACK=du-hijack-cgvolume-${randval}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE}_migration_src 1GB --count 2 --consistencyGroup=${CGNAME}

    # Run change vpool, but suspend will happen

    # Run the export group command
    echo === volume change_cos "${PROJECT}/${HIJACK}-1,${PROJECT}/${HIJACK}-2" ${VPOOL_BASE}_migration_tgt --consistencyGroup=${CGNAME} --suspend
    resultcmd=`volume change_cos "${PROJECT}/${HIJACK}-1,${PROJECT}/${HIJACK}-2" ${VPOOL_BASE}_migration_tgt --consistencyGroup=${CGNAME} --suspend`

    if [ $? -ne 0 ]; then
	echo "volume change_cos_multi command failed outright"
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    # Delete the volume we created
    runcmd volume delete ${PROJECT}/${HIJACK}-1 --wait
    runcmd volume delete ${PROJECT}/${HIJACK}-2 --wait
    runcmd blockconsistencygroup delete ${CGNAME}
}

# Export Test 24
#
# Summary: Remove Volume: Tests a volume sneaking into a masking view outside of ViPR doesn't remove the zone.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. Customer add a volume to the export mask outside of ViPR
# 3. Customer removes managed volume from the export group
# 4. Verify the operation succeeds:  volume is removed
# 5. Verify the zone is NOT removed from the switch
# 6. Cleanup
#
test_24() {
    echot "Test 24: Remove Volume doesn't remove the zone when extra volume is in the mask"
    expname=${EXPORT_GROUP_NAME}t24

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Just clean the zones. Previous tests paid no attention to zoning.
    clean_zones ${FC_ZONE_A:7} ${HOST1}

    ## Verify there are no zones on the switch
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 1

    # Find the zones that were created
    load_zones ${HOST1} 

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Create a new vplex volume that we can add to the mask
    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1
    
    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    # Inventory-only delete the volume we created
    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Add an unrelated volumer to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Remove the vipr volume from the export group
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"

    # Verify the volume is removed
    verify_export ${expname}1 ${HOST1} 2 1

    # Verify the zone names, as we know them, are still on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Now add back the other vipr volume to the mask
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"

    # Verify the volume is added
    verify_export ${expname}1 ${HOST1} 2 2

    # Now remove the hijack volume from the export mask
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Make sure it removed the volume
    verify_export ${expname}1 ${HOST1} 2 1

    # Now let Vipr delete the export group.
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure we end clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Delete the zones (no need to verify zones, we intentionally left one behind)
    delete_zones ${HOST1}

    # Verify delete zones did what it is supposed to
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# DU Prevention Validation Test 25
#
# Summary: Remove Initiator: Tests an initiator sneaking into a masking view outside of ViPR removes the proper zone.
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. Customer adds an initiator to the export mask outside of ViPR
# 3. Customer removes the managed host from the mask
# 4. Verify the operation succeeds:  host is removed
# 5. Verify the zones are removed from the switch 
# 6. Cleanup
#
test_25() {
    if [ "$SS" = "xio" ]; then
        echo "Test 25 is not applicable for XtremIO. Skipping."
        return
    fi

    echot "Test 25: Remove Initiator doesn't remove zones when extra initiators are in it"
    expname=${EXPORT_GROUP_NAME}t25

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Just clean the zones. Previous tests paid no attention to zoning.
    clean_zones ${FC_ZONE_A:7} ${HOST1}

    # Verify there are no zones on the switch
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Find the zones that were created
    load_zones ${HOST1} 

    verify_zones ${FC_ZONE_A:7} exists

    PWWN=`randwwn | sed 's/://g'`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
   
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 3 1

    # Run the export group command 
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST1}

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 1 1

    # Remove initiator from the mask (done differently per array type)
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 0 1

    # Verify the zones we know about are gone
    verify_zones ${FC_ZONE_A:7} gone

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # The mask is out of our control at this point, delete mask
    arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
}

# pull in the vplextests.sh so it can use the dutests framework
source vplextests.sh

cleanup() {
    if [ "${docleanup}" = "1" ]; then
	for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
	do
	  runcmd export_group delete ${id} > /dev/null
	  echo "Deleted export group: ${id}"
	done
	runcmd volume delete --project $PROJECT --wait
    fi
    echo There were $VERIFY_EXPORT_COUNT export verifications
    echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures
}

# Clean up any exports or volumes from previous runs, but not the volumes you need to run tests
cleanup_previous_run_artifacts() {
   for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
   do
      echo "Deleting old export group: ${id}"
      runcmd export_group delete ${id} > /dev/null
   done

   for id in `volume list ${PROJECT} | grep YES | grep hijack | awk '{print $7}'`
   do
      echo "Deleting old volume: ${id}"
      runcmd volume delete ${id} --wait > /dev/null
   done
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
    if [ "$SS" = "xio" -o "$SS" = "vplex" ]; then
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



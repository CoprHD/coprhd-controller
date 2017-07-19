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
#set -x

source $(dirname $0)/wftests_host_cluster.sh
source $(dirname $0)/wftests_host_expand_mount.sh
source $(dirname $0)/common_subs.sh

Usage()
{
    echo 'Usage: wftests.sh <sanity conf file path> [vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity | vblock | srdf [sync | async]] [-setup(hw) | -setupsim] [-report] [-cleanup] [-resetsim]  [test_1 test_2 ...]'
    echo ' (vmax2 | vmax3 ...: Storage platform to run on.'
    echo ' [-setup(hw) | setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes (Required to run first, can be used with tests'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' [-cleanup]: Clean up the pre-created volumes and exports associated with -setup operation (Optional)'
    echo ' [-resetsim]: Resets the simulator as part of setup (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    print_test_names
    echo ' Example:  ./wftests.sh sanity.conf vmax3 -setupsim -report -cleanup test_7+'
    echo '           Will start from clean DB, report results to reporting server, clean-up when done, and start on test_7 (and run all tests after test_7'
    exit 2
}

# Print functions in this file that start with test_
print_test_names() {
    for test_name in `grep -E "^test_.+\(\)" $0 | sed -E "s/\(.*$//"`
    do
      echo "  $test_name"
    done
}

cd $(dirname $0)

# Extra debug output
DUTEST_DEBUG=${DUTEST_DEBUG:-0}

# Global test repo location
GLOBAL_RESULTS_IP=10.247.101.46
GLOBAL_RESULTS_PATH=/srv/www/htdocs
LOCAL_RESULTS_PATH=/tmp
GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR=output_files
RESULTS_SET_FILE=results-set.csv
TEST_OUTPUT_FILE=default-output.txt

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

# Overall suite counts
VERIFY_COUNT=0
VERIFY_FAIL_COUNT=0

# Per-test counts
TRIP_VERIFY_COUNT=0
TRIP_VERIFY_FAIL_COUNT=0

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
VPOOL_BASE_NOCG=vpool-nocg
VPOOL_CHANGE=${VPOOL_BASE}-change
VPOOL_FAST=${VPOOL_BASE}-fast

BASENUM=${BASENUM:=$RANDOM}
VOLNAME=wftest${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
HOST1=wfhost1export${BASENUM}
HOST2=wfhost2export${BASENUM}
CLUSTER=cl${BASENUM}

# Allow for a way to easily use different hardware
if [ -f "./myhardware.conf" ]; then
    echo Using ./myhardware.conf
    source ./myhardware.conf
fi

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}

prerun_setup() {		
    # Reset system properties
    reset_system_props

    # Convenience, clean up known artifacts
    cleanup_previous_run_artifacts

    # Get the latest tools
    retrieve_tooling

    project list --tenant emcworld > /dev/null 2> /dev/null
    if [ $? -eq 0 ]; then
	   PROJECT=`project list --tenant emcworld | grep YES | head -1 | awk '{print $1}'`
	   echo PROJECT ${PROJECT}
	   echo "Seeing if there's an existing base of volumes"
	   BASENUM=`volume list ${PROJECT} | grep YES | head -1 | awk '{print $1}' | awk -Ft '{print $3}' | awk -F- '{print $1}'`
    else
	   BASENUM=""
    fi

    if [ "${BASENUM}" != "" ]
    then
       echo "Volumes were found!  Base number is: ${BASENUM}"
       VOLNAME=wftest${BASENUM}
       EXPORT_GROUP_NAME=export${BASENUM}
       HOST1=wfhost1export${BASENUM}
       HOST2=wfhost2export${BASENUM}
       CLUSTER=cl${BASENUM}

       sstype=${SS:0:3}
       if [ "${SS}" = "xio" ]; then
	       sstype="xtremio"
       fi
       if [ "${SS}" = "srdf" ]; then
	       sstype="vmax"
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

    storageprovider list | grep SIM > /dev/null
    if [ $? -eq 0 -o "${SIM}" = "1" ];
    then
        ZONE_CHECK=0
        SIM=1;
        echo "Shutting off zone check for simulator environment"
        VCENTER_IP=${VCENTER_SIMULATOR_IP}
        VCENTER_PORT=${VCENTER_SIMULATOR_PORT}
        VCENTER_USERNAME=${VCENTER_SIMULATOR_USERNAME}
        VCENTER_PASSWORD=${VCENTER_SIMULATOR_PASSWORD}
        VCENTER_DATACENTER=${VCENTER_SIMULATOR_DATACENTER}
        VCENTER_CLUSTER=${VCENTER_SIMULATOR_CLUSTER}
        VCENTER_HOST=${VCENTER_SIMULATOR_HOST}
    else
        VCENTER_IP=${VCENTER_HW_IP}
        VCENTER_PORT=${VCENTER_HW_PORT}
        VCENTER_USERNAME=${VCENTER_HW_USERNAME}
        VCENTER_PASSWORD=${VCENTER_HW_PASSWORD}
        VCENTER_DATACENTER=${VCENTER_HW_DATACENTER}
        VCENTER_CLUSTER=${VCENTER_HW_CLUSTER} 
        VCENTER_HOST=${VCENTER_HW_HOST}
    fi  

    if [ "${SS}" = "vnx" ]
    then
	   array_ip=${VNXB_IP}
	   FC_ZONE_A=FABRIC_vplex154nbr2
    elif [ "${SS}" = "vmax2" ]
    then
        FC_ZONE_A=FABRIC_VPlex_LGL6220_FID_30-10:00:00:27:f8:58:f6:c1
    fi
    
    if [ "${SIM}" = "1" ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}	  
    fi

    # Some failures are brocade or cisco specific
    /opt/storageos/bin/dbutils list NetworkSystem | grep -i brocade > /dev/null
    if [ $? -eq 0 ]
    then
	   BROCADE=1
	   secho "Found Brocade switch"
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
    	exportAddVolumesDeviceStep=VPlexDeviceController.storageViewAddVolumes
    	exportRemoveVolumesDeviceStep=VPlexDeviceController.storageViewRemoveVolumes
    	exportAddInitiatorsDeviceStep=VPlexDeviceController.storageViewAddInitiators
    	exportRemoveInitiatorsDeviceStep=VPlexDeviceController.storageViewRemoveInitiators
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

# Set all properties the way we expect them to be before we start the entire suite
# We don't expect individual tests to change these, or at least if they do, we expect
# the test to change it back.
reset_system_props() {
    reset_system_props_pre_test
    set_suspend_on_class_method "none"
    set_suspend_on_error false
    set_validation_check true
    set_validation_refresh true
    set_controller_cs_discovery_refresh_interval 60
    set_controller_discovery_refresh_interval 5
}

# Reset all of the system properties so settings are back to normal that are changed 
# during the tests themselves.
reset_system_props_pre_test() {
    set_artificial_failure "none"
}

# Clean zones from previous tests, verify no zones are on the switch
prerun_tests() {
    if [ "${SS}" != "vblock" ]; then
        clean_zones ${FC_ZONE_A:7} ${HOST1}
        verify_no_zones ${FC_ZONE_A:7} ${HOST1}
    fi
    cleanup_previous_run_artifacts
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

    VNX_PROVIDER_NAME=VNX-PROVIDER
    if [ "${SIM}" = "1" ]; then
	vnx_sim_setup
    fi

    run smisprovider create ${VNX_PROVIDER_NAME} ${VNX_SMIS_IP} ${VNX_SMIS_PORT} ${SMIS_USER} "$SMIS_PASSWD" ${VNX_SMIS_SSL}
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VNXB_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VNXB_NATIVEGUID} | grep COMPLETE | awk '{print $2}'`
    do
	run storagedevice deregister ${id}
	run storagedevice delete ${id}
    done

    run storagepool update $VNXB_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VNXB_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    driveType=""
    if [ "${SIM}" = "1" ]
    then
        driveType="--drive_type SATA"
    fi
    
    # Chose thick because we need a thick pool for VNX metas
    # Choose SATA as drive type because simulator's non-Unified pool is SATA.
    run cos create block ${VPOOL_BASE}	\
	--description Base false                \
	--protocols FC 			                \
	--numpaths 2				            \
	--multiVolumeConsistency \
	--provisionType 'Thick'	${driveType}		        \
	--max_snapshots 10                      \
	--expandable true \
	--neighborhoods $NH  

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 4				            \
	--multiVolumeConsistency \
	--provisionType 'Thick'	${driveType}		        \
	--max_snapshots 10                      \
	--expandable true \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${VNXB_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${VNXB_NATIVEGUID}
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
	--numpaths 2				            \
	--multiVolumeConsistency \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true \
	--neighborhoods $NH                    

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 4				            \
	--multiVolumeConsistency \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true \
	--neighborhoods $NH 

    run cos update block $VPOOL_BASE --storage ${UNITY_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${UNITY_NATIVEGUID}

}

vmax2_sim_setup() {
    VMAX_PROVIDER_NAME=VMAX2-PROVIDER-SIM
    VMAX_SMIS_IP=$SIMULATOR_SMIS_IP
    VMAX_SMIS_PORT=7009
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VMAX_SMIS_SSL=true
    VMAX_NATIVEGUID=$SIMULATOR_VMAX2_NATIVEGUID
    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
}

vmax2_setup() {
    SMISPASS=0

    if [ "${SIM}" = "1" ]; then
	   vmax2_sim_setup
    else
        VMAX_PROVIDER_NAME=VMAX2-PROVIDER-HW
        VMAX_NATIVEGUID=${VMAX2_DUTEST_NATIVEGUID}
    fi
 
    # do this only once
    echo "Setting up SMIS for VMAX2"
    storage_password=$SMIS_PASSWD

    run smisprovider create $VMAX_PROVIDER_NAME $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VMAX_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VMAX_NATIVEGUID} | grep COMPLETE | awk '{print $2}'`
    do
	run storagedevice deregister ${id}
	run storagedevice delete ${id}
    done

    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--multiVolumeConsistency \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH  

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--multiVolumeConsistency \
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH                  

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${VMAX_NATIVEGUID}
}

vmax3_sim_setup() {
    VMAX_PROVIDER_NAME=VMAX3-PROVIDER-SIM
    VMAX_SMIS_IP=$SIMULATOR_SMIS_IP
    VMAX_SMIS_PORT=7009
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VMAX_SMIS_SSL=true
    VMAX_NATIVEGUID=$SIMULATOR_VMAX3_NATIVEGUID
    VMAX_FAST_POLICY="${VMAX_NATIVEGUID}+${SIMULATOR_VMAX3_FAST_POLICY_SUFFIX}"
    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
}

vmax3_setup() {
    SMISPASS=0

    if [ "${SIM}" = "1" ]; then
	   vmax3_sim_setup
    else
        VMAX_PROVIDER_NAME=VMAX3-PROVIDER-HW
    fi
 
   # do this only once
    echo "Setting up SMIS for VMAX3"
    storage_password=$SMIS_PASSWD

    run smisprovider create $VMAX_PROVIDER_NAME $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VMAX_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VMAX_NATIVEGUID} | grep COMPLETE | awk '{print $2}'`
    do
	run storagedevice deregister ${id}
	run storagedevice delete ${id}
    done

    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    if [ "${SIM}" = "1" ]; then
	run storageport update $VMAX_NATIVEGUID --tzone ${NH}/${FC_ZONE_A} FC
    fi

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
  --system_type vmax                     \
  --auto_tiering_policy_name "${VMAX_FAST_POLICY}" \
	--protocols FC 			                \
	--multiVolumeConsistency \
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
  --system_type vmax                     \
  --auto_tiering_policy_name "${VMAX_FAST_POLICY}" \
	--protocols FC 			                \
	--multiVolumeConsistency \
	--numpaths 4				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${VMAX_NATIVEGUID}
}

vplex_sim_setup() {
    secho "Setting up VPLEX environment connected to simulators on: ${VPLEX_SIM_IP}"

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
    secho "Setting up the VPLEX cluster-2 virtual array $VPLEX_VARRAY2"
    run neighborhood create $VPLEX_VARRAY2

    secho "Setting up the VPLEX cluster-1 virtual array $VPLEX_VARRAY1"
    run storageport update $VPLEX_GUID FC --group director-1-1-A --addvarrays $NH
    run storageport update $VPLEX_GUID FC --group director-1-1-B --addvarrays $NH
    run storageport update $VPLEX_GUID FC --group director-1-2-A --addvarrays $VPLEX_VARRAY1
    run storageport update $VPLEX_GUID FC --group director-1-2-B --addvarrays $VPLEX_VARRAY1
    # The arrays are assigned to individual varrays as well.
    run storageport update $VPLEX_SIM_VMAX1_NATIVEGUID FC --addvarrays $NH
    run storageport update $VPLEX_SIM_VMAX2_NATIVEGUID FC --addvarrays $NH
    run storageport update $VPLEX_SIM_VMAX3_NATIVEGUID FC --addvarrays $NH
    run storageport update $VPLEX_SIM_VMAX4_NATIVEGUID FC --addvarrays $NH

    run storageport update $VPLEX_GUID FC --group director-2-1-A --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-1-B --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-2-A --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_GUID FC --group director-2-2-B --addvarrays $VPLEX_VARRAY2
    run storageport update $VPLEX_SIM_VMAX5_NATIVEGUID FC --addvarrays $NH2
    run storageport update $VPLEX_SIM_VMAX6_NATIVEGUID FC --addvarrays $NH2
    run storageport update $VPLEX_SIM_VMAX7_NATIVEGUID FC --addvarrays $NH2

    common_setup

    SERIAL_NUMBER=$VPLEX_GUID

    case "$VPLEX_MODE" in 
        local)
            secho "Setting up the virtual pool for local VPLEX provisioning"
            run cos create block $VPOOL_BASE false                            \
                             --description 'vpool-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX2_NATIVEGUID

            secho "Setting up the virtual pool for change vpool operation"
            run cos create block $VPOOL_CHANGE false                            \
                             --description 'vpool-change-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 4                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_CHANGE --storage $VPLEX_SIM_VMAX2_NATIVEGUID

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
		             --multiVolumeConsistency \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true

            secho "Setting up the virtual pool for distributed VPLEX change vpool"
            run cos create block $VPOOL_CHANGE true                                \
                             --description 'vpool-change-for-vplex-distributed-volumes'    \
                             --protocols FC                                         \
                             --numpaths 4                                           \
                             --provisionType 'Thin'                                 \
		             --multiVolumeConsistency \
                             --highavailability vplex_distributed                   \
                             --neighborhoods $VPLEX_VARRAY1 $VPLEX_VARRAY2          \
                             --haNeighborhood $VPLEX_VARRAY2                        \
                             --max_snapshots 1                                      \
                             --max_mirrors 0                                        \
                             --expandable true
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
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_BASE --storage $VPLEX_VNX1_NATIVEGUID

	    # Change vpool test
            secho "Setting up the virtual pool for local VPLEX provisioning"
            run cos create block $VPOOL_CHANGE true                            \
                             --description 'vpool-change-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 4                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
		             --multiVolumeConsistency \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_CHANGE --storage $VPLEX_VNX1_NATIVEGUID


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
		             --multiVolumeConsistency \
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

xio_sim_setup() {
    XTREMIO_PROVIDER_NAME=XIO-PROVIDER-SIM
    XTREMIO_3X_IP=$XIO_SIMULATOR_IP
    XTREMIO_PORT=$XIO_4X_SIMULATOR_PORT
    XTREMIO_NATIVEGUID=$XIO_4X_SIM_NATIVEGUID
    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
}

xio_setup() {
    # do this only once
    echo "Setting up XtremIO"
    storage_password=$XTREMIO_3X_PASSWD
    XTREMIO_PORT=443
    XTREMIO_NATIVEGUID=XTREMIO+$XTREMIO_3X_SN
    XTREMIO_PROVIDER_NAME=XIO-PROVIDER

    if [ "${SIM}" = "1" ]; then
	   xio_sim_setup
    fi    
    
    run storageprovider create ${XTREMIO_PROVIDER_NAME} $XTREMIO_3X_IP $XTREMIO_PORT $XTREMIO_3X_USER "$XTREMIO_3X_PASSWD" xtremio
    run storagedevice discover_all --ignore_error

    run storagepool update $XTREMIO_NATIVEGUID --type block --volume_type THIN_ONLY

    if [ "${SIM}" = "1" ]; then
	run storageport update $XTREMIO_NATIVEGUID --tzone ${NH}/${FC_ZONE_A} FC
    fi

    setup_varray

    run storagepool update $XTREMIO_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
        --description Base true \
        --protocols FC 			                \
        --numpaths 2				            \
        --provisionType 'Thin'			        \
        --max_snapshots 10                      \
        --multiVolumeConsistency        \
	--expandable true                       \
        --neighborhoods $NH                    

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 4				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
        --multiVolumeConsistency        \
	--expandable true                       \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${XTREMIO_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${XTREMIO_NATIVEGUID}
}

srdf_ssh(){
    SRDF_SSH_ERROR=0
    SRDF_SSH_RESULT=""
    SRDF_SSH_RESULT=$(sshpass -p $2 ssh -q -o StrictHostKeyChecking=no -o UserKnownHostsFile=no root@$1 $3) || SRDF_SSH_ERROR=$?
}

srdf_generate_rdfg_name() {
    # RDF group name maximum 10 characters
    echo $(date +"S"%m%d$RANDOM | cut -c1-10)
}

srdf_create_rdfg() {
    retry=${SRDF_RDF_CREATE_RETRY}
    while : ; do
        SRDF_USED_RDFGS=()

        srdf_get_rdfg_number
        echo "Creating RDF group ${SRDF_PROJECT} with group number ${SRDF_RDF_NUMBER}"
        srdf_run_symrdf addgrp -rdfg ${SRDF_RDF_NUMBER} -dir ${SRDF_V3_VMAXA_DIR} -remote_rdfg ${SRDF_RDF_NUMBER} -remote_sid ${SRDF_V3_VMAXB_SID} -remote_dir ${SRDF_V3_VMAXB_DIR}
        ret=${SRDF_SSH_ERROR}
        if [ $ret -eq 0 ]; then
            echo "Completed creating group"
            return
        elif [ $ret -eq 1 ]; then
            echo "Failed to create group"
            return 1
        elif [ $retry -gt 0 ]; then
            retry=$[$retry-1]
            if [ $ret -eq 2 ]; then
                sleep ${SRDF_SLEEPING_SECONDS}
            fi

            if [ $ret -eq 3 ]; then
                echo "Generate new RDF name"
                SRDF_PROJECT=$(srdf_generate_rdfg_name)
            fi

            echo "Retrying with new group name/number"
        else
            echo "Failed to create group"
            return 1
        fi
    done
}

srdf_get_rdfg_number() {
    srdf_find_rdfgs "${SRDF_V3_VMAXA_SMIS_IP}" "${SRDF_V3_VMAXA_SSH_PW}" "${SRDF_V3_VMAXA_SID}"
    srdf_find_rdfgs "${SRDF_V3_VMAXB_SMIS_IP}" "${SRDF_V3_VMAXB_SSH_PW}" "${SRDF_V3_VMAXB_SID}"

    declare -A rdfgs
    for rdfg in ${SRDF_USED_RDFGS}; do
        rdfgs[$rdfg]=1
    done

    candidates=()
    for candidate in {1..250}; do
        if [[ ! ${rdfgs[$candidate]} ]]; then
            candidates+=($candidate)
        fi
    done

    if [ ${#candidates[@]} -eq 0 ]; then
        echo "Cannot find available RGF group number"
        return 1
    fi

    SRDF_RDF_NUMBER=${candidates[$RANDOM % ${#candidates[@]}]}
}

srdf_find_rdfgs() {
    cmd="${SYMCLI_PATH}/symcfg list -sid ${3} -rdfg all | grep '^[ 0-9].*[0-9]$' | awk '{print \$1}' 2>&1"
    srdf_ssh "${1}" "${2}" "$cmd"

    if [ "${SRDF_SSH_RESULT}" != "" ]; then
        SRDF_USED_RDFGS+=(${SRDF_SSH_RESULT})
    else
        echo "Failed to get RDF groups"
        return 1
    fi
}

srdf_run_symrdf() {
    cmd="${SYMCLI_PATH}/symrdf $@ -label $SRDF_PROJECT -sid ${SRDF_V3_VMAXA_SID} -noprompt 2>&1"
    srdf_ssh "${SRDF_V3_VMAXA_SMIS_IP}" "${SRDF_V3_VMAXA_SSH_PW}" "$cmd"
    ret=${SRDF_SSH_ERROR}
    if [ $ret -ne 0 ]; then
        echo "Failed to call symrdf $1 - $SRDF_SSH_RESULT"

        is_present=$(echo ${SRDF_SSH_RESULT} | (grep "^The SYMAPI database file is already locked by another process" || echo ''))
        if [ "$is_present" != '' ]; then
            SRDF_SSH_ERROR=2
            return
        fi

        is_present=$(echo ${SRDF_SSH_RESULT} | (grep "^The specified dynamic RDF group label is in use" || echo ''))
        if [ "$is_present" != '' ]; then
           SRDF_SSH_ERROR=3
           return
        fi

        is_present=$(echo ${SRDF_SSH_RESULT} | (grep "^The .* RDF group number specified is already defined" || echo ''))
        if [ "$is_present" != '' ]; then
            SRDF_SSH_ERROR=4
            return
        fi

        SRDF_SSH_ERROR=1
        return
    fi

    is_present=$(echo ${SRDF_SSH_RESULT} | (grep "^Successfully" || echo ''))
    if [ "$is_present" == '' ]; then
        echo "Operation $1 failed for RDF group $SRDF_PROJECT - ${SRDF_SSH_RESULT}"
        SRDF_SSH_ERROR=1
    else
        echo "Operation $1 completed for RDF group ${SRDF_PROJECT}"
    fi
}


srdf_sim_setup() {
    echo "Test with simulator"

    SRDF_SLEEPING_SECONDS=1
    SRDF_PROJECT=SRDF$(shuf -i 1-29 -n 1)
    V3_SRDF_VARRAY=nh

    SRDF_V3_VMAXA_SMIS_IP=${VMAX3_SIMULATOR_SMIS_IP}
    SRDF_V3_VMAXA_SMIS_PORT=${VMAX3_SIMULATOR_SMIS_PORT}
    SRDF_V3_VMAXA_SMIS_SSL=${VMAX3_SIMULATOR_SMIS_SSL}
    SRDF_V3_VMAXA_NATIVEGUID=${VMAX3_SIMULATOR_NATIVE_GUID}
    SRDF_V3_VMAXB_NATIVEGUID=${VMAX3_SIMULATOR_R2_NATIVE_GUID}

    SRDF_V3_VMAXA_FAST_POLICY="${SRDF_V3_VMAXA_NATIVEGUID}+${SIMULATOR_VMAX3_FAST_POLICY_SUFFIX}"
    SRDF_V3_VMAXB_FAST_POLICY="${SRDF_V3_VMAXB_NATIVEGUID}+${SIMULATOR_VMAX3_FAST_POLICY_SUFFIX}"
}

srdf_setup() {
    # do this only once
    echo "Setting up SRDF"
    SRDF_V3_VMAXA_SMIS_DEV=SRDF-V3-VMAX-1-SIM
    SRDF_V3_VMAXA_SID=${SRDF_V3_VMAXA_NATIVEGUID:10:24}
    SRDF_V3_VMAXB_SID=${SRDF_V3_VMAXB_NATIVEGUID:10:24}
    SRDF_SSH_ERROR=0
    SRDF_SSH_RESULT=""
    SRDF_USED_RDFGS=()
    SRDF_RDF_NUMBER=1
    SRDF_RDF_CREATE_RETRY=1 # number of times for retry in case of group name/number collision


    if [ "${SIM}" = "1" ]; then
	   srdf_sim_setup
    else
        echo "Executing SRDF group setup"
        SRDF_PROJECT=$(srdf_generate_rdfg_name)
        # create RDF group first, SRDF_PROJECT may be changed in the call
        srdf_create_rdfg
        sleep 1
        # COP-25856 RDF group is not available 3 minutes after created via symcli, add more time
        sleep 300
    fi

    run project create $SRDF_PROJECT --tenant $TENANT
    PROJECT=$SRDF_PROJECT

    storage_password=$SMIS_PASSWD

    smisprovider show $SRDF_V3_VMAXA_SMIS_DEV &> /dev/null && return $?
    run smisprovider create $SRDF_V3_VMAXA_SMIS_DEV $SRDF_V3_VMAXA_SMIS_IP $SRDF_V3_VMAXA_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $SRDF_V3_VMAXA_SMIS_SSL

    if [ "${SIM}" != "1" ]; then
        smisprovider show $SRDF_V3_VMAXB_SMIS_DEV &> /dev/null && return $?
        run smisprovider create $SRDF_V3_VMAXB_SMIS_DEV $SRDF_V3_VMAXB_SMIS_IP $SRDF_V3_VMAXB_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $SRDF_V3_VMAXB_SMIS_SSL
    fi

    run storagedevice discover_all --ignore_error

    run storagepool update $SRDF_V3_VMAXA_NATIVEGUID --type block --volume_type THIN_ONLY
    run storagepool update $SRDF_V3_VMAXA_NATIVEGUID --type block --volume_type THICK_ONLY

    setup_varray

    run storagepool update $SRDF_V3_VMAXA_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`

    echo "SRDF V3 Virtual Pool setup" 	

    # Create the target first so it exists when we create the source vpool
    # Workaround for COP-25718, switch to use matchedPools once it is fixed
    run cos create block ${VPOOL_BASE}_SRDF_TARGET		          \
	--description 'Target-Virtual-Pool-for-V3-SRDF-Protection' false \
	--auto_tiering_policy_name "${SRDF_V3_VMAXB_FAST_POLICY}" \
			 --protocols FC 		          \
			 --numpaths 1				  \
			 --max_snapshots 10 			  \
			 --provisionType 'Thin'	          \
			 --neighborhoods $NH                      \
                         --multiVolumeConsistency                  \
                         --system_type vmax			
    
    run cos update block ${VPOOL_BASE}_SRDF_TARGET --storage ${SRDF_V3_VMAXB_NATIVEGUID}
    run cos allow ${VPOOL_BASE}_SRDF_TARGET block $TENANT

    # As above, but without multivolume consistency
    run cos create block ${VPOOL_BASE_NOCG}_SRDF_TARGET		          \
	--description 'Target-Virtual-Pool-for-V3-SRDF-Protection' false \
	--auto_tiering_policy_name "${SRDF_V3_VMAXB_FAST_POLICY}" \
			 --protocols FC 		          \
			 --numpaths 1				  \
			 --max_snapshots 10 			  \
			 --provisionType 'Thin'	          \
			 --neighborhoods $NH                      \
                         --system_type vmax

    run cos update block ${VPOOL_BASE_NOCG}_SRDF_TARGET --storage ${SRDF_V3_VMAXB_NATIVEGUID}
    run cos allow ${VPOOL_BASE_NOCG}_SRDF_TARGET block $TENANT

    case "$SRDF_MODE" in 
        async)
            echo "Setting up the virtual pool for SRDF async mode"
    	    run cos create block ${VPOOL_BASE}                 \
			 --description 'Source-Virtual-Pool-for-Async-SRDF-Protection' true \
			 --auto_tiering_policy_name "${SRDF_V3_VMAXA_FAST_POLICY}" \
			 --protocols FC 		        \
			 --numpaths 1				\
			 --max_snapshots 10			\
	                 --provisionType 'Thin'	        \
                         --system_type vmax                     \
                         --multiVolumeConsistency		\
			 --neighborhoods $NH                    \
			 --srdf "${NH}:${VPOOL_BASE}_SRDF_TARGET:ASYNCHRONOUS"

    	   run cos update block ${VPOOL_BASE} --storage ${SRDF_V3_VMAXA_NATIVEGUID}
           run cos allow ${VPOOL_BASE} block $TENANT

           echo "Setting up the virtual pool for SRDF async mode (nocg)"
    	    run cos create block ${VPOOL_BASE_NOCG}                 \
			 --description 'Source-Virtual-Pool-for-Async-SRDF-Protection' true \
			 --auto_tiering_policy_name "${SRDF_V3_VMAXA_FAST_POLICY}" \
			 --protocols FC 		        \
			 --numpaths 1				\
			 --max_snapshots 10			\
	                 --provisionType 'Thin'	        \
                         --system_type vmax                     \
			 --neighborhoods $NH                    \
			 --srdf "${NH}:${VPOOL_BASE_NOCG}_SRDF_TARGET:ASYNCHRONOUS"

    	   run cos update block ${VPOOL_BASE_NOCG} --storage ${SRDF_V3_VMAXA_NATIVEGUID}
           run cos allow ${VPOOL_BASE_NOCG} block $TENANT
	;; 
	sync)
        echo "Setting up the virtual pool for SRDF sync mode"
	    run cos create block ${VPOOL_BASE}                 \
		 	 --description 'Source-Virtual-Pool-for-Sync-SRDF-Protection' true \
		 	 --auto_tiering_policy_name "${SRDF_V3_VMAXA_FAST_POLICY}" \
			 --protocols FC 		        \
			 --numpaths 1				\
			 --max_snapshots 10			\
	                 --provisionType 'Thin'	        \
                         --system_type vmax                     \
                         --multiVolumeConsistency		\
			 --neighborhoods $NH                    \
			 --srdf "${NH}:${VPOOL_BASE}_SRDF_TARGET:SYNCHRONOUS"

	    run cos update block ${VPOOL_BASE} --storage ${SRDF_V3_VMAXA_NATIVEGUID}
	    run cos allow ${VPOOL_BASE} block $TENANT

	    echo "Setting up the virtual pool for SRDF sync mode (nocg)"
	    run cos create block ${VPOOL_BASE_NOCG}                 \
		 	 --description 'Source-Virtual-Pool-for-Sync-SRDF-Protection' true \
		 	 --auto_tiering_policy_name "${SRDF_V3_VMAXA_FAST_POLICY}" \
			 --protocols FC 		        \
			 --numpaths 1				\
			 --max_snapshots 10			\
	                 --provisionType 'Thin'	        \
                         --system_type vmax                     \
			 --neighborhoods $NH                    \
			 --srdf "${NH}:${VPOOL_BASE_NOCG}_SRDF_TARGET:SYNCHRONOUS"

	    run cos update block ${VPOOL_BASE_NOCG} --storage ${SRDF_V3_VMAXA_NATIVEGUID}
	    run cos allow ${VPOOL_BASE_NOCG} block $TENANT
	;;
	*)
            secho "Invalid SRDF_MODE: $SRDF_MODE (should be 'sync' or 'async')"
            Usage
        ;;
    esac

    echo "Setting up the virtual pool for VPool change (Add SRDF)"
    run cos create block ${VPOOL_CHANGE}                 \
         --description 'Source-Virtual-Pool-for-VPoolChange' false \
         --auto_tiering_policy_name "${SRDF_V3_VMAXA_FAST_POLICY}" \
         --protocols FC 		        \
         --numpaths 1				\
         --max_snapshots 10			\
         --provisionType 'Thin'	        \
         --system_type vmax                     \
         --multiVolumeConsistency		\
         --neighborhoods $NH

    run cos update block ${VPOOL_CHANGE} --storage ${SRDF_V3_VMAXA_NATIVEGUID}
    run cos allow ${VPOOL_CHANGE} block $TENANT
}

host_setup() {
    run project create $PROJECT --tenant $TENANT 
    secho "Project $PROJECT created."
    secho "Setup ACLs on neighborhood for $TENANT"
    run neighborhood allow $NH $TENANT

    run transportzone add $NH/${FC_ZONE_A} $H1PI1
    run transportzone add $NH/${FC_ZONE_A} $H1PI2
    run transportzone add $NH/${FC_ZONE_A} $H1PI3
    run transportzone add $NH/${FC_ZONE_A} $H1PI4
    run transportzone add $NH/${FC_ZONE_A} $H2PI1
    run transportzone add $NH/${FC_ZONE_A} $H2PI2
    run transportzone add $NH/${FC_ZONE_A} $H2PI3
    run transportzone add $NH/${FC_ZONE_A} $H2PI4
    run transportzone add $NH/${FC_ZONE_A} $H3PI1
    run transportzone add $NH/${FC_ZONE_A} $H3PI2

    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        run cluster create --project ${PROJECT} ${CLUSTER} ${TENANT}

        run hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        run initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        run initiator create ${HOST1} FC $H1PI2 --node $H1NI2
        run initiator create ${HOST1} FC $H1PI3 --node $H1NI3
        run initiator create ${HOST1} FC $H1PI4 --node $H1NI4

        run hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        run initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        run initiator create ${HOST2} FC $H2PI2 --node $H2NI2
        run initiator create ${HOST2} FC $H2PI3 --node $H2NI3
        run initiator create ${HOST2} FC $H2PI4 --node $H2NI4
    else
        run hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0
        run initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        run initiator create ${HOST1} FC $H1PI2 --node $H1NI2
        run initiator create ${HOST1} FC $H1PI4 --node $H1NI3
        run initiator create ${HOST1} FC $H1PI2 --node $H1NI4

        run hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0
        run initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        run initiator create ${HOST2} FC $H2PI2 --node $H2NI2
        run initiator create ${HOST2} FC $H2PI3 --node $H2NI3
        run initiator create ${HOST2} FC $H2PI4 --node $H2NI4
    fi
}

linux_setup() {
    if [ "${SIM}" != "1" ]; then
        secho "Setting up Linux hardware host"
        run hosts create linuxhost1 $TENANT Linux ${LINUX_HOST_IP} --port ${LINUX_HOST_PORT} --username ${LINUX_HOST_USERNAME} --password ${LINUX_HOST_PASSWORD} --discoverable true
    fi
}

windows_setup() {
    if [ "${SIM}" == "1" ]; then
        secho "Setting up Windows simulator host"
        WINDOWS_HOST_IP=winhost1
        WINDOWS_HOST_PORT=$WINDOWS_SIMULATOR_PORT
        WINDOWS_HOST_USERNAME=$WINDOWS_SIMULATOR_USERNAME
        WINDOWS_HOST_PASSWORD=$WINDOWS_SIMULATOR_PASSWORD 
    fi

    run hosts create winhost1 $TENANT Windows ${WINDOWS_HOST_IP} --port ${WINDOWS_HOST_PORT} --username ${WINDOWS_HOST_USERNAME} --password ${WINDOWS_HOST_PASSWORD} --discoverable true 

    if [ "${SIM}" == "1" ]; then
        run transportzone add $NH/${FC_ZONE_A} "00:00:00:00:00:00:00:11"
        run transportzone add $NH/${FC_ZONE_A} "00:00:00:00:00:00:00:12"
        run transportzone add $NH/${FC_ZONE_A} "00:00:00:00:00:00:00:13"
        run transportzone add $NH/${FC_ZONE_A} "00:00:00:00:00:00:00:14"
    fi 
}

hpux_setup() {
    if [ "${SIM}" != "1" ]; then
        secho "Setting up HP-UX hardware host"
        run hosts create hpuxhost1 $TENANT HPUX ${HPUX_HOST_IP} --port ${HPUX_HOST_PORT} --username ${HPUX_HOST_USERNAME} --password ${HPUX_HOST_PASSWORD} --discoverable true 
    else
        secho "HP-UX simulator does not exist!  Failing."
    fi 
}

vcenter_setup() {
    if [ "${SIM}" = "1" ]; then
        vcenter_sim_setup
    else    
        secho "Setup virtual center real hardware..."
        runcmd vcenter create vcenter1 ${TENANT} ${VCENTER_HW_IP} ${VCENTER_HW_PORT} ${VCENTER_HW_USERNAME} ${VCENTER_HW_PASSWORD}                
    fi
}

vcenter_sim_setup() {
    secho "Setup virtual center sim..."
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
    windows_setup;
    hpux_setup;
    linux_setup;
}

setup_varray() {
    run neighborhood create $NH
    run transportzone assign ${FC_ZONE_A} ${NH}
}

setup() {
    storage_type=$1;
    if [ "${SS}" = "srdf" ]; then
        storage_type="vmax3";
    fi


    # Reset the simulator if requested.
    if [ ${RESET_SIM} = "1" ]; then
	reset_simulator;
    fi

    syssvc $SANITY_CONFIG_FILE localhost setup
    security add_authn_provider ldap ldap://${LOCAL_LDAP_SERVER_IP} cn=manager,dc=viprsanity,dc=com secret ou=ViPR,dc=viprsanity,dc=com uid=%U CN Local_Ldap_Provider VIPRSANITY.COM ldapViPR* SUBTREE --group_object_classes groupOfNames,groupOfUniqueNames,posixGroup,organizationalRole --group_member_attributes member,uniqueMember,memberUid,roleOccupant
    tenant create $TENANT VIPRSANITY.COM OU VIPRSANITY.COM
    echo "Tenant $TENANT created."
    # Increase allocation percentage
    syssvc $SANITY_CONFIG_FILE localhost set_prop controller_max_thin_pool_subscription_percentage 600
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check true

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" -o "${SS}" = "srdf" ]; then
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

    if [ "${SIM}" != "1" -a "${SS}" != "vblock" ]; then
	run networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false
	BROCADE=1;
    elif [ "${SS}" = "vblock" ]; then
        secho "Configure vblock switches"
    else
	FABRIC_SIMULATOR=fabric-sim
	if [ "${SS}" = "vplex" ]; then
	    secho "Configuring MDS/Cisco Simulator using SSH on: $VPLEX_SIM_MDS_IP"
	    run networksystem create $FABRIC_SIMULATOR mds --devip $VPLEX_SIM_MDS_IP --devport 22 --username $VPLEX_SIM_MDS_USER --password $VPLEX_SIM_MDS_PW
	else
	    secho "Configuring MDS/Cisco Simulator using SSH on: $SIMULATOR_CISCO_MDS"
	    run networksystem create $FABRIC_SIMULATOR mds --devip $SIMULATOR_CISCO_MDS --devport 22 --username $SIMULATOR_CISCO_MDS_USER --password $SIMULATOR_CISCO_MDS_PW
	fi
    fi

    ${SS}_setup

    run cos allow $VPOOL_BASE block $TENANT
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
    if [ "$1" = "none" ]; then
        # Reset the failure injection occurence counter
        run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure_counter_reset "true"
    else
        # Start incrementing the failure occurence counter for this injection point
        run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure_counter_reset "false"                                                                                                                                             
    fi
        
    run syssvc $SANITY_CONFIG_FILE localhost set_prop artificial_failure "$1"
}

set_controller_cs_discovery_refresh_interval() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop controller_cs_discovery_refresh_interval $1
}

set_controller_discovery_refresh_interval() {
    run syssvc $SANITY_CONFIG_FILE localhost set_prop controller_discovery_refresh_interval $1
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

# SRDF Test 0
#
# Test existing functionality of creating a simple SRDF volume and verify that tests are ready to run.
#
test_0_srdf() {
    echot "Test 0 for SRDF begins"
    item=${RANDOM}
    volname=${VOLNAME}-${item}
    echo "Creating a single SRDF ${SRDF_MODE} mode volume - ${volname}"
    run volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
    sleep 1
    # Remove the volume
    echo "Deleting the volume"
    runcmd volume delete ${PROJECT}/${volname} --wait        
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

# Given a DB snap file, filter out any backend VPLEX masks.
# A VPLEX backend mask would have label VPLEX_xxxx_xxxx_CLX_*
# Arguments:
# 1) snap slot number.
filter_backend_vplex_masks() {
  if [ "${SS}" != "vplex" ]
  then
    return 1
  fi

  slot=$1
  column_families="ExportMask ExportGroup"

  rem_leading_spaces="| sed -e 's/^\s*//' "
  grep_by_id_and_label="| grep -E '^label = VPLEX_([[:digit:]]{4}_){2}_|id: ' "
  grep_by_id="| grep -E '^id: ' "
  print_id="| awk '{ print \$NF }' "

  for cf in ${column_families}
  do
    result_file="results/${item}/${cf}-${slot}.txt"

    # Skip if the file does not exist.
    [ ! -f ${result_file} ] && continue

    execute="cat ${result_file} ${rem_leading_spaces} ${grep_by_id_and_label} ${grep_by_id} ${print_id}"
    ids=`eval $execute`
    
    for id in $ids
    do
      tmp=/tmp/result-$RANDOM
      cat ${result_file} | sed "/^id: $id/,/^\s*$/d" > $tmp
      mv $tmp ${result_file}
    done
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

# Verify the failures in the variable were actually hit when the job ran.
verify_failures() {
    INVOKE_FAILURE_FILE=/opt/storageos/logs/invoke-test-failure.log
    FAILURES=${1}

    # cat ${INVOKE_FAILURE_FILE}

    for failure_check in `echo ${FAILURES} | sed 's/:/ /g'`
    do
        
    # Remove any trailing &# used to represent specific failure occurrences    
    failure_check=${failure_check%&*}
            
	grep ${failure_check} ${INVOKE_FAILURE_FILE} > /dev/null
	if [ $? -ne 0 ]; then
	    secho 
	    secho "FAILED: Failure injection ${failure_check} was not encountered during the operation execution."
	    secho 
	    incr_fail_count
	fi
    done

    # delete the invoke test failure file.  It will get recreated.
    rm -f ${INVOKE_FAILURE_FILE}
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
                               failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	# Would love to have injections in the vplex package itself somehow, but hard to do since I stuck InvokeTestFailure in controller,
	# which depends on vplex project, not the other way around.
	storage_failure_injections="failure_004:failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers \
                                    failure_045_VPlexDeviceController.createVirtualVolume_before_create_operation \
                                    failure_046_VPlexDeviceController.createVirtualVolume_after_create_operation \
                                    failure_004:failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                                    failure_004:failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                                    failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation"
    fi

    if [ "${SS}" = "vmax3" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool \
	                            failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch"
    fi

    if [ "${SS}" = "vnx" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyElementFromStoragePool \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch"
    fi

    if [ "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_004:failure_040_XtremIOStorageDeviceController.doDeleteVolume_before_delete_volume \
                                    failure_004:failure_041_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume"
    fi

    if [ "${SS}" = "srdf" ]
    then
	common_failure_injections="failure_004_final_step_in_workflow_complete \
			       failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete
                               failure_074_SRDFDeviceController.createSRDFVolumePairStep_before_link_create \
                               failure_075_SRDFDeviceController.createSRDFVolumePairStep_after_link_create \
                               failure_004:failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback \
                               failure_004:failure_077_SRDFDeviceController.rollbackSRDFLinksStep_after_link_rollback"

	storage_failure_injections="failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool \
	                            failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
		                    failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
	                            failure_015_SmisCommandHelper.invokeMethod_CreateElementReplica"
    fi


    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation"

    if [ "${SS}" = "vplex" ]
    then
      cfs=("Volume ExportGroup ExportMask FCZoneReference")
    else
      cfs=("Volume")
    fi

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 1 with failure scenario: ${failure}..."
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"
      filter_backend_vplex_masks 1

      #For XIO, before failure 6 is invoked the task would have completed successfully
      if [ "${SS}" = "xio" -a "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        # Remove the volume
        runcmd volume delete ${PROJECT}/${volname} --wait
      else
        # If this is a rollback inject, make sure we get the "additional message"
        echo ${failure} | grep failure_004 | grep ":" > /dev/null

        if [ $? -eq 0 ]
        then
          # Make sure it fails with additional errors accounted for in the error message
          fail -with_error "Additional errors occurred" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        else
          # Create the volume
          fail volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        fi

        # Verify injected failures were hit
        verify_failures ${failure}

        # Let the async jobs calm down
        sleep 5
      fi

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}" 
      filter_backend_vplex_masks 2

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none

      # Determine if re-running the command under certain failure scenario's is expected to fail (like Unity) or succeed.
      if [ "${SS}" = "unity" ] && [ "${failure}" = "failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete"  -o "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
          # Unity is expected to fail because the array doesn't like duplicate LUN names
          fail -with_error "LUN with this name already exists" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
          # TODO Delete the original volume
      else
          runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
          # Remove the volume
          runcmd volume delete ${PROJECT}/${volname} --wait
      fi

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"
      filter_backend_vplex_masks 3

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}

      # Report results
      report_results test_1 ${failure}
    done
}

# Test 2
#
# Test creating a volume in a CG and verify the DB is in an expected state after it fails due to injected failure at end of workflow
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
test_2() {
    echot "Test 2 Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	# Would love to have injections in the vplex package itself somehow, but hard to do since I stuck InvokeTestFailure in controller,
	# which depends on vplex project, not the other way around.
	storage_failure_injections="failure_004:failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers \
                                    failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
                                    failure_045_VPlexDeviceController.createVirtualVolume_before_create_operation \
                                    failure_046_VPlexDeviceController.createVirtualVolume_after_create_operation \
                                    failure_004:failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                                    failure_004:failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                                    failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation"
    fi

    if [ "${SS}" = "vmax3" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"
    fi

    # VMAX3 only failure injections
    if [ "${SS}" = "vmax3" ]
    then
      storage_failure_injections="${storage_failure_injections} \
                                  failure_004:failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries"
    fi

    if [ "${SS}" = "vnx" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyElementFromStoragePool \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch"
    fi

    if [ "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_004:failure_040_XtremIOStorageDeviceController.doDeleteVolume_before_delete_volume \
                                    failure_004:failure_041_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume"
    fi

    if [ "${SS}" = "srdf" ]
    then
    # Ensure empty RDF groups
    volume delete --project ${PROJECT} --wait

	common_failure_injections="failure_004_final_step_in_workflow_complete \
			       failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete
                               failure_078_SRDFDeviceController.createSrdfCgPairsStep_before_cg_pairs_create \
                               failure_079_SRDFDeviceController.createSrdfCgPairsStep_after_cg_pairs_create \
                               failure_004:failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback \
                               failure_004:failure_077_SRDFDeviceController.rollbackSRDFLinksStep_after_link_rollback"

	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
	                            failure_015_SmisCommandHelper.invokeMethod_CreateGroupReplica \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"    
    fi


    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool"

    if [ "${SS}" = "vplex" ]
    then
      cfs=("Volume ExportGroup ExportMask BlockConsistencyGroup FCZoneReference")
    else
      cfs=("Volume BlockConsistencyGroup")
    fi

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 2 with failure scenario: ${failure}..."
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Create a new CG
      CGNAME=wf-test2-cg-${item}
      if [ "${SS}" = "srdf" ]
      then
          CGNAME=cg${item}
      fi

      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"
      filter_backend_vplex_masks 1

      #For XIO, before failure 6 is invoked the task would have completed successfully
      if [ "${SS}" = "xio" -a "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        # Remove the volume
        runcmd volume delete ${PROJECT}/${volname} --wait
      else
        # If this is a rollback inject, make sure we get the "additional message"
        echo ${failure} | grep failure_004 | grep ":" > /dev/null

        if [ $? -eq 0 ]
        then
          # Make sure it fails with additional errors accounted for in the error message
          fail -with_error "Additional errors occurred" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        else
          # Create the volume
          fail volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        fi

        # Verify injected failures were hit
        verify_failures ${failure}

        if [ "${SS}" = "srdf" ]
        then
            # run discovery to update RemoteDirectorGroups
            runcmd storagedevice discover_all
        fi

        # Let the async jobs calm down
        sleep 5
      fi

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"
      filter_backend_vplex_masks 2

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none

      # Should be able to delete the CG and recreate it.
      runcmd blockconsistencygroup delete ${CGNAME}

      if [ "${failure}" = "failure_004:failure_015_SmisCommandHelper.invokeMethod_DeleteGroup" ]; then
        CGNAME=wf-test2-cg-${item}1
        if [ "${SS}" = "srdf" ]
        then
          CGNAME=cg${item}1
        fi
      fi 

      # Re-create the consistency group
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"
      filter_backend_vplex_masks 3

      # Determine if re-running the command under certain failure scenario's is expected to fail (like Unity) or succeed.
      if [ "${SS}" = "unity" ] && [ "${failure}" = "failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete"  -o "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
          # Unity is expected to fail because the array doesn't like duplicate LUN names
          fail -with_error "LUN with this name already exists" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
          # TODO Delete the original volume
      else
          runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
          # Remove the volume
          runcmd volume delete ${PROJECT}/${volname} --wait
      fi

      # Perform any DB validation in here
      snap_db 4 "${cfs[@]}"
      filter_backend_vplex_masks 4

      # Validate nothing was left behind
      validate_db 3 4 ${cfs}

      runcmd blockconsistencygroup delete ${CGNAME}
    
      # Report results
      report_results test_2 ${failure}
    done
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

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation"
    fi

    if [ "${SS}" = "vmax3" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool"
    fi

    if [ "${SS}" = "vnx"  ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyElementFromStoragePool"
    fi

    if [ "${SS}" = "unity" ]
    then
	storage_failure_injections="failure_022_VNXeStorageDevice_CreateVolume_before_async_job \
                                    failure_023_VNXeStorageDevice_CreateVolume_after_async_job"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004"

    if [ "${SS}" = "vplex" ]
    then
      cfs=("Volume ExportGroup ExportMask FCZoneReference")
    else
      cfs=("Volume")
    fi

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 3 with failure scenario: ${failure}..."
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      project=${PROJECT}-${item}
      reset_counts

      # Create the project
      runcmd project create ${project} --tenant $TENANT
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"
      filter_backend_vplex_masks 1

      # If this is a rollback inject, make sure we get the "additional message"
      echo ${failure} | grep failure_004 | grep ":" > /dev/null

      if [ $? -eq 0 ]
      then
        # Make sure it fails with additional errors accounted for in the error message
        fail -with_error "Additional errors occurred" volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8
      else
        # Create the volume
        fail volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8
      fi

      # Verify injected failures were hit
      verify_failures ${failure}

      # Let the async jobs calm down
      sleep 5

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"
      filter_backend_vplex_masks 2

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none
      # Determine if re-running the command under certain failure scenarios is expected to fail (like Unity) or succeed.
      if [ "${SS}" = "unity" ] && [ "${failure}" = "failure_023_VNXeStorageDevice_CreateVolume_after_async_job" ]
      then
          # Unity is expected to fail because the array doesn't like duplicate LUN names
          fail -with_error "LUN with this name already exists" volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8
          # TODO Delete the original volume
      else
        runcmd volume create ${volname} ${project} ${NH} ${VPOOL_BASE} 1GB --count 8
        # Remove the volume
        runcmd volume delete --project ${project} --wait
      fi

      # Delete the project
      runcmd project delete ${project}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"
      filter_backend_vplex_masks 3

      # Validate nothing was left behind
      validate_db 2 3 ${cfs}

      # Report results
      report_results test_3 ${failure}
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

    common_failure_injections="failure_047_NetworkDeviceController.zoneExportMaskCreate_before_zone \
                               failure_048_NetworkDeviceController.zoneExportMaskCreate_after_zone \
                               failure_004_final_step_in_workflow_complete \
                               failure_004:failure_020_Export_zoneRollback_before_delete \
                               failure_004:failure_021_Export_zoneRollback_after_delete"


    network_failure_injections=""
    if [ "${BROCADE}" = "1" ]
    then
	network_failure_injections="failure_049_BrocadeNetworkSMIS.getWEBMClient"
    fi

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_004:failure_084_VPlexDeviceController.deleteStorageView_before_delete"
    fi 

    if [ "${SS}" = "vnx" ]
    then
        storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateStorageHardwareID \
                                    failure_004:failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
                                    failure_004:failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    if [ "${SS}" = "unity" ]; then
      storage_failure_injections="failure_004:failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections} ${network_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_004:failure_084_VPlexDeviceController.deleteStorageView_before_delete"

    for failure in ${failure_injections}
    do
      clean_zones ${FC_ZONE_A:7} ${HOST1}
      prerun_tests
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 4 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the export that doesn't exist
      snap_db 1 "${cfs[@]}"

      # If this is a rollback inject, make sure we get the "additional message"
      echo ${failure} | grep failure_004 | grep ":" > /dev/null

      if [ $? -eq 0 ]
      then
	  # Make sure it fails with additional errors accounted for in the error message
	  fail -with_error "Additional errors occurred" export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
      else
	  # Create the export
	  fail export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
      fi

      # Verify injected failures were hit
      verify_failures ${failure}

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Remove the export
      runcmd export_group delete $PROJECT/${expname}1
      
      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 3 "${cfs[@]}"

      # Report results
      report_results test_4 ${failure}
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

    common_failure_injections=""

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_084_VPlexDeviceController.deleteStorageView_before_delete"
    fi

    if [ "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    if [ "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    if [ "${SS}" = "vnx" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteProtocolController \
                                    failure_015_SmisCommandHelper.invokeMethod_DeleteStorageHardwareID \
	                            failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections} ${network_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_AddMembers"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 5 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Check the state of the export that it doesn't exist
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Delete the export
      fail export_group delete $PROJECT/${expname}1

      # Verify injected failures were hit
      verify_failures ${failure}

      # Don't validate in here.  It's a delete operation and it's allowed to leave things behind, as long as the retry cleans it up.

      # Rerun the command
      set_artificial_failure none
      runcmd export_group delete $PROJECT/${expname}1

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_5 ${failure}
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

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "unity" ]
    then
	storage_failure_injections="failure_004:failure_017_Export_doRemoveVolume"
    fi

    if [ "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_004:failure_017_Export_doRemoveVolume \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers"
    fi

    if [ "${SS}" = "xio" ]
    then
        storage_failure_injections="failure_052_XtremIOExportOperations.runLunMapCreationAlgorithm_before_addvolume_to_lunmap \
                                    failure_053_XtremIOExportOperations.runLunMapCreationAlgorithm_after_addvolume_to_lunmap"
    fi


    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 6 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Snap the state before the export group was created
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

      # Snap the DB state with the export group created
      snap_db 2 "${cfs[@]}" "| grep -v existingVolumes"

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # If this is a rollback inject, make sure we get the "additional message"
      echo ${failure} | grep failure_004 | grep ":" > /dev/null

      if [ $? -eq 0 ]
      then
	  # Make sure it fails with additional errors accounted for in the error message
      	  fail -with_error "Additional errors occurred" export_group update ${PROJECT}/${expname}1 --addVol ${PROJECT}/${VOLNAME}-2
      else
	  # Delete the export
	  fail export_group update ${PROJECT}/${expname}1 --addVol ${PROJECT}/${VOLNAME}-2
      fi

      # Verify injected failures were hit
      verify_failures ${failure}

      # Validate nothing was left behind
      snap_db 3 "${cfs[@]}" "| grep -v existingVolumes"
      validate_db 2 3 "${cfs[@]}"

      # rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --addVol ${PROJECT}/${VOLNAME}-2

      # Delete the export group
      runcmd export_group delete ${PROJECT}/${expname}1

      # Validate the DB is back to its original state
      snap_db 4 "${cfs[@]}"
      validate_db 1 4 "${cfs[@]}"

      # Report results
      report_results test_6 ${failure}
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

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_004:failure_016_Export_doRemoveInitiator"

    network_failure_injections="failure_058_NetworkDeviceController.zoneExportAddInitiators_before_zone"
    if [ "${BROCADE}" = "1" ]
    then
	network_failure_injections="failure_049_BrocadeNetworkSMIS.getWEBMClient"
    fi

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_004:failure_020_Export_zoneRollback_before_delete \
                                    failure_004:failure_021_Export_zoneRollback_after_delete \
                                    failure_060_VPlexDeviceController.storageViewAddInitiators_storageview_nonexisting"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" -o "${SS}" = "unity" ]
    then
	storage_failure_injections="failure_004:failure_024_Export_zone_removeInitiator_before_delete \
                                    failure_004:failure_025_Export_zone_removeInitiator_after_delete"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections} ${network_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_016_Export_doRemoveInitiator"

    zone2new="true"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 7 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Check the state of the export that it doesn't exist
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Exclusive --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1}/${H1PI1}"

      # Verify the zone names, as we know them, are on the switch
      zone1=`get_zone_name ${HOST1} ${H1PI1}`
      if [[ -z $zone1 ]]; then
        echo -e "\e[91mERROR\e[0m: Could not find a zone corresponding to host ${HOST1} and initiator ${initiator}"
        incr_fail_count
      else
        # Only verify that the zone exists on the switch if the zone is newly created.  
        # Newly created zone names will contain the current host name.  
        if newly_created_zone_for_host $zone1 $HOST1; then  
            verify_zone ${zone1} ${FC_ZONE_A} exists
        fi      
      fi    
      
      # Snsp the DB so we can validate after failures later
      snap_db 2 "${cfs[@]}"

      # Strip out colons for array helper command
      h1pi2=`echo ${H1PI2} | sed 's/://g'`

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # If this is a rollback inject, make sure we get the "additional message"
      echo ${failure} | grep failure_004 | grep ":" > /dev/null

      if [ $? -eq 0 ]
      then
	      # Make sure it fails with additional errors accounted for in the error message
      	  fail -with_error "Additional errors occurred" export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}
      else
	      # Attempt to add an initiator
	      fail export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}
      fi
      
      if [ "${failure}" = "failure_004:failure_024_Export_zone_removeInitiator_before_delete" -o \
           "${failure}" = "failure_004:failure_016_Export_doRemoveInitiator" ]; then
          # This specific failure does not rollback the zone for host initiator 2.  Therefore from this point on, the zone
          # will be existing and all verfications to see if zone 2 has been removed can be skipped.
          zone2new="false"
      fi

      # Verify injected failures were hit
      verify_failures ${failure}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 3 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}

      # Verify the zone for host initiator 2
      zone2=`get_zone_name ${HOST1} ${H1PI2}`
      if [[ -z $zone2 ]]; then
        echo -e "\e[91mERROR\e[0m: Could not find a ViPR zone corresponding to host ${HOST1} and initiator ${H1PI2}. COP-30518 has been created to track this issue"
        incr_fail_count
      else
        # Verify the zone exists
        verify_zone ${zone2} ${FC_ZONE_A} exists
      fi

      # Perform any DB validation in here
      snap_db 4 "${cfs[@]}"

      # Delete the export
      runcmd export_group delete ${PROJECT}/${expname}1

      # Only verify the zone has been removed if it is a newly created zone
      if [ "${zone1}" != "" ]; then
          # Only verify that the zone is removed from the switch if the zone was newly created.  
          # Newly created zone names will contain the current host name.    
          if newly_created_zone_for_host $zone1 $HOST1; then
              verify_zone ${zone1} ${FC_ZONE_A} gone    
          fi          
      fi
       
      if [ "${zone2}" != "" ]; then
          if newly_created_zone_for_host $zone2 $HOST1; then
              if [ "${zone2new}" = "true" ]; then
                  # Only verify that the zone is removed from the switch if the zone was newly created.  
                  verify_zone ${zone2} ${FC_ZONE_A} gone  
              else
                  echo "Skipping check to see if zone ${zone2} has been removed because it is an existing zone"       
              fi
          else
              echo "Skipping check to see if zone ${zone2} has been removed because it is an existing zone"            
          fi    
      fi          

      # Verify the DB is back to the original state
      snap_db 5 "${cfs[@]}"
      validate_db 1 5 "${cfs[@]}"

      # Report results
      report_results test_7 ${failure}
    done
}

# Test 8 (volume create with metas)
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
test_8() {
    echot "Test 8 Begins"

    if [ "${SS}" != "vmax2" -a "${SS}" != "vnx" ]
    then
	echo "Test case only executes for vmax2 and vnx."
	return;
    fi

    common_failure_injections="failure_004_final_step_in_workflow_complete"
    meta_size=260GB

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                                    failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                                    failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation"
    fi

    if [ "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_GetCompositeElements \
                                    failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyCompositeElement \
                                    failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                                    failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete"
    fi

    if [ "${SS}" = "vnx" ]
    then
	if [ "${SIM}" != "1" ]
	then
	    meta_size=280GB
	fi
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyElementFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_CreateOrModifyCompositeElement"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 1 with failure scenario: ${failure}..."
      if [ "${SS}" = "vplex" ]
      then
	  cfs=("Volume ExportGroup ExportMask FCZoneReference")
      else
	  cfs=("Volume")
      fi
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}

      # run discovery to ensure we have enough space for the upcoming allocation
      runcmd storagedevice discover_all

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"

      #For XIO, before failure 6 is invoked the task would have completed successfully
      if [ "${SS}" = "xio" -a "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
	  runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} ${meta_size}
	  # Remove the volume
      	  runcmd volume delete ${PROJECT}/${volname} --wait
      else
      	  # If this is a rollback inject, make sure we get the "additional message"
	  echo ${failure} | grep failure_004 | grep ":" > /dev/null

	  if [ $? -eq 0 ]
	  then
	      # Make sure it fails with additional errors accounted for in the error message
	      fail -with_error "Additional errors occurred" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} ${meta_size}
	  else
      	      # Create the volume
	      fail volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} ${meta_size}
	  fi

	  # Verify injected failures were hit
	  verify_failures ${failure}

      	  # Let the async jobs calm down
      	  sleep 5
      fi

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none

      # run discovery to ensure we have enough space for the upcoming allocation
      runcmd storagedevice discover_all

      # Determine if re-running the command under certain failure scenario's is expected to fail (like Unity) or succeed.
      if [ "${SS}" = "unity" ] && [ "${failure}" = "failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete"  -o "${failure}" = "failure_006_BlockDeviceController.createVolumes_after_device_create" ]
      then
          # Unity is expected to fail because the array doesn't like duplicate LUN names
          fail -with_error "LUN with this name already exists" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} ${meta_size}
          # TODO Delete the original volume
      else
          runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} ${meta_size}
          # Remove the volume
          runcmd volume delete ${PROJECT}/${volname} --wait
      fi

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 3 "${cfs[@]}"

      # Report results
      report_results test_8 ${failure}
    done
}

# Test 9
#
# Test deleting a volume that is in a CG, making sure we can retry if failures occur during processing.
#
# 1. Save off state of DB (1)
# 2. Create a CG in ViPR
# 2. Perform volume create operation that will fail at the end of execution (and other locations)
# 3. Inject a failure at a location that will cause the delete to fail
# 4. Run the volume delete, verify failure and injection was hit
# 5. Retry operation without failure injection
# 7. Save off state of DB (2)
# 8. Compare state (1) and (2)
#
test_9() {
    echot "Test 9 Begins"

    # Typically we have failure_004 here, but in the case of delete, there's no real rollback.
    common_failure_injections=""

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation"
    fi

    if [ "${SS}" = "unity" ]
    then
	storage_failure_injections="failure_034_VNXUnityBlockStorageDeviceController.doDeleteVolume_before_remove_from_cg \
                                    failure_037_VNXUnityBlockStorageDeviceController.doDeleteVolume_after_delete_volume_cg_version \
                                    failure_035_VNXUnityBlockStorageDeviceController.doDeleteVolume_before_delete_volume \
                                    failure_036_VNXUnityBlockStorageDeviceController.doDeleteVolume_after_delete_volume"
    fi

    if [ "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_038_XtremIOStorageDeviceController.doDeleteVolume_before_remove_from_cg \
	                            failure_039_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume \
                                    failure_040_XtremIOStorageDeviceController.doDeleteVolume_before_delete_volume \
                                    failure_041_XtremIOStorageDeviceController.doDeleteVolume_after_delete_volume"
    fi

    if [ "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                                    failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries \
	                            failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
	                            failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_DeleteGroup"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesFromExportMask_before_operation"

    if [ "${SS}" = "vplex" ]
    then
      cfs=("Volume ExportGroup ExportMask BlockConsistencyGroup")
    else
      cfs=("Volume BlockConsistencyGroup")
    fi

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 9 with failure scenario: ${failure}..."
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}

      # Create a new CG
      CGNAME=wf-test2-cg-${item}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"
      filter_backend_vplex_masks 1

      # Create the CG
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Create the volume in a CG (unless it's a couple special cases where we need to test w/o CG)
      if [ "${failure}" = "failure_035_VNXUnityBlockStorageDeviceController.doDeleteVolume_before_delete_volume" -o "${failure}" = "failure_036_VNXUnityBlockStorageDeviceController.doDeleteVolume_after_delete_volume" ]
      then
	  runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
      else
	  runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
      fi

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"
      filter_backend_vplex_masks 2

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Remove the volume
      fail volume delete ${PROJECT}/${volname} --wait

      # Turn on failure at a specific point
      set_artificial_failure none

      # Remove the volume
      if [ "${failure}" != "failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries" ]; then
          runcmd volume delete ${PROJECT}/${volname} --wait
      fi

      # Remove the CG object
      runcmd blockconsistencygroup delete ${CGNAME}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"
      filter_backend_vplex_masks 3

      # Validate nothing was left behind
      validate_db 1 3 "${cfs[@]}"

      # Report results
      report_results test_9 ${failure}
    done
}

# Test 10
#
# Test removing volumes while injecting several different failures that cause the job to not get
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
test_10() {
    echot "Test 10 Begins"
    expname=${EXPORT_GROUP_NAME}t10

    common_failure_injections="failure_004_final_step_in_workflow_complete failure_firewall"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "unity" -o "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_017_Export_doRemoveVolume"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_017_Export_doRemoveVolume \
                                    failure_015_SmisCommandHelper.invokeMethod_*"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004"
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_*"

    for failure in ${failure_injections}
    do
      # By default, turn off the firewall test as it takes a really long time to run.
      firewall_test=0
      if [ "${failure}" = "failure_firewall" ]
      then
	  # Find the IP address we need to firewall
	  if [ "${SIM}" = "1" ]
	  then
	      firewall_ip=${HW_SIMULATOR_IP}
	  elif [ "${SS}" = "unity" ]
	  then
	      firewall_ip=${UNITY_IP}
	  else
	      secho "Firewall testing disabled for combo of ${SS} with simualtor=${SIM}"
	      continue;
	  fi
      fi

      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 10 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Snap the state before the export group was created
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

      # Turn on suspend of export after orchestration
      set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

      # Run the export group command
      runcmd_suspend test_10 export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2
      
      if [ "${failure}" = "failure_firewall" ]
      then
	  # turn on firewall
	  runcmd /usr/sbin/iptables -I INPUT 1 -s ${firewall_ip} -p all -j REJECT
      fi
	
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Resume the workflow
      runcmd workflow resume $workflow

      # Turn on suspend of export after orchestration
      set_suspend_on_class_method none

      # Follow the task.  It should fail because of Poka Yoke validation
      echo "*** Following the export_group update task to verify it FAILS because of firewall"
      fail task follow $task

      if [ "${failure}" = "failure_firewall" ]
      then
	  # turn off firewall
	  runcmd /usr/sbin/iptables -D INPUT 1
      elif [ "${failure}" != "failure_015_SmisCommandHelper.invokeMethod_*" ]
      then
	  # Verify injected failures were hit
	  verify_failures ${failure}
      fi

      # rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --remVol ${PROJECT}/${VOLNAME}-2

      # Delete the export group
      runcmd export_group delete ${PROJECT}/${expname}1

      # Validate the DB is back to its original state
      snap_db 2 "${cfs[@]}"
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_10 ${failure}
    done
}

# Test 11
#
# Test increasing max path while injecting several different failures that cause the job to not get
# done, or not get done effectively enough.
#
# 1. Save off state of DB (1)
# 2. Export a volume to a host
# 3. Save off state of DB (2)
# 4. Perform change vpool operation to increase max path that will fail at the end of execution (and other locations)
# 5. Save off state of DB (3)
# 6. Compare state (2) and (3)
# 7. Retry operation without failure injection
# 8. Save off state of DB (4)
# 9. Compare state (1) and (4)
#
test_11() {
    echot "Test 11 Begins"
    expname=${EXPORT_GROUP_NAME}t11

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_058_NetworkDeviceController.zoneExportAddInitiators_before_zone \
                               failure_059_NetworkDeviceController.zoneExportAddInitiators_after_zone"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_003_late_in_add_initiator_to_mask \
                                    failure_083_VPlexDeviceController_late_in_add_targets_to_view"
    fi

    if [ "${SS}" = "unity" -o "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_003_late_in_add_initiator_to_mask"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_*"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_083_VPlexDeviceController_late_in_add_targets_to_view"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 11 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB

      # Check the state of the export that it doesn't exist
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname} $NH --type Host --volspec ${PROJECT}/${volname} --hosts "${HOST1}"

      # Snsp the DB so we can validate after failures later
      snap_db 2 "${cfs[@]}"

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Attempt to change vpool
      fail volume change_cos ${PROJECT}/${volname} ${VPOOL_CHANGE}

      # Verify injected failures were hit
      verify_failures ${failure}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 3 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none
      runcmd volume change_cos ${PROJECT}/${volname} ${VPOOL_CHANGE}

      # Delete the export
      runcmd export_group delete ${PROJECT}/${expname}

      # Verify the DB is back to the original state
      snap_db 4 "${cfs[@]}"
      validate_db 1 4 "${cfs[@]}"

      # Remove the volume
      runcmd volume delete ${PROJECT}/${volname} --wait

      # Report results
      report_results test_11 ${failure}
    done
}

# Test 12
#
# Test to ensure rerunnability of a failed remove host operation (export group update remove host)
#
# 1. Save off state of DB (1)
# 2. Export a volume to two hosts
# 4. Inject a failure in the upcoming remove host operation
# 5. Run export group update remove host, expect failure
# 7. Retry operation without failure injection
# 8. Save off state of DB (2)
# 9. Compare state (1) and (4)
#
test_12() {
    echot "Test 12 Begins"
    expname=${EXPORT_GROUP_NAME}t12

    common_failure_injections="failure_004_final_step_in_workflow_complete"
    # Removing check for firewall as a regular test because it takes a very long time, but it is supported.
    # common_failure_injections="failure_004_final_step_in_workflow_complete \
    #                           failure_firewall"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "unity" -o "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_016_Export_doRemoveInitiator"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_016_Export_doRemoveInitiator \
                                    failure_024_Export_zone_removeInitiator_before_delete \
                                    failure_025_Export_zone_removeInitiator_after_delete \
                                    failure_015_SmisCommandHelper.invokeMethod_*"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_024_Export_zone_removeInitiator_before_delete"

    for failure in ${failure_injections}
    do
      firewall_test=1
      if [ "${failure}" = "failure_firewall" ]
      then
	  # Find the IP address we need to firewall
	  if [ "${SIM}" = "1" ]
	  then
	      firewall_ip=${HW_SIMULATOR_IP}
	  elif [ "${SS}" = "unity" ]
	  then
	      firewall_ip=${UNITY_IP}
	  else
	      secho "Firewall testing disabled for combo of ${SS} with simualtor=${SIM}"
	      continue;
	  fi
      fi

      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 12 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Snap the state before the export is created
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Exclusive --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --inits "${HOST1}/${H1PI1},${HOST1}/${H1PI2}"

      # Remove an initiator
      runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

      # Snap the state after the initiator is removed so we have something to compare against
      snap_db 2 "${cfs[@]}"

      # Readd it
      runcmd export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}
      
      # Turn on suspend of export after orchestration
      set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

      # Run the export group command
      runcmd_suspend test_12 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

      if [ "${failure}" = "failure_firewall" ]
      then
	  # turn on firewall
	  runcmd /usr/sbin/iptables -I INPUT 1 -s ${firewall_ip} -p all -j REJECT
      fi
	
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Turn off suspend of export after orchestration
      set_suspend_on_class_method none

      # Resume the workflow
      runcmd workflow resume $workflow

      # Follow the task. 
      echo "*** Following the export_group update task to verify it FAILS because of injection or firewall..."
      fail task follow $task

      if [ "${failure}" = "failure_firewall" ]
      then
	  # turn off firewall
	  runcmd /usr/sbin/iptables -D INPUT 1
      else
	  # Verify injected failures were hit
	  verify_failures ${failure}
      fi

      # rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --remInits ${HOST1}/${H1PI1}

      # Validate the DB is back to the state before we added init 1
      snap_db 3 "${cfs[@]}"
      validate_db 2 3 "${cfs[@]}"

      # Delete the export group
      runcmd export_group delete ${PROJECT}/${expname}1

      # Validate the DB is back to its original state
      snap_db 4 "${cfs[@]}"
      validate_db 1 4 "${cfs[@]}"

      # Report results
      report_results test_12 ${failure}
    done
}

# Test 13
#
# Test to ensure rerunnability of a failed remove host operation (export group update remove host)
#
# 1. Save off state of DB (1)
# 2. Export a volume to two hosts
# 4. Inject a failure in the upcoming remove host operation
# 5. Run export group update remove host, expect failure
# 7. Retry operation without failure injection
# 8. Save off state of DB (2)
# 9. Compare state (1) and (4)
#
test_13() {
    echot "Test 13 Begins"
    expname=${EXPORT_GROUP_NAME}t13

    common_failure_injections="failure_004_final_step_in_workflow_complete"
    # Removing check for firewall as a regular test because it takes a very long time, but it is supported.
    # common_failure_injections="failure_004_final_step_in_workflow_complete \
    #                           failure_firewall"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "unity" -o "${SS}" = "xio" ]
    then
	storage_failure_injections="failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
      storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_*"
    fi

    if [ "${SS}" = "vnx" ]
    then
      storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_* \
                                 failure_018_Export_doRollbackExportCreate_before_delete"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_018_Export_doRollbackExportCreate_before_delete"

    for failure in ${failure_injections}
    do
      firewall_test=1
      if [ "${failure}" = "failure_firewall" ]
      then
	  # Find the IP address we need to firewall
	  if [ "${SIM}" = "1" ]
	  then
	      firewall_ip=${HW_SIMULATOR_IP}
	  elif [ "${SS}" = "unity" ]
	  then
	      firewall_ip=${UNITY_IP}
	  else
	      secho "Firewall testing disabled for combo of ${SS} with simualtor=${SIM}"
	      continue;
	  fi
      fi

      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 13 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Snap the state before the export is created
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CLUSTER}"

      # Snap the state before the second host was added.  This is the state we expect after successful retry of remHost later.
      runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOST2}
      snap_db 2 "${cfs[@]}"

      # Add the second host back
      runcmd export_group update $PROJECT/${expname}1 --addHosts ${HOST2}

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Run the export group command
      fail export_group update $PROJECT/${expname}1 --remHosts ${HOST2}

      # Turn off suspend of export after orchestration
      set_suspend_on_class_method none

      if [ "${failure}" = "failure_firewall" ]
      then
	  # turn off firewall
	  runcmd /usr/sbin/iptables -D INPUT 1
      else 
	  # Verify injected failures were hit
	  verify_failures ${failure}
      fi

      # rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOST2}

      # Validate the DB is back to the state before we added host2
      snap_db 3 "${cfs[@]}"
      validate_db 2 3 "${cfs[@]}"

      # Delete the export group
      runcmd export_group delete ${PROJECT}/${expname}1

      # Validate the DB is back to its original state
      snap_db 4 "${cfs[@]}"
      validate_db 1 4 "${cfs[@]}"

      # Report results
      report_results test_13 ${failure}
    done
}

# Test 14
#
# Test deleting CGs that require deleting the CG on the array, failure, and retry-ability
#
# 1. Save off state of DB (1)
# 2. Create a CG, create two volumes
# 3. Delete the volumes
# 4. Set artificial failure to fail delete of CG
# 5. Shut off failure injection
# 6. Retry delete CG operation
# 7. Save off state of DB (2)
# 8. Compare state (1) and (2)
#
test_14() {
    echot "Test 14 Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete"

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_085_VPlexApiDiscoveryManager_find_consistency_group \
                                    failure_086_BlockDeviceController.deleteReplicationGroupInCG_BeforeDelete"
    elif [ "${SS}" = "vmax3" -o "${SS}" = "vmax2" ]
    then
	storage_failure_injections="failure_086_BlockDeviceController.deleteReplicationGroupInCG_BeforeDelete"
    elif [ "${SS}" = "vmax3" ]
    then
      storage_failure_injections="failure_086_BlockDeviceController.deleteReplicationGroupInCG_BeforeDelete"
    elif [ "${SS}" = "vnx" ]
    then
	storage_failure_injections=""
    elif [ "${SS}" = "xio" ]
    then
	storage_failure_injections=""
    elif [ "${SS}" = "srdf" ]
    then
	storage_failure_injections=""
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_086_BlockDeviceController.deleteReplicationGroupInCG_BeforeDelete"

    cfs=("Volume BlockConsistencyGroup")

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 14 with failure scenario: ${failure}..."
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      
      # Create a new CG
      CGNAME=wf-test2-cg-${item}
      if [ "${SS}" = "srdf" ]
      then
          CGNAME=cg${item}
      fi

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"

      # Create the CG
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Make sure it fails with additional errors accounted for in the error message
      runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count=2 --consistencyGroup=${CGNAME}

      # Delete one volume
      runcmd volume delete ${PROJECT}/${volname}-1 --wait

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Delete the other volume
      fail volume delete ${PROJECT}/${volname}-2 --wait

      # Try to delete the CG.  A failure will occur.
      fail blockconsistencygroup delete ${CGNAME}

      # Shut off the artifical failure
      set_artificial_failure none

      # In some failure cases, the volume is not actually deleted.  It would be ideal if the Volume record
      # persisted if the volume delete operation failed, but for this test case, it's the replication group
      # that wasn't deleted, so the Volume record goes away and the BlockConsistencyGroup artifact is left
      # behind.  Best to check to see if the volume is still there.
      runcmd volume show ${PROJECT}/${volname}-2
      if [ $? -eq 0 ]
      then
	  # Volume remained after failed volume delete.  Delete now.
	  runcmd volume delete ${PROJECT}/${volname}-2 --vipronly --wait
      fi

      # Retry the command after removing the artificial failure
      runcmd blockconsistencygroup delete ${CGNAME}

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 1 ${cfs}

      # Report results
      report_results test_14 ${failure}
    done
}

test_vpool_change_add_srdf() {
    echot "Test VPool Change for Adding SRDF Begins"

    # Setup
    cfs=("Volume")
    item=${RANDOM}
    volname="test-add_srdf-${item}"
    mkdir -p results/${item}
    snap_db_esc=" | grep -Ev \"^Volume:|srdfLinkStatus = OTHER|personality = null\""

    # Failures
    failure_injections="failure_004_final_step_in_workflow_complete \
        failure_015_SmisCommandHelper.invokeMethod_* \
        failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
        failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
        failure_015_SmisCommandHelper.invokeMethod_CreateElementReplica \
        failure_004_final_step_in_workflow_complete:failure_015_SmisCommandHelper.invokeMethod_ModifyListSynchronization \
        failure_004_final_step_in_workflow_complete:failure_015_SmisCommandHelper.invokeMethod_RemoveMembers"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_*"

    for failure in ${failure_injections}
    do
        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_CHANGE} 1GB

        # Turn on failure at a specific point
        set_artificial_failure ${failure}

        # Snap the state before the vpool change
        snap_db 1 "${cfs[@]}" "${snap_db_esc}"

        fail volume change_cos ${PROJECT}/${volname} ${VPOOL_BASE_NOCG}

        # Verify injected failures were hit
	    verify_failures ${failure}

        # Validate the DB is back to the original state
        snap_db 2 "${cfs[@]}" "${snap_db_esc}"
        validate_db 1 2 "${cfs[@]}"

        # Turn off failures
        set_artificial_failure none

        # Rerun the command
        runcmd volume change_cos ${PROJECT}/${volname} ${VPOOL_BASE_NOCG}

        # Remove the volume
        runcmd volume delete ${PROJECT}/${volname} --wait

        # Report results
        report_results test_vpool_change_add_srdf ${failure}
    done
}

test_vpool_change_add_srdf_cg() {
    echot "Test VPool Change for Adding SRDF in CG Begins"
    # Ensure empty RDF groups
    volume delete --project ${PROJECT} --wait
    # Setup
    cfs=("Volume BlockConsistencyGroup RemoteDirectorGroup")
    item=${RANDOM}
    volname="test-add_srdf-${item}"
    mkdir -p results/${item}
    snap_db_esc=" | grep -Ev \"^Volume:|srdfLinkStatus = OTHER|personality = null\""

    # Create a new CG
    CGNAME=cg${item}

    runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

    # Failures
    failure_injections="failure_004_final_step_in_workflow_complete \
        failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
        failure_015_SmisCommandHelper.invokeMethod_* \
        failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
        failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
        failure_015_SmisCommandHelper.invokeMethod_CreateGroupReplica \
        failure_004_final_step_in_workflow_complete:failure_015_SmisCommandHelper.invokeMethod_ModifyReplicaSynchronization"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_015_SmisCommandHelper.invokeMethod_ModifyReplicaSynchronization"

    for failure in ${failure_injections}
    do
        secho "Running Test with failure scenario: ${failure}..."
        item=${RANDOM}
        volname="test-add_srdf-${item}"
        mkdir -p results/${item}

        # Create a new CG
        CGNAME=cg${item}
        runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

        #Create a non SRDF volume in CG
        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_CHANGE} 1GB --consistencyGroup=${CGNAME}

        # Turn on failure at a specific point
        set_artificial_failure ${failure}

        # Snap the state before the vpool change
        snap_db 1 "${cfs[@]}" "${snap_db_esc}"

        fail volume change_cos ${PROJECT}/${volname} ${VPOOL_BASE}

        # Verify injected failures were hit
	verify_failures ${failure}

        # Validate the DB is back to the original state
        snap_db 2 "${cfs[@]}" "${snap_db_esc}"
        validate_db 1 2 "${cfs[@]}"

        # Turn off failures
        set_artificial_failure none

        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all

        # Rerun the command
        runcmd volume change_cos ${PROJECT}/${volname} ${VPOOL_BASE}

        # Remove the volume
        runcmd volume delete ${PROJECT}/${volname} --wait

        #Remove the CG
        runcmd blockconsistencygroup delete ${CGNAME}

        # Report results
        report_results test_vpool_change_add_srdf_cg ${failure}
    done
}

test_add_to_existing_cg_srdf() {
    _add_to_cg_srdf "existing"
}

test_add_to_empty_cg_srdf() {
    # Ensure empty RDF groups
    volume delete --project ${PROJECT} --wait
    _add_to_cg_srdf "empty"
}

# Main logic for add_to_[empty|existing]_cg_srdf.
# Used by test_add_to_existing_cg_srdf, test_add_to_empty_cg_srdf
_add_to_cg_srdf() {
    srdf_cg_test=$1
    echot "Test add_to_${srdf_cg_test}_cg_srdf Begins"

    if [ "${SS}" != "srdf" ]
    then
        echo "Skipping non-srdf system"
        return
    fi

    # FIXME: 076 requires manual cleanup of the RDF groups, so run this last.
    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete
                               failure_078_SRDFDeviceController.createSrdfCgPairsStep_before_cg_pairs_create \
                               failure_004:failure_077_SRDFDeviceController.rollbackSRDFLinksStep_after_link_rollback \
                               failure_004:failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback"

    # Test specific failures (empty or existing)
    empty_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroupReplica \
                              failure_078_SRDFDeviceController.createSrdfCgPairsStep_before_cg_pairs_create \
                              failure_079_SRDFDeviceController.createSrdfCgPairsStep_after_cg_pairs_create"
    existing_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateListReplica"

    failure_injections="${common_failure_injections}"
    [ "${srdf_cg_test}" = "empty" ] && failure_injections="${failure_injections} ${empty_failure_injections}"
    [ "${srdf_cg_test}" = "existing" ] && failure_injections="${failure_injections} ${existing_failure_injections}"

    cfs=("Volume BlockConsistencyGroup RemoteDirectorGroup")
    snap_db_esc=" | grep -Ev \"^sourceGroup = null|targetGroup = null\""
    symm_sid=`storagedevice list | grep SYMM | tail -n1 | awk -F' ' '{print $2}' | awk -F'+' '{print $2}'`

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_004:failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test add_to_cg_srdf with failure scenario: ${failure}..."
      reset_counts
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      # Create a new CG
      CGNAME=ib${item}

      if [ "${SIM}" = "0" ]
      then
        symhelper.sh cleanup_rdfg ${symm_sid} ${PROJECT}
      fi
      # run discovery to update RemoteDirectorGroups
      runcmd storagedevice discover_all

      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      if [ "${srdf_cg_test}" = "existing" ]
      then
        runcmd volume create ${volname}-existing ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all
      fi

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}" "${snap_db_esc}"

      # If this is a rollback inject, make sure we get the "additional message"
      echo ${failure} | grep failure_004 | grep ":" > /dev/null

      if [ $? -eq 0 ]
      then
        # Make sure it fails with additional errors accounted for in the error message
        fail -with_error "Additional errors occurred" volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
      else
        # Create the volume
        fail volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
      fi

      # Verify injected failures were hit
      verify_failures ${failure}

      # run discovery to update RemoteDirectorGroups
      runcmd storagedevice discover_all

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}" "${snap_db_esc}"

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Rerun the command
      set_artificial_failure none

      if [ "${srdf_cg_test}" = "existing" ]
      then
        runcmd volume delete ${PROJECT}/${volname}-existing --wait
      fi

      # Should be able to delete the CG and recreate it.
      runcmd blockconsistencygroup delete ${CGNAME}

      if [ "${failure}" = "failure_004:failure_076_SRDFDeviceController.rollbackSRDFLinksStep_before_link_rollback" ]
      then
        # At this point, the volumes have been created in their respective CGs.  Instead of simply retrying, the
        # user must instead discover & ingest the volumes (target first).  Then add the source volume into the source
        # CG; ViPR will know to place the source volume into the source CG and recreate the target CG for the target
        # volume.
        echo "failure_076 requires ingestion as the retry method, so skipping automated recreation steps"

        # Report results
        report_results "test_add_to_${srdf_cg_test}_cg_srdf" ${failure}
        continue
      fi

      # Let providers sync up...
      echo "Sleeping to allow for providers to sync up"
      sleep 60

      # Change the CG name, but re-use the same RDF group.
      item=${RANDOM}
      volname=${VOLNAME}-${item}
      # Create a new CG
      CGNAME=ib${item}

      # Re-create the consistency group
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      if [ "${srdf_cg_test}" = "existing" ]
      then
        runcmd volume create ${volname}-existing ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all
      fi

      runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
      # Remove the volume
      runcmd volume delete ${PROJECT}/${volname} --wait

      # run discovery to update RemoteDirectorGroups
      runcmd storagedevice discover_all

      if [ "${srdf_cg_test}" = "existing" ]
      then
        runcmd volume delete ${PROJECT}/${volname}-existing --wait
      fi

      runcmd blockconsistencygroup delete ${CGNAME}

      # Report results
      report_results "test_add_to_${srdf_cg_test}_cg_srdf" ${failure}
    done
}

# Test test_delete_srdf
#
# Test deleting SRDF volumes whilst injecting various failures, then test the retryability.
#
# 1. Create an SRDF volume
# 2. Save off state of DB (1)
# 3. Set artificial failure to fail the operation
# 4. Delete the SRDF volume pair
# 5. Save off the state of the DB (2)
# 6. Compare state (1) and (2)
# 7. Retry delete operation
test_delete_srdf() {
    echot "Test test_delete_srdf Begins"

    if [ "${SS}" != "srdf" ]
    then
        echo "Skipping non-srdf system"
        return
    fi

    common_failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes&1 \
                        failure_087_BlockDeviceController.before_doDeleteVolumes&2 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&1 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&2 \
                        failure_089_SRDFDeviceController.before_doSuspendLink \
                        failure_090_SRDFDeviceController.after_doSuspendLink \
                        failure_091_SRDFDeviceController.before_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                        failure_092_SRDFDeviceController.after_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"

    async_only="failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
                failure_015_SmisCommandHelper.invokeMethod_ModifyReplicaSynchronization"
    sync_only="failure_015_SmisCommandHelper.invokeMethod_ModifyListSynchronization"

    cfs=("Volume")
    snap_db_esc=" | grep -Ev \"srdfLinkStatus = |^Volume\:|accessState = READWRITE|accessState = NOT_READY\""
    symm_sid=`storagedevice list | grep SYMM | tail -n1 | awk -F' ' '{print $2}' | awk -F'+' '{print $2}'`

    if [ "${SRDF_MODE}" = "async" ];
    then
      failure_injections="${common_failure_injections} ${async_only}"
    else
      failure_injections="${common_failure_injections} ${sync_only}"
    fi

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes"

    for failure in ${failure_injections}
    do
        item=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${item}.log
        secho "Running Test test_delete_srdf with failure scenario: ${failure}..."
        reset_counts
        mkdir -p results/${item}
        volname=${VOLNAME}-${item}

        # Clear existing volumes
        cleanup_srdf $PROJECT

        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all

        snap_db 1 "${cfs[@]}" "${snap_db_esc}"

        set_artificial_failure ${failure}

        # Remove the volume
        if [ "${failure}" = "failure_088_BlockDeviceController.after_doDeleteVolumes&1" -o \
               "${failure}" = "failure_088_BlockDeviceController.after_doDeleteVolumes&2" -o \
               "${failure}" = "failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData" -o \
               "${failure}" = "failure_090_SRDFDeviceController.after_doSuspendLink" -o \
               "${failure}" = "failure_092_SRDFDeviceController.after_doDetachLink" ]
        then
          # Failure on GetDefaultReplicationSettingData is ignored.
          # failure_90, failure_092 occurs at the idempotent-friendly SRDF phase, where enough has already happened for volume deletion to succeed.
          # failure_088 is expected to pass due to the asynchronous job taking control of the completer status.
          runcmd volume delete ${PROJECT}/${volname} --wait

          # Verify injected failures were hit
          verify_failures ${failure}

          set_artificial_failure none
        else
          fail volume delete ${PROJECT}/${volname} --wait

          runcmd storagedevice discover_all
          snap_db 2 "${cfs[@]}" "${snap_db_esc}"

          # Verify injected failures were hit
          verify_failures ${failure}

          set_artificial_failure none

          # No sense in validating db for failure_087, since source or target would have been deleted.
          if [ "${failure}" = "failure_087_BlockDeviceController.before_doDeleteVolumes&1" -o "${failure}" = "failure_087_BlockDeviceController.before_doDeleteVolumes&2" ]
          then
            # failure_087 leaves the source or target behind, so we expect to be able to retry deleting whichever one was left behind.
            runcmd volume delete --project ${PROJECT} --wait
          else
            # Validate volume was left for retry
            validate_db 1 2 ${cfs}

            # Retry the delete operation on remaining source volumes
            for vol in `volume list $PROJECT | grep "SOURCE" | awk '{ print $7}'`
            do
              runcmd volume delete $vol --wait
            done
          fi
        fi

        if [ "${SIM}" = "0" ]
        then
          symhelper.sh cleanup_rdfg ${symm_sid} ${PROJECT}
        fi

        report_results "test_delete_srdf" ${failure}
    done
}

# Test test_delete_srdf_cg_last_vol
#
# Test deleting SRDF CG volumes whilst injecting various failures, then test the retryability.
#
# 1. Create an SRDF volume in a CG
# 2. Save off state of DB (1)
# 3. Set artificial failure to fail the operation
# 4. Delete the SRDF volume pair
# 5. Save off the state of the DB (2)
# 6. Compare state (1) and (2)
# 7. Retry delete operation
test_delete_srdf_cg_last_vol() {
    echot "Test test_delete_srdf_cg_last_vol Begins"

    if [ "${SS}" != "srdf" ]
    then
        echo "Skipping non-srdf system"
        return
    fi

    # Clear existing volumes
    cleanup_srdf $PROJECT

    common_failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes&1 \
                        failure_087_BlockDeviceController.before_doDeleteVolumes&2 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&1 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&2 \
                        failure_089_SRDFDeviceController.before_doSuspendLink \
                        failure_090_SRDFDeviceController.after_doSuspendLink \
                        failure_091_SRDFDeviceController.before_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                        failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                        failure_092_SRDFDeviceController.after_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_ModifyReplicaSynchronization \
                        failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"

    async_only="failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData"

    cfs=("Volume BlockConsistencyGroup")
    snap_db_esc=" | grep -Ev \"srdfLinkStatus = |^Volume\:|accessState = READWRITE|accessState = NOT_READY\""
    symm_sid=`storagedevice list | grep SYMM | tail -n1 | awk -F' ' '{print $2}' | awk -F'+' '{print $2}'`

    if [ "${SRDF_MODE}" = "async" ];
    then
      failure_injections="${common_failure_injections} ${async_only}"    
    fi

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes"

    for failure in ${failure_injections}
    do
        item=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${item}.log
        secho "Running Test test_delete_srdf_cg_last_vol with failure scenario: ${failure}..."
        reset_counts
        mkdir -p results/${item}
        volname=${VOLNAME}-${item}
        # Create a new CG
        CGNAME=cg${item}

        runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all

        snap_db 1 "${cfs[@]}" "${snap_db_esc}"

        set_artificial_failure ${failure}

        # Remove the volume
        if [ "${failure}" = "failure_090_SRDFDeviceController.after_doSuspendLink" -o "${failure}" = "failure_092_SRDFDeviceController.after_doDetachLink" ];
        then
          runcmd volume delete ${PROJECT}/${volname} --wait

          # Verify injected failures were hit
          verify_failures ${failure}
        else
          fail volume delete ${PROJECT}/${volname} --wait

          runcmd storagedevice discover_all
          snap_db 2 "${cfs[@]}" "${snap_db_esc}"

          # Verify injected failures were hit
          verify_failures ${failure}

          # Validate volume was left for retry
          validate_db 1 2 ${cfs}

          set_artificial_failure none

          # Retry the delete operation
          runcmd volume delete ${PROJECT}/${volname} --wait
        fi

        if [ "${SIM}" = "0" ]
        then
          symhelper.sh cleanup_rdfg ${symm_sid} ${PROJECT}
        fi

        runcmd blockconsistencygroup delete ${CGNAME}
        report_results "test_delete_srdf_cg_last_vol" ${failure}
    done
}

# Test test_delete_srdf_cg_vol
#
# Test deleting SRDF CG volumes whilst injecting various failures, then test the retryability.
#
# 1. Create an SRDF volume in a CG
# 2. Save off state of DB (1)
# 3. Set artificial failure to fail the operation
# 4. Delete the SRDF volume pair
# 5. Save off the state of the DB (2)
# 6. Compare state (1) and (2)
# 7. Retry delete operation
test_delete_srdf_cg_vol() {
    echot "Test test_delete_srdf_cg_vol Begins"

    if [ "${SS}" != "srdf" ]
    then
        echo "Skipping non-srdf system"
        return
    fi

    # Clear existing volumes
    cleanup_srdf $PROJECT

    common_failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes&1 \
                        failure_087_BlockDeviceController.before_doDeleteVolumes&2 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&1 \
                        failure_088_BlockDeviceController.after_doDeleteVolumes&2 \
                        failure_089_SRDFDeviceController.before_doSuspendLink \
                        failure_090_SRDFDeviceController.after_doSuspendLink \
                        failure_091_SRDFDeviceController.before_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                        failure_092_SRDFDeviceController.after_doDetachLink \
                        failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"

    async_only="failure_015_SmisCommandHelper.invokeMethod_GetDefaultReplicationSettingData \
                failure_015_SmisCommandHelper.invokeMethod_ModifyReplicaSynchronization"
    sync_only="failure_015_SmisCommandHelper.invokeMethod_ModifyListSynchronization"

    cfs=("Volume BlockConsistencyGroup")
    snap_db_esc=" | grep -Ev \"srdfLinkStatus = |^Volume\:|accessState = READWRITE|accessState = NOT_READY\""
    symm_sid=`storagedevice list | grep SYMM | tail -n1 | awk -F' ' '{print $2}' | awk -F'+' '{print $2}'`

    if [ "${SRDF_MODE}" = "async" ];
    then
      failure_injections="${common_failure_injections} ${async_only}"
    else
      failure_injections="${common_failure_injections} ${sync_only}"
    fi

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_087_BlockDeviceController.before_doDeleteVolumes"
    
    random=${RANDOM}
    cgvolname=${VOLNAME}-${random}
    # Create a new CG
    CGNAME=cg${random}

    runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

    runcmd volume create ${cgvolname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}

    for failure in ${failure_injections}
    do
        item=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${item}.log
        secho "Running Test test_delete_srdf_cg_vol with failure scenario: ${failure}..."
        reset_counts
        mkdir -p results/${item}
        volname=${VOLNAME}-${item}

        runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --consistencyGroup=${CGNAME}
        # run discovery to update RemoteDirectorGroups
        runcmd storagedevice discover_all

        snap_db 1 "${cfs[@]}" "${snap_db_esc}"

        set_artificial_failure ${failure}

        # Remove the volume
        if [ "${failure}" = "failure_090_SRDFDeviceController.after_doSuspendLink" -o "${failure}" = "failure_092_SRDFDeviceController.after_doDetachLink" ];
        then
          runcmd volume delete ${PROJECT}/${volname} --wait

          # Verify injected failures were hit
          verify_failures ${failure}
        else
          fail volume delete ${PROJECT}/${volname} --wait

          runcmd storagedevice discover_all
          snap_db 2 "${cfs[@]}" "${snap_db_esc}"

          # Verify injected failures were hit
          verify_failures ${failure}

          # Validate volume was left for retry
          validate_db 1 2 ${cfs}

          set_artificial_failure none

          # Retry the delete operation
          runcmd volume delete ${PROJECT}/${volname} --wait
        fi

        if [ "${SIM}" = "0" ]
        then
          symhelper.sh cleanup_rdfg ${symm_sid} ${PROJECT}
        fi

        report_results "test_delete_srdf_cg_vol" ${failure}
    done
    #Cleanup the remaining CG vol and CG
    runcmd volume delete ${PROJECT}/${cgvolname} --wait
    runcmd blockconsistencygroup delete ${CGNAME}

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
    echo There were $VERIFY_FAIL_COUNT failures
}

# Clean up any exports or volumes from previous runs, but not the volumes you need to run tests
cleanup_previous_run_artifacts() {
    project list --tenant emcworld  > /dev/null 2> /dev/null
    if [ $? -eq 1 ]; then
	return;
    fi
    PROJECT=`project list --tenant emcworld | grep YES | head -1 | awk '{print $1}'`

    events list emcworld &> /dev/null
    if [ $? -eq 0 ]; then
        for id in `events list emcworld | grep ActionableEvent | awk '{print $1}'`
        do
            echo "Deleting old event: ${id}"
            runcmd events delete ${id} > /dev/null
        done
    fi

    export_group list $PROJECT | grep YES > /dev/null 2> /dev/null
    if [ $? -eq 0 -a "${SS}" != "vblock" ]; then
	for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
	do
	    echo "Deleting old export group: ${id}"
	    runcmd export_group delete ${id} > /dev/null2> /dev/null
	done
    fi

    volume list ${PROJECT} | grep YES | grep "hijack\|fake" > /dev/null 2> /dev/null
    if [ $? -eq 0 ]; then
	for id in `volume list ${PROJECT} | grep YES | grep "hijack\|fake" | awk '{print $7}'`
	do
	    echo "Deleting old volume: ${id}"
	    runcmd volume delete ${id} --wait > /dev/null
	done
    fi

    hosts list emcworld &> /dev/null
    if [ $? -eq 0 ]; then
        for id in `hosts list emcworld | grep fake | awk '{print $4}'`
        do
            echo "Deleting old host: ${id}"
            runcmd hosts delete ${id} > /dev/null
        done
    fi

    cluster list emcworld &> /dev/null
    if [ $? -eq 0 ]; then
        for id in `cluster list emcworld | grep fake | awk '{print $4}'`
        do
            echo "Deleting old cluster: ${id}"
            runcmd cluster delete ${id} > /dev/null
        done
    fi
}

cleanup_srdf() {
  project=$1

  for source in `volume list $project | grep SOURCE | awk '{ print $7}'`
  do
    volume delete $source --wait
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

# Create an XIO Fail-On-Demand
create_fod() {
    curl -ikL --header "Content-Type: application/json" -X POST http://${HW_SIMULATOR_IP}:8020/fail-on-demand -d '{"uri":"api/json/v2/types/initiator-groups","method":"POST","conditions":{"fail-on":"response"}}'  &> /dev/null    
}

# vblock setup
vblock_setup() {
    echo "======================== Setting up vBlock environment, using real hardware =============================="

    #Add compute image server
    echo "Adding/creating compute image server details..."
    run computeimageserver create $VBLOCK_COMPUTE_IMAGE_SERVER_NAME \
                                  $VBLOCK_COMPUTE_IMAGE_SERVER_IP \
                                  $VBLOCK_COMPUTE_IMAGE_SERVER_SECONDARY_IP \
                                  $VBLOCK_COMPUTE_IMAGE_SERVER_USERNAME \
                                  "$VBLOCK_COMPUTE_IMAGE_SERVER_PASSWORD" \
                                  --tftpBootDir $VBLOCK_COMPUTE_IMAGE_SERVER_TFTPBOOTDIR \
                                  --osinstall_timeout $VBLOCK_COMPUTE_IMAGE_SERVER_OS_INSTALL_TIMEOUT \
                                  --ssh_timeout $VBLOCK_COMPUTE_IMAGE_SERVER_SSH_TIMEOUT \
                                  --imageimport_timeout $VBLOCK_COMPUTE_IMAGE_SERVER_IMAGE_IMPORT_TIMEOUT

    sleep 2

    #Discover UCS
    echo "Discover UCS compute system"
    run computesystem create $VBLOCK_COMPUTE_SYSTEM_NAME \
                             $VBLOCK_COMPUTE_SYSTEM_IP \
                             $VBLOCK_COMPUTE_SYSTEM_PORT \
                             $VBLOCK_COMPUTE_SYSTEM_USER \
                             "$VBLOCK_COMPUTE_SYSTEM_PASSWORD" \
                             $VBLOCK_COMPUTE_SYSTEM_TYPE \
                             $VBLOCK_COMPUTE_SYSTEM_SSL \
                             $VBLOCK_COMPUTE_SYSTEM_OS_INSTALL_NETWORK \
                             --compute_image_server $VBLOCK_COMPUTE_IMAGE_SERVER_NAME

    #Discover storage system to which UCS is connected
    # do this only once
    echo "Setting up SMIS for VMAX3"
    storage_password=$SMIS_PASSWD

    run smisprovider create $VBLOCK_VMAX_PROVIDER_NAME $VBLOCK_VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
    run storagedevice discover_all --ignore_error

    # Remove all arrays that aren't VBLOCK_VMAX_NATIVEGUID
    for id in `storagedevice list |  grep -v ${VBLOCK_VMAX_NATIVEGUID} | grep COMPLETE | awk '{print $2}'`
    do
       run storagedevice deregister ${id}
       run storagedevice delete ${id}
    done

    #Discover switches to which UCS has connections
    echo "Configuring MDS/Cisco switches"
    run networksystem create $VBLOCK_MDS_SWITCH_1_NAME_1 mds --devip $VBLOCK_MDS_SWITCH_1_IP_1 --devport 22 --username $VBLOCK_MDS_SWITCH_1_USER --password $VBLOCK_MDS_SWITCH_1_PW
    run networksystem create $VBLOCK_MDS_SWITCH_1_NAME_2 mds --devip $VBLOCK_MDS_SWITCH_1_IP_2 --devport 22 --username $VBLOCK_MDS_SWITCH_1_USER --password $VBLOCK_MDS_SWITCH_1_PW
    run networksystem create $VBLOCK_MDS_SWITCH_2_NAME_1 mds --devip $VBLOCK_MDS_SWITCH_2_IP_1 --devport 22 --username $VBLOCK_MDS_SWITCH_2_USER --password $VBLOCK_MDS_SWITCH_2_PW
    run networksystem create $VBLOCK_MDS_SWITCH_2_NAME_2 mds --devip $VBLOCK_MDS_SWITCH_2_IP_2 --devport 22 --username $VBLOCK_MDS_SWITCH_2_USER --password $VBLOCK_MDS_SWITCH_2_PW

    sleep 5

    #Add compute images
    echo "Adding compute images..."
    run computeimage create $VBLOCK_COMPUTE_IMAGE_NAME $VBLOCK_COMPUTE_IMAGE_IMAGEURL

    #Setup vArray and other virtual resources...
    run neighborhood create $NH
    run transportzone assign VSAN_3124 ${NH}
    run transportzone assign VSAN_3125 ${NH}
    run storagepool update $VBLOCK_VMAX_NATIVEGUID --type block --volume_type THIN_AND_THICK
    run storagepool update $VBLOCK_VMAX_NATIVEGUID --nhadd $NH --type block
    run storageport update $VBLOCK_VMAX_NATIVEGUID FC --addvarrays $NH

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`

    run cos create block ${VPOOL_BASE}\
    --description Base true                 \
    --protocols FC                \
    --multiVolumeConsistency \
    --numpaths 2            \
    --provisionType 'Thin'        \
    --max_snapshots 10                      \
    --expandable true                       \
    --neighborhoods $NH

    run cos update block $VPOOL_BASE --storage ${VBLOCK_VMAX_NATIVEGUID}

    run cos allow $VPOOL_BASE block $TENANT
    run project create $PROJECT --tenant $TENANT
    secho "Project $PROJECT created."
    run vcenter create $VBLOCK_VCENTER_NAME $TENANT $VBLOCK_VCENTER_IP $VBLOCK_VCENTER_PORT $VBLOCK_VCENTER_USER $VBLOCK_VCENTER_PASSWORD


    # Create and prepare compute virtual pool
    echo $VBLOCK_SERVICE_PROFILE_TEMPLATE_NAMES
    run computevirtualpool create $VBLOCK_COMPUTE_VIRTUAL_POOL_NAME $VBLOCK_COMPUTE_SYSTEM_NAME Cisco_UCSM false $NH $VBLOCK_SERVICE_PROFILE_TEMPLATE_NAMES $VBLOCK_SERVICE_PROFILE_TEMPLATE_TYPE
    sleep 2
    run computevirtualpool assign $VBLOCK_COMPUTE_VIRTUAL_POOL_NAME $VBLOCK_COMPUTE_SYSTEM_NAME $VBLOCK_COMPUTE_ELEMENT_NAMES

    echo "======= vBlock base setup done ======="
}

# ============================================================
# -    M A I N
# ============================================================

H1PI1=`pwwn 00`
H1NI1=`nwwn 00`
H1PI2=`pwwn 01`
H1NI2=`nwwn 01`
H1PI3=`pwwn 02`
H1NI3=`nwwn 02`
H1PI4=`pwwn 03`
H1NI4=`nwwn 03`

H2PI1=`pwwn 04`
H2NI1=`nwwn 04`
H2PI2=`pwwn 05`
H2NI2=`nwwn 05`
H2PI3=`pwwn 06`
H2NI3=`nwwn 06`
H2PI4=`pwwn 07`
H2NI4=`nwwn 07`

H3PI1=`pwwn 08`
H3NI1=`nwwn 08`
H3PI2=`pwwn 09`
H3NI2=`nwwn 09`

# Delete and setup are optional
if [ "$1" = "delete" ]
then
    login
    cleanup
    finish
fi

setup=0;

# Expected that storage platform is argument after sanity.conf
# Expected that switches occur after that, in any order
# Expected that test name(s) occurs last

SS=${1}
shift

case $SS in
    vmax2|vmax3|vnx|xio|unity|vblock)
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
    srdf)
        # set sync or async mode
        SRDF_MODE=${1}
        shift
        echo "SRDF_MODE is $SRDF_MODE"
        [[ ! "sync async" =~ "$SRDF_MODE" ]] && Usage
        export SRDF_MODE
    ;;
    *)
    Usage
    ;;
esac

# By default, check zones
ZONE_CHECK=${ZONE_CHECK:-1}
REPORT=0
DO_CLEANUP=0;
RESET_SIM=0;
while [ "${1:0:1}" = "-" ]
do
    if [ "${1}" = "setuphw" -o "${1}" = "setup" -o "${1}" = "-setuphw" -o "${1}" = "-setup" ]
    then
    	echo "Setting up testing based on real hardware"
    	setup=1;
    	SIM=0;
    	shift 1;
    elif [ "${1}" = "setupsim" -o "${1}" = "-setupsim" ]; then
    	if [ "$SS" = "xio" -o "$SS" = "vmax3" -o "$SS" = "vmax2" -o "$SS" = "vnx" -o "$SS" = "vplex" -o "$SS" = "srdf" ]; then
    	    echo "Setting up testing based on simulators"
    	    SIM=1;	    
    	    ZONE_CHECK=0;
    	    setup=1;
    	    shift 1;
    	else
    	    echo "Simulator-based testing of this suite is not supported on ${SS} due to lack of CLI/arraytools support to ${SS} provider/simulator"
    	    exit 1
    	fi
    # Whether to report results to the master data collector of all things
    elif [ "${1}" = "-report" ]; then
        echo "Reporting is ON"
        REPORT=1
        shift;
    elif [ "$1" = "-cleanup" ]
    then
	DO_CLEANUP=1;
	shift
    elif [ "$1" = "-resetsim" ]
    then
	if [ ${setup} -ne 1 ]; then
	    echo "FAILURE: Setup not specified.  Not recommended to reset simulator in the middle of an active configuration.  Or put -resetsim after your -setup param"
	    exit;
	else
	    RESET_SIM=1;
	    shift
	fi
    else
	echo "Bad option specified: ${1}"
	Usage
    fi
done

login

# setup required by all runs, even ones where setup was already done.
prerun_setup;

if [ ${setup} -eq 1 ]
then
    setup
    setup_yaml;
    if [ "$SS" = "vmax2" -o "$SS" = "vmax3" -o "$SS" = "vnx" -o "$SS" = "srdf" ]; then
	   setup_provider;
    fi
fi

test_start=1
test_end=13

# If there's a last parameter, take that
# as the name of the test to run - ignore "none" as that is usually for setup only.
#
# To start your suite on a specific test-case, just type the name of the first test case with a "+" after, such as:
# ./wftest.sh sanity.conf vplex local test_7+
if [ "$1" != "none" ]
then
    if [ "$1" = "hosts" ]
    then
       secho Request to run ${HOST_TEST_CASES}
       for t in ${HOST_TEST_CASES}
       do
          secho "\n\nRun $t"
          reset_system_props_pre_test
          prerun_tests
          $t
          reset_system_props_pre_test
       done
    elif [ "$1" != "" -a "${1:(-1)}" != "+"  ]
    then
       secho Request to run $*
       for t in $*
       do
          secho "\n\nRun $t"
          reset_system_props_pre_test
          prerun_tests
          $t
          reset_system_props_pre_test
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
         reset_system_props_pre_test
         prerun_tests
         test_${num}
         reset_system_props_pre_test
         num=`expr ${num} + 1`
       done
    fi
fi    

cleanup_previous_run_artifacts

echo There were $VERIFY_COUNT verifications
echo There were $VERIFY_FAIL_COUNT verification failures
echo `date`
echo `git status | grep 'On branch'`

if [ "${DO_CLEANUP}" = "1" ]; then
    cleanup;
fi

finish;

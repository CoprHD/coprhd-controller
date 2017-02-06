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
source $(dirname $0)/common_subs.sh

Usage()
{
    echo 'Usage: wftests.sh <sanity conf file path> (vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity | vblock] [-setuphw|-setupsim) [-report] [-cleanup]  [test1 test2 ...]'
    echo ' (vmax 2 | vmax3 ...: Storage platform to run on.'
    echo ' [-setup(hw) | setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes (Required to run first, can be used with tests'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' [-cleanup]: Clean up the pre-created volumes and exports associated with -setup operation (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    echo ' Example:  ./wftests.sh sanity.conf vmax3 -setupsim -report -cleanup test_7+'
    echo '           Will start from clean DB, report results to reporting server, clean-up when done, and start on test_7 (and run all tests after test_7'
    exit 2
}

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
    if [ $? -eq 0 ];
    then
	   ZONE_CHECK=0
	   SIM=1;
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
    
    # Chose thick because we need a thick pool for VNX metas
    # Choose SATA as drive type because simulator's non-Unified pool is SATA.
    run cos create block ${VPOOL_BASE}	\
	--description Base false                \
	--protocols FC 			                \
	--numpaths 2				            \
	--multiVolumeConsistency \
	--provisionType 'Thick'			        \
	--drive_type 'SATA' \
	--max_snapshots 10                      \
	--neighborhoods $NH  

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 4				            \
	--multiVolumeConsistency \
	--provisionType 'Thick'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH                    

    if [ "${SIM}" = "1" ]
    then
	# Remove the thin pool that doesn't support metas on the VNX simulator
	run cos update_pools block $VPOOL_BASE --rem ${VNXB_NATIVEGUID}/${VNXB_NATIVEGUID}+POOL+U+TP0000
        run cos update_pools block $VPOOL_CHANGE --rem ${VNXB_NATIVEGUID}/${VNXB_NATIVEGUID}+POOL+U+TP0000
    else
	run cos update block $VPOOL_BASE --storage ${VNXB_NATIVEGUID}
        run cos update block $VPOOL_CHANGE --storage ${VNXB_NATIVEGUID}
    fi
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
	--neighborhoods $NH                    

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 4				            \
	--multiVolumeConsistency \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
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
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH  

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
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

vmax3_sim_setup() {
    VMAX_PROVIDER_NAME=VMAX3-PROVIDER-SIM
    VMAX_SMIS_IP=$SIMULATOR_SMIS_IP
    VMAX_SMIS_PORT=7009
    SMIS_USER=$SMIS_USER
    SMIS_PASSWD=$SMIS_PASSWD
    VMAX_SMIS_SSL=true
    VMAX_NATIVEGUID=$SIMULATOR_VMAX3_NATIVEGUID
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

    setup_varray

    run storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--multiVolumeConsistency \
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--expandable true                       \
	--neighborhoods $NH

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
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

            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX4_NATIVEGUID
            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX5_NATIVEGUID

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

            run cos update block $VPOOL_CHANGE --storage $VPLEX_SIM_VMAX4_NATIVEGUID
            run cos update block $VPOOL_CHANGE --storage $VPLEX_SIM_VMAX5_NATIVEGUID
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

    setup_varray

    run storagepool update $XTREMIO_NATIVEGUID --nhadd $NH --type block

    common_setup

    SERIAL_NUMBER=`storagedevice list | grep COMPLETE | awk '{print $2}' | awk -F+ '{print $2}'`
    
    run cos create block ${VPOOL_BASE}	\
        --description Base true \
        --protocols FC 			                \
        --numpaths 1				            \
        --provisionType 'Thin'			        \
        --max_snapshots 10                      \
        --multiVolumeConsistency        \
        --neighborhoods $NH                    

    run cos create block ${VPOOL_CHANGE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 2				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
        --multiVolumeConsistency        \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${XTREMIO_NATIVEGUID}
    run cos update block $VPOOL_CHANGE --storage ${XTREMIO_NATIVEGUID}
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
    run transportzone assign ${FC_ZONE_A} ${NH}
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

snap_db() {
    slot=$1
    column_families=$2
    escape_seq=$3

    base_filter="| sed -r '/6[0]{29}[A-Z0-9]{2}=/s/\=-?[0-9][0-9]?[0-9]?/=XX/g' | sed -r 's/vdc1=-?[0-9][0-9]?[0-9]?/vdc1=XX/g' | grep -v \"status = OpStatusMap\" | grep -v \"lastDiscoveryRunTime = \" | grep -v \"successDiscoveryTime = \" | grep -v \"storageDevice = URI: null\" | grep -v \"StringSet \[\]\" | grep -v \"varray = URI: null\" | grep -v \"Description:\" | grep -v \"Additional\" | grep -v -e '^$' | grep -v \"Rollback encountered problems\" | grep -v \"clustername = null\" | grep -v \"cluster = URI: null\" | grep -v \"vcenterDataCenter = \" $escape_seq"
    
    secho "snapping column families [set $slot]: ${column_families}"

    IFS=' ' read -ra cfs_array <<< "$column_families"
    for cf in "${cfs_array[@]}"; do
       execute="/opt/storageos/bin/dbutils list ${cf} $base_filter > results/${item}/${cf}-${slot}.txt"
       eval $execute
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
	storage_failure_injections="failure_004:failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesToExportMask_before_operation \
                                    failure_004:failure_044_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesToExportMask_after_operation \
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

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_040 failure_004:failure_041"

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
	storage_failure_injections="failure_004:failure_043_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesToExportMask_before_operation \
                                    failure_004:failure_044_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesToExportMask_after_operation \
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
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_RemoveMembers \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool"
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

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup"

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
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Check the state of the volume that doesn't exist
      snap_db 1 "${cfs[@]}"

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

      	  # Let the async jobs calm down
      	  sleep 5
      fi

      # Perform any DB validation in here
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 ${cfs}

      # Should be able to delete the CG and recreate it.
      runcmd blockconsistencygroup delete ${CGNAME}

      # Re-create the consistency group
      runcmd blockconsistencygroup create ${PROJECT} ${CGNAME}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none

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
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool"

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
                               failure_004:failure_018_Export_doRollbackExportCreate_before_delete \
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
	storage_failure_injections=""
    fi 

    if [ "${SS}" = "vnx" ]
    then
        storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateStorageHardwareID"
    fi

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_CreateGroup"
    fi

    if [ "${SS}" = "unity" ]; then
      storage_failure_injections=""
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections} ${network_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_004:failure_018_Export_doRollbackExportCreate_before_delete"

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

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                               failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                               failure_018_Export_doRollbackExportCreate_before_delete"

    network_failure_injections=""
    if [ "${BROCADE}" = "1" ]
    then
	network_failure_injections="failure_049_BrocadeNetworkSMIS.getWEBMClient"
    else
        network_failure_injections="failure_057_MdsNetworkSystemDevice.removeZones"
    fi

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections=""
    fi

    if [ "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_AddMembers"
    fi

    if [ "${SS}" = "vnx" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteProtocolController \
                                    failure_015_SmisCommandHelper.invokeMethod_DeleteStorageHardwareID"
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
                                    failure_015_SmisCommandHelper.invokeMethod_CreateGroup"
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
      snap_db 2 "${cfs[@]}"

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

    network_failure_injections="failure_047_NetworkDeviceController.zoneExportMaskCreate_before_zone"
    if [ "${BROCADE}" = "1" ]
    then
	network_failure_injections="failure_049_BrocadeNetworkSMIS.getWEBMClient"
    fi

    storage_failure_injections=""
    if [ "${SS}" = "vplex" ]
    then
	storage_failure_injections="failure_004:failure_024_Export_zone_removeInitiator_before_delete \
                                    failure_004:failure_025_Export_zone_removeInitiator_after_delete \
                                    failure_060_VPlexDeviceController.storageViewAddInitiators_storageview_nonexisting"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" -o "${SS}" = "unity" ]
    then
	storage_failure_injections="failure_004:failure_024_Export_zone_removeInitiator_before_delete \
                                    failure_004:failure_025_Export_zone_removeInitiator_after_delete"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections} ${network_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_004:failure_024_Export_zone_removeInitiator_before_delete"

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

      # Verify injected failures were hit
      verify_failures ${failure}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 2 3 "${cfs[@]}"

      # Rerun the command
      set_artificial_failure none
      runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}

      # Delete the export
      runcmd export_group delete ${PROJECT}/${expname}1

      # Verify the DB is back to the original state
      snap_db 4 "${cfs[@]}"
      validate_db 1 4 "${cfs[@]}"

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
	storage_failure_injections="failure_009_VPlexVmaxMaskingOrchestrator.deleteOrRemoveVolumesToExportMask_before_operation"
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
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries"

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

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Remove the volume
      fail volume delete ${PROJECT}/${volname} --wait

      # Turn on failure at a specific point
      set_artificial_failure none

      # Remove the volume
      if [ "${failure}" != "failure_015_SmisCommandHelper.invokeMethod_EMCListSFSEntries" -a "${failure}" != "failure_015_SmisCommandHelper.invokeMethod_DeleteGroup" ]; then
          runcmd volume delete ${PROJECT}/${volname} --wait
      fi

      # Remove the CG object
      runcmd blockconsistencygroup delete ${CGNAME}

      # Perform any DB validation in here
      snap_db 3 "${cfs[@]}"

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
    # failure_injections="failure_firewall"
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_*"

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
	storage_failure_injections=""
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
    # failure_injections="failure_057_NetworkDeviceController.zoneExportAddInitiators_before_zone"

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
# Tests removing the last volume from a CG with failures.
# 1. Create 2 volumes
# 2. Save state
# 3. Export both to a host
# 4. Remove one volume from export group
# 5. Set failure
# 6. Remove last volume from export group
# 7. Verify failures were hit
# 8. Re-run the operation, it should be a NOOP
# 9. Delete the export group
# 10. Save state
# 11. Compare before/after states.  There should be no orphaned export masks.
test_12() {
    echot "Test 12 Begins"
    expname=${EXPORT_GROUP_NAME}t12

    common_failure_injections="failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone"

    if [ "${SS}" != "unity" ]; then
      echo "Only applicable for Unity."
      exit 1
    fi
    
    failure_injections="${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_057_NetworkDeviceController.zoneExportAddInitiators_before_zone"

    for failure in ${failure_injections}
    do
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 12 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      runcmd volume create ${volname} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 2
      volname1=${volname}-1
      volname2=${volname}-2

      # Check the state of the export that it doesn't exist
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname} $NH --type Host --volspec ${PROJECT}/${volname1},${PROJECT}/${volname2} --hosts "${HOST1}"

      # Successfully remove first volume
      export_group update ${PROJECT}/${expname} --remVol ${PROJECT}/${volname1}

      # Set failure for removing the last volume
      set_artificial_failure ${failure}

      # Attempt to remove the last volume
      fail export_group update ${PROJECT}/${expname} --remVol ${PROJECT}/${volname2}

      # Verify injected failures were hit
      verify_failures ${failure}

      # Disable failures
      set_artificial_failure "none"

      # Re-run the operation, it should perform a NOOP.
      runcmd export_group update ${PROJECT}/${expname} --remVol ${PROJECT}/${volname2}

      # Delete the export
      runcmd export_group delete ${PROJECT}/${expname}

      # If all went well, we should be in the original state (no orphaned ExportMask instances).
      snap_db 2 "${cfs[@]}"

      # Verify the DB is back to the original state
      validate_db 1 2 "${cfs[@]}"

      # Remove the volumes
      runcmd volume delete ${PROJECT}/${volname1} --wait
      runcmd volume delete ${PROJECT}/${volname2} --wait

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
	storage_failure_injections="failure_017_Export_doRemoveInitiator"
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
    # failure_injections="failure_firewall"
    # failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup"

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
      runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

      # Remove an initiator
      runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

      # Snap the state after the initiator is removed so we have something to compare against
      snap_db 2 "${cfs[@]}"

      # Readd it
      runcmd export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}
      
      # Turn on suspend of export after orchestration
      set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

      # Run the export group command
      runcmd_suspend test_13 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

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
      elif [ "${failure}" != "failure_015_SmisCommandHelper.invokeMethod_*" ]
      then
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
      report_results test_13 ${failure}
    done
}

# Test 14
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
test_14() {
    echot "Test 14 Begins"
    expname=${EXPORT_GROUP_NAME}t14

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
	storage_failure_injections="failure_017_Export_doRemoveInitiator"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
	storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup \
                                    failure_015_SmisCommandHelper.invokeMethod_*"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_firewall"
    failure_injections="failure_015_SmisCommandHelper.invokeMethod_DeleteGroup"

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
      secho "Running Test 14 with failure scenario: ${failure}..."
      cfs=("ExportGroup ExportMask FCZoneReference")
      mkdir -p results/${item}
      volname=${VOLNAME}-${item}
      reset_counts
      
      # Snap the state before the export is created
      snap_db 1 "${cfs[@]}"

      # prime the export
      runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CLUSTER}"

      # Snap the state before the second host was added.  This is the state we expect after successful retry of remHost later.
      snap_db 2 "${cfs[@]}"

      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      # Run the export group command
      fail export_group update $PROJECT/${expname}1 --remHosts ${HOST2}

      exit;
      # Turn off suspend of export after orchestration
      set_suspend_on_class_method none

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
      report_results test_14 ${failure}
    done
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

    # Create and prepare compute virtual pool
    run computevirtualpool create $VBLOCK_COMPUTE_VIRTUAL_POOL_NAME $VBLOCK_COMPUTE_SYSTEM_NAME Cisco_UCSM false $NH $VBLOCK_SERVICE_PROFILE_TEMPLATE_NAMES
    sleep 2
    run computevirtualpool assign $VBLOCK_COMPUTE_VIRTUAL_POOL_NAME $VBLOCK_COMPUTE_SYSTEM_NAME $VBLOCK_COMPUTE_ELEMENT_NAMES

    run cos allow $VPOOL_BASE block $TENANT
    run project create $PROJECT --tenant $TENANT
    secho "Project $PROJECT created."

    #run bourne.catalog_order $VBLOCK_CATALOG_PROVISION_CLUSTER $TENANT

    echo "======= vBlock base setup done ======="
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
    *)
    Usage
    ;;
esac

# By default, check zones
ZONE_CHECK=${ZONE_CHECK:-1}
REPORT=0
DO_CLEANUP=0;
while [ "${1:0:1}" = "-" ]
do
    if [ "${1}" = "setuphw" -o "${1}" = "setup" -o "${1}" = "-setuphw" -o "${1}" = "-setup" ]
    then
	echo "Setting up testing based on real hardware"
	setup=1;
	SIM=0;
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

    # Whether to report results to the master data collector of all things
    if [ "${1}" = "-report" ]; then
	REPORT=1
	shift;
    fi

    if [ "$1" = "-cleanup" ]
    then
	DO_CLEANUP=1;
	shift
    fi
done


login

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


test_start=1
test_end=14

# If there's a last parameter, take that
# as the name of the test to run
# To start your suite on a specific test-case, just type the name of the first test case with a "+" after, such as:
# ./wftest.sh sanity.conf vplex local test_7+
if [ "$1" = "hosts" ]
then
   secho Request to run ${HOST_TEST_CASES}
   for t in ${HOST_TEST_CASES}
   do
      secho Run $
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
      secho Run $t
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

cleanup_previous_run_artifacts

echo There were $VERIFY_COUNT verifications
echo There were $VERIFY_FAIL_COUNT verification failures
echo `date`
echo `git status | grep 'On branch'`

if [ "${DO_CLEANUP}" = "1" ]; then
    cleanup;
fi

finish;

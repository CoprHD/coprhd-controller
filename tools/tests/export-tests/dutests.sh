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
# Requirements for XtremIO, Unity and VPLEX:
# -----------------------------------
# - XIO, Unity and VPLEX testing requires the ArrayTools.jar file.  For now, see Bill, Tej, or Nathan for this file.
# - These platforms will create a tools.yml file that the jar file will use based on variables in sanity.conf
#
#set -x

source $(dirname $0)/common_subs.sh

Usage()
{
    echo 'Usage: dutests.sh <sanity conf file path> (vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity] [-setuphw|-setupsim) [-report] [-cleanup] [-resetsim]  [test1 test2 ...]'
    echo ' (vmax 2 | vmax3 ...: Storage platform to run on.'
    echo ' [-setup(hw) | setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes (Required to run first, can be used with tests'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' [-cleanup]: Clean up the pre-created volumes and exports associated with -setup operation (Optional)'
    echo ' [-resetsim]: Resets the simulator as part of setup (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    echo ' Example:  ./dutests.sh sanity.conf vmax3 -setupsim -report -cleanup test_7+'
    echo '           Will start from clean DB, report results to reporting server, clean-up when done, and start on test_7 (and run all tests after test_7'
    exit 2
}

# Extra debug output
DUTEST_DEBUG=${DUTEST_DEBUG:-0}

SANITY_CONFIG_FILE=""
: ${USE_CLUSTERED_HOSTS=1}

SERIAL_NUMBER="XX.XX.XX"

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

# Global test repo location
GLOBAL_RESULTS_IP=10.247.101.46
GLOBAL_RESULTS_PATH=/srv/www/htdocs
LOCAL_RESULTS_PATH=/tmp
GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR=output_files
RESULTS_SET_FILE=results-set-du.csv
TEST_OUTPUT_FILE=default-output.txt

# Overall suite counts
VERIFY_COUNT=0
VERIFY_FAIL_COUNT=0
VERIFY_EXPORT_STATUS=0

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

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}

dbupdate() {
    runcmd dbupdate.sh $*
}

prerun_setup() {
    # Reset system properties
    reset_system_props

    # Convenience, clean up known artifacts
    cleanup_previous_run_artifacts

    # Get the latest tools
    retrieve_tooling

    BASENUM=""
    echo "Check if tenant and project exist"
    isTenantCreated=$(tenant list | grep $TENANT | wc -l)
    if [ $isTenantCreated -ne 0 ]; then
        echo "Found tenant $TENANT"

        isProjectCreated=$(project list --tenant $TENANT | grep $PROJECT | wc -l)
        if [ $isProjectCreated -ne 0 ]; then
            echo "Found project $PROJECT"
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
		elif [ "${storage_type}" = "unity" ]; then
            storage_password=${UNITY_PW}
		fi
	    fi
        else
            echo "The project $PROJECT doesn't exist"
        fi
    else
        echo "The tenant $TENANT doesn't exist"
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
    rm -f /tmp/verify*
}

# get the device ID of a created volume
get_device_id() {
    label=$1

    if [ "$SS" = "xio" -o "$SS" = "vplex" ]; then
        volume show ${label} | grep device_label | awk '{print $2}' | cut -d '"' -f2
    elif [ "$SS" = "unity" ]; then
        volume show ${label} | grep native_id | awk '{print $2}' | cut -d '"' -f2
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
    verify_export ${expname}1 ${HOST1} gone
    if [ ${VERIFY_EXPORT_STATUS} -ne 0 ]; then
        echo "The export was found on the device, attempting to delete..."
        arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}
        sleep 60
        # reset variables because this is just a clean up task
        VERIFY_EXPORT_STATUS=0
        VERIFY_COUNT=`expr $VERIFY_COUNT - 1`
        VERIFY_FAIL_COUNT=`expr $VERIFY_FAIL_COUNT - 1`
    fi
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
    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 2				            \
	--multiVolumeConsistency \
	--provisionType 'Thick'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH  

    if [ "${SIM}" = "1" ]
    then
	# Remove the thin pool that doesn't support metas on the VNX simulator
	run cos update_pools block $VPOOL_BASE --rem ${VNXB_NATIVEGUID}/${VNXB_NATIVEGUID}+POOL+U+TP0000
    else
	run cos update block $VPOOL_BASE --storage ${VNXB_NATIVEGUID}
    fi
}

unity_setup()
{
    # do this only once
    echo "Setting up Unity"
    storage_password=$UNITY_PW
    SERIAL_NUMBER=$UNITY_SN

    storagedevice list
    isSystemCreated=$(storagedevice list | grep $UNITY_SN | wc -l)
    [ "$isSystemCreated" -gt 1 ] && return

    secho "Discovering Unity ..."
    run discoveredsystem create $UNITY_DEV unity $UNITY_IP $UNITY_PORT $UNITY_USER $UNITY_PW --serialno=$UNITY_SN
    run storagedevice discover_all
    sleep 30
    isSystemCreated=$(storagedevice list | grep $UNITY_SN | wc -l)
    if [ $isSystemCreated -eq 0 ]; then
        echo "${UNITY_SN} has not been discovered"
        exit 1
    fi

    run storagepool update $UNITY_NATIVEGUID --type block --volume_type THIN_AND_THICK
    run transportzone add ${SRDF_VMAXA_VSAN} $UNITY_INIT_PWWN1
    run transportzone add ${SRDF_VMAXA_VSAN} $UNITY_INIT_PWWN2

    setup_varray
    common_setup

    run cos create block ${VPOOL_BASE}	\
	--description Base true                 \
	--protocols FC 			                \
	--numpaths 1				            \
	--provisionType 'Thin'			        \
	--max_snapshots 10                      \
	--neighborhoods $NH                    

    run cos update block $VPOOL_BASE --storage ${UNITY_NATIVEGUID}
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

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
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

    run cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}

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
            run cos create block $VPOOL_BASE false                              \
                             --description 'vpool-for-vplex-local-volumes'      \
                             --protocols FC                                     \
                             --numpaths 2                                       \
                             --provisionType 'Thin'                             \
                             --highavailability vplex_local                     \
                             --neighborhoods $VPLEX_VARRAY1                     \
                             --max_snapshots 1                                  \
                             --max_mirrors 0                                    \
                             --expandable true 

            run cos update block $VPOOL_BASE --storage $VPLEX_SIM_VMAX2_NATIVEGUID

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
        --description Base true                 \
        --protocols FC 			                \
        --numpaths 1				            \
        --provisionType 'Thin'			        \
        --max_snapshots 10                      \
        --multiVolumeConsistency        \
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
    run transportzone assign ${FC_ZONE_A} ${NH}
}

setup() {
    storage_type=$1;

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
        echo "Existing network system list - $(networksystem list)"
        isNetworkDiscovered=$(networksystem list | grep $BROCADE_NETWORK | wc -l)
        if [ $isNetworkDiscovered -eq 0 ]; then
            secho "Discovering Brocade SAN Switch ..."
	    result=$(networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false 2>&1)
	    isNetworkDiscovered=$(echo $result | grep "Network system discovered" | wc -l)
            if [ $isNetworkDiscovered -eq 0 ]; then
                sleep 30
                isNetworkDiscovered=$(networksystem list | grep $BROCADE_NETWORK | wc -l)
                if [ $isNetworkDiscovered -eq 0 ]; then
                    echo $result
                    exit 1
		fi
	    fi

            isNetworkDiscovered=$(transportzone listall | (grep ${SRDF_VMAXA_VSAN} || echo ''))
	    if [ "$isNetworkDiscovered" == '' ]; then
	        echo "Discovering network"
	        run networksystem discover $BROCADE_NETWORK
	        secho "Sleeping 30 seconds..."
	        sleep 30
	    fi
        fi
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
    sleep 30
    run volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 3
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
    runcmd_suspend test_1 export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Resume the workflow and follow
    resume_follow_task
    
    # Perform verifications
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export before orchestration
    set_suspend_on_class_method ${exportDeleteOrchStep}

    # run export group, which is expected to suspend
    runcmd_suspend test_1 export_group delete $PROJECT/${expname}1

    # resume and follow the task to completion
    resume_follow_task

    # Make verifications
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_1
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
    runcmd_suspend test_2 export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Resume and follow the workflow/task
    resume_follow_task

    # Verify results
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command
    runcmd_suspend test_2 export_group delete $PROJECT/${expname}1

    # Resume and follow the workflow/task
    resume_follow_task

    # Verify results
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_2
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

    # Find the zones that were created
    load_zones ${HOST1}

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Create the volume and inventory-only delete it so we can use it later.
    HIJACK=du-hijack-volume-${RANDOM}
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Run the export group command 
    runcmd_suspend test_3 export_group delete $PROJECT/${expname}1

    # Add volume to mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of Poka Yoke validation
    if [ "$SS" = "unity" ]; then
        # deleting exort mask with unknown volume is allowed, unknown volume and initiators are untouched
        runcmd task follow $task
        verify_export ${expname}1 ${HOST1} 2 1

        # create the export group again
        runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

        verify_export ${expname}1 ${HOST1} 2 2
    else
        echo "*** Following the export_group delete task to verify it FAILS because of the additional volume"
        fail task follow $task
    fi

    # Now remove the volume from the storage group (masking view)
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Delete the volume we created.
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Try the export operation again
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_3
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

    # Create the mask with the 2 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    # Find the zones that were created
    load_zones ${HOST1}

    verify_export ${expname}1 ${HOST1} 2 2

    if [ "$SS" != "xio" ]; then
        PWWN=`echo ${H2PI1} | sed 's/://g'`
        if [ "$SS" = "unity" ]; then
            # shouldn't add initiator of host2 to host1, using a random one
            PWWN=`getwwn`
        fi

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

        # Verify the zone names, as we know them, are on the switch
        verify_zones ${FC_ZONE_A:7} exists

        # Now remove the initiator from the export mask
        arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

        # Verify the mask is back to normal
        verify_export ${expname}1 ${HOST1} 2 2
    fi

    # Reset test: test lower levels (original test_4 test)
    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command 
    runcmd_suspend test_4 export_group delete $PROJECT/${expname}1

    PWWN=`getwwn`
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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Now remove the initiator from the export mask
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 2

    # Reset the test, now try to add initiators from other hosts we know about
    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportDeleteDeviceStep}

    # Run the export group command 
    runcmd_suspend test_4 export_group delete $PROJECT/${expname}1

    PWWN=`echo ${H2PI1} | sed 's/://g'`
    if [ "$SS" = "unity" ]; then
        # shouldn't add initiator of host2 to host1, using a random one
        PWWN=`getwwn`
    fi

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

        # Verify the zone names, as we know them, are on the switch
        verify_zones ${FC_ZONE_A:7} exists

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

        # Verify the zone names, as we know them, are on the switch
        verify_zones ${FC_ZONE_A:7} exists
    fi

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_4
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

    # Create the mask with 2 volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 2

    # Find the zones that were created
    load_zones ${HOST1}

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command 
    runcmd_suspend test_5 export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2

    PWWN=`getwwn`

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    if [ "$SS" != "xio" ]; then    
        # Run the export group command 
	runcmd_suspend test_5 export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"

        PWWN=`echo ${H2PI1} | sed 's/://g'`
        if [ "$SS" = "unity" ]; then
            # shouldn't add initiator of host2 to host1, using a random one
            PWWN=`getwwn`
        fi
    
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

        # Verify the zone names, as we know them, are on the switch
        verify_zones ${FC_ZONE_A:7} exists
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

    # Report results
    report_results test_5
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

    # Find the zones that were created
    load_zones ${HOST1}

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

    # Verify the mask without the new volume in it
    verify_export ${expname}1 ${HOST1} 2 1

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command 
    runcmd_suspend test_6 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command 
    runcmd_suspend test_6 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

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

    # Report results
    report_results test_6
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

    # Find the zones that were created
    load_zones ${HOST1}

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Delete the volume we created
    runcmd volume delete ${PROJECT}/${volname} --wait

    # Report results
    report_results test_7
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

    # Find the zones that were created
    load_zones ${HOST1}

    # Strip out colons for array helper command
    h1pi2=`echo ${H1PI2} | sed 's/://g'`
    if [ "$SS" = "unity" ]; then
        h1pi2="${H1NI2}:${H1PI2}"
    fi

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${h1pi2} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname} ${HOST1} 2 1

    # Now add that volume using ViPR
    runcmd export_group update $PROJECT/${expname} --addInits ${HOST1}/${H1PI2}

    # Verify the mask is "normal" after that command
    verify_export ${expname} ${HOST1} 2 1

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}

    # Make sure it really did kill off the mask
    verify_export ${expname} ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_8
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

    # Find the zones that were created
    load_zones ${HOST1}

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}

    # Run the export group command 
    runcmd_suspend test_9 export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_9
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

    # Find the zones that were created
    load_zones ${HOST1}

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_10
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

    # Find the zones that were created
    load_zones ${HOST1} 

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Find information about volume 2 so we can do stuff to it outside of ViPR
    volname="${VOLNAME}-2"

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${volname}`

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}

    # Run the export group command 
    runcmd_suspend test_11 export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2

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

    # Verify the zone names, as we know them, are on the switch
    verify_zones ${FC_ZONE_A:7} exists

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

    echo "COP-31191 is opened against dangling FCZoneReference and zones"
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_11
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

    # Report results
    report_results test_12
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

    # Load the zone state
    load_zones ${HOST1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddVolumesDeviceStep}
    set_artificial_failure failure_002_late_in_add_volume_to_mask

    # Run the export group command 
    runcmd_suspend test_13 export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2

    PWWN=`getwwn`

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

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

    # Remove initiator from the mask (done differently per array type)
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the initiator was removed
    verify_export ${expname}1 ${HOST1} 2 1

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone

    echo "COP-31191 is opened against dangling FCZoneReference and zones"
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_13
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
    echot "Test 14: Test rollback of add initiator, verify it removes the new initiators when volume sneaks into mask"
    expname=${EXPORT_GROUP_NAME}t14-${RANDOM}

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Remove one of the initiator so we can add it back.
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    # Load the zone state
    load_zones ${HOST1}

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

    # Run the export group command 
    runcmd_suspend test_14 export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI1}

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 1 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should fail because of artificial failure
    echo "*** Following the export_group update task to verify it FAILS because of artificial failure"
    fail task follow $task

    # Verify that ViPR rollback removed only the initiator that was previously added
    verify_export ${expname}1 ${HOST1} 1 2

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

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

    # Report results
    report_results test_14
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

    # Load the zone state
    load_zones ${HOST1}

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportAddInitiatorsDeviceStep}
    set_artificial_failure failure_003_late_in_add_initiator_to_mask

    # Run the export group command 
    runcmd_suspend test_15 export_group update $PROJECT/${expname}1 --addInits ${HOST1}/${H1PI2}

    # Strip out colons for array helper command
    h1pi2=`echo ${H1PI2} | sed 's/://g'`
    if [ "$SS" = "unity" ]; then
        h1pi2="${H1NI2}:${H1PI2}"
    fi

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

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

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

    # Report results
    report_results test_15
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

    # Run the export group command 
    runcmd_suspend test_16 export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

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

    # Report results
    report_results test_16
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

    # Run the export group command 
    runcmd_suspend test_17 export_group delete $PROJECT/${expname}1

    # Add the volume to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should pass because validation is run but not enforced
    echo "*** Following the export_group delete task to verify it PASSES because validation is disabled"
    runcmd task follow $task

    if [ "${SS}" = "xio" -o "${SS}" = "unity" ]; then
        # XIO/Unity will still protect the lun mapping due to the additional volume, leaving it behind
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

    # Report results
    report_results test_17
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

    # Load the zone state
    load_zones ${HOST1}

    verify_export ${expname}1 ${HOST1} 2 2

    HIJACK=du-hijack-volume-${RANDOM}

    # Create another volume that we will inventory-only delete
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command 
    runcmd_suspend test_18 export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2

    # Add an unrelated initiator to the mask (done differently per array type)
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    
    # Verify the mask has the new initiator in it
    verify_export ${expname}1 ${HOST1} 2 3

    # Resume the workflow
    runcmd workflow resume $workflow

    # Follow the task.  It should succeed, even though a volume crept in.
    echo "*** Following the export_group update task to verify it succeeds despite unrelated volume"
    runcmd task follow $task

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 2

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

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

    # Report results
    report_results test_18
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

    # Load the zone state
    load_zones ${HOST1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command 
    runcmd_suspend test_19 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

    PWWN=`getwwn`

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

    # Verify zones were not affected
    echo "COP-31206:  Not all zones will likely be here, as this is a export group update that passed.  filter zones needs to be implemented in verification"
    verify_zones ${FC_ZONE_A:7} exists

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

    # Report results
    report_results test_19
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

    # Load the zone state
    load_zones ${HOST1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 2

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveVolumesDeviceStep}

    # Run the export group command 
    runcmd_suspend test_20 export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2

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

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

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

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_20
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

    # Load the zone state
    load_zones ${HOST1}

    # Verify the mask has been created
    verify_export ${expname}1 ${HOST1} 2 1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ${exportRemoveInitiatorsDeviceStep}

    # Run the export group command 
    runcmd_suspend test_21 export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}

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

    # Verify zones were not affected
    verify_zones ${FC_ZONE_A:7} exists

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

    # Verify zones were not affected
    echo "COP-31206: It is expected that the zone from the remInits call above will be missing in the zone list.  TODO: improve granularity of the verification check"
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_21
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

    # Run the export group command
    runcmd_suspend test_22 volume change_cos ${PROJECT}/${HIJACK} ${VPOOL_BASE}_migration_tgt --suspend

    # Resume and follow the workflow/task
    resume_follow_task

    # Resume and follow the workflow/task (again)
    resume_follow_task

    # Delete the volume we created
    runcmd volume delete ${PROJECT}/${HIJACK} --wait

    # Report results
    report_results test_22
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
    runcmd_suspend test_23 volume change_cos "${PROJECT}/${HIJACK}-1,${PROJECT}/${HIJACK}-2" ${VPOOL_BASE}_migration_tgt --consistencyGroup=${CGNAME} --suspend

    # Resume and follow the workflow/task
    resume_follow_task

    # Resume and follow the workflow/task (again)
    resume_follow_task

    # Delete the volumes we created
    runcmd volume delete ${PROJECT}/${HIJACK}-1 --wait
    runcmd volume delete ${PROJECT}/${HIJACK}-2 --wait
    runcmd blockconsistencygroup delete ${CGNAME}

    # Report results
    report_results test_23
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
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

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

    # Report results
    report_results test_24
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
    echot "Test 25: Remove Initiator doesn't remove zones when extra initiators are in it"
    expname=${EXPORT_GROUP_NAME}t25

    if [ "$SS" = "xio" ]; then
        echo "Test 25 is not applicable for XtremIO. Skipping."
        return
    elif [ "$SS" = "unity" ]; then
        echo "Test 25 is not applicable for Unity. Skipping."
        return
    fi

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

    PWWN=`getwwn`

    # Add another initiator to the mask (done differently per array type)
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}
   
    # Verify the mask has the new initiator in it
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

    # The mask is out of our control at this point, delete mask
    arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    # Report results
    report_results test_25
}

# DU Prevention Validation Test 26 (Unity only)
# Unity specific DU test case in unitytests.sh
#
test_26() {
    echot "Test 26: Unity specific DU test case"

    # Check to make sure we're running Unity only
    if [ "${SS}" != "unity" ]; then
        echo "test_26 only runs on Unity. Bypassing for ${SS}."
        return
    fi

    unity_test_all
}

# ============================================================
# -    M A I N
# ============================================================

H1PI1=`pwwn 00`
H1NI1=`nwwn 00`
H1PI2=`pwwn 01`
H1NI2=`nwwn 01`
H1ID="${H1NI1}:${H1PI1} ${H1NI2}:${H1PI2}"

H2PI1=`pwwn 02`
H2NI1=`nwwn 02`
H2PI2=`pwwn 03`
H2NI2=`nwwn 03`
H2ID="${H2NI1}:${H2PI1} ${H2NI2}:${H2PI2}"

H3PI1=`pwwn 04`
H3NI1=`nwwn 04`
H3PI2=`pwwn 05`
H3NI2=`nwwn 05`
H3ID="${H3NI1}:${H3PI1} ${H3NI2}:${H3PI2}"

# pull in the vplextests.sh so it can use the dutests framework
source vplextests.sh
source unitytests.sh

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
    # Whether to report results to the master data collector of all things
    elif [ "${1}" = "-report" ]; then
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
    if [ "$SS" = "xio" -o "$SS" = "vplex" -o "$SS" = "unity" ]; then
	setup_yaml;
    fi
    if [ "$SS" = "vmax2" -o "$SS" = "vmax3" -o "$SS" = "vnx" ]; then
	setup_provider;
    fi
fi

test_start=0
test_end=26

# If there's a last parameter, take that
# as the name of the test to run
# To start your suite on a specific test-case, just type the name of the first test case with a "+" after, such as:
# ./dutest.sh sanity.conf vplex local test_7+
if [ "$1" != "" -a "${1:(-1)}" != "+"  ]
then
   secho Request to run $*
   for t in $*
   do
      secho Run $t
      reset_system_props
      prerun_tests
      TEST_OUTPUT_FILE=test_output_${RANDOM}.log
      reset_counts
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
     TEST_OUTPUT_FILE=test_output_${RANDOM}.log
     reset_counts
     test_${num}
     reset_system_props
     num=`expr ${num} + 1`
   done
fi

cleanup_previous_run_artifacts

echo There were $VERIFY_COUNT export verifications
echo There were $VERIFY_FAIL_COUNT export verification failures
echo `date`
echo `git status | grep 'On branch'`

if [ "${DO_CLEANUP}" = "1" ]; then
    cleanup;
fi

finish;

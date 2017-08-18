#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#
#
# Rollback and WF Validation Tests
# ==========================
#
#

#set -x
source $(dirname $0)/common_subs.sh
seed=`date "+%H%M%S%N"`
SANITY_BOX_IP=localhost
source /opt/storageos/cli/viprcli.profile
PATH=$PATH:$VIPR_CLI_HOME/bin:$(dirname $0):$(dirname $0)/..:/bin:/usr/bin
export PATH
COOKIE_DIR=/tmp
COOKIE_FILE_NAME=cookie_sanity_${seed}
COOKIE_FILE=$COOKIE_DIR/$COOKIE_FILE_NAME
fsname=testFS_${seed}
PROJECT=fileTestProject_${seed}
TENANT='Provider Tenant'
NH=varray_isilon_${seed}
COS=vpool_isilon_${seed}
NETWORK=network_${seed}
FS_SIZEMB=1024MB
LOCAL_RESULTS_PATH=/tmp
TEST_OUTPUT_FILE=default-output.txt


# Isilon configuration
ISI_DEV=isilon_device
ISI_NATIVEGUID=ISILON+001b21c257c492bdfa56c01b1d3436e39c38

# Overall suite counts
VERIFY_COUNT=0
VERIFY_FAIL_COUNT=0

# Per-test counts
TRIP_VERIFY_COUNT=0
TRIP_VERIFY_FAIL_COUNT=0

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}


Usage()
{
    echo 'Usage: filetests.sh <sanity conf file path> [isilon | unity | vnxe | vnxfile] [test_1 test_2 ...]'
    exit 2
}

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
      #shift
      source $SANITY_CONFIG_FILE
   fi
fi

# Print functions in this file that start with test_
print_test_names() {
    for test_name in `grep -E "^test_.+\(\)" $0 | sed -E "s/\(.*$//"`
    do
      echo "  $test_name"
    done
}


date
#[ $# -eq 0 ] || Usage
[ "$1" = "help" ] && Usage
[ "$1" = "-h" ] && Usage

case $2 in
    all|isilon|netapp7|netappc|vnxfile|datadomain|vnxe)
    echo "Sanity Script will run for $1 devices"
     ;;
    *)
     Usage
esac


# create a project for running the tests

project_setup()
{
    project show $PROJECT &> /dev/null && return $?
    run project create $PROJECT
}


#
# add an ISILON storage device with a default pool that supports NFS and N+2:1 protection
#
isilon_setup_once()
{
    # do this only once
    discoveredsystem show  $ISI_DEV &> /dev/null && return $?
    
    #Add and discover the Isilon Array 
    discoveredsystem create $ISI_DEV isilon $ISI_IP 8080 $ISI_USER $ISI_PASSWD --serialno=$ISI_SN
    
    #Create Virtual Array
    neighborhood create $NH
    
    #Create Network
    transportzone create $NETWORK $NH --type IP

    #Attach Isilon ports to network
    storageport update  $ISI_NATIVEGUID  IP --tzone $NETWORK
 
    #Create file thin vpool
    cos create file $COS true --description 'Virtual-Pool-Isilon' --protocols NFS CIFS --max_snapshots 10 --provisionType 'Thin' --neighborhoods $NH
	
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

#####################################################
###     ALL TEST CASES                            ###
#####################################################
test_1()
{

echot "Test 1 Begins"

    common_failure_injections="failure_505_FileDeviceController.createFS_before_filesystem_create"
                               
    failure_injections="${common_failure_injections}"
    cfs=("FileShare")
   
   for failure in ${failure_injections}
    do
      
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 1 with failure scenario: ${failure}..."
      mkdir -p results/${item}
      reset_counts
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}
     
      # Check the state of the fileShare that it doesn't exist
      snap_db 1 "${cfs[@]}"
     
      #Create File System
      run fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # Report results
      report_results test_4 ${failure}
    done
}


isilon_test() 
{
    login
    isilon_setup_once
    test_1
}


SS=$2
if [ $SS = all ] ; then
    
    echo -e "\n===========================FILE TESTS FOR isilon STARTED ==================================\n"
    echo "FILE TESTS FOR isilon " >fileSanity_output.txt
    isilon_test
    echo -e "\n===========================FILE TESTS FOR vnx STARTED ==================================\n"
    echo "FILE TESTS FOR vnx " >>fileSanity_output.txt
    vnx_test
    echo -e "\n===========================FILE TESTS FOR vnxe STARTED ==================================\n"
    echo "FILE TESTS FOR vnxe " >>fileSanity_output.txt
    vnxe_test
    echo -e "\n===========================FILE TESTS FOR netapp7 STARTED ==================================\n"
    echo "FILE TESTS FOR netapp7 " >>fileSanity_output.txt
    netapp7_test
    echo -e "\n===========================FILE TESTS FOR netappc STARTED ==================================\n"
    echo "FILE TESTS FOR netappc " >>fileSanity_output.txt
    netappc_test
    echo -e "\n===========================FILE TESTS FOR datadomain STARTED ==================================\n"
    echo "FILE TESTS FOR datadomain " >>fileSanity_output.txt
    #datadomain_test
else
    echo -e "\n===========================FILE TESTS FOR $SS STARTED ==================================\n"
    echo "FILE TESTS FOR $SS " >fileSanity_output.txt
    ${SS}_test
fi

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

cd $(dirname $0)
SANITY_BOX_IP=localhost
PATH=$PATH:$(dirname $0):$(dirname $0)/..:/bin:/usr/bin
export PATH



# Extra debug output
DUTEST_DEBUG=${DUTEST_DEBUG:-0}

# Global test repo location
GLOBAL_RESULTS_IP=10.247.101.46
GLOBAL_RESULTS_PATH=/srv/www/htdocs
LOCAL_RESULTS_PATH=/tmp
GLOBAL_RESULTS_OUTPUT_FILES_SUBDIR=output_files
RESULTS_SET_FILE=results-set.csv
TEST_OUTPUT_FILE=default-output.txt
REPORT=0
DO_CLEANUP=0


PROJECT=fileTestProject
TENANT='Provider Tenant'
NH=varrayisilon
COS=vpoolisilon
NETWORK=network
FS_SIZEMB=1024MB


# Isilon configuration
ISI_DEV=isilon_device
ISI_NATIVEGUID=ISILON+001b21c257c492bdfa56c01b1d3436e39c38


# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}

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

Usage()
{
    echo 'Usage: filetests.sh <sanity conf file path> [isilon | unity | vnxe | vnxfile] [-report] [-cleanup] [test_1 test_2 ...]'
    echo ' (isilon | unity  ...: Storage platform to run on.'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' [-cleanup]: Clean up the pre-created volumes and exports associated with -setup operation (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    print_test_names
    echo ' Example:  ./wftests.sh sanity.conf isilon -report -cleanup test_7+'
    echo '           Will start from clean DB, report results to reporting server, clean-up when done, and start on test_7 (and run all tests after test_7'
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
      shift
      source $SANITY_CONFIG_FILE
   fi
fi



date
#[ $# -eq 0 ] || Usage
[ "$1" = "help" ] && Usage
[ "$1" = "-h" ] && Usage


SS=$1
case $1 in
    all|isilon|netapp7|netappc|vnxfile|datadomain|vnxe)
    echo "Script will run for $1 devices"
    shift
     ;;
    *)
     Usage
esac

if [ "${1}" = "-report" ]; then
        echo "Reporting is ON"
        REPORT=1
        shift;
fi

if [ "$1" = "-cleanup" ]; then
	DO_CLEANUP=1;
	shift;
fi


# create a project for running the tests
project_setup()
{
    project show $PROJECT &> /dev/null && return $?
    project create $PROJECT
}


#
# add an ISILON storage device with a default pool that supports NFS and N+2:1 protection
#
isilon_setup()
{
    # do this only once

    #Create the project, if not already there..   
    project_setup
   
    # Add and discover the Isilon Array
    discoveredsystem show  $ISI_DEV &> /dev/null && return $?
    run discoveredsystem create $ISI_DEV isilon $ISI_IP 8080 $ISI_USER $ISI_PASSWD --serialno=$ISI_SN
    
    #Create Virtual Array
    run neighborhood create $NH
    
    #Create Network
    run transportzone create $NETWORK $NH --type IP

    #Attach Isilon ports to network
    run storageport update  $ISI_NATIVEGUID  IP --tzone $NETWORK
 
    #Create file thin vpool
    run cos create file $COS true --description 'Virtual-Pool-Isilon' --protocols NFS CIFS --max_snapshots 10 --provisionType 'Thin' --neighborhoods $NH
	
}

# Reset all of the system properties so settings are back to normal that are changed 
# during the tests themselves.
reset_system_props_pre_test() {
    set_artificial_failure "none"
}
 
#####################################################
###     ALL TEST CASES                            ###
#####################################################

# WF Reliability Test to check create file system WF rollback working as expected. 
# 1. Inject failure_505_FileDeviceController.createFS_before_filesystem_create
# 2. Create File System From ViPR-it should fail
# 3. Check any inconsistency in DB
# 4. Rerun create file system without any error injection- Pass
#
test_505()
{

echot "Test 505 Begins"

    common_failure_injections="failure_505_FileDeviceController.createFS_before_filesystem_create"
                               
    failure_injections="${common_failure_injections}"
    cfs=("FileShare")
   
    item=${RANDOM}
    TEST_OUTPUT_FILE=test_output_${item}.log
    secho "Running Test 1 with failure scenario: ${failure}..."
    mkdir -p results/${item}
    reset_counts
    fsname=test505_${item}    
  
    # Turn on failure at a specific point
      set_artificial_failure ${failure}
     
    # snap the state of DB before excecuting createFS
      snap_db 1 "${cfs[@]}"
     
    # Create File System
      fail fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
    # snap the state of DB before excecuting createFS
      snap_db 2 "${cfs[@]}"

    # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"
  
    # Turn off the failure
      set_artificial_failure none

    # Rerun create file system without any error injection- Pass 
      run fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB

    # Report results
      report_results test_505 ${failure}
 
}

# DL Test to check ViPR do not delete duplicate file system from array while file system provisioning.
# 1. Create file system from Array directly
# 3. Rerun the create file system with same name once again- IT should fail
# 4. Check Array for file system existence.-IT should exist..
#

test_506()
{
echot "Test 506 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test506_${item}

       # snap the state of DB before excecuting createFS
      snap_db 1 "${cfs[@]}"

      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create file system directly from array
      run java -jar Isilon.jar $SANITY_CONFIG_FILE create_directory $fsPath
     
      #2.Create File System
      fail fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      #3.Check for on Array for directory existence
      run java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_506 ${failure}

}

#  Test to check ViPR do not delete CIFS share already on backend
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly.
# 3. Try to create CIFS share with same name from ViPR- it should fail.
# 4. Check Array for share existence. 
# 5. ViPR DB check for any stale entry 

test_507()
{
echot "Test 507 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test507_${item}
      ISI_SMBFILESHARE=test507_share_${item}
      

      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      run fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3. Try to create same named CIFS share from ViPR.
      fail fileshare share $PROJECT/$fsname  $ISI_SMBFILESHARE --description 'New_SMB_Share'     

      #4. Check on array for share existence.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_507 ${failure}

}

#  Test to check ViPR do not delete CIFS share already on backend
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. Try to create CIFS share with same name but UPPER CASE from ViPR- it should fail.
# 4. Check Array for share existence. 
# 5. ViPR DB check for any stale entry 

test_508()
{
echot "Test 508 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test508_${item}
      ISI_SMBFILESHARE=test508_share_${item}
      ISI_SMBFILESHARE_UPPERCASE=TEST508_SHARE_${item}


      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      run fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3. Try to create same named (UPPER CASED) CIFS share from ViPR.
      fail fileshare share $PROJECT/$fsname  $ISI_SMBFILESHARE_UPPERCASE --description 'New_SMB_Share'     

      #4. Check on array for share existence.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_508 ${failure}

}

#  Test to check ViPR do not delete file system having CIFS share created by array directly.
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. Try to delete file system from ViPR- it should fail.
# 4. Check Array for fs existence.
# 5. Check Array for share existence.
# 6. ViPR DB check for any inconsistency.

test_509()
{
echot "Test 509 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test509_${item}
      ISI_SMBFILESHARE=test509_share_${item}
      
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      run fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3.Try to delete file system from ViPR- it should fail. 
      fail fileshare delete $PROJECT/$fsname   

      #4.Check for on Array for directory existence
      run java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      #5. Check on array for share existence.
      run java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_509 ${failure}

}
isilon_test() 
{    # Reset system properties
    reset_system_props_pre_test

    # Get the latest tools
    retrieve_tooling

    isilon_setup
    
    test_509
}


if [ $SS = all ] ; then
    login
    isilon_test
    vnx_test
    vnxe_test
    netapp7_test
    netappc_test
    datadomain_test
else
    login
echo $BOURNE_IP
    ${SS}_test
fi

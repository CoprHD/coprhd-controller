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

Usage()
{
    echo 'Usage: wftests.sh <sanity conf file path> [vmax2 | vmax3 | vnx | vplex [local | distributed] | xio | unity | vblock | srdf [sync | async]] [-setup(hw) | -setupsim] [-report] [-cleanup] [-resetsim] [-setuponly] [test_1 test_2 ...]'
    echo ' (vmax2 | vmax3 ...: Storage platform to run on.'
    echo ' [-setup(hw) | setupsim]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes (Required to run first, can be used with tests'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' [-cleanup]: Clean up the pre-created volumes and exports associated with -setup operation (Optional)'
    echo ' [-resetsim]: Resets the simulator as part of setup (Optional)'
    echo ' [-setuponly]: Only performs setup, no tests are run. (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    print_test_names
    echo ' Example:  ./wftests.sh sanity.conf vmax3 -setupsim -report -cleanup test_7+'
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


# Overall suite counts
VERIFY_COUNT=0
VERIFY_FAIL_COUNT=0

# Per-test counts
TRIP_VERIFY_COUNT=0
TRIP_VERIFY_FAIL_COUNT=0

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}



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

run2()
{
    cmd=$*
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP $cmd 2>&1
    status=$?
    if [ $status = 0 ] ; then
        tstatus=DONE
    else
        tstatus=FAILED
    fi
    echo -e "\n$cmd =====:$tstatus"
       
}

login2()
{
    echo "Login into sanity box"
    echo $SYSADMIN_PASSWORD | viprcli -hostname $SANITY_BOX_IP authenticate -u $SYSADMIN -d $COOKIE_DIR -cf $COOKIE_FILE_NAME &> /dev/null && return $?
    echo "Login failed".
    exit 1
}


#setting up license
add_license()
{
    echo "Setting up license"
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP system get-license | grep ViPR_Controller &> /dev/null && return $?
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP system add-license -licensefile /root/yoda_temp_license.lic
}

# create a project for running the sanity tests

project_setup()
{
    run2 project create -n project_test
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

#####################################################
###     ALL TEST CASES                            ###
#####################################################
test_1()
{

echot "Test 1 Begins"

    common_failure_injections="failure_505_FileDeviceController.createFS_before_filesystem_create"
                               
    failure_injections="${common_failure_injections}"

   
   for failure in ${failure_injections}
    do
      
      TEST_OUTPUT_FILE=test_output_${item}.log
      secho "Running Test 1 with failure scenario: ${failure}..."
      mkdir -p results/${item}
      reset_counts
      
      # Turn on failure at a specific point
      set_artificial_failure ${failure}

      
      # Report results
      report_results test_4 ${failure}
    done
}


#####################################################
###     BASIC SETUP FOR ALL STORAGE SYSTEMS       ###
#####################################################

add_storage_system()
{
    NAME=$1
    TYPE=$2
    IP=$3
    PORT=$4
    USER=$5
#below two lines are intentionally not formatted to do automatic login
    PASSWD="$6
$6"

    if [ "$TYPE" = "vnxfile" ] ; then 
        SMIS_IP=$VNXF_SMIS_IP
        SMIS_PORT=$VNXF_SMIS_PORT
        SMIS_USER=$VNXF_SMIS_USER
#below four lines are intentionally not formatted to do automatic login
    PASSWD="$6
$6
$6
$6"
        echo "$PASSWD" | viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem create -n $NAME -t $TYPE -dip $IP -dp $PORT -u $USER -smisuser $SMIS_USER -smisip $SMIS_IP -smisport $SMIS_PORT
        wait_for_discovery $NAME $TYPE
   
    elif [ "$TYPE" = "datadomain" ] ; then
        echo "$PASSWD" | viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storageprovider create -n $NAME -provip $IP -provport $PORT -u $USER -if ddmc
        wait_for_discovery $NAME $TYPE

   elif [ "$TYPE" = "vnxe" ] ; then
        echo "$PASSWD" | viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem create -n $NAME -t $TYPE -deviceip $IP -dp $PORT -u $USER
        sleep 20
        VNXE_NAME=`viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem list -t vnxe -l`
        VNXE_NAME=`echo "$VNXE_NAME" |  grep $IP | awk '{print $1}'`
        wait_for_discovery $VNXE_NAME $TYPE
    else
        echo "$PASSWD" | viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem create -n $NAME -t $TYPE -deviceip $IP -dp $PORT -u $USER
        wait_for_discovery $NAME $TYPE
    fi
}

wait_for_discovery()
{
 NAME=$1
 TYPE=$2
 discovery_status=`viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem show -t $TYPE -n $NAME `
 discovery_status=`echo "$discovery_status" | grep job_discovery_status | awk '{print $2}'`
 a=1
 while [ "$discovery_status" != "\"COMPLETE\"," ]
 do  
     echo "waiting for storage device to be discovered....."
     if [ $a -lt 20 ] ; then 
         sleep 10
         discovery_status=`viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP storagesystem show -t $TYPE -n $NAME `
         discovery_status=`echo "$discovery_status" | grep job_discovery_status | awk '{print $2}'`
         a=`expr $a + 1`
     else
         echo "Discovery couldn't be finished in 200 seconds ...so existing"
         exit 2
     fi
 done
}

virtual_array_setup()
{
    VIRTUAL_ARRAY=$1
    NETWORK=$2
    run2 varray create -n $VIRTUAL_ARRAY
    run2 network create -n $NETWORK -varray $VIRTUAL_ARRAY -transport_type IP
}

update_storage_port()
{
    NAME=$1
    TYPE=$2
    NETWORK=$3
    VIRTUAL_ARRAY=$4
    run2 storageport update -storagesystem $NAME -t $TYPE -network $NETWORK -varray_add $VIRTUAL_ARRAY -transporttype IP
}

virtual_pool_setup()
{
    VPOOL=$1 
    run2 vpool create -name $VPOOL -protocol NFS CIFS -t file -maxsnapshots 10 -varrays $VIRTUAL_ARRAY -pt Thin -description 'vpool_for_Sanity'
}

run2()
{
    cmd=$*
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP $cmd 2>&1
    status=$?
    if [ $status = 0 ] ; then
        tstatus=DONE
    else
        tstatus=FAILED
    fi
    echo -e "\n$cmd =====:$tstatus"
       
}

common_setup() 
{
    login
    add_license
    project_setup
}


isilon_test() 
{
login
   # add_storage_system isilon_device_sanity isilon $ISI_IP 8080 $ISI_USER $ISI_PASSWD
   # virtual_array_setup varray_isilon_${seed} network_isilon_${seed}
   # update_storage_port isilon_device_sanity isilon network_isilon_${seed} varray_isilon_${seed}
   # virtual_pool_setup vpool_isilon_${seed}
    test_1
}
#Common setup call
common_setup

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
common_cleanup
echo 
cat fileSanity_output.txt 
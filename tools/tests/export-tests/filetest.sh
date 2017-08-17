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
source $(dirname $0)/common_subs.sh
seed=`date "+%H%M%S%N"`
source filetest.conf
SANITY_BOX_IP=$VIPR_HOSTNAME
source $VIPR_CLI_HOME/viprcli.profile
PATH=$PATH:$VIPR_CLI_HOME/bin
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
[ $# -ge 1 ] || Usage
[ "$1" = "help" ] && Usage
[ "$1" = "-h" ] && Usage

case $1 in
    all|isilon|netapp7|netappc|vnxfile|datadomain|vnxe)
    echo "Sanity Script will run for $1 devices"
     ;;
    *)
     Usage
esac

#setting up license
add_license()
{
    echo "Setting up license"
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP system get-license | grep ViPR_Controller &> /dev/null && return $?
    viprcli -cookiefile $COOKIE_FILE -hostname $SANITY_BOX_IP system add-license -licensefile $VIPR_LICENSE
}

# create a project for running the sanity tests

project_setup()
{
    run project create -n $PROJECT
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
    run varray create -n $VIRTUAL_ARRAY
    run network create -n $NETWORK -varray $VIRTUAL_ARRAY -transport_type IP
}

update_storage_port()
{
    NAME=$1
    TYPE=$2
    NETWORK=$3
    VIRTUAL_ARRAY=$4
    run storageport update -storagesystem $NAME -t $TYPE -network $NETWORK -varray_add $VIRTUAL_ARRAY -transporttype IP
}

virtual_pool_setup()
{
    VPOOL=$1 
    run vpool create -name $VPOOL -protocol NFS CIFS -t file -maxsnapshots 10 -varrays $VIRTUAL_ARRAY -pt Thin -description 'vpool_for_Sanity'
}

run()
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
    add_storage_system isilon_device_sanity isilon $ISI_IP $ISI_PORT $ISI_USER $ISI_PASSWD
    virtual_array_setup varray_isilon_${seed} network_isilon_${seed}
    update_storage_port isilon_device_sanity isilon network_isilon_${seed} varray_isilon_${seed}
    virtual_pool_setup vpool_isilon_${seed}
    file_tests varray_isilon_${seed} vpool_isilon_${seed} isilon
    cleanup isilon_device_sanity isilon vpool_isilon_${seed} network_isilon_${seed} varray_isilon_${seed}
}
#Common setup call
common_setup

SS=$1
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
#!/bin/sh
#
# Copyright (c) 2017 EMC Corporation
# All Rights Reserved
#
#
# File Rollback Reliabilty and DU/DL Test due to ECD. 
# ==========================
#@Author: mudit.jain@emc.com
#
#set -x

source $(dirname $0)/common_subs.sh

cd $(dirname $0)
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
    echo 'Usage: filetest.sh <sanity conf file path> [isilon | unity | vnxe | vnxfile] [-report] [test_1 test_2 ...]'
    echo ' (isilon | unity  ...: Storage platform to run on.'
    echo ' [-report]: Report results to reporting server: http://lglw1046.lss.emc.com:8081/index.html (Optional)'
    echo ' test names: Space-delimited list of tests to run.  Use + to start at a specific test.  (Optional, default will run all tests in suite)'
    print_test_names
    echo ' Example:  ./filetest.sh sanity.conf isilon -report test_7+'
    echo '           Will start from clean DB, report results to reporting server, and start on test_7 (and run all tests after test_7'
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

testcases=$*

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
    ISI_DEV=isilon_device

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

 
#####################################################
###     ALL TEST CASES                            ###
#####################################################

# WF Reliability Test to check create file system WF rollback working as expected. 
# 1. Inject failure_505_FileDeviceController.createFS_before_filesystem_create
# 2. Create File System From ViPR-it should fail
# 3. Check any inconsistency in DB
# 4. Rerun create file system without any error injection- Pass
#
test_1()
{

echot "Test 1 Begins"

    common_failure_injections="failure_505_FileDeviceController.createFS_before_filesystem_create"
                               
    failure="${common_failure_injections}"
    cfs=("FileShare")
   
    item=${RANDOM}
    TEST_OUTPUT_FILE=test_output_${item}.log
    secho "Running Test 1 with failure scenario: ${failure}..."
    mkdir -p results/${item}
    reset_counts
    fsname=test1_${item}    
  
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
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB

    # Report results
      report_results test_505 ${failure}
 
}




#  Test to check ViPR do not delete CIFS share already on backend
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. Try to create CIFS share with same name but UPPER CASE from ViPR- it should fail.
# 4. Check Array for share existence. 
# 5. ViPR DB check for any stale entry 

test_2()
{
echot "Test 2 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test2_${item}
      ISI_SMBFILESHARE=test2_share_${item}
      ISI_SMBFILESHARE_UPPERCASE=TEST2_SHARE_${item}


      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3. Try to create same named (UPPER CASED) CIFS share from ViPR.
      fail fileshare share $PROJECT/$fsname  $ISI_SMBFILESHARE_UPPERCASE --description 'New_SMB_Share'     

      #4. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_2 ${failure}

}

#  Test to check ViPR do not delete CIFS share already on backend and creates new one from ViPR
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. create CIFS share with different from ViPR- it should PASS.
# 4. Check Array for share existence. 

test_3()
{
echot "Test 3 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test3_${item}
      ISI_SMBFILESHARE=test3_share_${item}
      ISI_SMBFILESHARE2=test3_share2_${item}


      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      #2.CIFS share that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3. Try to create different name CIFS share from ViPR.
      runcmd fileshare share $PROJECT/$fsname $ISI_SMBFILESHARE2 --description 'New_SMB_Share2'     

      #4. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # Report results
      report_results test_3 ${failure}

}

#  Test to check ViPR do not delete file system having CIFS share created by array directly.
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. Try to delete file system from ViPR- it should fail.
# 4. Check Array for fs existence.
# 5. Check Array for share existence.
# 6. ViPR DB check for any inconsistency.

test_4()
{
echot "Test 4 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test4_${item}
      ISI_SMBFILESHARE=test4_share_${item}
      
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3.Try to delete file system from ViPR- it should fail. 
      fail fileshare delete $PROJECT/$fsname   

      #4.Check for on Array for directory existence
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      #5. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_4 ${failure}

}

#  Test to check ViPR do not delete file system having CIFS share created by array directly.
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly
# 3. Try to delete file system from ViPR- it should fail.
# 4. Check Array for fs existence.
# 5. Check Array for share existence.
# 6. ViPR DB check for any inconsistency.

test_5()
{
echot "Test 5 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test5_${item}
      ISI_SMBFILESHARE=test5_share_${item}
      
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3.Try to delete file system from ViPR- it should fail. 
      fail fileshare delete $PROJECT/$fsname   

      #4.Check for on Array for directory existence
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      #5. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_5 ${failure}

}

# Test to check ViPR do not delete renamed cifs share from array
# 1. Create file system from ViPR.
# 2. CIFS share that file system from ViPR.
# 3. Rename that share from array directly.
# 4. Try to delte share from ViPR- it should fail.
# 5. Check updated share existence on array. 
# 6. ViPR DB check for any inconsistency.

test_6()
{
echot "Test 6 begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test6_${item}
      ISI_SMBFILESHARE=test6_share_${item}
      ISI_SMBFILESHARE_RENAMED=test6_share_RENAMED_${item} 
      
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      #2.create  CIFS share from ViPR.
      runcmd fileshare share $PROJECT/$fsname $ISI_SMBFILESHARE --description 'New_SMB_Share'
      
      # snap the state of DB after excecuting createFS and share
      snap_db 1 "${cfs[@]}"
      
      #3.Rename that share from array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE update_cifs_share_name $fsPath $ISI_SMBFILESHARE $ISI_SMBFILESHARE_RENAMED

      #4.Try to delete share from ViPR- it should fail. 
      fail fileshare unshare $PROJECT/$fsname $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      #5. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE_RENAMED

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"


      # Report results
      report_results test_6 ${failure}


}

# Test to check ViPR do not delete renamed cifs share from array
# 1. Create file system from ViPR.
# 2. CIFS share that file system from ViPR.
# 3. Rename that share from array directly.
# 4. Create a directory from array.
# 5. CIFS share the directory from array-give name as given in step2.  
# 6. Try to delte share from ViPR- it should fail.
# 7. Check renamed share existence on array.
# 8. Check newly created share on array. 
# 9. ViPR DB check for any inconsistency.

test_7()
{
echot "Test 7 begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test7_${item}
      ISI_SMBFILESHARE=test7_share_${item}
      ISI_SMBFILESHARE_RENAMED=test7_share_RENAMED_${item} 
      
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      fsPath2=/ifs/vipr/testFS
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      #2.create  CIFS share from ViPR.
      runcmd fileshare share $PROJECT/$fsname $ISI_SMBFILESHARE --description 'New_SMB_Share'
      
      # snap the state of DB after excecuting createFS and share
      snap_db 1 "${cfs[@]}"
      
      #3.Rename that share from array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE update_cifs_share_name $fsPath $ISI_SMBFILESHARE $ISI_SMBFILESHARE_RENAMED

      #4.Create a directory from array.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_directory $fsPath2
     
      #5.CIFS share the directory from array-give name as given in step2
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath2 $ISI_SMBFILESHARE
      
      #6.Try to delete share from ViPR- it should fail. 
      fail fileshare unshare $PROJECT/$fsname $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      #7. Check on array for shares existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath2 $ISI_SMBFILESHARE
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE_RENAMED

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_7 ${failure}

}

#  Test to check ViPR do not delete file system having NFS export created by array directly.
# 1. Create file system from ViPR.
# 2. NFS export that file system from Array directly
# 3. Try to delete file system from ViPR- it should fail.
# 4. Check Array for fs existence.
# 5. Check Array for export existence.
# 6. ViPR DB check for any inconsistency.

test_8()
{
echot "Test 8 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test8_${item}
     
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.NFS export that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_nfs_export $fsPath

      #3.Try to delete file system from ViPR- it should fail. 
      fail fileshare delete $PROJECT/$fsname   

      #4.Check for on Array for directory existence
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      #5. Check on array for export existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_export $fsPath
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_8 ${failure}

}

#  Test to check ViPR do not delete file system having snapshot created by array directly.
# 1. Create file system from ViPR.
# 2. snapshot that file system from Array directly
# 3. Try to delete file system from ViPR- it should fail.
# 4. Check Array for fs existence.
# 5. Check Array for sanpshot existence.
# 6. ViPR DB check for any inconsistency.

test_9()
{
echot "Test 9 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test9_${item}
      snapshotName=test9_snapshot_${item}
     
      
      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.snapshot that file system from Array directly
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_snapshot $fsPath $snapshotName

      #3.Try to delete file system from ViPR- it should fail. 
      fail fileshare delete $PROJECT/$fsname   

      #4.Check for on Array for directory existence
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      #5. Check on array for snapshot existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_snapshot $fsPath $snapshotName
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_9 ${failure}

}

# DL Test to check ViPR do not delete duplicate file system from array while file system provisioning.
# 1. Create file system from Array directly
# 3. Rerun the create file system with same name once again- IT should fail
# 4. Check Array for file system existence.-IT should exist..
#

test_10()
{
echot "Test 10 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test10_${item}

       # snap the state of DB before excecuting createFS
      snap_db 1 "${cfs[@]}"

      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create file system directly from array
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_directory $fsPath
     
      #2.Create File System
      fail fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      #3.Check for on Array for directory existence
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_dir $fsPath

      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_10 ${failure}

}
#  Test to check ViPR do not delete CIFS share already on backend
# 1. Create file system from ViPR.
# 2. CIFS share that file system from Array directly.
# 3. Try to create CIFS share with same name from ViPR- it should fail.
# 4. Check Array for share existence. 
# 5. ViPR DB check for any stale entry 

test_11()
{
echot "Test 11 Begins"

      cfs=("FileShare")
      item=${RANDOM}
      TEST_OUTPUT_FILE=test_output_${item}.log
      mkdir -p results/${item}
      reset_counts
      fsname=test11_${item}
      ISI_SMBFILESHARE=test11_share_${item}
      

      fsPath=/ifs/vipr/$COS/ProviderTenant/$PROJECT/$fsname
      
      #1.Create File System
      runcmd fileshare create $fsname $PROJECT $NH $COS $FS_SIZEMB
      
      # snap the state of DB after excecuting createFS
      snap_db 1 "${cfs[@]}"

      #2.CIFS share that file system from Array directly.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE create_cifs_share $fsPath $ISI_SMBFILESHARE

      #3. Try to create same named CIFS share from ViPR.
      fail fileshare share $PROJECT/$fsname  $ISI_SMBFILESHARE --description 'New_SMB_Share'     

      #4. Check on array for share existence.
      runcmd java -jar Isilon.jar $SANITY_CONFIG_FILE check_for_share $fsPath $ISI_SMBFILESHARE
      
      # snap the state of DB after excecuting test
      snap_db 2 "${cfs[@]}"

      # Validate nothing was left behind
      validate_db 1 2 "${cfs[@]}"

      # Report results
      report_results test_11 ${failure}

}

#####################################################
###     ALL TEST CASES END
### PLEASE UPDATE test_end while adding new test cases 
#####################################################

test_start=1
test_end=11

run_test()
{

if [ "$1" != "none" ] 
then
    
    if [ "$1" != "" -a "${1:(-1)}" != "+"  ]
    then
       secho Request to run $*
       for t in $*
       do 
          secho "\n\nRun $t"
          $t
       done
    else
       if [ "${1:(-1)}" = "+" ]
       then
          num=`echo $1 | sed 's/test_//g' | sed 's/+//g'`
       else
          num=${test_start}
       fi
       while [ ${num} -le ${test_end} ]; 
       do
         test_${num}
         num=`expr ${num} + 1`
       done
   fi
fi    

}



isilon_test() 
{ 
    isilon_setup
    run_test $testcases
}

#Download the Isilon jar from location specified in conf file.. 
 retrieve_arraytools

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
    ${SS}_test
fi

echo There were $VERIFY_COUNT verifications
echo There were $VERIFY_FAIL_COUNT verification failures
echo `date`
echo `git status | grep 'On branch'`
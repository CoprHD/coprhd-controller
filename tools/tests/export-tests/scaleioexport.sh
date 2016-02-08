#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
#
# Export Test Suite for ScaleIO
#
#
# set -x

if [ "$DEBUG_SCRIPT" -eq 1 ]; then 
  set -x
fi 

SANITY_CONFIG_FILE=""

# The token file name will have a suffix which is this shell's PID
# It will allow to run the sanity in parallel
export BOURNE_TOKEN_FILE="/tmp/token$$.txt"
BOURNE_SAVED_TOKEN_FILE="/tmp/token_saved.txt"

PATH=$(dirname $0):$(dirname $0)/..:/bin:/usr/bin:

BOURNE_IPS=${1:-$BOURNE_IPADDR}
IFS=',' read -ra BOURNE_IP_ARRAY <<< "$BOURNE_IPS"
BOURNE_IP=localhost
IP_INDEX=0

macaddr=`/sbin/ifconfig eth0 | /usr/bin/awk '/HWaddr/ { print $5 }'`
if [ "$macaddr" = "" ] ; then
    macaddr=`/sbin/ifconfig en0 | /usr/bin/awk '/ether/ { print $2 }'`
fi
seed=`date "+%H%M%S%N"`
ipaddr=`/sbin/ifconfig eth0 | /usr/bin/perl -nle 'print $1 if(m#inet addr:(.*?)\s+#);' | tr '.' '-'`
export BOURNE_API_SYNC_TIMEOUT=700

if [ "$BOURNE_IP" = "localhost" ]; then
    SHORTENED_HOST="ip-$ipaddr"
fi
SHORTENED_HOST=${SHORTENED_HOST:=`echo $BOURNE_IP | awk -F. '{ print $1 }'`}
: ${TENANT=tenant}
: ${PROJECT=sanity}
SIO_CLI=/opt/emc/scaleio/mdm/bin/cli
SIO_CLI_OUTPUT_FILE=/tmp/.sio-cli-output${RANDOM}.txt

# ============================================================
# - Export testing parameters                                -
# ============================================================
BASENUM=${RANDOM}
VOLNAME=EXPTest${BASENUM}
BLOCKSNAPSHOT_NAME=EXPTestSnap${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
PROJECT=project

# ============================================================
# ScaleIO CLI access is through SSH. In order to make this
# automated, we would need to have the DevKit VM's SSH keys
# copied over to the known/authorized host list on the
# ScaleIO MDM host. This method runs these steps (if they
# haven't already been done.
# ============================================================
validate_auto_ssh_access() {
    know_host=`grep $SCALEIO_IP ~/.ssh/known_hosts|wc -l`
    if [ $know_host -eq 0 ]; then
        if [ -e /root/.ssh/id_dsa ]; then
            echo SSH KEY has already been generated
        else
            echo We will have to generated the SSH KEY and push it to $SCALEIO_IP
            echo This should only be done once
            echo Accept all the defaults from ssh-keygen
            ssh-keygen
        fi
        echo Going to copy the SSH KEY to $SCALEIO_IP. Please type in the password when requested
        ssh-copy-id -i /root/.ssh/id_rsa.pub $SCALEIO_IP
    fi
    echo Checking if SSH KEY already on $SCALEIO_IP. If you get prompted for a password
    echo after this message, then it means that you will have to manually run the steps
    echo to copy the SSH KEY to $SCALEIO_IP. These are the steps:
    echo 1. ssh-keygen 2. ssh-copy-id -i /root/.ssh/id_rsa.pub $SCALEIO_IP
    ssh $SCALEIO_IP uname -a
}

VERIFY_EXPORT_COUNT=0
VERIFY_EXPORT_FAIL_COUNT=0
verify_export() {
    host=$1
    expected_mapped_volumes=$2
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
#    ssh $SCALEIO_IP $SIO_CLI --query_all_volumes | grep -B2 "Mapped SDC.*${host}" | perl -nle 'printf("%s %s ", $1, $2) if (m#Volume ID\s+(\w+)\s+Name:\s+(\w+)#); if (m#Mapped SDC\(s\):\s+(.*)#) { @ips = split(" ",$1); foreach $ip (@ips) { printf("%s ", $ip); } printf("\n"); }' > $SIO_CLI_OUTPUT_FILE
    ssh $SCALEIO_IP "$SIO_CLI --query_all_volumes | grep Mapped | gawk '{ print \$3 }' | xargs -i $SIO_CLI --query_volume --volume_id {} | egrep \"${host}\"" > $SIO_CLI_OUTPUT_FILE
    actual_count=`cat $SIO_CLI_OUTPUT_FILE | wc -l`
    if [ x"$expected_mapped_volumes" = x"gone" ]; then
        if [ $actual_count -ne "0" ]; then
            echo === FAILED: There was a failure. Expected no mappings for $host, but there were $actual_count
            echo === FAILED: Below are the results of the last ScaleIO CLI command:
            cat $SIO_CLI_OUTPUT_FILE
            VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
            cleanup
            finish
        fi
        echo PASSED: No more volumes mapped to $host
    elif [ $expected_mapped_volumes -ne $actual_count ]; then
            echo === FAILED: Expected $expected_mapped_volumes for $host, but found $actual_count
            VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
            cleanup
            finish
    else
        echo PASSED: $expected_mapped_volumes volumes mapped to $host
    fi
}

runcmd() {
    echo === $*
    $*
    if [ $? -ne 0 ]; then
       VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
       echo === FAILED: $*
       cleanup
       finish
    fi
}

login() {
    security login $SYSADMIN $SYSADMIN_PASSWORD
    syssvc $SANITY_CONFIG_FILE localhost setup
    security add_authn_provider ldap ldap://${LOCAL_LDAP_SERVER_IP} cn=manager,dc=viprsanity,dc=com secret    ou=ViPR,dc=viprsanity,dc=com uid=%U CN Local_Ldap_Provider VIPRSANITY.COM ldapViPR* SUBTREE --group_object_classes groupOfNames,groupOfUniqueNames,posixGroup,organizationalRole --group_member_attributes member,uniqueMember,memberUid,roleOccupant
    echo "Tenant $TENANT being used."
    TENANT=`tenant root|head -1`
    echo "Tenant is ${TENANT}";
}

setup() {
    sleep 15
    runcmd project create $PROJECT --tenant $TENANT
    runcmd storageprovider create ${SCALEIO_PROVIDER} ${SCALEIO_IP} 22 root admin scaleio --secondary_username admin --secondary_password Danger0us1
    runcmd storagedevice discover_all
    sleep 60
    # Get the first, non-IP ScaleIO network
    NETWORK=`transportzone listall | grep -v "IP" | head -1`
    set_hosts;
    runcmd neighborhood create $SCALEIO_VARRAY
    runcmd transportzone assign $NETWORK $SCALEIO_VARRAY
    storagedevice list | grep COMPLETE | gawk '{ print $2 }' | xargs -i storagepool update {} --nhadd $SCALEIO_VARRAY --pool $SCALEIO_VPOOL --type block --volume_type THIN
    storagedevice list | grep COMPLETE | gawk '{ print $2 }' | xargs -i storageport update {} ScaleIO --tzone $SCALEIO_VARRAY/$NETWORK
    runcmd neighborhood allow $SCALEIO_VARRAY $TENANT
    runcmd cos create block ${SCALEIO_VPOOL} true --description='ScaleIO-VPool' --protocols=ScaleIO --provisionType='Thick' --max_snapshots=8 --neighborhoods="$SCALEIO_VARRAY"
    storagedevice list | grep COMPLETE | gawk '{ print $2 }' | xargs -i cos update block $SCALEIO_VPOOL --storage {}
    runcmd cos allow ${SCALEIO_VPOOL} block $TENANT
    runcmd volume create ${VOLNAME} ${PROJECT} ${SCALEIO_VARRAY} ${SCALEIO_VPOOL} 1GB --count 4
}

set_hosts() {
    SIO_HOST1ID=`hosts list $TENANT | grep ${SIO_HOST1} | awk '{print $4}'`
    H1PI1=`initiator list $SIO_HOST1ID | grep YES | gawk '{ print $1 }'`
    SIO_HOST2ID=`hosts list $TENANT | grep ${SIO_HOST2} | awk '{print $4}'`
    H2PI1=`initiator list $SIO_HOST2ID | grep YES | gawk '{ print $1 }'`
    SIO_HOST3ID=`hosts list $TENANT | grep ${SIO_HOST3} | awk '{print $4}'`
    H3PI1=`initiator list $SIO_HOST3ID | grep YES | gawk '{ print $1 }'`
}

# Export Test 0
#
# Most basic test. Create an export with hosts and volumes
#
test_0() {
    echo "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}_t0
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST1ID},${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 1
#
# Basic+. Added hosts, remove hosts
#
test_1() {
    echo "Test 1 Begins"
    expname=${EXPORT_GROUP_NAME}t1_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts "${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    verify_export ${SIO_HOST3} 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts "${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    verify_export ${SIO_HOST3} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
    verify_export ${SIO_HOST3} gone
}

# Export Test 2
#
# Basic+. Add volumes. Remove volumes.
#
test_2() {
    echo "Test 2 Begins"
    expname=${EXPORT_GROUP_NAME}t2_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VOLNAME}-2
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 3
#
# Reverse order of create from Basic Test Case
#
# Tests to ensure we can modify existing masks as part of an export group create operation.
#
# Export data volumes to all hosts
# Export boot volumes to each host
# Delete individual boot volume export group
# Delete shared volume to all hosts
#
test_3() {
    echo "Test 3 Begins"
    expname=${EXPORT_GROUP_NAME}t3_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST2ID},${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}3 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 4
#
# Reversed order of create, reversed order of delete
#
# Tests to ensure we prevent removing initiators from a mask when another export group references it.
# Tests to ensure we can remove mask elements properly when no remaining export group references exist.
#
# Export data volumes to all hosts
# Export boot volumes to each host
# Delete shared volume to all hosts
# Delete individual boot volume export group
#
test_4() {
    echo "Test 4 Begins"
    expname=${EXPORT_GROUP_NAME}t4_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST2ID},${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}3 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 5
#
# Test basic functionality of add/remove volume, add/remove host.
#
# Test to ensure removing a host does not delete mask when another export group also has that host.
# Test to ensure removing a volume does not delete mask when another export group also has that volume/host combo.
# Test to ensure adding a host does not touch the array if already exists in another mask
# Test to ensure adding a host uses existing export mask, if it already exists (and not create a duplicate)
#
test_5() {
    echo "Test 5 Begins"
    expname=${EXPORT_GROUP_NAME}t5_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST2ID},${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}3 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${SIO_HOST2ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 6
#
# Test to ensure we create separate export groups per Host for Host type. (and name them properly)
# Since the hosts are part of a cluster, I expect the mask to contain the cluster name.
#
test_6() {
    echo "Test 6 Begins"
    expname=${EXPORT_GROUP_NAME}t6_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST2ID},${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    verify_export ${SIO_HOST3} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
    verify_export ${SIO_HOST3} gone
}

# Export Test 7
#
# Test removing a host from an export group when another group still has a reference.
#
test_7() {
    echo "Test 7 Begins"
    expname=${EXPORT_GROUP_NAME}t7_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${SIO_HOST2ID},${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST2ID},${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    verify_export ${SIO_HOST3} 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 2
    verify_export ${SIO_HOST3} 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    verify_export ${SIO_HOST3} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    verify_export ${SIO_HOST3} 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
    verify_export ${SIO_HOST3} gone
}

# Export Test 8
#
# Same as test 2, but tests removing volume from existing mask when an initiator goes away.
#
#
test_8() {
    echo "Test 8 Begins"
    expname=${EXPORT_GROUP_NAME}t8_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST3} 1
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST2ID}"
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}3 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${SIO_HOST2ID},${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 2
    echo "running remove host, expect to remove reference to mask 1 in export group 1"
    runcmd export_group update $PROJECT/${expname}1 --remHosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 2
    verify_export ${SIO_HOST3} 1
    echo "running delete export 1"
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 2
    verify_export ${SIO_HOST3} gone
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test 9
#
# Test to make sure removing and adding volumes removes and adds the mask if the mask doesn't exist before this test.
#
test_9() {
    echo "Test 9 Begins"
    expname=${EXPORT_GROUP_NAME}t9_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${SIO_HOST1} gone
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
}

# Export Test 10
#
# Test to make sure removing and adding volumes does NOT remove the mask if the mask DOES exist before this test.
#
# Note, the first time you run this test, you must "exit;" after the export group create.  Then clean your DB, remove the "exit" and run again.
#
#
test_10_standalone() {
    echo "Test 10 Begins"
    expname=${EXPORT_GROUP_NAME}t10_
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS, YOU'RE GOOD HERE"
    verify_export ${SIO_HOST1} 1
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID}"
    # comment-out this "exit" after the first exportgroup create is done, recreate your DB, then run again.
    exit; 
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS + ONE, YOU'RE GOOD HERE"
    verify_export ${SIO_HOST1} 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
}

# Export Test 11
#
# Defect that Tom's found:
# export create vol1,host1
# export update add host2
# export update rem host1
#  removes vol1 from a shared host1,host2 existing mask
#
# Note, the first time you run this test, you must "exit;" after the export group create.  Then clean your DB, remove the "exit" and run again.
#
#
test_11_standalone() {
    echo "Test 11 Begins"
    expname=${EXPORT_GROUP_NAME}t11_
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS, YOU'RE GOOD HERE"
    verify_export ${SIO_HOST1} 1
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID},${SIO_HOST2ID}"
    # comment-out this "exit" after the first exportgroup create is done, recreate your DB, then run again.
    exit; 
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS + ONE, YOU'RE GOOD HERE"
    verify_export ${SIO_HOST1} 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
}
 
# Export Test 12
#
# An issue Tom found with export update.
#
test_12() {
    echo "Test 12 Begins"
    expname=${EXPORT_GROUP_NAME}t12_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts "${SIO_HOST2ID},${SIO_HOST3ID}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    verify_export ${SIO_HOST3} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
    verify_export ${SIO_HOST3} gone
}

# Export Test 13
#
# Test export of initiators of a single host individually. That is, export1 - Host1:I1, export2 - Host1:I2
# Should create and maintain a single StorageGroup
#
test_13() {
    echo "Test 13 Begins"
    expname=${EXPORT_GROUP_NAME}t13_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${SIO_HOST1ID}/${H1PI1}"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 2
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${SIO_HOST1} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
}

# Export Test 14
#
# Test export where all initiators or all volumes are removed, then they are added back.
# The removal should result in the removal of StorageGroups. The additions should recreate
# the StorageGroups.
#
test_14() {
    echo "Test 14 Begins"
    expname=${EXPORT_GROUP_NAME}t14_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts ${SIO_HOST1ID}
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${SIO_HOST1} gone
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${SIO_HOST1ID}
    verify_export ${SIO_HOST1} gone
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${SIO_HOST1ID}
    verify_export ${SIO_HOST1} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
}

test_15() {
    expname=${EXPORT_GROUP_NAME}t15_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${SIO_HOST1ID}
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${SIO_HOST2ID}
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts ${SIO_HOST1ID}
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${SIO_HOST2ID}
    verify_export ${SIO_HOST1} 2
    verify_export ${SIO_HOST2} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} gone
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

#
# Piecemeal export creation test case.
# Create export group without volumes or hosts
# Add volumes
# Add hosts
#
test_16() {
    echo "Test 16 Begins"
    expname=${EXPORT_GROUP_NAME}t16_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host
    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-1
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${SIO_HOST1ID}
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${SIO_HOST2ID}
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${SIO_HOST2ID}
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

#
# Use-case is to create two separate exports for the same host, but different
# volumes. Then remove the volumes from each export
#
test_17() {
    echo "Test 17 Begins"
    expname=${EXPORT_GROUP_NAME}t17_
    hostXP1=${expname}H1
    hostXP2=${expname}H2
    runcmd export_group create ${PROJECT} $hostXP1 $SCALEIO_VARRAY --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${SIO_HOST1ID}" --type Host
    verify_export ${SIO_HOST1} 1
    runcmd export_group create ${PROJECT} $hostXP2 $SCALEIO_VARRAY --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${SIO_HOST1ID}" --type Host
    verify_export ${SIO_HOST1} 2
    runcmd export_group update ${PROJECT}/$hostXP1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ${SIO_HOST1} 1
    runcmd export_group update ${PROJECT}/$hostXP2 --remVols ${PROJECT}/${VOLNAME}-2
    verify_export ${SIO_HOST1} gone
    runcmd export_group delete ${PROJECT}/$hostXP1
    runcmd export_group delete ${PROJECT}/$hostXP2
}

# Export Test 
#
# Test defect where Initiator export groups with crazy configuration does not delete volume as expected. (cq to be filed)
#
test_18() {
    echo "Test initiator remove volume defect Begins"
    expname=${EXPORT_GROUP_NAME}t18_
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${SIO_HOST1ID}/${H1PI1}"
    verify_export ${SIO_HOST1} 1
    runcmd export_group create $PROJECT ${expname}2 $SCALEIO_VARRAY --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${SIO_HOST2ID}/${H2PI1}"
    verify_export ${SIO_HOST2} 1
    runcmd export_group create $PROJECT ${expname}3 $SCALEIO_VARRAY --type Initiator --volspec "${PROJECT}/${VOLNAME}-3,${PROJECT}/${VOLNAME}-2" --inits "${SIO_HOST1ID}/${H1PI1}"
    verify_export ${SIO_HOST1} 3
    verify_export ${SIO_HOST2} 1
    # I expect this to remove VOLNAME-2 from SIO_HOST2's mask, but it will not
    runcmd export_group update $PROJECT/${expname}3 --remInits "${SIO_HOST1ID}/${H1PI1}"
    verify_export ${SIO_HOST1} 1
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${SIO_HOST1} gone
    verify_export ${SIO_HOST2} gone
}

# Export Test
#
# Test blocksnapshot, clones, and volume exports
#
test_19() {
    echo "Test 19 - Export blocksnapshot, clones, and volumes"
    expname=${EXPORT_GROUP_NAME}t19_
    runcmd blocksnapshot create $PROJECT/${VOLNAME}-1 ${BLOCKSNAPSHOT_NAME}
    runcmd volume full_copy ${VOLNAME}-copy $PROJECT/${VOLNAME}-1
    runcmd export_group create $PROJECT ${expname}1 $SCALEIO_VARRAY --type Host --volspec "${PROJECT}/${VOLNAME}-1","${PROJECT}/${VOLNAME}-1/${BLOCKSNAPSHOT_NAME}" --hosts "${SIO_HOST1ID}"
    verify_export ${SIO_HOST1} 2
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1/${BLOCKSNAPSHOT_NAME}"
    verify_export ${SIO_HOST1} 1
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1/${BLOCKSNAPSHOT_NAME}"
    verify_export ${SIO_HOST1} 2
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-copy"
    verify_export ${SIO_HOST1} 3
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-copy"
    verify_export ${SIO_HOST1} 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${SIO_HOST1} gone
    runcmd blocksnapshot delete $PROJECT/${VOLNAME}-1/${BLOCKSNAPSHOT_NAME}
}

deletevols() {
   for id in `volume list project | grep YES | awk '{print $7}'`
   do
      runcmd volume delete ${id} > /dev/null
      echo "Deleting volume: ${id}"
   done
}

cleanup() {
   for id in `export_group list project | grep YES | awk '{print $5}'`
   do
      runcmd export_group delete ${id} > /dev/null
      echo "Deleted export group: ${id}"
   done
   volume delete $PROJECT --project --wait
   echo There were $VERIFY_EXPORT_COUNT export verifications
   echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures
   if [ -e $SIO_CLI_OUTPUT_FILE ]; then
      rm $SIO_CLI_OUTPUT_FILE
   fi
}

finish() {
   if [ $VERIFY_EXPORT_FAIL_COUNT -ne 0 ]; then 
       exit $VERIFY_EXPORT_FAIL_COUNT
   fi
   exit 0
}

# ============================================================
# -    M A I N
# ============================================================

# ============================================================
# Check if there is a sanity configuration file specified 
# on the command line. In, which case, we should use that 
# ============================================================
if [ "$1"x != "x" ]; then
   if [ -f "$1" ]; then 
      SANITY_CONFIG_FILE=$1
      echo Using sanity configuration file $SANITY_CONFIG_FILE
      source $SANITY_CONFIG_FILE
      shift
   fi
fi

validate_auto_ssh_access
login

if [ "$1" = "regression" ]
then
   test_0;
fi

if [ "$1" = "deletevol" ]
then
  deletevols;
  finish
fi

if [ "$1" = "delete" ]
then
  cleanup
  finish
fi

if [ "$1" = "setup" ]
then
    setup;
else
    set_hosts;
    set_cluster;
fi

# If there's a 2nd parameter, take that
# as the name of the test to run
if [ "$2" != "" ]
then
   echo Request to run $2
   $2
   cleanup
   finish
fi

test_0
test_1
test_2
test_3
test_4
test_5
test_6
test_7
test_8
test_9
test_12
test_13
test_14
test_15
test_16
test_17
test_18
test_19
cleanup
finish

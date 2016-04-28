#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
#
# Export Test
#
# Make sure if you create an export group where there's already export masks created, that it does the right thing.
#
# How to read this script:
#
# - This script builds up one cluster, three hosts, two initiators per host.
# - Each test will create a series of export groups.
# - In between each "export_group" command, you'll see a verification script that runs.
# - The verification script will contact the Hitachi via HiCommandcli to verify the expectations of the command that was run:
#      ./hitachihelper <mask-name> <#-Initiators-Expected> <#-LUNs-Expected>
#      "After the previous export_group command, I expect mask "billhost1" to have 2 initiators and 2 LUNs in it..."
# - Exports are cleaned at the end of the script, and since remove is just as complicated, verifications are done there as well.
#
# These test cases exercise our ability to perform:
# -	Create export (Host, Volumes)
# -	Create export (Cluster, Volumes)
# -	Add Host to export*
# -	Remove Host from export*
# -	Add Cluster to export*
# -	Remove Cluster from export*
# -     Add Volume to export
# -     Remove Volume from export
#
#set -x

Usage()
{
    echo 'Usage: hitachiexport.sh [setup]'
    echo ' [setup]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    exit 2
}

SANITY_CONFIG_FILE=""
# The token file name will have a suffix which is this shell's PID
# It will allow to run the sanity in parallel
export BOURNE_TOKEN_FILE="/tmp/token$$.txt"
BOURNE_SAVED_TOKEN_FILE="/tmp/token_saved.txt"

PATH=$(dirname $0):$(dirname $0)/..:/bin:/usr/bin:

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
BOURNE_IP=localhost

#
# Zone configuration
#
NH=nh
FC_ZONE_A=fctz_a
FC_ZONE_B=fctz_b
IP_ZONE=iptz

FCTZ_A=$NH/$FC_ZONE_A
FCTZ_B=$NH/$FC_ZONE_B

if [ "$BOURNE_IP" = "localhost" ]; then
    SHORTENED_HOST="ip-$ipaddr"
fi
SHORTENED_HOST=${SHORTENED_HOST:=`echo $BOURNE_IP | awk -F. '{ print $1 }'`}
: ${TENANT=tenant}
: ${PROJECT=sanity}

VPOOL_BASE=vpool
PROJECT=project

# If you want to change all the managed resource object names, change this value
BASENUM=${RANDOM}

VOLNAME=hitachiexp
EXPORT_GROUP_NAME=export${BASENUM}
HOST1=host1export
HOST2=host2export
HOST3=host3export
CLUSTER=cl${BASENUM}

which hitachihelper.sh
echo Note: Please make sure you have installed HiCommand DM Agent linux version in your devkit /opt folder.
if [ $? -ne 0 ]; then
    echo Could not find hitachihelper.sh path. Please add the directory where the script exists to the path.
    locate hitachihelper.sh
    exit
fi

runcmd() {
    echo === $*
    $*
}

pwwn()
{
    idx=$1
    echo 1E:${macaddr}:${idx}
}

nwwn()
{
    idx=$1
    echo 2E:${macaddr}:${idx}
}

: ${USE_CLUSTERED_HOSTS=1}
VERIFY_EXPORT_COUNT=0
VERIFY_EXPORT_FAIL_COUNT=0
verify_export() {
    no_host_name=0
    export_name=$1
    host_name=$2
    shift 2

    if [ "$host_name" = "-x-" ]; then
        # The host_name parameter is special, indicating no hostname, so
        # set it as an empty string
        host_name=""
        no_host_name=1
    fi

    cluster_name_if_any="_"
    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        cluster_name_if_any="_${CLUSTER}_"
        if [ "$no_host_name" -eq 1 ]; then
            # No hostname is applicable, so this means that the cluster name is the
            # last part of the MaskingView name. So, it doesn't need to end with '_'
            cluster_name_if_any="_${CLUSTER}"
        fi
    fi
    masking_view_name="${export_name}${cluster_name_if_any}${host_name}"
    if [ "$host_name" = "-exact-" ]; then
        masking_view_name=$export_name
    fi

    runcmd hitachihelper.sh $masking_view_name $host_name $*
    if [ $? -ne "0" ]; then
       echo There was a failure
       VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
       # cleanup
    fi
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
}


login() {
    security login $SYSADMIN $SYSADMIN_PASSWORD
    syssvc $SANITY_CONFIG_FILE localhost setup
    echo "Tenant $TENANT being used."
    TENANT=`tenant root|head -1`
    echo "Tenant is ${TENANT}";
}

setup() {
    
    echo "Setting up StorageProvider for Hitachi arrays."
    HITACHIPASS=0
    storageprovider create $HDS_PROVIDER $HDS_PROVIDER_IP $HDS_PROVIDER_PORT $HDS_PROVIDER_USER "$HDS_PROVIDER_PASSWD" $HDS_PROVIDER_INTERFACE_TYPE --usessl false
    while [ ${HITACHIPASS} -eq 0 ]
      do
      storagedevice discover_all
      storagedevice list | grep hds
      if [ $? -eq 1 ]
	  then
	  sleep 5
      else
	  HITACHIPASS=1
      fi
    done
    storagedevice discover_all --ignore_error
    neighborhood create $NH
    transportzone create $FC_ZONE_A $NH --type FC
    transportzone add $NH/$FC_ZONE_A $HDSB_INITIATOR 
    storagepool update $HDS_NATIVEGUID --nhadd $NH --pool "$HITACHIB_POOL" --type block --volume_type THIN_ONLY
    storagepool update $HDS_NATIVEGUID --nhadd $NH --pool "$HITACHIB_POOL" --type block --volume_type THIN_ONLY
    seed=`date "+%H%M%S%N"`
    storageport update --name 'None' $HDS_NATIVEGUID FC --tzone $NH/$FC_ZONE_A
    project create $PROJECT --tenant $TENANT 
    echo "Project $PROJECT created."
    echo "Setup ACLs on neighborhood for $TENANT"
    neighborhood allow $NH $TENANT

    FC_ZONE_A=fctz_a
    transportzone add $NH/${FC_ZONE_A} $H1PI1
    transportzone add $NH/${FC_ZONE_A} $H1PI2
    transportzone add $NH/${FC_ZONE_A} $H2PI1
    transportzone add $NH/${FC_ZONE_A} $H2PI2
    transportzone add $NH/${FC_ZONE_A} $H3PI1
    transportzone add $NH/${FC_ZONE_A} $H3PI2

    cluster create --project ${PROJECT} ${CLUSTER} ${TENANT}
    set_cluster;

    hosts create $HOST1 $TENANT Windows ${HOST1}.lss.emc.com --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${CLUSTERID}
    hosts create $HOST2 $TENANT Windows ${HOST2}.lss.emc.com --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${CLUSTERID}
    hosts create $HOST3 $TENANT Windows ${HOST3}.lss.emc.com --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${CLUSTERID}
    set_hosts;

    initiator create ${HOST1ID} FC $H1PI1 --node $H1NI1
    initiator create ${HOST1ID} FC $H1PI2 --node $H1NI2
    initiator create ${HOST2ID} FC $H2PI1 --node $H2NI1
    initiator create ${HOST2ID} FC $H2PI2 --node $H2NI2
    initiator create ${HOST3ID} FC $H3PI1 --node $H3NI1
    initiator create ${HOST3ID} FC $H3PI2 --node $H3NI2

    # make a base cos for protected volumes
    cos create block ${VPOOL_BASE}					\
	--description 'Base CoS for Export Tests' true \
	--protocols FC 			\
	--numpaths 1				\
	--provisionType 'Thick'			\
	--neighborhoods $NH                    

    cos update block ${VPOOL_BASE} --storage ${HDS_NATIVEGUID}
    cos allow $VPOOL_BASE block $TENANT
    runcmd volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 3

}

set_cluster() {
    CLUSTERID=`cluster list ${TENANT} | perl -nle 'printf("urn:storageos:Cluster:%s\n", $1) if (m#urn:storageos:Cluster:(.*?).,#);'`
}

set_hosts() {
    HOST1ID=`hosts list $TENANT | grep ${HOST1} | awk '{print $4}'`
    HOST2ID=`hosts list $TENANT | grep ${HOST2} | awk '{print $4}'`
    HOST3ID=`hosts list $TENANT | grep ${HOST3} | awk '{print $4}'`
}

# Verify no masks
#
# Makes sure there are no masks on the array before running tests
#
verify_nomasks() {
    echo "Verifying no masks exist on storage array"
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
    verify_export ${HOST3} gone
}

# Export Test 0
#
# Test existing functionality of export
# Also required to run "sanity quick & vncblock" to cover other export situations, like snapshots.
#
test_0() {
    echo "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID},${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
}

# Export Test 1
#
# Basic Use Case for clustered Hosts
#
# Export boot volumes to each host
# Export data volumes to all hosts
#
test_1() {
    echo "Test 1 Begins"
    expname=${EXPORT_GROUP_NAME}t1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2ID}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}2 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
}

# Export Test 2
#
# Basic Use Case for clustered hosts, but reverse order deleting export groups.
#
# Tests to ensure that we remove export mask references from export groups when those masks no longer exist.
# Tests to ensure that we maintain proper references to export masks in multiple export groups.
#
# Export boot volumes to each host
# Export data volumes to all hosts
# Delete individual boot volume export group
# Delete shared volume to all hosts
#
test_2() {
    echo "Test 2 Begins"
    expname=${EXPORT_GROUP_NAME}t2
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2ID}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}2 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
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
    expname=${EXPORT_GROUP_NAME}t3
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
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
    expname=${EXPORT_GROUP_NAME}t4
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
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
    expname=${EXPORT_GROUP_NAME}t5
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
}

# Export Test 6
#
# Test to ensure we create separate export groups per Host for Host type. (and name them properly)
# Since the hosts are part of a cluster, I expect the mask to contain the cluster name.
#
test_6() {
    echo "Test 6 Begins"
    expname=${EXPORT_GROUP_NAME}t6
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID},${HOST2ID},${HOST3ID}"
	verify_export ${expname}1 ${HOST1} 2 1
	verify_export ${expname}1 ${HOST2} 2 1
	verify_export ${expname}1 ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}1
	verify_export ${expname}1 ${HOST1} gone
	verify_export ${expname}1 ${HOST2} gone
	verify_export ${expname}1 ${HOST3} gone
}

# Export Test 7
#
# Cluster type version: Test to ensure we create export groups with proper names upon one export group command.
# Technically this isn't testing HDSMasking, it's testing the name generation code.
#
# EG:  VOL1: CLUSTER
#
# Export Masks:
# cluster_billhost1: VOL1
# cluster_billhost2: VOL2
# cluster_billhost3: VOL3
#
test_7() {
    echo "Test 7 Begins"
    expname=${EXPORT_GROUP_NAME}t7
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --cluster ${CLUSTERID}
    verify_export ${expname}1 ${HOST1} 2 1
	verify_export ${expname}1 ${HOST2} 2 1
	verify_export ${expname}1 ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
}

# Export Test 8
#
# Cluster and Host export group mix
#
# Technically the controller only talks "Initiator", so this test never expected to give us trouble.
#
# EG1:  VOL1,VOL2: CLUSTER
# EG2:  VOL3: HOST1
#
# Export Masks (at its peak)
# cluster_billhost1: VOL1,VOL2,VOL3
# cluster_billhost2: VOL1,VOL2
# cluster_billhost3: VOL1,VOL2
#
test_8() {
    echo "Test 8 Begins"
    expname=${EXPORT_GROUP_NAME}t8
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --cluster ${CLUSTERID}
	verify_export ${expname}1 ${HOST1} 2 2
	verify_export ${expname}1 ${HOST2} 2 2
	verify_export ${expname}1 ${HOST3} 2 2
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST1ID}"
	verify_export ${expname}2 ${HOST1} 2 3
	verify_export ${expname}1 ${HOST2} 2 2
	verify_export ${expname}1 ${HOST3} 2 2
    runcmd export_group delete $PROJECT/${expname}1
	verify_export ${expname}2 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} gone
	verify_export ${expname}1 ${HOST3} gone
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
	verify_export ${expname}1 ${HOST3} gone
}

# Export Test 9
#
# Test to ensure we create export groups with proper names when mixing host and cluster export groups in reverse order
#
# EG1:  VOL3: HOST1
# EG2:  VOL1,VOL2: CLUSTER
#
# Export Masks (at its peak)
# cluster_billhost1: VOL1,VOL2,VOL3
# cluster_billhost2: VOL1,VOL2
# cluster_billhost3: VOL1,VOL2
#
test_9() {
    echo "Test 9 Begins"
    expname=${EXPORT_GROUP_NAME}t9
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster ${CLUSTERID}
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}2  ${HOST2} 2 2
    verify_export ${expname}2  ${HOST3} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}2 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 2
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
    verify_export ${expname}2 ${HOST3} gone
}

# Export Test 10
#
# Tests our ability to "manage" the references to Export Masks.
#
# No-Op tests.  If you have an export group with everything, subset export groups should do nothing on the array.
# Excuse the length of the test; I have to touch add/remove vols, add/remove hosts, and create/delete export group in different ways.
#
# A good idea is to scan the controllersvc.log while this test is running.  After the first three export groups are created, you
# shouldn't see any masking operations until way down at the delete of {expname}2.  That's the point of the test, try to poke the
# HDSMaskingOrchestrator to take action in any way possible, and verify it doesn't.
#
test_10() {
    echo "Test 10 Begins"
    expname=${EXPORT_GROUP_NAME}t10
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster ${CLUSTERID}
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster ${CLUSTERID}
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group create $PROJECT ${expname}3 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-3" --cluster ${CLUSTERID}
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group create $PROJECT ${expname}4 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-3" --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    echo "PASSED: Checkpoint 1"
    runcmd export_group create $PROJECT ${expname}5 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-3" --hosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group create $PROJECT ${expname}6 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-3" --hosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group update ${PROJECT}/${expname}6 --addHosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    echo "PASSED: Checkpoint 2"
    runcmd export_group update ${PROJECT}/${expname}6 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group update ${PROJECT}/${expname}5 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group update ${PROJECT}/${expname}4 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    echo "PASSED: Checkpoint 3"
    runcmd export_group update ${PROJECT}/${expname}6 --remHosts "${HOST2ID}"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group update ${PROJECT}/${expname}5 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group update ${PROJECT}/${expname}4 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    echo "PASSED: Checkpoint 4"
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 3
    verify_export ${expname}1 ${HOST3} 2 3
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}1 ${HOST3} 2 2
    echo "PASSED: Checkpoint 5"
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} 2 3
    verify_export ${expname}1 ${HOST2} 2 1
    verify_export ${expname}1 ${HOST3} gone
    runcmd export_group delete $PROJECT/${expname}4
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    echo "PASSED: Checkpoint 6"
    runcmd export_group delete $PROJECT/${expname}5
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}6
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}1 ${HOST3} gone
}

# Export Test 11
#
# Test removing a host from an export group when another group still has a reference.
#
test_11() {
    echo "Test 11 Begins"
    expname=${EXPORT_GROUP_NAME}t11
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID},${HOST2ID},${HOST3ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
}

# Export Test 12
#
# Same as test 2, but tests removing volume from existing mask when an initiator goes away.
#
#
test_12() {
    echo "Test 12 Begins"
    expname=${EXPORT_GROUP_NAME}t12
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID},${HOST3ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST3} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2ID}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2ID},${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}2 ${HOST2} 2 2
    echo "running remove host, expect to remove reference to mask 1 in export group 1"
    runcmd export_group update $PROJECT/${expname}1 --remHosts "${HOST1ID}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 2
    verify_export ${expname}1 ${HOST3} 2 1
    echo "running delete export 1"
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 2
    verify_export ${expname}1 ${HOST3} gone
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
}

# Export Test 13
#
# Test to make sure removing and adding volumes removes and adds the mask if the mask doesn't exist before this test.
#
test_13() {
    echo "Test 13 Begins"
    expname=${EXPORT_GROUP_NAME}t13
    verify_export ${HOST1} gone
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    verify_export ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${HOST1} gone
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} gone
}

# Export Test 14
#
# Test to make sure removing and adding volumes does NOT remove the mask if the mask DOES exist before this test.
#
# Note, the first time you run this test, you must "exit;" after the export group create.  Then clean your DB, remove the "exit" and run again.
#
#
test_14() {
    echo "Test 14 Begins"
    expname=${EXPORT_GROUP_NAME}t14
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS, YOU'RE GOOD HERE"
    verify_export ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    # comment-out this "exit" after the first exportgroup create is done, recreate your DB, then run again.
    exit; 
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS + ONE, YOU'RE GOOD HERE"
    verify_export ${HOST1} 2 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${HOST1} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} 2 1
}

# Export Test 15
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
test_15() {
    echo "Test 15 Begins"
    expname=${EXPORT_GROUP_NAME}t15
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS, YOU'RE GOOD HERE"
    verify_export ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID},${HOST2ID}"
    # comment-out this "exit" after the first exportgroup create is done, recreate your DB, then run again.
    exit; 
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS + ONE, YOU'RE GOOD HERE"
    verify_export ${HOST1} 2 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${HOST1} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} 2 1
}
 
# Export Test 16
#
# An issue Tom found with export update.
#
test_16() {
    echo "Test 16 Begins"
    expname=${EXPORT_GROUP_NAME}t16
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1ID}"
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts "${HOST2ID},${HOST3ID}"
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} 2 1
    verify_export ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
    verify_export ${HOST3} gone
}

# Export Test 17
#
# Test (Initiator Export Group) initiator usage
#
test_17() {
    echo "Test 17 Begins"
    expname=${EXPORT_GROUP_NAME}t17
    runcmd export_group create $PROJECT ${expname}1 $NH --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1ID}/${H1PI1}"
    verify_export ${HOST1} 1 1
    runcmd export_group update $PROJECT/${expname}1 --addInits "${HOST1ID}/${H1PI2}"
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --addInits "${HOST2ID}/${H2PI1}"
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} gone
    runcmd export_group update $PROJECT/${expname}1 --remInits "${HOST1ID}/${H1PI1}"
    verify_export ${HOST1} 1 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} gone
}

# Export Test 18
#
# Test export of initiators of a single host individually. That is, export1 - Host1:I1, export2 - Host1:I2
# Should create and maintain a single StorageGroup
#
test_18() {
    echo "Test 18 Begins"
    expname=${EXPORT_GROUP_NAME}t18
    runcmd export_group create $PROJECT ${expname}1 $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${HOST1ID}/${H1PI1}"
    verify_export ${HOST1} 1 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${HOST1ID}/${H1PI2}"
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    runcmd export_group update $PROJECT/${expname}2 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${HOST1} 2 2
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    runcmd export_group update $PROJECT/${expname}2 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${HOST1} gone
}

# Export Test 19
#
# Test export where all initiators or all volumes are removed, then they are added back.
# The removal should result in the removal of StorageGroups. The additions should recreate
# the StorageGroups.
#
test_19() {
    echo "Test 19 Begins"
    expname=${EXPORT_GROUP_NAME}t19
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts ${HOST1ID}
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${HOST1} gone
    runcmd export_group update $PROJECT/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST1ID}
    verify_export ${HOST1} gone
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${HOST1ID}
    verify_export ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} gone
}

test_20() {
    expname=${EXPORT_GROUP_NAME}t21
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1ID}
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${HOST2ID}
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts ${HOST1ID}
    verify_export ${HOST1} 2 2
    verify_export ${HOST2} 2 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST2ID}
    verify_export ${HOST1} 2 2
    verify_export ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
}

#
# Piecemeal export creation test case.
# Create export group without volumes or hosts
# Add volumes
# Add hosts
#
test_21() {
    echo "Test 21 Begins"
    expname=${EXPORT_GROUP_NAME}t21
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host
    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VOLNAME}-1
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${HOST1ID}
    verify_export ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts ${HOST2ID}
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} 2 1
    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST2ID}
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
}

# Export Test 0
#
# Test existing functionality of export
# Also required to run "sanity quick & vncblock" to cover other export situations, like snapshots.
#
test_01() {
    echo "Test 01 Begins"
    expname=${EXPORT_GROUP_NAME}t01
    runcmd export_group create $PROJECT ${expname} $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --inits "${HOST1ID}/${H1PI1}"
    verify_export ${expname} ${HOST1} 1 1
    runcmd export_group update ${PROJECT}/${expname} --remVols "${PROJECT}/${VOLNAME}-1" --remInits "${HOST1ID}/${H1PI2}" --addInits "${HOST1ID}/${H1PI1}"
    #verify_export ${HOST1} 1 1
    #runcmd export_group delete $PROJECT/${expname}
    #verify_export ${HOST1} gone
}

# Export Test 
#
# Test existing functionality of export
# Also required to run "sanity quick & vncblock" to cover other export situations, like snapshots.
#
test_607408() {
    echo "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    runcmd export_group create $PROJECT ${expname} $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --hosts "${HOST1ID}"
    exit;
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
}

# Export Test 
#
# Test existing functionality of export
# Also required to run "sanity quick & vncblock" to cover other export situations, like snapshots.
#
test_608072() {
    echo "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    runcmd export_group create $PROJECT ${expname} $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1ID}"
    exit;
    verify_export ${HOST1} 2 1
    verify_export ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
}

test_multi() {
    howmany=50
    echo "Test banger begins"
    i=1
    while [ $i -ne ${howmany} ]
    do
       runcmd volume create ${VOLNAME}-o-${i} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
       i=`expr $i + 1`
    done

    i=1
    while [ $i -ne ${howmany} ]
    do
       runcmd export_group create $PROJECT oulti-${i} $NH --type Host --volspec "${PROJECT}/${VOLNAME}-o-${i}" --hosts "${HOST2ID}"
       i=`expr $i + 1`
    done
}
    
# Export Test 
#
# Test defect where Initiator export groups with crazy configuration does not delete volume as expected. (cq to be filed)
#
test_exclusivedefect() {
    echo "Test initiator remove volume defect Begins"
    expname=${EXPORT_GROUP_NAME}tEx
    runcmd export_group create $PROJECT ${expname}1 $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${HOST1ID}/${H1PI1}"
    verify_export ${HOST1} 1 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1" --inits "${HOST2ID}/${H2PI2}"
    verify_export ${HOST2} 1 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Initiator --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --inits "${HOST1ID}/${H1PI1},${HOST2ID}/${H2PI1}"
    verify_export ${HOST1} 1 2
    verify_export ${HOST2} 2 2
    # I expect this to remove VOLNAME-2 from HOST2's mask, but it will not
    runcmd export_group update $PROJECT/${expname} --remInits "${HOST2ID}${H2PI1}"
    verify_export ${HOST1} 1 2
    verify_export ${HOST2} 1 1
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT/${expname}2
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${HOST1} gone
    verify_export ${HOST2} gone
}

deletevols() {
   for id in `volume list project | grep YES | awk '{print $7}'`
   do
      runcmd volume delete ${id} --wait > /dev/null
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
   exit
}

# call this to generate a random WWN for exports.
# Hitachi (especially) does not like multiple initiator registrations on the same
# WWN to different hostnames, which only our test scripts tend to do.
# Give two arguments if you want the first and last pair to be something specific
# to help with debugging/diagnostics
randwwn() {
   if [ "$1" = "" ]
   then
      PRE="FF"
   else
      PRE=$1
   fi

   if [ "$2" = "" ]
   then
      POST="00"
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

login

H1PI1=`pwwn A0`
H1NI1=`nwwn A0`
H1PI2=`pwwn A1`
H1NI2=`nwwn A1`

H2PI1=`pwwn B0`
H2NI1=`nwwn B0`
H2PI2=`pwwn B1`
H2NI2=`nwwn B1`

H3PI1=`pwwn C0`
H3NI1=`nwwn C0`
H3PI2=`pwwn C1`
H3NI2=`nwwn C1`

if [ "$1" = "regression" ]
then
   test_0;
fi

if [ "$1" = "deletevol" ]
then
  deletevols;
  exit;
fi

if [ "$1" = "delete" ]
then
  cleanup;
  exit;
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
   shift
   echo Request to run $*

   for t in $*
   do
      echo Run $t
      $t
   done
   cleanup
   exit
fi

#test_608072;
#test_13;
#test_14;

#test_multi;
#test_607408;
#test_01;

# Passing tests:
verify_nomasks;
test_0;
test_1;
test_2;
test_3;
test_4;
test_5;
test_6;
test_7;
test_8;
test_9;
test_10;
test_11;
test_12;
test_16;
test_17;
test_18;
test_19;
test_20;
test_21;
cleanup

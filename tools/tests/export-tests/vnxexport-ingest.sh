#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
#
# Export Test for Ingest and coexistence
#
# This script will test various scenarios where creating an export group will have an impact on an existing
# VMAX masking layout that was there before ViPR came along.
#
# How to read this script:
#
# - This script builds up one cluster, three hosts, two initiators per host.
# - Each test will create a series of export groups.
# - In between each "export_group" command, you'll see a verification script that runs.
# - The verification script will contact the VMAX via symcli to verify the expectations of the command that was run:
#      ./symhelper <mask-name> <#-Initiators-Expected> <#-LUNs-Expected>
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
#
#
#
#
#  set -x

Usage()
{
    echo 'Usage: vmaxexport-ingest.sh [setup]'
    echo ' [setup]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    exit 2
}

SANITY_CONFIG_FILE=""
: ${USE_CLUSTERED_HOSTS=0}

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
BOURNE_IP=localhost

#
# Zone configuration
#
NH=nh
FC_ZONE_A=fctz_a

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

BASENUM=${BASENUM:=$RANDOM}
VOLNAME=vnxexp${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
CN=ingclnew
HN=ingestnew

# Allow for a way to easily use different hardware
if [ -f "./myhardware.conf" ]; then
    echo Using ./myhardware.conf
    source ./myhardware.conf
fi

which navihelper.sh
if [ $? -ne 0 ]; then
    echo Could not find navihelper.sh path. Please add the directory where the script exists to the path
    locate navihelper.sh
    exit
fi

VERIFY_EXPORT_COUNT=0
VERIFY_EXPORT_FAIL_COUNT=0
verify_export() {
    INIT_PATTERN="${I2}:${I3}:${I4}"
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
    runcmd navihelper.sh $BF_VNX_SP_IP ${INIT_PATTERN} $*
    if [ $? -ne "0" ]; then
       echo There was a failure
       VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
       cleanup
       finish
    fi
}

finish() {
    if [ $VERIFY_EXPORT_FAIL_COUNT -ne 0 ]; then 
        exit $VERIFY_EXPORT_FAIL_COUNT
    fi
    exit 0
}

runcmd() {
    echo === $*
    $*
}

pwwn()
{
    idx=$1
    echo 10:${macaddr}:${idx}
}

nwwn()
{
    idx=$1
    echo 20:${macaddr}:${idx}
}

drawstars() {
    repeatchar=`expr $1 + 2`
    while [ ${repeatchar} -gt 0 ]
    do 
       echo -n "*"
       repeatchar=`expr ${repeatchar} - 1`
    done
    echo "*"
}

echot() {
    numchar=`echo $* | wc -c`
    echo ""
    drawstars $numchar
    echo "* $* *"
    drawstars $numchar
}

login() {
    echo "Tenant is ${TENANT}";
    security login $SYSADMIN $SYSADMIN_PASSWORD
}

setup() {
    syssvc $SANITY_CONFIG_FILE localhost setup
    security add_authn_provider ldap ldap://${LOCAL_LDAP_SERVER_IP} cn=manager,dc=viprsanity,dc=com secret ou=ViPR,dc=viprsanity,dc=com uid=%U CN Local_Ldap_Provider VIPRSANITY.COM ldapViPR* SUBTREE --group_object_classes groupOfNames,groupOfUniqueNames,posixGroup,organizationalRole --group_member_attributes member,uniqueMember,memberUid,roleOccupant
    tenant create $TENANT VIPRSANITY.COM OU VIPRSANITY.COM
    echo "Tenant $TENANT created."

    # increase the pool subscription percentage
    syssvc $SANITY_CONFIG_FILE localhost set_prop controller_max_thin_pool_subscription_percentage 600

    SMISPASS=0
    # do this only once
    echo "Setting up SMIS"
    smisprovider create $BF_VNX_SMIS_DEV $BF_VNX_SMIS_IP 5988 admin '#1Password' false
    storagedevice discover_all --ignore_error

    storagepool update $BF_VNX_NATIVEGUID --type block --volume_type THIN_ONLY
    storagepool update $BF_VNX_NATIVEGUID --type block --volume_type THICK_ONLY

    neighborhood create $NH
    transportzone create $FC_ZONE_A $NH --type FC

    storagepool update $BF_VNX_NATIVEGUID --nhadd $NH --type block
    storageport update $BF_VNX_NATIVEGUID FC --tzone $NH/$FC_ZONE_A

    storagepool update ${BF_VNX_NATIVEGUID} --nhadd $NH --pool "$RP_VNXB_POOL" --type block --volume_type THIN_ONLY
    seed=`date "+%H%M%S%N"`
    storageport update ${BF_VNX_NATIVEGUID} FC --tzone $NH/$FC_ZONE_A
    project create $PROJECT --tenant $TENANT 
    echo "Project $PROJECT created."
    echo "Setup ACLs on neighborhood for $TENANT"
    neighborhood allow $NH $TENANT

    for clusternum in 10 12
    do
      CLUSTER=${CN}-${clusternum}
      cluster create --project ${PROJECT} ${CN}-${clusternum} ${TENANT}
      for hostnum in ${clusternum} `expr ${clusternum} + 1`
      do
	PI1=`hostwwn 11 ${hostnum}`
	NI1=`hostwwn 21 ${hostnum}`
	PI2=`hostwwn 12 ${hostnum}`
	NI2=`hostwwn 22 ${hostnum}`
	HOSTN=${HN}-${hostnum}
	transportzone add $NH/${FC_ZONE_A} $PI1
	transportzone add $NH/${FC_ZONE_A} $PI2
	hosts create ${HOSTN} $TENANT Windows ${HOSTN} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
	initiator create ${HOSTN} FC $PI1 --node $NI1
	initiator create ${HOSTN} FC $PI2 --node $NI2
      done
    done

    for clusternum in 14
    do
      CLUSTER=${CN}-${clusternum}
      cluster create --project ${PROJECT} ${CN}-${clusternum} ${TENANT}
      for hostnum in ${clusternum} `expr ${clusternum} + 1` `expr ${clusternum} + 2`
      do
	PI1=`hostwwn 11 ${hostnum}`
	NI1=`hostwwn 21 ${hostnum}`
	PI2=`hostwwn 12 ${hostnum}`
	NI2=`hostwwn 22 ${hostnum}`
	HOSTN=${HN}-${hostnum}
	transportzone add $NH/${FC_ZONE_A} $PI1
	transportzone add $NH/${FC_ZONE_A} $PI2
	hosts create ${HOSTN} $TENANT Windows ${HOSTN} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
	initiator create ${HOSTN} FC $PI1 --node $NI1
	initiator create ${HOSTN} FC $PI2 --node $NI2
      done
    done

    for hostnum in 00 01 02 03 04
    do
      PI1=`hostwwn 11 ${hostnum}`
      NI1=`hostwwn 21 ${hostnum}`
      PI2=`hostwwn 12 ${hostnum}`
      NI2=`hostwwn 22 ${hostnum}`
      HOSTN=${HN}-${hostnum}
      transportzone add $NH/${FC_ZONE_A} $PI1
      transportzone add $NH/${FC_ZONE_A} $PI2
      
      hosts create ${HOSTN} $TENANT Windows ${HOSTN} --port 8111 --username user --password 'password' --osversion 1.0
      initiator create ${HOSTN} FC $PI1 --node $NI1
      initiator create ${HOSTN} FC $PI2 --node $NI2
    done

    runcmd cos create block ${VPOOL_BASE} \
	  --description 'VirtualPool' true \
	  --protocols FC 			\
	  --numpaths 2				\
	  --provisionType 'Thin'			\
	  --max_snapshots 10                     \
	  --neighborhoods $NH

    runcmd cos update block ${VPOOL_BASE} --storage ${BF_VNX_NATIVEGUID}
    runcmd cos allow ${VPOOL_BASE} block $TENANT

    runcmd volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 2
}

setup_hosts_onvnx() {
    #expname=${HN}
    #for hostnum in 04
    #do
    #   runcmd export_group create $PROJECT ${expname}-$hostnum $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${TENANT}/${HN}-${hostnum}"
    #done
    #exit;
    expname=${CN}
    for hostnum in 14
    do
       runcmd export_group create $PROJECT ${expname}-$hostnum $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-${hostnum}"
    done
    echo "Recommend you delete your vipr database"
    finish
}

verify_setup() {
    if [ "$1" == "" ]
    then
        echot "Setup Verification Test: Verify Setup is in Proper Brownfield State"
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-00" ]
    then
	verify_export ingesthost-00_SG 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-01" ]
    then
	verify_export ingesthost-01_SG 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-02" ]
    then
	verify_export ingesthost-02_SG 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-03" ]
    then
	verify_export ingesthost-03_SG 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-10" ]
    then
	verify_export ingclhost_10_ingesthost-10_SG 2 1
	verify_export ingclhost_10_ingesthost-11_SG 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-12" ]
    then
	verify_export ingclhost-12_SG 4 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-14" ]
    then
	verify_export ingclhost-14_SG 4 1
    fi
}

# Verify no masks
#
# Makes sure there are no masks on the array before running tests
#
verify_nomasks() {
    echo "Verifying no masks exist on storage array"
    verify_export ${expname}1 ${HN}-00 gone
    verify_export ${expname}1 ${HN}-01 gone
    verify_export ${expname}1 ${HN}-02 gone
    verify_export ${expname}2 ${HN}-00 gone
    verify_export ${expname}2 ${HN}-01 gone
    verify_export ${expname}2 ${HN}-02 gone
}

# Export Test 1
#
# Test co-existence of two masking views for one host with two initiators.
#
test_1() {
    echot "Test 1 FAST Brownfield basic export group create/delete operations, multi-masking views"
    expname=${EXPORT_GROUP_NAME}t1
    verify_setup ingesthost-00

    # Now let's just try to create a boring export.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-00"
    verify_export ingesthost-00_SG 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingesthost-00_SG 2 1

    # Separate export group to add the second volume
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HN}-00"
    verify_export ingesthost-00_SG 2 2

    runcmd export_group delete $PROJECT/${expname}2
    verify_export ingesthost-00_SG 2 1

    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-1" --hosts "${HN}-00"
    verify_export ingesthost-00_SG 2 2

    runcmd export_group delete $PROJECT/${expname}3
    verify_setup ingesthost-00
}

# Export Test 2
#
# Test add and remove volume functionality on a brownfield environment
#
test_2() {
    echot "Test 2 BROWNFIELD Test add/remove volumes with multi-masking views"
    expname=${EXPORT_GROUP_NAME}t2
    verify_setup ingesthost-00

    # Greenfield/brownfield: just add a simple volume, then start adding/removing stuff.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-00"
    verify_export ingesthost-00_SG 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ingesthost-00_SG 2 1

    # Add a volume
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ingesthost-00_SG 2 2

    # Add the volume again
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-1
    verify_export ingesthost-00_SG 2 3

    # Remove both volumes
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-00_SG 2 1
    
    # Re-add both volumes
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-00_SG 2 3

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-00
}

# Export Test 3
#
# Test ability to add the same volumes to multiple hosts that aren't clusters.
#
test_3() {
    echot "Test 3 Brownfield Test multi-host, non-cluster"
    expname=${EXPORT_GROUP_NAME}t3
    verify_setup ingesthost-00
    verify_setup ingesthost-01

    # Should add the volume to any host that uses phantom storage groups.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-00,${HN}-01"
    verify_export ingesthost-00_SG 2 2
    verify_export ingesthost-01_SG 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ingesthost-00_SG 2 1
    verify_export ingesthost-01_SG 2 1

    # Add a volume
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ingesthost-00_SG 2 2
    verify_export ingesthost-01_SG 2 2

    # Add the volume again
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-1
    verify_export ingesthost-00_SG 2 3
    verify_export ingesthost-01_SG 2 3

    # Remove both volumes
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-00_SG 2 1
    verify_export ingesthost-01_SG 2 1
    
    # Re-add both volumes
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-00_SG 2 3
    verify_export ingesthost-01_SG 2 3

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-00
    verify_setup ingesthost-01
}

# Export Test 4
#
# Test 4 cluster MV with a 1 SG each node in the cluster.  (this is likely a common scenario)
#
test_4() {
    echot "Test 4 Brownfield cluster with with 1 SG per node in the cluster."
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t4
    verify_setup ingcl-10

    # Now let's just try to create a boring export.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-10"
    verify_export ingclhost_10_ingesthost-10_SG 2 2
    verify_export ingclhost_10_ingesthost-11_SG 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingclhost_10_ingesthost-10_SG 2 1
    verify_export ingclhost_10_ingesthost-11_SG 2 1

    # Separate export group to add the second volume
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CN}-10"
    verify_export ingclhost_10_ingesthost-10_SG 2 2
    verify_export ingclhost_10_ingesthost-11_SG 2 2

    runcmd export_group create $PROJECT ${expname}3 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-10"
    verify_export ingclhost_10_ingesthost-10_SG 2 3
    verify_export ingclhost_10_ingesthost-11_SG 2 3

    runcmd export_group delete $PROJECT/${expname}3
    verify_export ingclhost_10_ingesthost-10_SG 2 2
    verify_export ingclhost_10_ingesthost-11_SG 2 2

    # If you want to test failure, you can add a test to try to add a volume to only one host.  In this case, you can't.
    # Not sure why anyone would do this scenario in the first place, personally.

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-10
}

# Export Test 5
#
# Test 5 cluster MV with a 1 SG for the whole cluster
#
test_5() {
    echot "Test 5 Brownfield cluster with with 1 SG for the whole cluster"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t5
    verify_setup ingcl-12

    # Now let's just try to create a boring export.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingclhost-12_SG 4 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingclhost-12_SG 4 1

    # Separate export group to add the second volume
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CN}-12"
    verify_export ingclhost-12_SG 4 2

    runcmd export_group create $PROJECT ${expname}3 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingclhost-12_SG 4 3

    runcmd export_group delete $PROJECT/${expname}3
    verify_export ingclhost-12_SG 4 2

    # If you want to test failure, you can add a test to try to add a volume to only one host.  In this case, you can't.
    # Not sure why anyone would do this scenario in the first place, personally.

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-12
}

# Export Test 6
#
# Test adding initiator to existing host
#
test_6() {
    echot "Test 6 Brownfield existing storage group with one of two initiators"
    expname=${EXPORT_GROUP_NAME}t6
    verify_setup ingesthost-03

    # Now let's just try to create a boring export.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-03"
    verify_export ingesthost-03_SG 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingesthost-03_SG 1 1

    # Separate export group to add the second volume
    #runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HN}-03"
    #verify_export ingesthost-03_SG 2 2

    #runcmd export_group delete $PROJECT/${expname}2
    #verify_export ingesthost-03_SG 2 1

    #runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-1" --hosts "${HN}-00"
    #verify_export ingesthost-03_SG 2 2

    #runcmd export_group delete $PROJECT/${expname}3
    #verify_setup ingesthost-03
}

# Export Test 7
#
# Test 7 cluster, add a volume, then remove a host from the cluster.
#
test_7() {
    echot "Test 7 cluster, add a volumes, then remove a host from the cluster."
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t7
    verify_setup ingcl-12

    # Now let's just try to create a boring export.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingclhost-12_SG 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HN}-13"
    verify_export ingclhost-12_SG 4 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
}

# Export Test 8
#
# Test 8 cluster, Cluster export with 3 nodes. 2 exist on the VNX in a single SG
#
test_8() {
    echot "Test 8 cluster, Cluster export with 3 nodes. 2 exist on the VNX in a single SG"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t8
    verify_setup ingcl-14

    # Add a volume to the cluster
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-14"
    verify_export ingclhost-14_SG 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HN}-16"
    verify_export ingclhost-14_SG 4 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
}

cleanup() {
   for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
   do
      runcmd export_group delete ${id} > /dev/null
      echo "Deleted export group: ${id}"
   done
   # runcmd volume delete $PROJECT --project --wait
   echo There were $VERIFY_EXPORT_COUNT export verifications
   echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures
}

# Fixed initiator fields
I2=12
I3=34
I4=12

hostwwn() {
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

   I5=$2
   I6=$2
   I7=$2

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
      shift
      source $SANITY_CONFIG_FILE
   fi
fi

login

if [ "$1" = "verify" ]
then
   verify_setup;
   finish
fi

if [ "$1" = "delete" ]
then
  cleanup;
  finish
fi

if [ "$1" = "setup" ]
then
    setup;
    finish
fi

# If there's a 2nd parameter, take that 
# as the name of the test to run
if [ "$2" != "" ]
then
   echo Request to run $2
   $2
   finish
fi

verify_setup;
test_1;
test_2;
test_3;
test_4;
test_5;
test_6;
test_7;
test_8;
verify_setup;
cleanup
finish

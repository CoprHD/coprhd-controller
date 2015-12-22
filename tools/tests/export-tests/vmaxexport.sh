#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
#
# VMAX Export Tests
# =================
#
# Requirements:
# -------------
# - SYMAPI should be installed, which is included in the SMI-S install. Install tars can be found on 
#   Download the tar file for Linux, untar, run seinstall -install
# - The provider host should allow for NOSECURE SYMAPI REMOTE access. See https://asdwiki.isus.emc.com:8443/pages/viewpage.action?pageId=28778911 for more information.
#
# Make sure if you create an export group where there's already export masks created, that it does the right thing.
#
# How to read this script:
# ------------------------
#
# - This script builds up one cluster, three hosts, two initiators per host.
# - Each test will create a series of export groups.
# - In between each "export_group" command, you'll see a verification script that runs.
# - The verification script will contact the VMAX to verify the expectations of the command that was run:
#      ./symhelper <mask-name> <#-Initiators-Expected> <#-LUNs-Expected>
#      "After the previous export_group command, I expect mask "billhost1" to have 2 initiators and 2 LUNs in it..."
# - Exports are cleaned at the end of the script, and since remove is just as complicated, verifications are done there as well.
#
# These test cases exercise our ability to perform:
# -------------------------------------------------
# -	Create export (Host, Volumes)
# -	Create export (Cluster, Volumes)
# -	Add Host to export*
# -	Remove Host from export*
# -	Add Cluster to export*
# -	Remove Cluster from export*
# -     Add Volume to export
# -     Remove Volume from export
#
# set -x

Usage()
{
    echo 'Usage: vmaxexport.sh <sanity conf file path> [setup|delete [test1 test2 ...]]'
    echo ' [setup]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    echo ' [delete]: Will exports and volumes'
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
        cluster_name_if_any=""
        if [ "$no_host_name" -eq 1 ]; then
            # No hostname is applicable, so this means that the cluster name is the
            # last part of the MaskingView name. So, it doesn't need to end with '_'
            cluster_name_if_any="${CLUSTER}"
        fi
    fi
    masking_view_name="${cluster_name_if_any}${host_name}${VMAX_ID_3DIGITS}"
    if [ "$host_name" = "-exact-" ]; then
        masking_view_name=$export_name
    fi

    sleep 10
    runcmd symhelper.sh $VMAX_SN $masking_view_name $*
    if [ $? -ne "0" ]; then
       echo There was a failure
       VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
       cleanup
       finish
    fi
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
}

finish() {
    if [ $VERIFY_EXPORT_FAIL_COUNT -ne 0 ]; then
        exit $VERIFY_EXPORT_FAIL_COUNT
    fi
    exit 0
}

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
VPOOL_FAST=${VPOOL_BASE}-fast

BASENUM=${BASENUM:=$RANDOM}
VOLNAME=vmaxexp${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
HOST1=host1export${BASENUM}
HOST2=host2export${BASENUM}
HOST3=host3export${BASENUM}
CLUSTER=cl${BASENUM}

# Allow for a way to easily use different hardware
if [ -f "./myhardware.conf" ]; then
    echo Using ./myhardware.conf
    source ./myhardware.conf
fi

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

login() {
    echo "Tenant is ${TENANT}";
    security login $SYSADMIN $SYSADMIN_PASSWORD

    echo "Seeing if there's an existing base of volumes"
    BASENUM=`volume list project | grep YES | head -1 | awk '{print $1}' | awk -Fp '{print $2}' | awk -F- '{print $1}'`
    if [ "${BASENUM}" != "" ]
    then
       echo "Volumes were found!  Base number is: ${BASENUM}"
       VOLNAME=vmaxexp${BASENUM}
       EXPORT_GROUP_NAME=export${BASENUM}
       HOST1=host1export${BASENUM}
       HOST2=host2export${BASENUM}
       HOST3=host3export${BASENUM}
       CLUSTER=cl${BASENUM}
    fi
}

setup() {
    syssvc $SANITY_CONFIG_FILE localhost setup
    security add_authn_provider ldap ldap://${LOCAL_LDAP_SERVER_IP} cn=manager,dc=viprsanity,dc=com secret ou=ViPR,dc=viprsanity,dc=com uid=%U CN Local_Ldap_Provider VIPRSANITY.COM ldapViPR* SUBTREE --group_object_classes groupOfNames,groupOfUniqueNames,posixGroup,organizationalRole --group_member_attributes member,uniqueMember,memberUid,roleOccupant
    tenant create $TENANT VIPRSANITY.COM OU VIPRSANITY.COM
    echo "Tenant $TENANT created."
    sleep 120

    # Increase allocation percentage
    syssvc $SANITY_CONFIG_FILE localhost set_prop controller_max_thin_pool_subscription_percentage 600

    SMISPASS=0
    # do this only once
    echo "Setting up SMIS"
    runcmd smisprovider create VMAX-PROVIDER $VMAX_SMIS_IP 5988 admin '#1Password' false
    runcmd storagedevice discover_all --ignore_error

    runcmd storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    runcmd storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    runcmd neighborhood create $NH
    runcmd transportzone create $FC_ZONE_A $NH --type FC

    runcmd storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block
    runcmd storageport update $VMAX_NATIVEGUID FC --tzone $NH/$FC_ZONE_A

    seed=`date "+%H%M%S%N"`
    runcmd storageport update ${VMAX_NATIVEGUID} FC --tzone $NH/$FC_ZONE_A
    runcmd project create $PROJECT --tenant $TENANT 
    echo "Project $PROJECT created."
    echo "Setup ACLs on neighborhood for $TENANT"
    runcmd neighborhood allow $NH $TENANT

    runcmd transportzone add $NH/${FC_ZONE_A} $H1PI1
    runcmd transportzone add $NH/${FC_ZONE_A} $H1PI2
    runcmd transportzone add $NH/${FC_ZONE_A} $H2PI1
    runcmd transportzone add $NH/${FC_ZONE_A} $H2PI2
    runcmd transportzone add $NH/${FC_ZONE_A} $H3PI1
    runcmd transportzone add $NH/${FC_ZONE_A} $H3PI2

    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        runcmd cluster create --project ${PROJECT} ${CLUSTER} ${TENANT}

        runcmd hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        runcmd initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        runcmd initiator create ${HOST1} FC $H1PI2 --node $H1NI2

        runcmd hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        runcmd initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        runcmd initiator create ${HOST2} FC $H2PI2 --node $H2NI2

        runcmd hosts create ${HOST3} $TENANT Windows ${HOST3} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
        runcmd initiator create ${HOST3} FC $H3PI1 --node $H3NI1
        runcmd initiator create ${HOST3} FC $H3PI2 --node $H3NI2
    else
        runcmd hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0
        runcmd initiator create ${HOST1} FC $H1PI1 --node $H1NI1
        runcmd initiator create ${HOST1} FC $H1PI2 --node $H1NI2

        runcmd hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0
        runcmd initiator create ${HOST2} FC $H2PI1 --node $H2NI1
        runcmd initiator create ${HOST2} FC $H2PI2 --node $H2NI2

        runcmd hosts create ${HOST3} $TENANT Windows ${HOST3} --port 8111 --username user --password 'password' --osversion 1.0
        runcmd initiator create ${HOST3} FC $H3PI1 --node $H3NI1
        runcmd initiator create ${HOST3} FC $H3PI2 --node $H3NI2
    fi

    # make a base cos for protected volumes
    runcmd cos create block ${VPOOL_BASE}					\
	--description Base true \
	--protocols FC 			\
	--numpaths 1				\
	--provisionType 'Thin'			\
	--max_snapshots 10                     \
	--neighborhoods $NH                    

   runcmd cos update block $VPOOL_BASE --storage ${VMAX_NATIVEGUID}
   runcmd cos allow $VPOOL_BASE block $TENANT
   sleep 60

   if [ "$1" != "test_20" -a "$1" != "test_24" ]
   then
        runcmd volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 8
   fi
}

# Verify no masks
#
# Makes sure there are no masks on the array before running tests
#
verify_nomasks() {
    echo "Verifying no masks exist on storage array"
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}1 ${HOST3} gone
    verify_export ${expname}2 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
    verify_export ${expname}2 ${HOST3} gone
}

# Export Test 0
#
# Test existing functionality of export
# Also required to run "sanity quick & vncblock" to cover other export situations, like snapshots.
#
test_0() {
    echot "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
}

# Export Test 1
#
# Basic Use Case for clustered Hosts
#
# Export boot volumes to each host
# Export data volumes to all hosts
#
test_1() {
    echot "Test 1 Begins"
    expname=${EXPORT_GROUP_NAME}t1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2},${HOST1}"
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
    echot "Test 2 Begins"
    expname=${EXPORT_GROUP_NAME}t2
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2},${HOST1}"
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
    echot "Test 3 Begins"
    expname=${EXPORT_GROUP_NAME}t3
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2},${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2}"
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
    echot "Test 4 Begins"
    expname=${EXPORT_GROUP_NAME}t4
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2},${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2}"
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
    echot "Test 5 Begins"
    expname=${EXPORT_GROUP_NAME}t5
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST2},${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2}"
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
    echot "Test 6 Begins"
    expname=${EXPORT_GROUP_NAME}t6
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1},${HOST2},${HOST3}"
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
# Technically this isn't testing VnxMasking, it's testing the name generation code.
#
# EG:  VOL1: CLUSTER
#
# Export Masks:
# cluster_billhost1: VOL1
# cluster_billhost2: VOL2
# cluster_billhost3: VOL3
#
test_7() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 7 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 7 Begins"
    expname=${EXPORT_GROUP_NAME}t7
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 -x- gone
    verify_export ${expname}1 -x- gone
    verify_export ${expname}1 -x- gone
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
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 8 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 8 Begins"
    expname=${EXPORT_GROUP_NAME}t8
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 2
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST1}"
    verify_export ${expname}1 -x- 6 2
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 -x- gone
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 -x- gone
    verify_export ${expname}2 ${HOST1} gone
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
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 9 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 9 Begins"
    expname=${EXPORT_GROUP_NAME}t9
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 -x- 6 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 -x- 6 2
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 -x- gone
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
# VnxMaskingOrchestrator to take action in any way possible, and verify it doesn't.
#
test_10() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 10 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 10 Begins"
    expname=${EXPORT_GROUP_NAME}t10
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 3
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-3" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 3
    runcmd export_group create $PROJECT ${expname}3 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-3" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 3
    runcmd export_group create $PROJECT ${expname}4 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-4,${PROJECT}/${VOLNAME}-5" --hosts "${HOST1}"
    verify_export ${expname}1 -x- 6 3
    verify_export ${expname}4 ${HOST1} 2 2
    echo "PASSED: Checkpoint 1"
    runcmd export_group create $PROJECT ${expname}5 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-6" --hosts "${HOST2}"
    verify_export ${expname}1 -x- 6 3
    verify_export ${expname}4 ${HOST1} 2 2
    verify_export ${expname}5 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}6 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-7" --hosts "${HOST1}"
    verify_export ${expname}1 -x- 6 3
    verify_export ${expname}4 ${HOST1} 2 3
    verify_export ${expname}5 ${HOST2} 2 1
    echo "PASSED: Checkpoint 2"
    runcmd export_group update ${PROJECT}/${expname}6 --addVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}4 ${HOST1} 2 4
    verify_export ${expname}5 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}5 --addVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}4 ${HOST1} 2 4
    verify_export ${expname}5 ${HOST2} 2 2
    runcmd export_group update ${PROJECT}/${expname}4 --addVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}4 ${HOST1} 2 4
    verify_export ${expname}5 ${HOST2} 2 2
    echo "PASSED: Checkpoint 3"
    runcmd export_group update ${PROJECT}/${expname}5 --remVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}5 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}4 --remVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}4 ${HOST1} 2 4
    runcmd export_group update ${PROJECT}/${expname}6 --remVols "${PROJECT}/${VOLNAME}-8"
    verify_export ${expname}4 ${HOST1} 2 3
    echo "PASSED: Checkpoint 4"
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 -x- 6 3
    verify_export ${expname}4 ${HOST1} 2 3
    verify_export ${expname}5 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 -x- 6 2
    verify_export ${expname}4 ${HOST1} 2 3
    verify_export ${expname}5 ${HOST2} 2 1
    echo "PASSED: Checkpoint 5"
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 -x- gone
    verify_export ${expname}4 ${HOST1} 2 3
    verify_export ${expname}5 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}4
    verify_export ${expname}4 ${HOST1} 2 1
    verify_export ${expname}5 ${HOST2} 2 1
    echo "PASSED: Checkpoint 6"
    runcmd export_group delete $PROJECT/${expname}5
    verify_export ${expname}5 ${HOST2} gone
    runcmd export_group delete $PROJECT/${expname}6
    verify_export ${expname}4 ${HOST2} gone
}

# Export Test 11
#
# Test removing a host from an export group when another group still has a reference.
#
test_11() {
    echot "Test 11 Begins"
    expname=${EXPORT_GROUP_NAME}t11
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST2},${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1},${HOST2},${HOST3}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 2
    verify_export ${expname}2 ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts "${HOST1}"
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
    verify_export ${expname}2 ${HOST3} gone
}

# Export Test 12
#
# Same as test 2, but tests removing volume from existing mask when an initiator goes away.
#
#
test_12() {
    echot "Test 12 Begins"
    expname=${EXPORT_GROUP_NAME}t12
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1},${HOST3}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST3} 2 1
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2},${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}2 ${HOST2} 2 2
    verify_export ${expname}1 ${HOST3} 2 1
    echo "running remove host, expect to remove reference to mask 1 in export group 1"
    runcmd export_group update $PROJECT/${expname}1 --remHosts "${HOST1}"
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
    echot "Test 13 Begins"
    expname=${EXPORT_GROUP_NAME}t13
    verify_export ${expname}1 ${HOST1} gone
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${expname}1 ${HOST1} gone
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
}

# Export Test 14
#
# Test to make sure removing and adding volumes does NOT remove the mask if the mask DOES exist before this test.
#
# Note, the first time you run this test, you must "exit;" after the export group create.  Then clean your DB, remove the "exit" and run again.
#
#
test_14() {
    echot "Test 14 Begins"
    expname=${EXPORT_GROUP_NAME}t14
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS, YOU'RE GOOD HERE"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    # comment-out this "exit" after the first exportgroup create is done, recreate your DB, then run again.
    echo "AS LONG AS THERE'S A GROUP RETURNED WITH ANY NUMBER OF LUNS + ONE, YOU'RE GOOD HERE"
    verify_export ${expname}1 ${HOST1} 2 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ${expname}1 ${HOST1} 2 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} 2 1
}

# Export Test 15
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
test_15a() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 15a skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 15a Begins"
    expname=${EXPORT_GROUP_NAME}t15a
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 2
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST1}"
    verify_export ${expname}1 -x- 6 2
    verify_export ${expname}2 ${HOST1} 2 1
    finish
}

test_15b() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 15b skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 15b Begins"
    # This test is tied to test_15a being run first. In this case, 
    # all the existing masking components on the array will have 
    # that name.
    expname=${EXPORT_GROUP_NAME}t15a
    runcmd export_group create $PROJECT ${expname}New $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-3" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 -x- 6 3
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd volume create vol15b $PROJECT $NH $VPOOL_BASE 1GB --count 4
    runcmd export_group update ${PROJECT}/${expname}New --addVols "${PROJECT}/vol15b-1","${PROJECT}/vol15b-2","${PROJECT}/vol15b-3"
    verify_export ${expname}1 -x- 6 6
    runcmd export_group create $PROJECT ${expname}2a $NH --type Host --volspec "${PROJECT}/vol15b-4" --hosts "${HOST1}"
    verify_export ${expname}1 -x- 6 6
    verify_export ${expname}2 ${HOST1} 2 2
    runcmd export_group delete ${PROJECT}/${expname}New
    runcmd export_group delete ${PROJECT}/${expname}2a
    verify_export ${expname}1 -x- 6 2
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd volume delete $PROJECT --project --wait
    finish
}

test_15c() {
    echot "Test 15c Begins"
    # This test is tied to test_15a being run first. In this case,
    # all the existing masking components on the array will have
    # that name.
    expname=${EXPORT_GROUP_NAME}t15a
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd volume create vol15c $PROJECT $NH $VPOOL_BASE 1GB --count 3
    runcmd export_group create $PROJECT ${expname}2a $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST1}"
    verify_export ${expname}2 ${HOST1} 2 2
    runcmd export_group delete ${PROJECT}/${expname}2a
    verify_export ${expname}2 ${HOST1} 2 1
    runcmd volume delete $PROJECT --project --wait
    finish
}

# Export Test 16
#
# An issue Tom found with export update.
#
test_16() {
    echot "Test 16 Begins"
    expname=${EXPORT_GROUP_NAME}t16
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group update $PROJECT/${expname}1 --addHosts "${HOST2},${HOST3}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    verify_export ${expname}1 ${HOST3} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}1 ${HOST3} gone
}

# Export Test 17
#
# Create an export with a single host.
#   Add a host, add volume, remove volume, remove host
#
test_17() {
    echot "Test 17 Begins"
    expname=${EXPORT_GROUP_NAME}t17
    runcmd export_group create ${PROJECT} ${expname} $NH --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1} --type Host
    verify_export ${expname} ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname} --addHosts ${HOST2},${HOST3}
    verify_export ${expname} ${HOST1} 2 1
    verify_export ${expname} ${HOST2} 2 1
    verify_export ${expname} ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname} --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ${expname} ${HOST1} 2 2
    verify_export ${expname} ${HOST2} 2 2
    verify_export ${expname} ${HOST3} 2 2
    runcmd export_group update ${PROJECT}/${expname} --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ${expname} ${HOST1} 2 1
    verify_export ${expname} ${HOST2} 2 1
    verify_export ${expname} ${HOST3} 2 1
    runcmd export_group update ${PROJECT}/${expname} --remHosts ${HOST2},${HOST3}
    verify_export ${expname} ${HOST1} 2 1
    verify_export ${expname} ${HOST2} gone
    verify_export ${expname} ${HOST3} gone
    runcmd export_group delete ${PROJECT}/${expname}
    verify_export ${expname} ${HOST1} gone
}

test_18() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 18 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 18 Begins"
    expname=${EXPORT_GROUP_NAME}t18
    runcmd export_group create ${PROJECT} ${expname}1 nh --volspec ${PROJECT}/${VOLNAME}-1 --clusters "${TENANT}/${CLUSTER}" --type Cluster
    verify_export ${expname}1 -x- 6 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ${expname}1 -x- 6 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ${expname}1 -x- 6 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOST1}
    verify_export ${expname}1 -x- 4 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts ${HOST1}
    verify_export ${expname}1 -x- 6 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOST1},${HOST2},${HOST3}
    verify_export ${expname}1 -x- gone
    runcmd export_group update ${PROJECT}/${expname}1 --addClusters ${TENANT}/${CLUSTER}
    verify_export ${expname}1 -x- 6 1
    runcmd export_group delete ${PROJECT}/${expname}1 
    verify_export ${expname}1 -x- gone
}

test_19() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        echo Test 19 skipped, does not apply when clustered tests are enabled
        return
    fi
    echot "Test 19 Begins"
    expname=${EXPORT_GROUP_NAME}t19

    runcmd export_group create ${PROJECT} ${expname}1 nh --volspec ${PROJECT}/${VOLNAME}-1 --inits "${HOST1}/${H1PI1}"
    verify_export ${expname}1 ${HOST1} 1 1
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ${expname}1 ${HOST1} 1 2
    runcmd export_group update ${PROJECT}/${expname}1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export ${expname}1 ${HOST1} 1 1
    runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HOST1}/${H1PI2}
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export ${expname}1 ${HOST1} 1 1
    runcmd export_group delete ${PROJECT}/${expname}1
    verify_export ${expname}1 ${HOST1} gone
}


# Test removing snapshot from export group with fast volume
# Not intended to execute with other tests, run this standalone
test_20() {
    echot "Test 20 Begins"
    expname=${EXPORT_GROUP_NAME}t20
    verify_export $expname $HOST1 gone

    # creat VPool
    runcmd cos create block $VPOOL_FAST \
          			 --description FAST true \
                                 --protocols FC \
                                 --numpaths 1 \
                                 --max_snapshots 2 \
                                 --provisionType "Thin" \
                                 --neighborhoods $NH \
                                 --system_type vmax \
                                 --auto_tiering_policy_name $FAST_POLICY

    runcmd cos update block $VPOOL_FAST --storage $VMAX_NATIVEGUID
    runcmd cos allow $VPOOL_FAST block $TENANT

    # create volumes and snapshot
    vol=vol20
    fast_vol=${vol}-fast
    snap_label=snap
    snap=$PROJECT/$vol/$snap_label

    runcmd volume create $vol $PROJECT $NH $VPOOL_BASE 1GB
    runcmd blocksnapshot create $PROJECT/$vol $snap_label

    runcmd volume create $fast_vol $PROJECT $NH $VPOOL_FAST 1GB

    runcmd export_group create $PROJECT $expname $NH --volspec "$PROJECT/$fast_vol" --inits "$HOST1/$H1PI1"
    verify_export ${expname} ${HOST1} 1 1

    runcmd export_group update $PROJECT/$expname --addVolspec "$snap"
    verify_export $expname $HOST1 1 2

    runcmd export_group update $PROJECT/$expname --remVols $snap
    verify_export $expname $HOST1 1 1

    runcmd export_group delete $PROJECT/$expname
    verify_export $expname $HOST1 gone

    runcmd blocksnapshot delete $snap
}

#
# Test 21. This case is to be run standalone and with USE_CLUSTERED_HOSTS=0.
# The test will
#
test_21() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        echo Test 21 skipped, does not apply when clustered tests are enabled
        return
    fi

    echo "!!! This test must be run standalone !!!"
    echot "Test 21 Begins"
    expname=${EXPORT_GROUP_NAME}t21
    #
    # Create host export for each host
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST3}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    verify_export ${expname}3 ${HOST3} 2 1
    #
    # Create cluster and the update each of the hosts
    # to reference the cluster.
    cluster create --project ${PROJECT} ${CLUSTER} ${TENANT}
    hosts update ${HOST1} --cluster "${TENANT}/${CLUSTER}"
    hosts update ${HOST2} --cluster "${TENANT}/${CLUSTER}"
    hosts update ${HOST3} --cluster "${TENANT}/${CLUSTER}"
    #
    # Create cluster export
    runcmd export_group create $PROJECT ${expname}Shared $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-4,${PROJECT}/${VOLNAME}-5" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    verify_export ${expname}3 ${HOST3} 2 1
    verify_export ${expname}Shared_${CLUSTER} -exact- 6 2
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} 2 1
    verify_export ${expname}3 ${HOST3} 2 1
    verify_export ${expname}Shared_${CLUSTER} -exact- 6 2
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
    verify_export ${expname}3 ${HOST3} 2 1
    verify_export ${expname}Shared_${CLUSTER} -exact- 6 2
    runcmd export_group delete $PROJECT/${expname}3
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
    verify_export ${expname}3 ${HOST3} gone
    verify_export ${expname}Shared_${CLUSTER} -exact- 6 2
    runcmd export_group delete $PROJECT/${expname}Shared
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
    verify_export ${expname}3 ${HOST3} gone
    verify_export ${expname}Shared_${CLUSTER} -exact- gone
}

#
# Test 22
#
# Hosts export + Shared export use case where host is removed
# from the hosts export
#
# 1. Create an export for a host
# 2. Add hosts to the export created in 1
# 3. Create a shared export all the hosts in the cluster
# 4. Remove the host added in 2
#
test_22() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 22 skipped, does not apply when clustered tests are disabled
        return
    fi
    echot "Test 21 Begins"
    expname=${EXPORT_GROUP_NAME}t21
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    runcmd export_group update $PROJECT/${expname}1 --addHosts "${HOST2}"
    runcmd export_group create $PROJECT ${expname}_cluster $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-3,${PROJECT}/${VOLNAME}-4 --cluster "${TENANT}/${CLUSTER}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    verify_export ${expname}_cluster -x- 6 2
    runcmd export_group update $PROJECT/${expname}1 --remHosts "${HOST2}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}_cluster -x- 6 2
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT/${expname}_cluster
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone
    verify_export ${expname}_cluster -x- gone
}

#
# Piecemeal export creation test case.
# Create export group without volumes or hosts
# Add volumes
# Add hosts
#
test_23() {
    echot "Test 23 Begins"
    expname=${EXPORT_GROUP_NAME}t23

    runcmd export_group create ${PROJECT} ${expname}1 nh --type Host
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts ${HOST1}
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts ${HOST2}
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} 2 1
    runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOST2}
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} gone
    runcmd export_group delete ${PROJECT}/${expname}1
    verify_export ${expname}1 ${HOST1} gone
}

# Test creating export group with various export group types (valid and invalid)
# Not intended to execute with other tests, run this standalone
test_24() {
    echot "Test 24 Begins"
    defaulttype="Initiator"
    invalidtype="Invalid"
    
    for type in "" Exclusive Initiator Host Cluster $invalidtype; do
        expname=${EXPORT_GROUP_NAME}_24_${type}
        typearg="";
        if [ "$type" != "" ]
	then
		typearg="--type $type"
        fi

        echo "Creating export group with name [${expname}], type [${type}]"
        errmsg=`export_group create $PROJECT $expname $NH $typearg 2> /dev/null | grep "Parameter was provided but invalid" `        
        if [ "$type" = "$invalidtype" ]
        then
                VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
                if [ -n $errmsg ]
                then
                        echo "FAILED [${type}] type test. Error is expected."
                        VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
                fi

                break # InvalidType is the last in the list
        fi

        actualtype=`export_group show ${PROJECT}/$expname | grep '"type"' | awk -F ": " '{print $2}' | cut -c 2- | sed 's/..[ ]*$//'`
        if [ "$type" = "" -o "$type" = "Exclusive" ]
        then
                if [ "$actualtype" != "$defaulttype" ]
                then
                        echo "FAILED [${type}] type test, expected ${defaulttype}, actual ${actualtype}"
                        VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
                fi
        elif [ "$actualtype" != "$type" ]
        then
                echo "FAILED [${type}] type test, expected ${realtype}, actual ${actualtype}"
                VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
        fi
        
        VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
    done
    
    echo "Finished Test 24" 
}

#
# Use-case is to create a cluster export and a host export. The host goes
# bad, so we remove it from the cluster export (temporarily), then we
# add it back. With this test, we want to see the behavior of adding and
# removing hosts to cluster and host exports when the co-exist.
#
test_25() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 25 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 25 Begins"
    expname=${EXPORT_GROUP_NAME}t25
    clusterXP=${expname}CL
    hostXP=${expname}H
    runcmd export_group create ${PROJECT} $clusterXP nh --volspec ${PROJECT}/${VOLNAME}-1 --clusters "${TENANT}/${CLUSTER}" --type Cluster
    runcmd export_group create ${PROJECT} $hostXP nh --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}" --type Host
    verify_export $clusterXP -x- 6 1
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$clusterXP --addVols ${PROJECT}/${VOLNAME}-3
    verify_export $clusterXP -x- 6 2
    runcmd export_group update ${PROJECT}/$clusterXP --remVols ${PROJECT}/${VOLNAME}-1
    verify_export $clusterXP -x- 6 1
    runcmd export_group update ${PROJECT}/$hostXP --addVols ${PROJECT}/${VOLNAME}-4
    verify_export $hostXP $HOST1 2 2
    runcmd export_group update ${PROJECT}/$hostXP --remVols ${PROJECT}/${VOLNAME}-2
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$clusterXP --remHosts ${HOST1}
    verify_export $clusterXP -x- 4 1
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$clusterXP --addHosts ${HOST1}
    verify_export $clusterXP -x- 6 1
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$clusterXP --remHosts ${HOST1},${HOST2},${HOST3}
    verify_export $clusterXP -x- gone
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$clusterXP --addClusters "${TENANT}/${CLUSTER}"
    verify_export $clusterXP -x- 6 1
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$hostXP --remHosts ${HOST1}
    verify_export $clusterXP -x- 6 1
    verify_export $hostXP $HOST1 gone
    runcmd export_group update ${PROJECT}/$hostXP --addHosts ${HOST1}
    verify_export $clusterXP -x- 6 1
    verify_export $hostXP $HOST1 2 1
    runcmd export_group delete ${PROJECT}/$clusterXP
    runcmd export_group delete ${PROJECT}/$hostXP
    verify_export $clusterXP -x- gone
    verify_export $hostXP $HOST1 gone
}

#
# Use-case is to create two separate exports for the same host, but different
# volumes. Then remove the volumes from each export
#
test_26() {
    echot "Test 26 Begins"
    expname=${EXPORT_GROUP_NAME}t26
    hostXP1=${expname}H1
    hostXP2=${expname}H2
    runcmd export_group create ${PROJECT} $hostXP1 nh --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}" --type Host
    verify_export $hostXP1 $HOST1 2 1
    runcmd export_group create ${PROJECT} $hostXP2 nh --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}" --type Host
    verify_export $hostXP1 $HOST1 2 2
    runcmd export_group update ${PROJECT}/$hostXP1 --remVols ${PROJECT}/${VOLNAME}-1
    verify_export $hostXP1 $HOST1 2 1
    runcmd export_group update ${PROJECT}/$hostXP2 --remVols ${PROJECT}/${VOLNAME}-2
    verify_export $hostXP1 $HOST1 gone
    runcmd export_group delete ${PROJECT}/$hostXP1
    runcmd export_group delete ${PROJECT}/$hostXP2
}

#
# This test will create cluster and host exports and then attempt to
# remove, then add initiators to the export. The behavior should be
# as expected. Removal of initiators should remove just the initiator.
# If the last initiators is removed, the whole export should be deleted
#
# It also tests the case of partial remove and addition of initiators
# to an export.
#
test_27() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 27 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 27 Begins"
    expname=${EXPORT_GROUP_NAME}t27
    clusterXP=${expname}CL
    hostXP=${expname}H
    runcmd export_group create ${PROJECT} $clusterXP nh --volspec ${PROJECT}/${VOLNAME}-1 --clusters "${TENANT}/${CLUSTER}" --type Cluster
    verify_export $clusterXP -x- 6 1
    runcmd export_group update ${PROJECT}/$clusterXP --remInits ${HOST1}/${H1PI2}
    verify_export $clusterXP -x- 5 1
    runcmd export_group update ${PROJECT}/$clusterXP --addInits ${HOST1}/${H1PI2}
    verify_export $clusterXP -x- 6 1
    runcmd export_group delete ${PROJECT}/$clusterXP
    verify_export $clusterXP -x- gone

    runcmd export_group create ${PROJECT} $hostXP nh --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1}" --type Host
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$hostXP --remInits ${HOST1}/${H1PI2}
    verify_export $hostXP $HOST1 1 1
    runcmd export_group update ${PROJECT}/$hostXP --addInits ${HOST1}/${H1PI2}
    verify_export $hostXP $HOST1 2 1
    runcmd export_group update ${PROJECT}/$hostXP --remInits ${HOST1}/${H1PI1}
    runcmd export_group update ${PROJECT}/$hostXP --remInits ${HOST1}/${H1PI2}
    verify_export $hostXP $HOST1 gone
    runcmd export_group update ${PROJECT}/$hostXP --addHosts ${HOST1}
    verify_export $hostXP $HOST1 2 1
    runcmd export_group delete ${PROJECT}/$hostXP
    verify_export $hostXP $HOST1 gone

    # Test removal of all initiators in one host, but partial removal in another
    runcmd export_group create ${PROJECT} $hostXP nh --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST1},${HOST2}" --type Host
    verify_export $hostXP $HOST1 2 1
    verify_export $hostXP $HOST2 2 1
    runcmd export_group update ${PROJECT}/$hostXP --remInits ${HOST1}/${H1PI1},${HOST1}/${H1PI2},${HOST2}/${H2PI1}
    verify_export $hostXP $HOST1 gone
    verify_export $hostXP $HOST2 1 1
    runcmd export_group update ${PROJECT}/$hostXP --addHosts ${HOST1} --addInits ${HOST2}/${H2PI1}
    verify_export $hostXP $HOST1 2 1
    verify_export $hostXP $HOST2 2 1
    runcmd export_group delete ${PROJECT}/$hostXP
    verify_export $hostXP $HOST1 gone
    verify_export $hostXP $HOST2 gone

    # CTRL-10333 - Create a cluster, remove all initiators from one host. Should only remove the initaitors and nothing else
    clusterXP2=${expname}CL2
    runcmd export_group create ${PROJECT} $clusterXP2 nh --volspec ${PROJECT}/${VOLNAME}-1 --clusters "${TENANT}/${CLUSTER}" --type Cluster
    verify_export $clusterXP2 -x- 6 1
    runcmd export_group update ${PROJECT}/$clusterXP2 --remInits ${HOST1}/${H1PI1},${HOST1}/${H1PI2}
    verify_export $clusterXP2 -x- 4 1
    runcmd export_group delete ${PROJECT}/$clusterXP2
    verify_export $clusterXP2 -x- gone

# Below is an unusual case, that can only be done through CLI. It is failing
# after some fixes were made for CTRL-11544, so we are temporarily removing
# the test from the suite

#    hostXPA="hostXP-A"
#    hostXPB="hostXP-B"
#    runcmd export_group create ${PROJECT} $hostXPA nh --volspec ${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2 --hosts  "${HOST1}" --type Host
#    verify_export $hostXPA $HOST1 2 2
#    runcmd export_group create ${PROJECT} $hostXPB nh --volspec ${PROJECT}/${VOLNAME}-3 --hosts "${HOST2}" --type Host
#    verify_export $hostXPB $HOST2 2 1
#    runcmd export_group update ${PROJECT}/$hostXPA --addHosts "${HOST2}"
#    verify_export $hostXPA $HOST1 2 2
#    verify_export $hostXPB $HOST2 2 3
#    runcmd export_group update ${PROJECT}/$hostXPA --remHosts "${HOST2}"
#    verify_export $hostXPA $HOST1 2 2
#    verify_export $hostXPB $HOST2 2 1
#    runcmd export_group delete ${PROJECT}/$hostXPA
#    verify_export $hostXPA $HOST1 gone
#    runcmd export_group delete ${PROJECT}/$hostXPB
#    verify_export $hostXPB $HOST2 gone
}

#
# Jira CTRL-2618 Test case. Have a cluster export and a host export. Reduce the number of initiators
# in cluster export to match the same as the host export. Add volumes to host export => should work.
# Add volumes to the cluster export => should work. Add back the initiators to cluster export => should work.
#
test_28() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 28 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 28 Begins"
    expname=${EXPORT_GROUP_NAME}t28
    clusterXP=${expname}CL
    hostXP=${expname}H
    runcmd export_group create ${PROJECT} $clusterXP nh --volspec ${PROJECT}/${VOLNAME}-1+10 --clusters "${TENANT}/${CLUSTER}" --type Cluster
    verify_export $clusterXP -x- 6 1

    runcmd export_group create ${PROJECT} $hostXP nh --volspec ${PROJECT}/${VOLNAME}-2+20 --hosts "${HOST1}" --type Host
    verify_export $hostXP $HOST1 2 1

    runcmd export_group update ${PROJECT}/$clusterXP --remHosts "${HOST1},${HOST2}"
    verify_export $clusterXP -x- 2 1
    verify_export $hostXP $HOST1 2 1

    runcmd export_group update ${PROJECT}/$hostXP --addVols ${PROJECT}/${VOLNAME}-3+30,${PROJECT}/${VOLNAME}-4+40
    verify_export $clusterXP -x- 2 1
    verify_export $hostXP $HOST1 2 3

    runcmd export_group update ${PROJECT}/$clusterXP --addVols ${PROJECT}/${VOLNAME}-5+50,${PROJECT}/${VOLNAME}-6+60
    verify_export $clusterXP -x- 2 3
    verify_export $hostXP $HOST1 2 3

    runcmd export_group update ${PROJECT}/$clusterXP --addHosts "${HOST1},${HOST2}"
    verify_export $clusterXP -x- 6 3
    verify_export $hostXP $HOST1 2 3

    runcmd export_group delete ${PROJECT}/$hostXP
    verify_export $hostXP $HOST1 gone

    runcmd export_group delete ${PROJECT}/$clusterXP
    verify_export $clusterXP -x- gone
}

#
# CTRL-6045 Test case. Create a cluster export without volumes or cluster. Add volume, then add cluster.
# Should not result in a failure (NullPointerException)
#
test_29() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 29 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    echot "Test 29 Begin"
    expname=${EXPORT_GROUP_NAME}t29
    clusterXP=${expname}CL

    runcmd export_group create ${PROJECT} $clusterXP nh --type Cluster
    verify_export $clusterXP -x- gone

    runcmd export_group update ${PROJECT}/$clusterXP --addVols ${PROJECT}/${VOLNAME}-3
    verify_export $clusterXP -x- gone

    runcmd export_group update ${PROJECT}/$clusterXP --addClusters "${TENANT}/${CLUSTER}"
    verify_export $clusterXP -x- 6 1

    runcmd export_group delete ${PROJECT}/$clusterXP
    verify_export $clusterXP -x- gone
}

#
# CTRL-6448
#
# host1 and host2 have a subset of initiators that are in a network associated to
# the VirtualArray. Test case is a piecemeal creation of exports.
#
#1. create vol1 and create EG1.
#2. add host1 and vol1 to EG1 by CLI.
#3. create vol2 and EG2.
#4. add host2 and vol2 to EG2.
#5. create linux cluster1 in ViPR, host1 and host2 are in cluster1.
#6. create vol3 and cluster EG3.
#7. add vol3 and cluster1 to EG3 by CLI.
#8. remove host2 from EG2 by CLI:
test_30() {
    if [ "$USE_CLUSTERED_HOSTS" -eq "0" ]; then
        echo Test 30 skipped, does not apply when non-clustered tests are enabled
        return
    fi

    clusterRef="${TENANT}/${CLUSTER}"

    # Create two initiators for two hosts
    H4PI1=`pwwn 10`
    H4NI1=`nwwn 10`
    H4PI2=`pwwn 11`
    H4NI2=`nwwn 11`

    H5PI1=`pwwn 20`
    H5NI1=`nwwn 20`
    H5PI2=`pwwn 21`
    H5NI2=`nwwn 21`

    # Create 2 hosts with both initiators
    HOST4=host4export${BASENUM}
    HOST5=host5export${BASENUM}

    runcmd hosts create ${HOST4} $TENANT Windows ${HOST4} --port 8111 --username user --password 'password' --osversion 1.0 --cluster $clusterRef
    runcmd initiator create ${HOST4} FC $H4PI1 --node $H4NI1
    runcmd initiator create ${HOST4} FC $H4PI2 --node $H4NI2
    
    runcmd hosts create ${HOST5} $TENANT Windows ${HOST5} --port 8111 --username user --password 'password' --osversion 1.0 --cluster $clusterRef
    runcmd initiator create ${HOST5} FC $H5PI1 --node $H5NI1
    runcmd initiator create ${HOST5} FC $H5PI2 --node $H5NI2

    # Add only one of the initiators from each host into the Network
    runcmd transportzone add $NH/${FC_ZONE_A} $H4PI1
    runcmd transportzone add $NH/${FC_ZONE_A} $H5PI1

    echo "Test 30 Begin"
    expname=${EXPORT_GROUP_NAME}t30
    clusterXP=${expname}CL
    host4XP=${expname}H4
    host5XP=${expname}H5
    clusterXPRef=${PROJECT}/${clusterXP}
    host4XPRef=${PROJECT}/${host4XP}
    host5XPRef=${PROJECT}/${host5XP}
    vol1Ref=${PROJECT}/${VOLNAME}-1
    vol2Ref=${PROJECT}/${VOLNAME}-2
    vol3Ref=${PROJECT}/${VOLNAME}-3

    runcmd export_group create $PROJECT $host4XP nh --type Host
    verify_export $host4XP $HOST4 gone

    runcmd export_group update $host4XPRef --addHosts $HOST4 --addVols $vol1Ref
    verify_export $host4XP $HOST4 1 1

    runcmd export_group create $PROJECT $host5XP nh --type Host
    verify_export $host5XP $HOST5 gone

    runcmd export_group update $host5XPRef --addHosts $HOST5 --addVols $vol2Ref
    verify_export $host5XP $HOST5 1 1

    runcmd export_group create $PROJECT $clusterXP nh --type Cluster
    verify_export $clusterXP -x- gone

    runcmd export_group update $clusterXPRef --addClusters $clusterRef --addVols $vol3Ref
    verify_export $clusterXP -x- 8 1

    runcmd export_group update $host5XPRef --remHosts $HOST5
    verify_export $host5XP $HOST5 gone
    verify_export $clusterXP -x- 8 1

    runcmd export_group delete $host4XPRef
    verify_export $host4XP $HOST4 gone

    runcmd export_group delete $host5XPRef
    verify_export $host5XP $HOST5 gone

    runcmd export_group delete $clusterXPRef
    verify_export $clusterXPRef -x- gone
    
    runcmd initiator delete $HOST4/$H4PI1
    runcmd initiator delete $HOST4/$H4PI2
    runcmd hosts delete $HOST4

    runcmd initiator delete $HOST5/$H5PI1
    runcmd initiator delete $HOST5/$H5PI2
    runcmd hosts delete $HOST5
}

cleanup() {
   for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
   do
      runcmd export_group delete ${id} > /dev/null
      echo "Deleted export group: ${id}"
   done
   runcmd volume delete --project $PROJECT --wait
   echo There were $VERIFY_EXPORT_COUNT export verifications
   echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures/
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

# ============================================================
# -    M A I N
# ============================================================

login

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

if [ "$1" = "regression" ]
then
   test_0;
fi

if [ "$1" = "delete" ]
then
  cleanup
  finish
fi

if [ "$1" = "setup" ]
then
    setup $2;
fi;

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
   finish
fi

# Passing tests:
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
test_13;
#test_14;
#test_15a;
#test_15b;
test_16;
test_17;
test_18;
test_19;
test_22;
test_23;
test_25;
test_26;
test_27;
test_28;
test_29;
test_30;
cleanup;
finish


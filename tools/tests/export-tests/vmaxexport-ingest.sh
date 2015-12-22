SN#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
#
# Export Test for Ingest and coexistence
# ======================================
#
# This script will test various scenarios where creating an export group will have an impact on an existing
# VMAX masking layout that was there before ViPR came along.
#
# Requirements:
# -------------
# - SYMAPI should be installed, which is included in the SMI-S install. Install tars can be found on
#   Download the tar file for Linux, untar, run seinstall -install
# - The provider host should allow for NOSECURE SYMAPI REMOTE access. See https://asdwiki.isus.emc.com:8443/pages/viewpage.action?  pageId=28778911 for more information.
# 
# How to read this script:
# ------------------------
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
    echo 'Usage: vmaxexport-ingest.sh <sanity conf file path> [setup]'
    echo ' [setup]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    exit 2
}

SANITY_CONFIG_FILE=""
: ${USE_CLUSTERED_HOSTS=0}

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

    cluster_name_if_any=""
    if [ "$USE_CLUSTERED_HOSTS" -eq "1" ]; then
        cluster_name_if_any="${CLUSTER}_"
        if [ "$no_host_name" -eq 1 ]; then
            # No hostname is applicable, so this means that the cluster name is the
            # last part of the MaskingView name. So, it doesn't need to end with '_'
            cluster_name_if_any="${CLUSTER}"
        fi
    fi
    masking_view_name="${cluster_name_if_any}${host_name}${BF_VMAX_ID_3DIGITS}"
    if [ "$host_name" = "-exact-" ]; then
        masking_view_name=$export_name
    fi

    runcmd symhelper.sh $BF_VMAX_SN $masking_view_name $*
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

verify_sg() {
    no_host_name=0
    sg_name=$1
    policy_name=$2
    shift 2

    runcmd sghelper.sh $BF_VMAX_SN $sg_name $policy_name $*
    if [ $? -ne "0" ]; then
       echo There was a failure
       VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
       cleanup
       finish
    fi
    VERIFY_EXPORT_COUNT=`expr $VERIFY_EXPORT_COUNT + 1`
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
#FC_ZONE_A=fctz_a
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

BASENUM=${BASENUM:=$RANDOM}
VOLNAME=vmaxexp${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
CN=ingcl
HN=ingesthost

# Allow for a way to easily use different hardware
if [ -f "./myhardware.conf" ]; then
    echo Using ./myhardware.conf
    source ./myhardware.conf
fi

which symhelper.sh
if [ $? -ne 0 ]; then
    echo Could not find symhelper.sh path. Please add the directory where the script exists to the path
    locate symhelper.sh
    exit 1;
fi

if [ ! -f /usr/emc/API/symapi/config/netcnfg ]; then
    echo SYMAPI does not seem to be installed on the system. Please install before running test suite.
    exit 1;
fi

export SYMCLI_CONNECT=SYMAPI_SERVER
symapi_entry=`grep SYMAPI_SERVER /usr/emc/API/symapi/config/netcnfg | wc -l`
if [ $symapi_entry -ne 0 ]; then
    sed -e "/SYMAPI_SERVER/d" -i /usr/emc/API/symapi/config/netcnfg
fi
echo "SYMAPI_SERVER - TCPIP  $BF_VMAX_SMIS_IP - 2707 ANY" >> /usr/emc/API/symapi/config/netcnfg
echo "Added entry into /usr/emc/API/symapi/config/netcnfg"

echo "Verifying SYMAPI connection to $BF_VMAX_SMIS_IP ..."
symapi_verify="/opt/emc/SYMCLI/bin/symcfg list"
echo $symapi_verify
result=`$symapi_verify`
if [ $? -ne 0 ]; then
    echo "SYMAPI verification failed: $result"
    echo "Check the setup on $BF_VMAX_SMIS_IP. See if the SYAMPI service is running"
    exit 1;
fi
echo $result

runcmdnocleanup() {
    echo === $*
    $*
    if [ $? -ne 0 ]
    then
       echo "Command FAILED. Continuing."
    fi
}

runcmd() {
    echo === $*
    $*
    if [ $? -ne 0 ]
    then
       echo "Command FAILED. Cleaning up"
       cleanup;
       finish
    fi
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

    # Increase allocation percentage
    syssvc $SANITY_CONFIG_FILE localhost set_prop controller_max_thin_pool_subscription_percentage 600

    SMISPASS=0
    # do this only once
    echo "Setting up SMIS"
    smisprovider create VMAX-PROVIDER $BF_VMAX_SMIS_IP 5988 admin '#1Password' false
    storagedevice discover_all --ignore_error

    storagepool update $BF_VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    storagepool update $BF_VMAX_NATIVEGUID --type block --volume_type THICK_ONLY

    neighborhood create $NH --autoSanZoning false
    
    # transportzone create $FC_ZONE_A $NH --type FC
    networksystem create --smisip $BROCADE_IP --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisport 5988 $BROCADE_NETWORK brocade
    transportzone assign $FC_ZONE_A nh
    transportzone assign FABRIC_vplex154nbr2 nh

    storagepool update $BF_VMAX_NATIVEGUID --nhadd $NH --type block
    storageport update $BF_VMAX_NATIVEGUID FC --tzone $NH/$FC_ZONE_A

    storagepool update ${BF_VMAX_NATIVEGUID} --nhadd $NH --pool "$RP_VNXB_POOL" --type block --volume_type THIN_ONLY
    seed=`date "+%H%M%S%N"`
    storageport update ${BF_VMAX_NATIVEGUID} FC --tzone $NH/$FC_ZONE_A
    project create $PROJECT --tenant $TENANT 
    echo "Project $PROJECT created."
    echo "Setup ACLs on neighborhood for $TENANT"
    neighborhood allow $NH $TENANT

    # Clusters with 2 nodes
    for clusternum in 10 12 14 16 18 33 38 40
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

    # Clusters with 3 nodes
    for clusternum in 20 23 26 29 35
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

    # Regular hosts, not in a cluster
    for hostnum in 00 01 02 03 04 05 06 07 08 09 32 42
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

    cos create block ${VPOOL_BASE} \
	  --description 'Virtual Pool' true \
	  --protocols FC 			\
	  --numpaths 1				\
	  --provisionType 'Thin'			\
	  --max_snapshots 10                     \
	  --neighborhoods $NH            \
          --system_type vmax

    runcmd cos update block ${VPOOL_BASE} --storage ${BF_VMAX_NATIVEGUID}
    runcmd cos allow ${VPOOL_BASE} block $TENANT

    if [ "$1" != "test_20" -a "$1" != "test_24" ]
    then
       runcmd volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 2
    fi

    for policy in GOLD Blue Green
      do
      cos create block ${VPOOL_BASE}_${policy} \
	  --description 'Virtual Pool for Fast policy' true \
 	  --protocols FC 			\
	  --numpaths 1				\
	  --provisionType 'Thin'			\
	  --auto_tiering_policy_name ${policy} \
	  --max_snapshots 10                     \
	  --neighborhoods $NH            \
          --system_type vmax
      runcmd cos update block ${VPOOL_BASE}_${policy} --storage ${BF_VMAX_NATIVEGUID}
      runcmd cos allow ${VPOOL_BASE}_${policy} block $TENANT
      if [ "$1" != "test_20" -a "$1" != "test_24" ]
	  then
          runcmd volume create ${VOLNAME}_${policy} ${PROJECT} ${NH} ${VPOOL_BASE}_${policy} 1GB --count 2
      fi
    done 
}

setup_hosts_onvmax() {
    expname=${CN}
    for hostnum in 18
    do
       runcmd export_group create $PROJECT ${expname}-$hostnum $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-${hostnum}"
    done
    echo "Recommend you delete your vipr database"
    finish
}

#
# Verify the setup.  Look at each block to see how it's set up.  (or look at the VMAX itself)
#
verify_setup() {
    if [ "${NOVERIFY}" == "1" -a "$1" == "" ]
    then
	echot "No verification done as part of scattered test.  Assumed site verification occurred as part of launch"
	return
    fi

    if [ "$1" == "" ]
    then
        echot "Setup Verification Test: Verify Setup is in Proper Brownfield State"
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-00" ]
    then
        # ingesthost-00 has a Green, GOLD, and non-FAST masking views with one volume
	verify_export ingesthost-00_Green_MV1 -exact- 2 1
	verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
	verify_export ingesthost-00_MV1 -exact- 2 1
	verify_sg ingesthost-00_Green_SG Green 1 1
	verify_sg ingesthost-00_GOLD_SG GOLD 1 1
	verify_sg ingesthost-00_SG None 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-01" ]
    then
        # ingesthost-01 has a non-FAST, non-cascading-SG MV with a non-fast volume and a phantom SG volume
	verify_export ingesthost-01_MV1 -exact- 2 2
	verify_sg ingesthost-01_SG None 1 2
        # Phantom Green storage group with 1 volume
	verify_sg ingesthost-phantom_Green_SG Green 0 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-02" ]
    then
        # ingesthost-02 has two non-FAST MVs, one with many more volumes than the other
	verify_export ingesthost-02_MV1 -exact- 2 10
	verify_export ingesthost-02_MV2 -exact- 2 1
	verify_sg ingesthost-02_SG1 None 1 10
	verify_sg ingesthost-02_SG2 None 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-03" ]
    then
        # ingesthost-03 has one non-FAST MVs with 2 SGs, one child storage group with many more volumes than the other
	verify_export ingesthost-03_MV1 -exact- 2 11
	verify_sg ingesthost-03_ChSG1 None 0 1
	verify_sg ingesthost-03_ChSG2 None 0 10
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-04" ]
    then
        # ingesthost-04 has a non-FAST, non-cascading-SG MV with a non-fast volume and a phantom SG volume
	verify_export ingesthost-04_MV1 -exact- 2 1
	verify_sg ingesthost-04_SG None 1 1
        # Phantom Green storage group with 1 volume
	verify_sg ingesthost-phantom_Green_SG Green 0 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-05" ]
    then
        # ingesthost-05 has one MV with cascading storage groups and one MV with non-cascading
	verify_export ingesthost-05_MV1 -exact- 2 1
	verify_export ingesthost-05_MV2 -exact- 2 1
	verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_CSG None 1 1
	verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_SG_NonFast None 0 1
	verify_sg ingesthost-05_SG None 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-06" ]
    then
        # ingesthost-06 has one MV with cascading storage groups and one MV with non-cascading with a FAST policy
	verify_export ingesthost-06_GOLD_CSG_MV -exact- 2 1
	verify_export ingesthost-06_GOLD_MV -exact- 2 1
	verify_sg ingesthost-06_GOLD_SG GOLD 1 1
	verify_sg ingesthost-06_CSG None 1 1
	verify_sg ingesthost-06_GOLD_ChildSG GOLD 0 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-07" ]
    then
        # ingesthost-07 has a Green, GOLD, masking views with one volume
	verify_export ingesthost-07_Green_MV -exact- 2 1
	verify_export ingesthost-07_GOLD_MV -exact- 2 1
	verify_sg ingesthost-07_Green_SG Green 1 1
	verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-08" ]
    then
        # ingesthost-08 has a non-FAST, non-cascading-SG MV with a non-fast volume only
	verify_export ingesthost-08_MV1 -exact- 2 1
	verify_sg ingesthost-phantom_Green_SG Green 0 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-09" ]
    then
        # ingesthost-09 doesn't exist.  Just make sure it doesn't and that
        # auto-created (greenfield) masking views don't exist.
	verify_export any1 ${HN}-09 gone
	verify_export ingesthost-09_MV -exact- gone
	verify_sg ingesthost-09_573_SG_NonFast gone
    fi

    if [ "$1" == "" -o "$1" = "ingcl-10" ]
    then
        # ingestcl-10 has a non-FAST, non-cascading-SG MV with a FAST volume
	verify_export ingcl-10_Green_MV -exact- 4 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-12" ]
    then
        # ingestcl-12 has a two masking views with non-cascading-SG MVs with different FAST volumes
	verify_export ingcl-12_Green_MV -exact- 4 1
	verify_export ingcl-12_GOLD_MV -exact- 4 1
	verify_sg ingcl-12_Green_SG Green 1 1
	verify_sg ingcl-12_GOLD_SG GOLD 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-14" ]
    then
        # ingestcl-14 has a single masking view with non-cascading-SG with no FAST volumes in it.
	verify_export ingcl-14_MV -exact- 4 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-16" ]
    then
        # ingestcl-16 has masking views with non-FAST SGs separately on nodes.  No single masking view for the whole cluster.
	verify_export ingcl-16-node1_MV -exact- 2 1
	verify_export ingcl-16-node2_MV -exact- 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-18" ]
    then
        # ingcl-18 has masking views with flat FAST SGs separately on nodes.  No single masking view for the whole cluster.
	verify_export ingcl-18-node1_MV -exact- 2 1
	verify_export ingcl-18-node2_MV -exact- 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-20" ]
    then
        # ingestcl-20 is a cascaded storage group with the consistent lun flag shut off with two of three child IGs
	verify_export ingcl-20_MV -exact- 4 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-23" ]
    then
        # ingestcl-23 is a mask-per-node sharing the same FAST SG
	verify_export ingcl-23-node1_MV -exact- 2 1
	verify_export ingcl-23-node2_MV -exact- 2 1
	verify_export ingcl-23-node3_MV -exact- 2 1
        verify_sg ingcl-23_Green_SG Green 3 1
    fi   

    if [ "$1" == "" -o "$1" = "ingcl-26" ]
    then
        # ingcl-26 is a cascaded storage group with the consistent lun flag shut off with all of three child IGs
	verify_export ingcl-26_MV -exact- 6 1
    fi   

    if [ "$1" == "" -o "$1" = "ingcl-29" ]
    then
        # ingcl-29 is a cascaded storage group, but only two of the three needed child IGs
        # If this fails, it's likely because ViPR doesn't remove the child IG of 31 after a delete of the export group.
	verify_export ingcl-29_MV -exact- 4 1
    fi   

    if [ "$1" == "" -o "$1" = "ingesthost-32" ]
    then
        # ingesthost-32 is a brownfield host with only 1 initiator.
	verify_export ingesthost-32_MV -exact- 1 1
    fi   

    if [ "$1" == "" -o "$1" = "ingcl-33" ]
    then
        # ingcl-33 is a single IG representing the entire cluster.
	verify_export ingcl-33_Green_MV -exact- 4 1
	verify_export ingcl-33_GOLD_MV -exact- 4 1
    fi   

    if [ "$1" == "" -o "$1" = "ingcl-35" ]
    then
        # ingcl-35 is two masking views with 3 nodes, but only 2 are in the pre-existing mask
	verify_export ingcl-35_Green_MV -exact- 4 1
	verify_export ingcl-35_GOLD_MV -exact- 4 1
	verify_sg ingcl-35_Green_SG Green 1 1
	verify_sg ingcl-35_GOLD_SG GOLD 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-38" ]
    then
        # ingcl-38 is a cluster with two hosts, each missing one initiator and a Non-FAST non-cascaded SG
	verify_export ingcl-38-node1_MV -exact- 1 1
	verify_export ingcl-38-node2_MV -exact- 1 1
    fi

    if [ "$1" == "" -o "$1" = "ingcl-40" ]
    then
        # ingcl-38 is a cluster with two hosts, each missing one initiator and a single MV
	verify_export ingcl-40_MV -exact- 2 1
    fi

    if [ "$1" == "" -o "$1" = "ingesthost-42" ]
    then
        # ingesthost-42 is a brownfield host with a gold storage group
	verify_export ingesthost-42_GOLD_MV -exact- 2 1
	verify_sg ingesthost-42_GOLD_SG GOLD 1 1
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
# Expected Brownfield on VMAX:
#
# All masking views use IG_1 with 2 initiators and PG_1 with 2-4 storage ports
#
# Masking View  |  Storage Group   |   Cascaded SG   |   FAST Policy   |  Existing Volumes 
# ----------------------------------------------------------------------------------------
#     MV_1            SG_1                No                No                   1
#     MV_2            SG_GOLD             No                GOLD                 1
#     MV_3            SG_Green            No                Green                1
#
# 0. Two masks already exist for the host (1 volume each).  One for FAST policy GOLD and one for Green.  No cascading groups at all.
# 1. Create a volume with the Green policy, verify it leverages the existing Green mask and storage group and doesn't just start creating new stuff.
# 2. Create a volume with the GOLD policy, same as above for verification.
# 3. Delete "Green" export_group, verify the green masking view only has 1 volume in it, as it did originally.
# 4. Delete "GOLD" export_group, same as above for verification.
# 5. Create one export group that has both Green and GOLD volumes, verify it spreads the volumes out properly.
# 6. Delete the combo export group, verify it removes the volumes from the existing masks only.
# 7. Create export group for Blue volume, which has no existing mask/SG.  Verify it creates a new mask, but leverages the existing IG without creating a CIG.
# 8. Delete export group and ensure it DELETES the entire masking view with the exception of the IG that belongs to the other masking views.
#
test_1() {
    echot "Test 1 FAST Brownfield basic export group create/delete operations, multi-masking views"
    expname=${EXPORT_GROUP_NAME}t1
    verify_setup ingesthost-00

    # Now let's just try to add a boring non-fast volume
    runcmd export_group create $PROJECT ${expname}4 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}4
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 1

    # Should add the volume to only the Green storage group, therefore the Green masking view
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_Green-1 --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1

    # Should add the volume to only the GOLD storage group, therefore the GOLD masking view    
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_GOLD-1 --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}2
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1

    # Now throw both volumes in one export group.  Ensure one export group operation can handle multiple volumes with different policy types
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}3
    verify_setup ingesthost-00
}

# Export Test 2
#
# Test co-existence when the host has a phantom storage group that accounts for the fast policy.
#
test_2() {
    echot "Test 2 FAST Brownfield PHANTOM test with non-fast, non-cascading storage group single host"
    expname=${EXPORT_GROUP_NAME}t2
    # Make sure the correct brownfield scenario is there.
    verify_setup ingesthost-01

    # Should add the volume to the green phantom storage group, as well as the non-fast, non-cascading storage group
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_Green-1 --hosts "${HN}-01"
    verify_export ingesthost-01_MV1 -exact- 2 3
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    # Will add to the same mask, creating another phantom storage group
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_GOLD-1 --hosts "${HN}-01"
    verify_export ingesthost-01_MV1 -exact- 2 4
    verify_export ${expname}2 ${HN}-01 gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingesthost-01_MV1 -exact- 2 3
    verify_sg ingesthost-phantom_Green_SG Green 0 1

    runcmd export_group delete $PROJECT/${expname}2
    verify_export ingesthost-01_MV1 -exact- 2 2

    # Now throw both volumes in one export group.  Ensure one export group operation can handle multiple volumes with different policy types
    runcmd export_group create $PROJECT ${expname}3 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-01"
    verify_export ingesthost-01_MV1 -exact- 2 4
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group delete $PROJECT/${expname}3
    verify_setup ingesthost-01
}

# Export Test 3
#
# Test that ensures that if there are multiple masking views for the host already, that we pick the right one
# for the new policy, which will in turn, create a phantom storage group.
#
test_3() {
    echot "Test 3 FAST Brownfield Begins: PHANTOM with multiple existing MVs"
    expname=${EXPORT_GROUP_NAME}t3
    verify_setup ingesthost-00

    # Greenfield/brownfield: there are masks, but not for this fast policy (yet)
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_Blue-1 --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 2
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-00
}

# Export Test 4
#
# Test add and remove volume functionality on a brownfield environment
#
# Expected Brownfield on VMAX:
#
test_4() {
    echot "Test 4 BROWNFIELD Test add/remove volumes with multi-masking views"
    expname=${EXPORT_GROUP_NAME}t4
    verify_setup ingesthost-00

    # Greenfield/brownfield: just add a simple volume (which will use MV_1), then start adding/removing stuff.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 2
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Going to change the behavior so this can't be done
    #PI=`hostwwn 11 00`
    #runcmd export_group update ${PROJECT}/${expname}1 --remInits ${HN}-00/${PI}

    # Add a non-fast volume
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}-2
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 3

    # Add a green volume
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}_Green-1
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 3

    #PI=`hostwwn 11 00`
    #runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HN}-00/${PI}

    # Add a blue volume (current implementation: will create phantom)
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}_Blue-1
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 4
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Add another blue volume
    runcmd export_group update ${PROJECT}/${expname}1 --addVols ${PROJECT}/${VOLNAME}_Blue-2
    verify_export ingesthost-00_Green_MV1 -exact- 2 2
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 5
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Remove a green volume
    runcmd export_group update ${PROJECT}/${expname}1 --remVols ${PROJECT}/${VOLNAME}_Green-1
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 5
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Remove blue volumes (test multi-volume remove with same fast policy)
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Blue-1,${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 3
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone
    
    # Re-add the blue volumes (test multi-volume add with same fast policy)
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Blue-1,${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 5
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Test delete when there are several volumes in several fast policies to remove
    # (Deleting the volumes as part of the script exit will test to ensure we cleaned volumes out of SGs)
    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-00
}

# Export Test 5
#
# Advanced test add and remove multiple volumes on a brownfield environment
#
test_5() {
    echot "Test 5 BROWNFIELD Test add/remove mixed-policy volumes with multi-masking views"
    expname=${EXPORT_GROUP_NAME}t5
    verify_setup ingesthost-00

    # Create a simple export group with one volume to prime the export group
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-00"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 2
    # verify it didn't create a new mask
    verify_export ${expname}1 ${HN}-00 gone

    # Add the entire house of volumes.
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2,${PROJECT}/${VOLNAME}_Blue-1,${PROJECT}/${VOLNAME}_Blue-2,${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingesthost-00_Green_MV1 -exact- 2 3
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 3
    verify_export ingesthost-00_MV1 -exact- 2 5

    # Remove all of them, including the first one we created the export group with
    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2,${PROJECT}/${VOLNAME}_Blue-1,${PROJECT}/${VOLNAME}_Blue-2,${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingesthost-00_Green_MV1 -exact- 2 1
    verify_export ingesthost-00_GOLD_MV1 -exact- 2 1
    verify_export ingesthost-00_MV1 -exact- 2 1

    # Test delete when there are several volumes in several fast policies to remove
    # (Deleting the volumes as part of the script exit will test to ensure we cleaned volumes out of SGs)
    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-00
}

# Export Test 6
#
# Test ability to add the same FAST volume to two hosts, where a phantom storage group is in use.
#
test_6() {
    echot "Test 6 FAST Brownfield Begins: PHANTOM two host test"
    expname=${EXPORT_GROUP_NAME}t6
    verify_setup ingesthost-01
    verify_setup ingesthost-04

    # Should add the volume to any host that uses phantom storage groups.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}_Green-1 --hosts "${HN}-01,${HN}-04"

    verify_export ingesthost-01_MV1 -exact- 2 3
    verify_export ingesthost-04_MV1 -exact- 2 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    # Add green-2 to both hosts
    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"

    verify_export ingesthost-01_MV1 -exact- 2 4
    verify_export ingesthost-04_MV1 -exact- 2 3
    verify_sg ingesthost-phantom_Green_SG Green 0 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"

    verify_export ingesthost-01_MV1 -exact- 2 3
    verify_export ingesthost-04_MV1 -exact- 2 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-01
    verify_setup ingesthost-04
}

# Export Test 7
#
# Test selection of least utilized storage group when multiple qualify.
# In the case where there are more than one storage groups available, use the least utilized one.
#
# Case 1: multi-masking views
# Case 2: cascading child views
#
# Expected Brownfield on VMAX:
#
# All masking views use IG_1 with 2 initiators and PG_1 with 2-4 storage ports
#
#
test_7() {
    echot "Test 7 Brownfield test, selecting correct MV/SG (less utilizes) when more than one qualifies"
    expname=${EXPORT_GROUP_NAME}t4
    verify_setup ingesthost-02
    verify_setup ingesthost-03

    # Make sure the correct brownfield scenario is there.

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-02"
    verify_export ingesthost-02_MV1 -exact- 2 10
    verify_export ingesthost-02_MV2 -exact- 2 2
    verify_sg ingesthost-02_SG1 None 1 10
    verify_sg ingesthost-02_SG2 None 1 2

    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HN}-03"
    verify_export ingesthost-03_MV1 -exact- 2 12
    verify_sg ingesthost-03_ChSG1 None 0 2
    verify_sg ingesthost-03_ChSG2 None 0 10

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ingesthost-02_MV1 -exact- 2 10
    verify_export ingesthost-02_MV2 -exact- 2 1
    verify_sg ingesthost-02_SG1 None 1 10
    verify_sg ingesthost-02_SG2 None 1 1

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingesthost-02
    verify_setup ingesthost-03
}

# Export Test 8
#
# Test selection of cascaded storage group masking view over non-cascaded storage group when both qualify
#
test_8() {
    echot "Test 8 Brownfield test, selecting correct MV/SG (cascaded over non-cascaded) when more than one qualifies"
    expname=${EXPORT_GROUP_NAME}t8
    verify_setup ingesthost-05

    # Make sure the correct brownfield scenario is there.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HN}-05"
    verify_export ingesthost-05_MV1 -exact- 2 1
    verify_export ingesthost-05_MV2 -exact- 2 3
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_CSG None 1 3
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_SG_NonFast None 0 3
    verify_sg ingesthost-05_SG None 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-05_MV1 -exact- 2 1
    verify_export ingesthost-05_MV2 -exact- 2 2
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_CSG None 1 2
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_SG_NonFast None 0 2
    verify_sg ingesthost-05_SG None 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-05_MV1 -exact- 2 1
    verify_export ingesthost-05_MV2 -exact- 2 3
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_CSG None 1 3
    verify_sg ingesthost-05${BF_VMAX_ID_3DIGITS}_SG_NonFast None 0 3
    verify_sg ingesthost-05_SG None 1 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-05
}

# Export Test 9
#
# Test selection of cascaded storage group FAST masking view over non-cascaded FAST storage group when both qualify
#
test_9() {
    echot "Test 9 Brownfield test, selecting correct FAST MV/SG (cascaded over non-cascaded) when more than one qualifies"
    expname=${EXPORT_GROUP_NAME}t8
    verify_setup ingesthost-06

    # Make sure the correct brownfield scenario is there.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2" --hosts "${HN}-06"
    verify_export ingesthost-06_GOLD_MV -exact- 2 1
    verify_export ingesthost-06_GOLD_CSG_MV -exact- 2 3
    verify_sg ingesthost-06_CSG None 1 3
    verify_sg ingesthost-06_GOLD_ChildSG GOLD 0 3
    verify_sg ingesthost-06_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingesthost-06_GOLD_MV -exact- 2 1
    verify_export ingesthost-06_GOLD_CSG_MV -exact- 2 2
    verify_sg ingesthost-06_CSG None 1 2
    verify_sg ingesthost-06_GOLD_ChildSG GOLD 0 2
    verify_sg ingesthost-06_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingesthost-06_GOLD_MV -exact- 2 1
    verify_export ingesthost-06_GOLD_CSG_MV -exact- 2 3
    verify_sg ingesthost-06_CSG None 1 3
    verify_sg ingesthost-06_GOLD_ChildSG GOLD 0 3
    verify_sg ingesthost-06_GOLD_SG GOLD 1 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-06
}

# Export Test 10
#
# Test 10 MV with a non-cascaded FAST policies without a non-FAST SG
#
test_10() {
    echot "Test 10 FAST Brownfield with non-cascading FAST SGs without a non-FAST SG"
    expname=${EXPORT_GROUP_NAME}t10
    verify_setup ingesthost-07

    # Same FAST type.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingesthost-07_Green_MV -exact- 2 3
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 3
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-07

    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 2
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 2

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingesthost-07

}

# Export Test 11
#
# Test 11 MV with a non-cascaded FAST policies without a non-FAST SG (Different FAST Type)
#
test_11() {
    echot "Test 11 FAST Brownfield with non-cascading FAST SGs without a non-FAST SG (Different FAST Type)"
    expname=${EXPORT_GROUP_NAME}t11
    USE_CLUSTERED_HOSTS=0
    verify_setup ingesthost-07

    # Different FAST type.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Blue-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-07
    verify_export ${expname}1 ${HN}-07 gone

    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 gone
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 gone

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingesthost-07
}

# Export Test 12
#
# Test 12 MV with a non-cascaded FAST policies without a non-FAST SG (Non-FAST volume)
#
test_12() {
    echot "Test 12 FAST Brownfield with non-cascading FAST SGs without a non-FAST SG (Non-FAST volume)"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t12
    verify_setup ingesthost-07

    # NO FAST volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-07_Green_MV -exact- 2 1
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 1
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-07
    verify_export ${expname}1 ${HN}-07 gone

    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-07"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 gone
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1
    verify_export ${expname}1 ${HN}-07 2 1

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingesthost-07_Green_MV -exact- 2 2
    verify_export ingesthost-07_GOLD_MV -exact- 2 1
    verify_sg ingesthost-07_Green_SG Green 1 2
    verify_sg ingesthost-07_GOLD_SG GOLD 1 1xs
    verify_export ${expname}1 ${HN}-07 gone

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingesthost-07
}

# Export Test 13
#
# Test 13 MV with a non-cascaded, non-FAST SG, add non-FAST volumes
#
test_13() {
    echot "Test 13 FAST Brownfield with non-cascading, non-FAST SG, CRUD non-FAST volume"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t13
    verify_setup ingesthost-08

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-08"
    verify_export ingesthost-08_MV1 -exact- 2 2
    verify_sg ingesthost-08_SG None 1 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-08_MV1 -exact- 2 3
    verify_sg ingesthost-08_SG None 1 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingesthost-08_MV1 -exact- 2 2
    verify_sg ingesthost-08_SG None 1 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-08
}

# Export Test 14
#
# Test 14 MV with a non-cascaded, non-FAST SG, add FAST volumes
#
test_14() {
    echot "Test 14 Brownfield with non-cascading, non-FAST SG, CRUD FAST volume"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t14
    verify_setup ingesthost-08
    verify_sg ingesthost-phantom_Green_SG Green 0 1

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Green-1" --hosts "${HN}-08"
    verify_export ingesthost-08_MV1 -exact- 2 2
    verify_sg ingesthost-08_SG None 1 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingesthost-08_MV1 -exact- 2 3
    verify_sg ingesthost-08_SG None 1 3
    verify_sg ingesthost-phantom_Green_SG Green 0 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingesthost-08_MV1 -exact- 2 2
    verify_sg ingesthost-08_SG None 1 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-08
}

# Export Test 15
#
# Test 15 cluster MV with a non-cascaded FAST policies without a non-FAST SG
#
test_15() {
    echot "Test 15 FAST Brownfield cluster with non-cascading FAST SGs without a non-FAST SG"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t15
    verify_setup ingcl-12

    # Same FAST type.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingcl-12_Green_MV -exact- 4 3
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 3
    verify_sg ingcl-12_GOLD_SG GOLD 1 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingcl-12_Green_MV -exact- 4 3
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 3
    verify_sg ingcl-12_GOLD_SG GOLD 1 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12

    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-12

}

# Export Test 16
#
# Test 16 cluster MV with a non-cascaded FAST policies without a non-FAST SG (Different FAST Type)
#
test_16() {
    echot "Test 16 FAST Brownfield cluster with non-cascading FAST SGs without a non-FAST SG (Different FAST Type)"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t16
    verify_setup ingcl-12
    CLUSTER="${CN}-12"

    # Different FAST type.
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Blue-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Blue-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
    verify_export ${expname}1 ${CN}-12 gone

    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- gone
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-12
}

# Export Test 17
#
# Test 17 cluster MV with a non-cascaded FAST policies without a non-FAST SG (Non-FAST volume)
#
test_17() {
    echot "Test 17 FAST Brownfield with non-cascading FAST SGs without a non-FAST SG (Non-FAST volume)"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t17
    verify_setup ingcl-12

    # NO FAST volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
    verify_export ${expname}1 -x- gone

    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- gone
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1xs
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-12
}

# Export Test 18
#
# Test 18 cluster MV with a non-cascaded, non-FAST SG, add non-FAST volumes
#
test_18() {
    echot "Test 18 Brownfield cluster with non-cascading, non-FAST SG, CRUD non-FAST volume"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t18
    verify_setup ingcl-14

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-14"
    verify_export ingcl-14_MV -exact- 4 2
    verify_sg ingcl-14_SG None 1 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-14_MV -exact- 4 3
    verify_sg ingcl-14_SG None 1 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-14_MV -exact- 4 2
    verify_sg ingcl-14_SG None 1 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-14
}

# Export Test 19
#
# Test 19 cluster MV with a non-cascaded, non-FAST SG, add FAST volumes
#
test_19() {
    echot "Test 19 FAST Brownfield cluster with non-cascading, non-FAST SG, CRUD FAST volume"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t19
    verify_setup ingcl-14
    verify_sg ingesthost-phantom_Green_SG Green 0 1

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-14"
    verify_export ingcl-14_MV -exact- 4 2
    verify_sg ingcl-14_SG None 1 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingcl-14_MV -exact- 4 3
    verify_sg ingcl-14_SG None 1 3
    verify_sg ingesthost-phantom_Green_SG Green 0 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingcl-14_MV -exact- 4 2
    verify_sg ingcl-14_SG None 1 2
    verify_sg ingesthost-phantom_Green_SG Green 0 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-14
}

# Export Test 20
#
# Test 20 two nodes of a cluster with separate masking views
#
test_20() {
    echot "Test 20 Brownfield cluster with one MV per node, no cluster MV; non-FAST and FAST"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t20
    verify_setup ingcl-16
    CLUSTER="${CN}-16"

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-16"
    verify_export ingcl-16-node1_MV -exact- 2 2
    verify_export ingcl-16-node2_MV -exact- 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-16-node1_MV -exact- 2 3
    verify_export ingcl-16-node2_MV -exact- 2 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-16-node1_MV -exact- 2 2
    verify_export ingcl-16-node2_MV -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-16

    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-16"
    verify_export ingcl-16-node1_MV -exact- 2 2
    verify_export ingcl-16-node2_MV -exact- 2 2
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-16-node1_MV -exact- 2 3
    verify_export ingcl-16-node2_MV -exact- 2 3

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-16-node1_MV -exact- 2 2
    verify_export ingcl-16-node2_MV -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-16
}

# Export Test 21
#
# Test 21 cluster MV with cascaded IGs, but missing one of the nodes and consistent LUN flag off
#
test_21() {
    echot "Test 21 Brownfield cluster with cascaded IG, missing one child IG, consisten LUN flag false"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t21
    verify_setup ingcl-20

    /opt/emc/SYMCLI/bin/symaccess delete -noprompt -sid 573 -type initiator -name ingesthost-22_IG -force
    /opt/emc/SYMCLI/bin/symaccess create -sid 573 -type initiator -name ingesthost-22_IG
    /opt/emc/SYMCLI/bin/symaccess add -sid 573 -type initiator -name ingesthost-22_IG -wwn 1212341222222222
    /opt/emc/SYMCLI/bin/symaccess add -sid 573 -type initiator -name ingesthost-22_IG -wwn 1112341222222222

    echo "Sleeping to give the provider a chance to see the new IG"
    sleep 120

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-20"
    verify_export ingcl-20_MV -exact- 6 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-20_MV -exact- 6 3
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-20_MV -exact- 6 2
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}1

    # ViPR will not remove the IG once it's added; side-effect of not knowing which initiators we can 
    # remove from a mask even though we borrowed an existing IG versus initiators we can delete completely.
    /opt/emc/SYMCLI/bin/symaccess remove -sid 573 -type initiator -name ingcl-20_573_CIG -ig ingesthost-22_IG
    verify_setup ingcl-20
}

# Export Test 22
#
# Test 22 cluster MV with cascaded IGs, consistent LUN flag off
#
test_22() {
    echot "Test 22 Brownfield cluster with cascaded IG, testing single host with consistent LUN flag set to false"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t22
    verify_setup ingcl-26
    verify_export ${expname}1 ${CN}-26_${HN}-28 gone

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-28"
    verify_export ingcl-26_MV -exact- 6 1
    verify_export ${expname}1 ${HN}-28 2 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-26_MV -exact- 6 1
    verify_export ${expname}1 ${HN}-28 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-26_MV -exact- 6 1
    verify_export ${expname}1 ${HN}-28 2 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-26
    verify_export ${expname}1 ${HN}-28 gone
}

# Export Test 23
#
# Test 23 cluster MV with cascaded IGs that's missing one of the nodes (so we need to add it)
#
test_23() {
    echot "Test 23 Brownfield cluster with cascaded IG, missing one node that ViPR needs to add"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t23
    verify_setup ingcl-29

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-29"
    verify_export ingcl-29_MV -exact- 6 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-29_MV -exact- 4 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-29_MV -exact- 6 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-29_MV -exact- 6 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-29_MV -exact- 6 2

    runcmd export_group delete $PROJECT/${expname}1

    verify_setup ingcl-29
}

# Export Test 24
#
# Test 24 Cluster with 2 nodes in ViPR, pre-existing one MV per node, sharing FAST non-cascading SG, each IG missing one initiator
#
test_24() {
    echot "Test 24 Cluster with 2 nodes in ViPR, pre-existing one MV per node, sharing FAST non-cascading SG, each IG missing one initiator"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t38
    CLUSTER="${CN}-38"
    verify_setup ${CLUSTER}

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${CLUSTER}-node1_MV -exact- 2 1
    verify_export ${CLUSTER}-node1_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ${CLUSTER}-node1_MV -exact- 2 1
    verify_export ${CLUSTER}-node1_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_setup ${CLUSTER}-node1_MV -exact- 1 1
    verify_setup ${CLUSTER}-node2_MV -exact- 1 1
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ${CLUSTER}-node1_MV -exact- 2 1
    verify_export ${CLUSTER}-node2_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ${CLUSTER}
    verify_export ${expname}1 -x- gone
}

# Export Test 25
#
# Test 25 Cluster with 2 nodes in ViPR, each IG missing one initiator, one MV
#
test_25() {
    echot "Test 25 Cluster with 2 nodes in ViPR, each IG missing one initiator, one MV"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t40
    CLUSTER="${CN}-40"
    verify_setup ingcl-40

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2" --cluster "${TENANT}/${CLUSTER}"
    verify_export ${CLUSTER}_MV -exact- 4 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ${CLUSTER}_MV -exact- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_setup ${CLUSTER}
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2"
    verify_export ${CLUSTER}_MV -exact- 4 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ${CLUSTER}
    verify_export ${expname}1 -x- gone
}

# Export Test 26
#
# Test 26 Greenfield: new masking view, non-fast then fast.
#         Note: I put this in greenfield because the script is already really good at managing FAST volumes
#
test_26() {
    echot "Test 26 FAST Greenfield, new masking view, non-fast then fast."
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t26
    verify_setup ingesthost-09

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-09"
    verify_export ${expname}1 ${HN}-09 2 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ${expname}1 ${HN}-09 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HN}-09 gone
}

# Export Test 27
#
# Test 27 Brownfield: ViPR has more WWNs for the host than the brownfield mask.
#
test_27() {
    echot "Test 27 FAST Brownfield: ViPR has more WWNs for host than brownfield mask."
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t27
    verify_setup ingesthost-32

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-32"
    verify_export ingesthost-32_MV -exact- 2 2

    runcmd export_group delete $PROJECT/${expname}1
    sleep 20
    verify_setup ingesthost-32
}

# Export Test 28
#
# Test 28 Brownfield: ViPR has more WWNs for the host than the brownfield mask.  Non-FAST, then FAST
#
test_28() {
    echot "Test 28 FAST Brownfield: ViPR has more WWNs for host than brownfield mask.  Non-FAST, then FAST"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t28
    verify_setup ingesthost-32

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HN}-32"
    verify_export ingesthost-32_MV -exact- 2 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingesthost-32_MV -exact- 2 3

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-32
}

# Export Test 29
#
# Test 29 cluster MV with a non-cascaded FAST policies without a non-FAST SG (Non-FAST and FAST volumes)
#
test_29() {
    echot "Test 29 FAST Brownfield with non-cascading FAST SGs without a non-FAST SG (Non-FAST and FAST volumes)"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t29
    CLUSTER="${CN}-12"
    verify_setup ingcl-12

    # NO FAST volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 3
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 3
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-2,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-2,${PROJECT}/${VOLNAME}-2,${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-12_Green_MV -exact- 4 3
    verify_export ingcl-12_GOLD_MV -exact- 4 1
    verify_sg ingcl-12_Green_SG Green 1 3
    verify_sg ingcl-12_GOLD_SG GOLD 1 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
    verify_export ${expname}1 -x- gone
}

# Export Test 30
#
# Test 30 One masking view per node in cluster with a fast policy, adding new fast policy volume 
# Note: VMAX doesn't allow you to add the same FAST volume to two storage groups.
#
test_30() {
    echot "Test 30 FAST Brownfield cluster, one masking view per node in cluster, adding/removing initiators"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t30
    verify_setup ingcl-18
    CLUSTER="${CN}-18"
    verify_export ${expname}1 -x- gone

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-18"
    verify_export ingcl-18-node1_MV -exact- 2 1
    verify_export ingcl-18-node2_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingcl-18-node1_MV -exact- 2 1
    verify_export ingcl-18-node2_MV -exact- 2 1
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-1,${PROJECT}/${VOLNAME}_Green-2"
    verify_export ingcl-18-node1_MV -exact- 2 1
    verify_export ingcl-18-node2_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 2

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingcl-18-node1_MV -exact- 2 1
    verify_export ingcl-18-node2_MV -exact- 2 1
    verify_export ${expname}1 -x- 4 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-18
}

# Export Test 31
#
# Test 31 two nodes of a cluster with separate masking views, but same non-cascaded SG, FAST
#
test_31() {
    echot "Test 31 Brownfield cluster with one MV per node, no cluster MV; share same FAST non-cascaded SG"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t31
    verify_setup ingcl-23
    CLUSTER="${CN}-23"
    verify_export ${expname}1 -x- gone
    verify_export ${expname}2 -x- gone

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_Green-1" --cluster "${TENANT}/${CN}-23"
    verify_export ingcl-23-node1_MV -exact- 2 2
    verify_export ingcl-23-node2_MV -exact- 2 2
    verify_export ingcl-23-node3_MV -exact- 2 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-23-node1_MV -exact- 2 2
    verify_export ingcl-23-node2_MV -exact- 2 2
    verify_export ingcl-23-node3_MV -exact- 2 2
    verify_export ${expname}1 -x- 6 1

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingcl-23-node1_MV -exact- 2 1
    verify_export ingcl-23-node2_MV -exact- 2 1
    verify_export ingcl-23-node3_MV -exact- 2 1
    verify_export ${expname}1 -x- 6 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-23

    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CN}-23"
    verify_export ingcl-23-node1_MV -exact- 2 1
    verify_export ingcl-23-node2_MV -exact- 2 1
    verify_export ingcl-23-node3_MV -exact- 2 1
    verify_export ${expname}2 -x- 6 1
        
    runcmd export_group update ${PROJECT}/${expname}2 --addVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-23-node1_MV -exact- 2 1
    verify_export ingcl-23-node2_MV -exact- 2 1
    verify_export ingcl-23-node3_MV -exact- 2 1
    verify_export ${expname}2 -x- 6 2

    runcmd export_group update ${PROJECT}/${expname}2 --remVols "${PROJECT}/${VOLNAME}_Blue-1"
    verify_export ingcl-23-node1_MV -exact- 2 1
    verify_export ingcl-23-node2_MV -exact- 2 1
    verify_export ingcl-23-node3_MV -exact- 2 1
    verify_export ${expname}2 -x- 6 1

    runcmd export_group delete $PROJECT/${expname}2
    verify_setup ingcl-23
    verify_export ${expname}2 -x- gone
}

# Export Test 32
#
# Test 32 cluster MV with cascaded IGs that's missing one of the nodes (so we need to add it)
#         Also, add another node to the cluster.
#
test_32() {
    echot "Test 32 Brownfield cluster with cascaded IG, missing one node that ViPR needs to add"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t32
    verify_setup ingcl-29
    CLUSTER=${CN}-29

    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster "${TENANT}/${CLUSTER}"
    verify_export ingcl-29_MV -exact- 6 2

    PI1=`hostwwn 11 99`
    NI1=`hostwwn 21 99`
    HOSTN=${HN}-99
    runcmd transportzone add $NH/${FC_ZONE_A} ${PI1}
    runcmd hosts create ${HOSTN} $TENANT Windows ${HOSTN} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
    runcmd initiator create ${HOSTN} FC ${PI1} --node $NI1
    
    runcmd export_group update ${PROJECT}/${expname}1 --addHosts ${HOSTN}
    verify_export ingcl-29_MV -exact- 7 2

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-29_MV -exact- 7 3

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export ingcl-29_MV -exact- 7 2

    runcmd export_group update ${PROJECT}/${expname}1 --remHosts ${HOSTN}
    verify_export ingcl-29_MV -exact- 6 2

    runcmd export_group delete $PROJECT/${expname}1

    runcmd initiator delete ${HOSTN}/${PI1}
    runcmd hosts delete ${HOSTN}

    verify_setup ingcl-29
}

# Export Test 33
#
# Test 33 cluster MV with a non-cascaded FAST policies, one FAST (GOLD) is a standalone, but is a child in another CSG
#
test_33() {
    echot "Test 33 FAST Brownfield cluster MV with a non-cascaded FAST policies, one FAST (GOLD) is a standalone, but is a child in another CSG"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t33
    CLUSTER="${CN}-12"
    verify_setup ingcl-12

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 3
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 3
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_setup ingcl-12

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-12
    verify_export ${expname}1 -x- gone
}

# Export Test 34
#
# Test 34 cluster MV with one IG; FAST and non-FAST support
#
test_34() {
    echot "Test 34 FAST Brownfield cluster MV with one IG; FAST and non-FAST support"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t34
    CLUSTER="${CN}-33"
    verify_setup ingcl-33

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2" --cluster "${TENANT}/${CLUSTER}"
    verify_export ingcl-33_Green_MV -exact- 4 1
    verify_export ingcl-33_GOLD_MV -exact- 4 3
    verify_sg ingcl-33_Green_SG Green 1 1
    verify_sg ingcl-33_GOLD_SG GOLD 1 3
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-33_Green_MV -exact- 4 1
    verify_export ingcl-33_GOLD_MV -exact- 4 2
    verify_sg ingcl-33_Green_SG Green 1 1
    verify_sg ingcl-33_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_setup ingcl-33

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-33_Green_MV -exact- 4 1
    verify_export ingcl-33_GOLD_MV -exact- 4 2
    verify_sg ingcl-33_Green_SG Green 1 1
    verify_sg ingcl-33_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingcl-33_Green_MV -exact- 4 2
    verify_export ingcl-33_GOLD_MV -exact- 4 2
    verify_sg ingcl-33_Green_SG Green 1 2
    verify_sg ingcl-33_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}-1"
    verify_export ingcl-33_Green_MV -exact- 4 2
    verify_export ingcl-33_GOLD_MV -exact- 4 2
    verify_sg ingcl-33_Green_SG Green 1 2
    verify_sg ingcl-33_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- 4 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-33
    verify_export ${expname}1 -x- gone
}

# Export Test 35
#
# Test 35 Multiple cluster MV with 2 nodes out of 3 in existing mask. 
#
test_35() {
    echot "Test 35 FAST Brownfield Multiple cluster MV with 2 nodes out of 3 in existing mask."
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t35
    CLUSTER="${CN}-35"
    verify_setup ingcl-35

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_GOLD-1,${PROJECT}/${VOLNAME}_GOLD-2" --cluster "${TENANT}/${CN}-35"
    verify_export ingcl-35_Green_MV -exact- 6 1
    verify_export ingcl-35_GOLD_MV -exact- 6 3
    verify_sg ingcl-35_Green_SG Green 1 1
    verify_sg ingcl-35_GOLD_SG GOLD 1 3
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-35_Green_MV -exact- 6 1
    verify_export ingcl-35_GOLD_MV -exact- 6 2
    verify_sg ingcl-35_Green_SG Green 1 1
    verify_sg ingcl-35_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_setup ingcl-35

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-2"
    verify_export ingcl-35_Green_MV -exact- 6 1
    verify_export ingcl-35_GOLD_MV -exact- 6 2
    verify_sg ingcl-35_Green_SG Green 1 1
    verify_sg ingcl-35_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingcl-35
    verify_export ${expname}1 -x- gone
}

# Export Test 36
#
# Test 36 Brownfield: Existing mask non-cascaded SG of FAST Policy X.  Create different policy FAST (new mask), then FAST X volume (old mask)
#
test_36() {
    echot "Test 36 Brownfield: Existing mask non-cascaded SG of FAST Policy X.  Create different policy FAST (new mask), then FAST X volume (old mask)"
    USE_CLUSTERED_HOSTS=0
    expname=${EXPORT_GROUP_NAME}t36
    verify_setup ingesthost-42
    verify_export ${expname}1 ${HN}-42 gone

    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}_Blue-1" --hosts "${HN}-42"
    verify_export ingesthost-42_GOLD_MV -exact- 2 1
    verify_export ${expname}1 ${HN}-42 2 1

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_GOLD-1"
    verify_export ingesthost-42_GOLD_MV -exact- 2 2
    verify_export ${expname}1 ${HN}-42 2 1

    runcmd export_group delete $PROJECT/${expname}1
    verify_setup ingesthost-42
    verify_export ${expname}1 ${HN}-42 gone
}

# Export Test 37
#
# Test 37 cluster MV with cascading IGs, export group with 2 different FAST policy masks referenced; add initiator that impacts both masks
#
test_37() {
    echot "Test 37 cluster MV with cascading IGs, export group with 2 different FAST policy masks referenced; add initiator that impacts both masks"
    USE_CLUSTERED_HOSTS=1
    expname=${EXPORT_GROUP_NAME}t37
    CLUSTER="${CN}-12"
    verify_setup ingcl-12

    # FAST volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}_GOLD-1" --cluster "${TENANT}/${CN}-12"
    verify_export ingcl-12_Green_MV -exact- 4 1
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 1
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addVols "${PROJECT}/${VOLNAME}_Green-1"
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    # Add an initiator to a host
    
    PI=`hostwwn 13 12`
    NI=`hostwwn 23 12`
    transportzone add $NH/${FC_ZONE_A} $PI
    runcmd initiator create ${HN}-12 FC $PI --node $NI
    # initiator create is returning immediately, but it's doing an export group update in the background.
    sleep 120
    verify_export ingcl-12_Green_MV -exact- 5 2
    verify_export ingcl-12_GOLD_MV -exact- 5 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --remInits ${HN}-12/${PI}
    verify_export ingcl-12_Green_MV -exact- 4 2
    verify_export ingcl-12_GOLD_MV -exact- 4 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group update ${PROJECT}/${expname}1 --addInits ${HN}-12/${PI}
    verify_export ingcl-12_Green_MV -exact- 5 2
    verify_export ingcl-12_GOLD_MV -exact- 5 2
    verify_sg ingcl-12_Green_SG Green 1 2
    verify_sg ingcl-12_GOLD_SG GOLD 1 2
    verify_export ${expname}1 -x- gone

    runcmd export_group delete $PROJECT/${expname}1
    runcmd initiator delete ${HN}-12/${PI}
    verify_setup ingcl-12
    verify_export ${expname}1 -x- gone
}

cleanup() {
   for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
   do
      runcmdnocleanup export_group delete ${id} > /dev/null
      echo "Deleted export group: ${id}"
   done

   if [ "${expname}" = "${EXPORT_GROUP_NAME}t32" ]
   then
      runcmdnocleanup initiator delete ${HOSTN}/${PI1}
      runcmdnocleanup hosts delete ${HOSTN}
   fi

   if [ "${expname}" = "${EXPORT_GROUP_NAME}t37" ]
   then
      runcmdnocleanup initiator delete ${HN}-12/${PI}
   fi

   echo There were $VERIFY_EXPORT_COUNT export verifications
   echo There were $VERIFY_EXPORT_FAIL_COUNT export verification failures
}

cleanup_vols() {
   runcmd volume delete $PROJECT --project --wait
}

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

   I2=12
   I3=34
   I4=12
   I5=$2
   I6=$2
   I7=$2

   echo "${PRE}:${I2}:${I3}:${I4}:${I5}:${I6}:${I7}:${POST}"   
}

# ============================================================
# -    M A I N
# ============================================================

login

if [ "$1" = "verify" ]
then
   verify_setup
   finish
fi

if [ "$1" = "delete" ]
then
  cleanup
  finish
fi

if [ "$1" = "prime" ]
then
   setup
   setup_hosts_onvmax
   finish
fi

if [ "$1" = "setup" -o "$1" = "full" ]
then
    # Verify there aren't old volumes around.
    volume list project | grep vmaxexp | grep YES
    if [ $? -eq 0 ]
    then
        echo "Volumes from a previous run were found!  Not running setup."
        finish
    fi

    setup $2
fi

if [ "$1" = "-only" ]
then
    shift;
    NOVERIFY=1
    tests="$*"
    echo "tests are: " ${tests};
    for test in ${tests}
    do
      ${test} 
    done
    finish
fi


# An attempt at making the script run faster by only serializing operations that would bump into each other, otherwise
# letting everything run in parallel
# This tracking was absolute chaos, but it's the 'true tracking'
#TRACK_1="test_1 test_3 test_4 test_5"
#TRACK_2="test_2 test_6"
#TRACK_3="test_10 test_11 test_12"
#TRACK_4="test_13 test_14"
#TRACK_5="test_15 test_16 test_17 test_29 test_33"
#TRACK_6="test_18 test_19"
#TRACK_7="test_23 test_32"
#TRACK_8="test_27 test_28"
#SINGLES="test_7 test_8 test_9 test_20 test_21 test_22 test_24 test_25 test_26 test_30 test_21 test_34 test_35"

# A little more under control?
TRACK_1="test_1 test_2 test_3 test_4 test_5 test_6 test_7 test_8 test_9 test_10 test_11 test_12 test_13 test_14 test_36"
TRACK_2="test_15 test_16 test_17 test_19 test_20 test_21 test_22 test_23 test_24 test_25 test_26 test_27 test_28 test_29 test_30 test_31 test_32 test_33 test_34 test_35"

# Scatter is good to run if you want to run it as fast as possible and it's OK if a failure requires some picking around.
if [ "$1" = "-scatter" ]
then
    echo "turn on verify setup once this is working!"
    # verify_setup;
    # Fire off all of the singles
    mkdir -p results/${BASENUM}
    #for test in ${SINGLES}
    #do
    #   $0 -only ${test} > results/${BASENUM}/${test} 2>&1 &
    #done

    $0 -only ${TRACK_1} > results/${BASENUM}/t1 2>&1 &
    $0 -only ${TRACK_2} > results/${BASENUM}/t2 2>&1 &
    #$0 -only ${TRACK_3} > results/${BASENUM}/t3 2>&1 &
    #$0 -only ${TRACK_4} > results/${BASENUM}/t4 2>&1 &
    #$0 -only ${TRACK_5} > results/${BASENUM}/t5 2>&1 &
    #$0 -only ${TRACK_6} > results/${BASENUM}/t6 2>&1 &
    #$0 -only ${TRACK_7} > results/${BASENUM}/t7 2>&1 &
    #$0 -only ${TRACK_8} > results/${BASENUM}/t8 2>&1 &
    
    finish
fi

# Scatter is good to run if you want to run it as fast as possible and it's OK if a failure requires some picking around.
if [ "$1" = "-track" ]
then
    #echo "turn on verify setup once this is working!"
    # verify_setup;
    # Fire off all of the singles
    #mkdir -p results/${BASENUM}
    if [ "$2" = "1" ]
    then
	track=${TRACK_1}
    else
	track=${TRACK_2}
    fi

    for test in ${track}
    do
       ${test}
    done

    #$0 -only $2 > results/${BASENUM}/t1 2>&1 &
    #$0 -only ${TRACK_2} > results/${BASENUM}/t2 2>&1 &
    #$0 -only ${TRACK_3} > results/${BASENUM}/t3 2>&1 &
    #$0 -only ${TRACK_4} > results/${BASENUM}/t4 2>&1 &
    #$0 -only ${TRACK_5} > results/${BASENUM}/t5 2>&1 &
    #$0 -only ${TRACK_6} > results/${BASENUM}/t6 2>&1 &
    #$0 -only ${TRACK_7} > results/${BASENUM}/t7 2>&1 &
    #$0 -only ${TRACK_8} > results/${BASENUM}/t8 2>&1 &

    finish
fi

if [ "$1" = "-start" ]
then
   start_test=$2
else
   start_test="test_1"
   # If there's a 2nd parameter, take that 
   # as the name of the test to run
   if [ "$2" != "" ]
   then
       echo Request to run $2
       $2
       finish
   fi
fi

if [ "${TESTS_DONE_PUT_HERE}" = "never" ]
then
  echo "tests in here are not run"
fi

verify_setup;

tests="test_1 test_2 test_3 test_4 test_5 test_6 test_7 test_8 test_9 test_10 test_11 test_12 test_13 test_14 test_15 test_16 test_17 test_18 test_19 test_20 test_21 test_22 test_24 test_25 test_26 test_27 test_28 test_29 test_30 test_31 test_33 test_34 test_35 test_36 test_37"
#tests="test_1"

run=0;
for test in $tests
do
  if [ "${test}" = "${start_test}" ]
  then
    run=1;
  fi

  if [ ${run} -eq 1 ]
  then
     ${test}
    # So cleanup doesn't think a test is in progress
    expname=NONE
  fi
done

if [ "${start_test}" != "test_1" ]
then
    run=1;
    for test in $tests
      do
      if [ "${test}" = "${start_test}" ]
      then
	  run=0;
      fi

      if [ ${run} -eq 1 ]
      then
	  ${test}
          # So cleanup doesn't think a test is in progress
          expname=NONE
      fi

    done
fi

verify_setup;

if [ "$1" = "full" ]
then
   echo "Test Complete!  Cleaning up Environment"
   cleanup;
   cleanup_vols;
fi

finish

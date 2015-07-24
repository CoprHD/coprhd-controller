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
# - The verification script will contact the VNX via navicli to verify the expectations of the command that was run:
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
# set -x

Usage()
{
    echo 'Usage: vmaxexport.sh [setup]'
    echo ' [setup]: Run on a new ViPR database, creates SMIS, host, initiators, vpools, varray, volumes'
    exit 2
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
BOURNE_IP=10.247.101.39

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
: ${PROJECT=project}
SYSADMIN=root
SYSADMIN_PASSWORD=${VIPR_ROOT_PASSWORD:-ChangeMe}

#
# cos configuration
#
VMAXPOOL=vmax_pool
VNXPOOL=vnx_pool

VNX_SMIS_IP=10.247.99.25
VNX_SP_IP=10.247.66.250
VNX_NATIVEGUID=CLARIION+APM00120400480
VNXB_INITIATOR='50:06:01:64:46:EF:3A:38'

VMAX_SMIS_IP=10.247.99.74
VMAX_ID=000198700406
VMAX_NATIVEGUID=SYMMETRIX+${VMAX_ID}
VMAX_SMIS_DEV=smis-provider-vmax

SMIS_USER=admin
SMIS_PASSWD='#1Password'

BASENUM=${RANDOM}
VMAX_VOLNAME=vmaxexp${BASENUM}
VNX_VOLNAME=vnxexp${BASENUM}
EXPORT_GROUP_NAME=export${BASENUM}
HOST1=host1export${BASENUM}
HOST2=host2export${BASENUM}
HOST3=host3export${BASENUM}
CLUSTER=cl${BASENUM}

which symhelper.sh
if [ $? -ne 0 ]; then
    echo Could not find symhelper.sh path. Please add the directory where the script exists to the path
    locate symhelper.sh
    exit
fi

which navihelper.sh
if [ $? -ne 0 ]; then
    echo Could not find navihelper.sh path. Please add the directory where the script exists to the path
    locate navihelper.sh
    exit
fi

login() {
   echo "Tenant is ${TENANT}";
    security login $SYSADMIN $SYSADMIN_PASSWORD
    syssvc localhost setup
    security add_authn_provider ldap ldap://10.247.100.65 test_cert CN=Manager,DC=root,DC=com secret ou=People,DC=root,DC=com uid=%U uid CN ldap-configuration tenant.domain *Admins*,*Test*
    tenant create $TENANT tenant.domain OU tenant.com
    echo "Tenant $TENANT created."
}

runcmd() {
    echo $*
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

verify_vmax_export() {
    runcmd symhelper.sh $*
    if [ $? -ne "0" ]; then
       echo Exiting due to failure ...
       cleanup
    fi
}

verify_vnx_export() {
    runcmd navihelper.sh $VNX_SP_IP $macaddr $*
    if [ $? -ne "0" ]; then
       echo Exiting due to failure ...
       cleanup
    fi
}

setup_common() {
    neighborhood create $NH
    transportzone create $FC_ZONE_A $NH --type FC
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

    hosts create ${HOST1} $TENANT Windows ${HOST1} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
    initiator create ${HOST1} FC $H1PI1 --node $H1NI1
    initiator create ${HOST1} FC $H1PI2 --node $H1NI2

    hosts create ${HOST2} $TENANT Windows ${HOST2} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
    initiator create ${HOST2} FC $H2PI1 --node $H2NI1
    initiator create ${HOST2} FC $H2PI2 --node $H2NI2

    hosts create ${HOST3} $TENANT Windows ${HOST3} --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${TENANT}/${CLUSTER}
    initiator create ${HOST3} FC $H3PI1 --node $H3NI1
    initiator create ${HOST3} FC $H3PI2 --node $H3NI2
}

setup_vmax() {
    # do this only once
    echo "Setting up $VMAX_NATIVEGUID"
    smisprovider create VMAX-PROVIDER $VMAX_SMIS_IP 5988 admin '#1Password' false
    storagedevice discover_all --ignore_error

    storagepool update $VMAX_NATIVEGUID --type block --volume_type THIN_ONLY
    storagepool update $VMAX_NATIVEGUID --type block --volume_type THICK_ONLY
    storagepool update $VMAX_NATIVEGUID --nhadd $NH --type block
    storageport update $VMAX_NATIVEGUID FC --tzone $FCTZ_A
    storageport update $VMAX_NATIVEGUID IP --tzone nh/iptz

    storagepool update ${VMAX_NATIVEGUID} --nhadd $NH --pool "$RP_VNXB_POOL" --type block --volume_type THIN_ONLY
    storageport update ${VMAX_NATIVEGUID} FC --tzone $NH/$FC_ZONE_A

    # make a base cos for protected volumes
    cos create block ${VMAXPOOL}					\
	--description 'Vpool for VMAX' true \
	--protocols FC 			\
	--numpaths 1				\
	--provisionType 'Thin'			\
	--max_snapshots 10                     \
	--neighborhoods $NH

   cos update block $VMAXPOOL --storage ${VMAX_NATIVEGUID}
   cos allow $VMAXPOOL block $TENANT
   runcmd volume create ${VMAX_VOLNAME} ${PROJECT} ${NH} ${VMAXPOOL} 1GB --count 3
}

setup_vnx() {
    # do this only once
    echo "Setting up $VNX_NATIVEGUID"
    smisprovider create VNX-PROVIDER $VNX_SMIS_IP 5988 admin '#1Password' false
    storagedevice discover_all --ignore_error

    storagepool update $VNX_NATIVEGUID --type block --volume_type THIN_ONLY
    storagepool update $VNX_NATIVEGUID --type block --volume_type THICK_ONLY
    storagepool update $VNX_NATIVEGUID --nhadd $NH --type block
    storageport update $VNX_NATIVEGUID FC --tzone $FCTZ_A
    storageport update $VNX_NATIVEGUID IP --tzone nh/iptz

    storagepool update ${VNX_NATIVEGUID} --nhadd $NH --pool "$RP_VNXB_POOL" --type block --volume_type THIN_ONLY
    storageport update ${VNX_NATIVEGUID} FC --tzone $NH/$FC_ZONE_A

    # make a base cos for protected volumes
    cos create block ${VNXPOOL}					\
	--description 'Vpool for VNX' true \
	--protocols FC 			\
	--numpaths 1				\
	--provisionType 'Thin'			\
	--max_snapshots 10                     \
	--neighborhoods $NH

   cos update block $VNXPOOL --storage ${VNX_NATIVEGUID}
   cos allow $VNXPOOL block $TENANT
   runcmd volume create ${VNX_VOLNAME} ${PROJECT} ${NH} ${VNXPOOL} 1GB --count 3
}

setup() {
    setup_common
    setup_vmax
    setup_vnx
}

# Export Test 0
#
# Base combine array test.
# 1. Create export for two hosts using VMAX volume
# 2. Added host to export
# 3. Added VNX volume to export
# 4. Remove host from 2 from export
#
test_0() {
    echo "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VMAX_VOLNAME}-1 --hosts "${HOST1},${HOST2}"
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group update $PROJECT/${expname}1 --addHosts "${HOST3}"
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VNX_VOLNAME}-1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VNX_VOLNAME}-1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST3}
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone
}

# Export Test 1
#
# Base combine array test.
# 1. Create export for two hosts using VNX volume
# 2. Added host to export
# 3. Added VMAX volume to export
# 4. Remove host from 2 from export
#
test_1() {
    echo "Test 1 Begins"
    expname=${EXPORT_GROUP_NAME}t1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VNX_VOLNAME}-1 --hosts "${HOST1},${HOST2}"
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group update $PROJECT/${expname}1 --addHosts "${HOST3}"
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} 2 1

    runcmd export_group update $PROJECT/${expname}1 --addVols ${PROJECT}/${VMAX_VOLNAME}-1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remVols ${PROJECT}/${VMAX_VOLNAME}-1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remHosts ${HOST3}
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} 2 1
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone

    runcmd export_group delete $PROJECT/${expname}1
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vmax_export ${expname}1_${CLUSTER}_${HOST3} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST1} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST2} gone
    verify_vnx_export ${expname}1_${CLUSTER}_${HOST3} gone
}

cleanup() {
   for id in `export_group list $PROJECT | grep YES | awk '{print $5}'`
   do
      export_group delete ${id} > /dev/null
      echo "Deleted export group: ${id}"
   done
   runcmd volume delete $PROJECT --project --wait
   exit;
}

# call this to generate a random WWN for exports.
# VNX (especially) does not like multiple initiator registrations on the same
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

if [ "$1" = "delete" ]
then
  cleanup;
  exit;
fi

if [ "$1" = "setup" ]
then
    setup;
fi;

# If there's a 2nd parameter, take that
# as the name of the test to run
if [ "$2" != "" ]
then
   echo Request to run $2
   $2
   cleanup
   exit
fi

# Passing tests:
test_0;
test_1;

cleanup;

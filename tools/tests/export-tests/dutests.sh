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
    echo 'Usage: dutests.sh <sanity conf file path> {vmax | vnx | vplex | xtremio} [setup|delete [test1 test2 ...] ]'
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
SS=${2}
if [ ${SS} = "xtremio" ]; then
    XTREMIO_TESTS=1
fi
case $SS in

vmax|vnx|vplex|xtremio)

    ;;
    Usage
esac

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
	if [ -f ${CMD_OUTPUT} ]; then
	    cat ${CMD_OUTPUT}
	fi
	echo There was a failure
	VERIFY_EXPORT_FAIL_COUNT=`expr $VERIFY_EXPORT_FAIL_COUNT + 1`
        #cleanup
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

# Place to put command output in case of failure
CMD_OUTPUT=/tmp/output.txt
rm -f ${CMD_OUTPUT}

runcmd() {
    cmd=$*
    echo === $cmd
    rm -f ${CMD_OUTPUT}
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
	$cmd &> ${CMD_OUTPUT}
    else
	$cmd 2>&1
    fi
}

#counterpart for run
#executes a command that is expected to fail
fail(){
    cmd=$*
    echo === $cmd
    if [ "${HIDE_OUTPUT}" = "" -o "${HIDE_OUTPUT}" = "1" ]; then
	$cmd &> ${CMD_OUTPUT}
    else
	$cmd 2>&1
    fi

    status=$?
    if [ $status -eq 0 ] ; then
        echo '**********************************************************************'
        echo $cmd succeeded, which should not have happened
	cat ${CMD_OUTPUT}
        echo '**********************************************************************'
        exit 1
    fi
    secho "$cmd failed, which is the expected ouput"
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
    runcmd smisprovider create VMAX-PROVIDER $VMAX_SMIS_IP $VMAX_SMIS_PORT $SMIS_USER "$SMIS_PASSWD" $VMAX_SMIS_SSL
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

   runcmd volume create ${VOLNAME} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 2
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
#
test_0() {
    echot "Test 0 Begins"
    expname=${EXPORT_GROUP_NAME}t0
    set_suspend_on_class_method "none"
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    runcmd export_group create $PROJECT ${expname}2 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-2 --hosts "${HOST2}"
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}2 ${HOST2} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT/${expname}2
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone
}

set_suspend_on_error() {
    runcmd syssvc $SANITY_CONFIG_FILE $BOURNE_IPADDR set_prop workflow_suspend_on_error $1
}

set_suspend_on_class_method() {
    runcmd syssvc $SANITY_CONFIG_FILE $BOURNE_IPADDR set_prop workflow_suspend_on_class_method "$1"
}

# Export Test 1
#
# Basic Use Case for single host, single volume
# Required to test suspend/resume functionality
#
#
test_1() {
    echot "Test 1 DU Check Begins"
    expname=${EXPORT_GROUP_NAME}t1

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ExportWorkflowEntryPoints.exportGroupCreate

    # Run the export group command
    echo === export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"
    resultcmd=`export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task
    runcmd task follow $task

    
    verify_export ${expname}1 ${HOST1} 2 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export ${expname}1 ${HOST1} gone
}

# Export Test 2
#
# Basic Use Case for single host, single volume
# 1. ViPR creates 1 volume, 1 host export.
# 2. Customer creates a volume outside of ViPR and adds the volume to the mask.
# 3. ViPR asked to remove the export group.
# 4. Verify the operation fails.
# 5. Remove the volume from the mask outside of ViPR.
# 6. Attempt operation again, succeeds.
#
test_2() {
    echot "Test 2 DU Check Begins"
    expname=${EXPORT_GROUP_NAME}t2

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts "${HOST1}"

    # Turn on suspend of export after orchestration
    set_suspend_on_class_method ExportWorkflowEntryPoints.exportGroupDelete

    # Run the export group command
    echo === export_group delete $PROJECT/${expname}1
    resultcmd=`export_group delete $PROJECT/${expname}1`

    if [ $? -ne 0 ]; then
	echo "export group command failed outright"
    fi

    # Show the result of the export group command for now (show the task and WF IDs)
    echo $resultcmd

    # Parse results (add checks here!  encapsulate!)
    taskworkflow=`echo $resultcmd | awk -F, '{print $2 $3}'`
    answersarray=($taskworkflow)
    task=${answersarray[0]}
    workflow=${answersarray[1]}

    # Create another volume that we will inventory-only delete
    runcmd volume create du-hijack-volume ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1
    device_id=`volume show ${PROJECT}/du-hijack-volume | grep native_id | awk '{print $2}' | cut -c2-6`
    runcmd volume delete ${PROJECT}/du-hijack-volume --vipronly

    # BEGIN: THIS SECTION OF CODE IS DIFFERENT DEPENDING ON THE ARRAY TYPE, SO IT SHOULD BE GENERALIZED INTO A MASKING METHOD PER ARRAY
    # arrayhelper OPERATION SERIAL_NUMBER ${device_id} ${HOST1}
    # arrayhelper add_vol_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Find out where the volume ended up, thanks to ViPR (formalize this!)
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid 612 list -type storage -dev ${device_id} | grep ViPR_Optimized | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid 612 list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`

    # Remove the volume from the storage group it is in
    runcmd /opt/emc/SYMCLI/bin/symaccess -sid 612 -type storage -name $sg_long_id remove dev ${device_id}

    # Add it to the storage group ViPR knows about
    sg_short_id=`/opt/emc/SYMCLI/bin/symaccess -sid 612 list -type storage | grep ${HOST1}_612_SG | cut -c1-31`
    sg_long_id=`/opt/emc/SYMCLI/bin/symaccess -sid 612 list -type storage -detail -v | grep ${sg_short_id} | awk -F: '{print $2}' | awk '{print $1}' | sed -e 's/^[[:space:]]*//'`
    runcmd /opt/emc/SYMCLI/bin/symaccess -sid 612 -type storage -name $sg_long_id add dev ${device_id}

    # END: THIS SECTION OF CODE IS DIFFERENT DEPENDING ON THE ARRAY TYPE, SO IT SHOULD BE GENERALIZED INTO A MASKING METHOD PER ARRAY

    # Verify the mask has the new volume in it
    verify_export ${expname}1 ${HOST1} 2 2

    # TODO: Update provider?  (ask harsha)

    # Resume the workflow
    runcmd workflow resume $workflow
    # Follow the task.  It should fail because of Poka Yoke validation
    fail task follow $task

    # Now remove the volume from the storage group (masking view)
    # array_helper remove_vol_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    runcmd /opt/emc/SYMCLI/bin/symaccess -sid 612 -type storage -name $sg_long_id remove dev ${device_id}
    # array_helper delete_vol ${SERIAL_NUMBER} ${device_id}

    # TODO: Delete the hijack device

    # Verify the mask is back to normal
    verify_export ${expname}1 ${HOST1} 2 1

    # TODO: Update provider?  (ask harsha)
    
    # Turn off suspend of export after orchestration
    set_suspend_on_class_method "none"

    # Try the export operation again
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure it really did kill off the mask
    verify_export ${expname}1 ${HOST1} gone
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

if [ "$2" = "regression" ]
then
   test_0;
fi

if [ "$2" = "delete" ]
then
  cleanup
  finish
fi

if [ "$2" = "setup" ]
then
    setup $2;
fi;

# If there's a 2nd parameter, take that
# as the name of the test to run
if [ "$3" != "" ]
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
test_2;
exit;
test_0;
test_1;
cleanup;
finish


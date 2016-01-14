#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# VPLEX EXPORT Test
# This can be run from within sanity, or standalone after sanity has initiatlized the system.
# For loggin in use: user@domain.com Password
#
# This script sets up two VPLEX environments, a cross-connected one, and non cross-connected on,
# using four Varrays. The Varrays are: VAcc1, VAcc2,  and  VAnc1, VAnc2.
# It builds a cluster for each enviornment with two virtual hosts.
# The identifiers for the cluster, host, and volumes have the hostseed embedded in them,
# which is based on the MAC address of your system, and makes them unique per system.
#
# These tests can be run multiple times- the setup code tries to be very intelligent
# about only setting up the required environment once. It generally tests for the
# existence of things like Virtual arrays, Vpools, Clusters, Hosts, etc. and only
# creates them if needed. Additionally, if the entire setup has completed, it
# is automatically bypassed, based on the Virtual Arrays having (non VPlex)
# Storage Ports assigned to them, which is the last thing done.
#
# The volumes used across the tests are left, making the running time more efficient
# if rerunning the tests. The cleanup() routine removes the volumes.


# Save the command arguments
ARGC=$#
[ $ARGC -eq 0 ] && {
    echo "usage: vplex_export_test [test1|test2|test3|test4|test5|cleanup]*"
    exit 2;
}

SANITY_CONFIG_FILE=""
# ============================================================
# Check if there is a sanity configuration file specified
# on the command line. In, which case, we should use that
# ============================================================
if [ "$1"x != "x" ]; then
   if [ -f "$1" ]; then
      SANITY_CONFIG_FILE=$1
      echo Using sanity configuration file $SANITY_CONFIG_FILE
      shift
   fi
fi

ARGV=$*
CWD=$(pwd)
export PATH=$CWD:$CWD/..:$(dirname $0):$(dirname $0)/..:$PATH
echo "PATH: " $PATH

# Virtual arrays
VACC1=VAcc1		# cross connected VPlex cluster-1
VACC2=VAcc2		# cross connected VPlex cluster-2
VANC1=VAnc1		# non-cross connected VPlex cluster-1
VANC2=VAnc2		# non-cross connected VPlex cluster-2

# Configuration file to be used  
source $SANITY_CONFIG_FILE 

# Variables that should be inherited from sanity
BLK_SIZE=${BLK_SIZE:-1073741824}

tenant=${tenant:-standalone}
[ "$tenant" ] || {
    tenant="standalone"
}
[ "$project" ] || {
    project="sanity"
}
# The altVipr variable allows use of another ViPR instance for Brownfield scenarios.
[ "$altVipr" ] || {
    altVipr="--ip ${ALTERNATE_COPRHD}"
}
[ "$macaddr" ] || {
    macaddr=`/sbin/ifconfig eth0 | /usr/bin/awk '/HWaddr/ { print $5 }'`
}
[ "$hostseed" ] || {
    hostseed=`echo ${macaddr} | awk -F: '{print $5$6}'`
    hostbase=host${hostseed}
    echo "hostbase $hostbase"
}

#Clusters
CCCluster=cluster${hostseed}CC
NCCluster=cluster${hostseed}NC

#Hosts
CCHost1=host${hostseed}CC1
CCHost2=host${hostseed}CC2
NCHost1=host${hostseed}NC1 
NCHost2=host${hostseed}NC2 

# VOLUMES - cross connected
    CC1DVol=vplxex${hostseed}CC1Dist
    CC2DVol=vplxex${hostseed}CC2Dist
    CC1LVol=vplxex${hostseed}CC1Local
    CC2LVol=vplxex${hostseed}CC2Local
    # Not exported cross connected
    CC1DNCCEVol=vplxex${hostseed}CC1DNCCE
    CC2DNCCEVol=vplxex${hostseed}CC2DNCCE

# VOLUMES - non cross connected
    NC1DVol=vplxex${hostseed}NC1Dist
    NC2DVol=vplxex${hostseed}NC2Dist
    NC1LVol=vplxex${hostseed}NC1Local
    NC2LVol=vplxex${hostseed}NC2Local
    NC1NoHAVol=vnx${hostseed}NC1NoHA
    NC2NoHAVol=vnx${hostseed}NC2NoHA

pwwn() {
    idx=$1; echo 50:${macaddr}:${idx}
}
nwwn() {
    idx=$1; echo 51:${macaddr}:${idx}
}
echoit() {
    echo "******************************************************************"
    echo $*
    echo "******************************************************************"
}

run() {
    cmd=$*
    date
    echoit $cmd
    $cmd 2>&1
    status=$?
    date
    if [ $status -ne 0 ]; then
        echoit $cmd failed
	date
	exit $status
    fi
}

# $1 = failure message
fail() {
    [ "$1" = ""	] && {
        $1="Failed- please see previous messages"
    }
    echoit fail: $1
    date
    exit 2
}

# $1=project/volume, $2=VPLEXCLx vplex cluster;  returns $ITLs
getITLs() {
    echo "Exports for $1 in VPLEX cluster designated $2:"
    volume exports $1 --v | grep $2 | tee /tmp/ITLs
    ITLs=$(cat /tmp/ITLs | grep ":" | awk ' {print $3,$1;}'| sort)
    rm /tmp/ITLs
}

# Verifiies zone names or initiators $* in the ITLs
inITLs() {
    initls=0;
    for zonename in $*
    do
        count=$(echo $ITLs | grep -c $zonename)
        [ $count -eq 0 ] && {
            echo "Expected zonename $zonename in ITLs but it is not"
            initls=1;
        }
    done
    return $initls;
}
# Verifiies zone names or initiators $* not in the ITLs
notInITLs() {
    notinitls=0;
    for zonename in $*
    do
        count=$(echo $ITLs | grep -c $zonename)
        [ $count -ne 0 ] && {
            echo "Did not expect zonename $zonename in ITLs but it is"
            notinitls=1;
        }
    done
    return $notinitls;
}

# The export test uses different virtual arrays than sanity.
# This is so we can have more explicit control--
# VACC1 -- cross connected in cluster-1 (both network ports)
# VACC2 -- cross connected in cluster-2 (both network ports)
# VANC1 -- non cross connected in cluster-1 (cluster-1 network ports)
# VANC2 -- non cross connected in cluster-2 (cluster-2 network ports)
setup_virtual_arrays() {
    existingVAs=$(neighborhood list | awk ' { print $1; }')
    # Ensure the virtual arrays are present
    $(echo $existingVAs | grep -q $VACC1 ) || {
        neighborhood create $VACC1
    }
    $(echo $existingVAs | grep -q $VACC2 ) || {
        neighborhood create $VACC2
    }
    $(echo $existingVAs | grep -q $VANC1 ) || {
        neighborhood create $VANC1
    }
    $(echo $existingVAs | grep -q $VANC2 ) || {
        neighborhood create $VANC2
    }
    existingVAs=$(neighborhood list | awk ' { print $1; }')
    echo "Virtual arrays: $existingVAs"
}

# Function to create a cluster. $1 is the cluster name
cluster_create() {
    name=$1
    exists=$(cluster list ${tenant} | grep $name | wc -l)
    [ $exists -ne 1 ] && {
        echoit "creating cluster $name"
        cluster create $name $tenant --project $project
    }
}

# Function to create a host.
# $1 = host name, $2 = cluster name
# $3 = arg to pwwn for first initiator, 
# $4 = arg to pwwn for second initiator
# $5 = network to add first initiator to
# $6 = network to add second initiator to
host_create() {
    name=$1; cluster=$2; init1=$3; init2=$4; net1=$5; net2=$6
    exists=$(hosts list ${tenant} | grep $name | wc -l)
    [ $exists -ne 1 ] && {
        echoit "creating host $name"
        hosts create $name $tenant Other ${name}.org --port 2222 --cluster $tenant/$cluster
        initiator create $name FC $(pwwn $init1) --node $(nwwn $init1)
        initiator create $name FC $(pwwn $init2) --node $(nwwn $init2)
        transportzone add $net1 $(pwwn $init1)
        transportzone add $net2 $(pwwn $init2)
    }
}

# Sets up hosts and clusters
setup_hosts() {
    # Cross connected cluster
    cluster_create $CCCluster
    host_create $CCHost1 $CCCluster E0 E1 $CLUSTER1NET $CLUSTER2NET
    host_create $CCHost2 $CCCluster E2 E3 $CLUSTER1NET $CLUSTER2NET
    # Non cross connected cluster
    cluster_create $NCCluster
    host_create $NCHost1 $NCCluster E8 E9 $CLUSTER1NET $CLUSTER1NET
    host_create $NCHost2 $NCCluster EA EB $CLUSTER2NET $CLUSTER2NET
}

# Assign a vplex port to a varray
# $1=port_names (plural), $2=port_group, $3=virtual_array
assign_vplex_ports() {
    echo assigning ports $1 , $2 , $3
    for port_name in $1
    do
        echoit "Assigning $port_name $2 to VA $3"
        storageport update $VPLEX_GUID FC --name $port_name --group $2 --addvarrays $3
    done
}

# Setup the Vplex ports
setup_vplexports() {
    TMPFILE=/tmp/storage.ports.setup
    storageport list $VPLEX_GUID --v >$TMPFILE

    # Front end port groups
    FECL1A082=`grep $CLUSTER1NET $TMPFILE | grep VPLEXCL1_PORTID_GREP | grep '^A0' | awk ' { print $1; }' `
    echo FECL1A082 : $FECL1A082
    FECL1B082=`grep $CLUSTER1NET $TMPFILE | grep $VPLEXCL1_PORTID_GREP | grep '^B0' | awk ' { print $1; }' `
    echo FECL1B082 : $FECL1B082
    FECL2A082=`grep $CLUSTER1NET $TMPFILE | grep $VPLEXCL2_PORTID_GREP | grep '^A0' | awk ' { print $1; }' `
    echo FECL2A082 : $FECL2A082
    FECL2B082=`grep $CLUSTER1NET $TMPFILE | grep $VPLEXCL2_PORTID_GREP | grep '^B0' | awk ' { print $1; }' `
    echo FECL2B082 : $FECL2B082
    FECL1A154=`grep $CLUSTER2NET $TMPFILE | grep $VPLEXCL1_PORTID_GREP | grep '^A0' | awk ' { print $1; }' `
    echo FECL1A154 : $FECL1A154
    FECL1B154=`grep $CLUSTER2NET $TMPFILE | grep $VPLEXCL1_PORTID_GREP | grep '^B0' | awk ' { print $1; }' `
    echo FECL1B154 : $FECL1B154
    FECL2A154=`grep $CLUSTER2NET $TMPFILE | grep $VPLEXCL2_PORTID_GREP | grep '^A0' | awk ' { print $1; }' `
    echo FECL2A154 : $FECL2A154
    FECL2B154=`grep $CLUSTER2NET $TMPFILE | grep $VPLEXCL2_PORTID_GREP | grep '^B0' | awk ' { print $1; }' `
    echo FECL2B154 : $FECL2B154

    # Back end port groups
    BECL1A=`grep $VPLEXCL1_PORTID_GREP $TMPFILE | grep '^A1' | awk ' { print $1; }' `
    echo BECL1A : $BECL1A
    BECL1B=`grep $VPLEXCL1_PORTID_GREP $TMPFILE | grep '^B1' | awk ' { print $1; }' `
    echo $BECL1B : $BECL1B
    BECL2A=`grep $VPLEXCL2_PORTID_GREP $TMPFILE | grep '^A1' | awk ' { print $1; }' `
    echo BECL2A : $BECL2A
    BECL2B=`grep $VPLEXCL2_PORTID_GREP $TMPFILE | grep '^B1' | awk ' { print $1; }' `
    echo $BECL2B : $BECL2B

    # Assign the ports to the Cross Connected Varrays
    assign_vplex_ports "$FECL1A082 $FECL1A154 $BECL1A" director-1-1-A $VACC1
    assign_vplex_ports "$FECL1B082 $FECL1B154 $BECL1B" director-1-1-B $VACC1
    assign_vplex_ports "$FECL2A082 $FECL2A154 $BECL2A" director-2-1-A $VACC2
    assign_vplex_ports "$FECL2B082 $FECL2B154 $BECL2B" director-2-1-B $VACC2

    # Assign the ports to the Non Cross Connected Varrays
    assign_vplex_ports "$FECL1A082 $BECL1A" director-1-1-A $VANC1
    assign_vplex_ports "$FECL1B082 $BECL1B" director-1-1-B $VANC1
    assign_vplex_ports "$FECL2A154 $BECL2A" director-2-1-A $VANC2
    assign_vplex_ports "$FECL2B154 $BECL2B" director-2-1-B $VANC2
}

setup_arrayports() {
    storageport update $VPLEX_VNX1_NATIVEGUID FC --addvarrays ${VACC1},${VANC1}
    storageport update $VPLEX_VNX2_NATIVEGUID FC --addvarrays ${VACC1},${VANC1}
    storageport update $VPLEX_VMAX_NATIVEGUID FC --addvarrays ${VACC2},${VANC2}
}

# Note- this has to happen after the Vplex ports are assigned
add_networks_to_varrays() {
    # Cross connected
    transportzone assign $CLUSTER1NET $VACC1
    transportzone assign $CLUSTER2NET $VACC1
    transportzone assign $CLUSTER1NET $VACC2
    transportzone assign $CLUSTER2NET $VACC2

    # Non cross connected
    transportzone assign $CLUSTER1NET $VANC1
    transportzone assign $CLUSTER2NET $VANC2
}

vplexexport_setup() {
    # Check if the environment has already been setup.
    # This is detected because array ports are assigned to each Varray.
    CC1COUNT=$(neighborhood storageports VAcc1 | grep -c CLAR )
    CC2COUNT=$(neighborhood storageports VAcc2 | grep -c SYMM )
    NC1COUNT=$(neighborhood storageports VAnc1 | grep -c CLAR )
    NC2COUNT=$(neighborhood storageports VAnc2 | grep -c SYMM )
    echo array counts: $CC1COUNT $CC2COUNT $NC1COUNT $NC2COUNT
    if [ $CC1COUNT -gt 0 -a $CC2COUNT -gt 0 -a $NC1COUNT -gt 0 -a $NC2COUNT -gt 0 ]; then
        echo "Skipping vplex export setup; varrays are already populated."
        return;
    fi

    # Check for the existence of our two networks
    CLUSTER1NET=$(transportzone listall | grep $CLUSTER1NET_NAME | wc -l )
    [ $CLUSTER1NET -eq 1 ] || {
        echo "No cluster-1 network found"
        exit 2
    }
    CLUSTER1NET=$(transportzone listall | grep $CLUSTER1NET_NAME )
    CLUSTER2NET=$(transportzone listall | grep $CLUSTER2NET_NAME | wc -l )
    [ $CLUSTER2NET -eq 1 ] || {
        echo "No cluster-2 network found"
	exit 2
    }
    CLUSTER2NET=$(transportzone listall | grep $CLUSTER2NET_NAME )
    echo $CLUSTER1NET $CLUSTER2NET

    setup_virtual_arrays
    setup_hosts
    setup_vplexports
    setup_arrayports
    add_networks_to_varrays
}

# Setup the virtual pools
setup_virtual_pools() {
    # Get the existing virtual pools
    existingVpools=$(cos list block)

    #################### Cross-Connected Vpools ##########################

    CC1NoHA_exists=$(echo $existingVpools | grep -c CC1NoHA)
    if [ $CC1NoHA_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool CC1NoHA"
	    cos create block CC1NoHA                                   \
			     --description 'Non HA CC1'	true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --neighborhoods $VACC1 			    \
			     --max_snapshots 1

	    cos allow CC1NoHA block $tenant
	    cos update block CC1NoHA --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block CC1NoHA --storage $VPLEX_VNX2_NATIVEGUID
    fi

    CC2NoHA=$(echo $existingVpools | grep -c CC2NoHA)
    if [ $CC2NoHA -eq 0 ]; then
	    echoit "Creating Virtual Pool CC2NoHA"
	    cos create block CC2NoHA                                   \
			     --description 'Non HA CC2' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --neighborhoods $VACC2 			    \
			     --max_snapshots 1

	    cos allow CC2NoHA block $tenant
	    cos update block CC2NoHA --storage $VPLEX_VMAX_NATIVEGUID
    fi

    CC1Dist_exists=$(echo $existingVpools | grep -c CC1Dist)
    if [ $CC1Dist_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool CC1Dist"
	    cos create block CC1Dist                                   \
			     --description 'Distributed CoS for VPlex' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VACC1 			    \
			     --haNeighborhood $VACC2                        \
			     --haCos CC2NoHA				    \
			     --max_snapshots 1

	    cos allow CC1Dist block $tenant
	    cos update block CC1Dist --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block CC1Dist --storage $VPLEX_VMAX_NATIVEGUID
    fi

    CC1DNCCE_exists=$(echo $existingVpools | grep -c CC1DNCCE)
    if [ $CC1DNCCE_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool CC1DNCCE"
	    cos create block CC1DNCCE                                   \
			     --description 'Distributed CoS for VPlex, no CC export' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VACC1 			    \
			     --haNeighborhood $VACC2                        \
			     --haCos CC2NoHA				    \
			     --max_snapshots 1				    \
			     --auto_cross_connect false

	    cos allow CC1DNCCE block $tenant
	    cos update block CC1DNCCE --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block CC1DNCCE --storage $VPLEX_VMAX_NATIVEGUID
    fi


    CC2Dist_exists=$(echo $existingVpools | grep -c CC2Dist)
    if [ $CC2Dist_exists -eq 0 ]; then
            echoit "Creating Virtual Pool CC2Dist"
	    cos create block CC2Dist                                   \
			     --description 'Distributed CoS for VPlex' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VACC2 			    \
			     --haNeighborhood $VACC1                        \
			     --haCos CC1NoHA				    \
			     --max_snapshots 1

	    cos allow CC2Dist block $tenant
	    cos update block CC2Dist --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block CC2Dist --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block CC2Dist --storage $VPLEX_VMAX_NATIVEGUID
    fi

    CC2DNCCE_exists=$(echo $existingVpools | grep -c CC2DNCCE)
    if [ $CC2DNCCE_exists -eq 0 ]; then
            echoit "Creating Virtual Pool CC2DNCCE"
	    cos create block CC2DNCCE                                   \
			     --description 'Distributed CoS for VPlex no CC export' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VACC2 			    \
			     --haNeighborhood $VACC1                        \
			     --haCos CC1NoHA				    \
			     --max_snapshots 1				    \
			     --auto_cross_connect false

	    cos allow CC2DNCCE block $tenant
	    cos update block CC2DNCCE --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block CC2DNCCE --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block CC2DNCCE --storage $VPLEX_VMAX_NATIVEGUID
    fi


    CCLocal_exists=$(echo $existingVpools | grep -c CCLocal)
    if [ $CCLocal_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool CCLocal"
	    cos create block CCLocal                            \
			     --description 'Local CoS for VPlex' true \
			     --protocols FC                           \
			     --numpaths 4                             \
			     --provisionType 'Thin'                   \
			     --highavailability vplex_local           \
			     --neighborhoods $VACC1 $VACC2            \
			     --max_snapshots 1                        \
			     --max_mirrors 1                          \
			     --expandable false 

	    cos allow CCLocal block $tenant
	    cos update block CCLocal --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block CCLocal --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block CCLocal --storage $VPLEX_VMAX_NATIVEGUID
    fi

    #################### Non Cross-Connected Vpools ######################

    NC1NoHA_exists=$(echo $existingVpools | grep -c NC1NoHA)
    if [ $NC1NoHA_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool NC1NoHA"
	    cos create block NC1NoHA                                   \
			     --description 'Non HA NC1'	true \
			     --protocols FC                                 \
			     --numpaths 2                                   \
			     --provisionType 'Thin'                         \
			     --neighborhoods $VANC1 			    \
			     --max_snapshots 1

	    cos allow NC1NoHA block $tenant
	    cos update block NC1NoHA --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block NC1NoHA --storage $VPLEX_VNX2_NATIVEGUID
    fi

    NC2NoHA=$(echo $existingVpools | grep -c NC2NoHA)
    if [ $NC2NoHA -eq 0 ]; then
	    echoit "Creating Virtual Pool NC2NoHA"
	    cos create block NC2NoHA                                   \
			     --description 'Non HA NC2' true \
			     --protocols FC                                 \
			     --numpaths 2                                   \
			     --provisionType 'Thin'                         \
			     --neighborhoods $VANC2 			    \
			     --max_snapshots 1

	    cos allow NC2NoHA block $tenant
	    cos update block NC2NoHA --storage $VPLEX_VMAX_NATIVEGUID
    fi

    NC1Dist_exists=$(echo $existingVpools | grep -c NC1Dist)
    if [ $NC1Dist_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool NC1Dist"
	    cos create block NC1Dist                                   \
			     --description 'Distributed CoS for VPlex' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VANC1 			    \
			     --haNeighborhood $VANC2                        \
			     --haCos NC2NoHA				    \
			     --max_snapshots 1

	    cos allow NC1Dist block $tenant
	    cos update block NC1Dist --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block NC1Dist --storage $VPLEX_VMAX_NATIVEGUID
    fi


    NC2Dist_exists=$(echo $existingVpools | grep -c NC2Dist)
    if [ $NC2Dist_exists -eq 0 ]; then
            echoit "Creating Virtual Pool NC2Dist"
	    cos create block NC2Dist                                   \
			     --description 'Distributed CoS for VPlex' true \
			     --protocols FC                                 \
			     --numpaths 4                                   \
			     --provisionType 'Thin'                         \
			     --highavailability vplex_distributed           \
			     --neighborhoods $VANC2 			    \
			     --haNeighborhood $VANC1                        \
			     --haCos NC1NoHA				    \
			     --max_snapshots 1

	    cos allow NC2Dist block $tenant
	    cos update block NC2Dist --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block NC2Dist --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block NC2Dist --storage $VPLEX_VMAX_NATIVEGUID
    fi


    NCLocal_exists=$(echo $existingVpools | grep -c NCLocal)
    if [ $NCLocal_exists -eq 0 ]; then
	    echoit "Creating Virtual Pool NCLocal"
	    cos create block NCLocal                            \
			     --description 'Local CoS for VPlex' true \
			     --protocols FC                           \
			     --numpaths 4                             \
			     --provisionType 'Thin'                   \
			     --highavailability vplex_local           \
			     --neighborhoods $VANC1 $VANC2            \
			     --max_snapshots 1                        \
			     --max_mirrors 1                          \
			     --expandable false 

	    cos allow NCLocal block $tenant
	    cos update block NCLocal --storage $VPLEX_VNX1_NATIVEGUID
	    cos update block NCLocal --storage $VPLEX_VNX2_NATIVEGUID
	    cos update block NCLocal --storage $VPLEX_VMAX_NATIVEGUID
    fi

    cos list block
}

#
# Create a volume
# $1=name, $2=varray, $3=vpool
volume_create() {
    name=$1; varray=$2; vpool=$3;
    exists=$(volume list $project | grep -c $name)
    if [ $exists -eq 0 ]; then
        echoit "Creating volume $name in $varray $vpool"
        volume create $name $project $varray $vpool $BLK_SIZE
    fi
}

# Create the volumes for the CC cluster.
create_cc_volumes() {
    volume_create $CC1DVol $VACC1 CC1Dist
    volume_create $CC2DVol $VACC2 CC2Dist
    volume_create $CC1DNCCEVol $VACC1 CC1DNCCE
    volume_create $CC2DNCCEVol $VACC2 CC2DNCCE
    volume_create $CC1LVol $VACC1 CCLocal
    volume_create $CC2LVol $VACC2 CCLocal
    volume list $project
}

# Create the volumes for the NC cluster.
create_nc_volumes() {
    volume_create $NC1DVol $VANC1 NC1Dist
    volume_create $NC2DVol $VANC2 NC2Dist
    volume_create $NC1LVol $VANC1 NCLocal
    volume_create $NC2LVol $VANC2 NCLocal
    volume list $project
}

# Create the vnx/vmax volumes for NC cluster
create_noha_volumes() {
    volume_create $NC1NoHAVol $VANC1 NC1NoHA
    volume_create $NC2NoHAVol $VANC2 NC2NoHA
}

dumpexportsCC() {
    echo "Exports for $CC1DVol"
    volume exports $project/$CC1DVol --v
    echo "Exports for $CC2DVol"
    volume exports $project/$CC2DVol --v
    echo "Exports for $CC1LVol"
    volume exports $project/$CC1LVol --v
    echo "Exports for $CC2LVol"
    volume exports $project/$CC2LVol --v
}

dumpexportsNC() {
    echo "Exports for $NC1DVol"
    volume exports $project/$NC1DVol --v
    echo "Exports for $NC2DVol"
    volume exports $project/$NC2DVol --v
    echo "Exports for $NC1LVol"
    volume exports $project/$NC1LVol --v
    echo "Exports for $NC2LVol"
    volume exports $project/$NC2LVol --v
}

dumpexportsNoHA() {
    echo "Exports for $NC1NoHAVol"
    volume exports $project/$NC1NoHAVol --v
    echo "Exports for $NC2NoHAVol"
    volume exports $project/$NC2NoHAVol --v
}

cleanup() {
    echoit "Cleaning up CC volumes"
    volume delete $project/$CC1DVol --wait
    volume delete $project/$CC2DVol --wait
    volume delete $project/$CC1LVol --wait
    volume delete $project/$CC2LVol --wait
    echoit "Cleaning up NC volumes"
    volume delete $project/$NC1DVol --wait
    volume delete $project/$NC2DVol --wait
    volume delete $project/$NC1LVol --wait
    volume delete $project/$NC2LVol --wait
    volume delete $project/$NC1NoHAVol --wait
}

test1() {
    echoit "VPlex Export Test1 - cross connected"
    create_cc_volumes

# Do export group create from Cluster1. Note that distributed volumes are exported
# from both clusters, except the NCCE volumes, which has the autoCrossConnectExport flag
# set to false in their Vpools, causing them to only be exported from Cluster1.
    run export_group create $project egtest1 $VACC1 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DVol,$project/$CC2DVol,$project/$CC1LVol,$project/$CC2LVol,$project/$CC1DNCCEVol,$project/$CC2DNCCEVol
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail

# Now do an export from cluster 2 and see that even the NCCE volumes are exported from both clusters.
    run export_group create $project egtest1b $VACC2 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DVol,$project/$CC2DVol,$project/$CC1DNCCEVol,$project/$CC2DNCCEVol
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

# Now delete the 2nd ExportGroup and we should revert to the original set of exports.
    run export_group delete $project/egtest1b
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail

    run export_group update $project/egtest1 --remVols $project/$CC2DVol,$project/$CC1LVol
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs cc1org cc2org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --addVolspec $project/$CC2DVol,$project/$CC1LVol
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --remHosts $CCHost1
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org ||  fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --addHosts $CCHost1
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --remHosts $CCHost2
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org  || fail
    notInITLs cc2org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --addHosts $CCHost2
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    
    run export_group update $project/egtest1 --remInits $CCHost2/$(pwwn E2)
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail

    run export_group update $project/egtest1 --remInits $CCHost1/$(pwwn E1)
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E1)  || fail
    
    run export_group update $project/egtest1 --addInits $CCHost2/$(pwwn E2)
    dumpexportsCC
    run export_group update $project/egtest1 --addInits $CCHost1/$(pwwn E1)
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group delete $project/egtest1
    echoit "Test 1 passed."
}

test2() {
    echoit "VPlex Export Test2 - non cross connected"
    create_nc_volumes
    run export_group create $project egtest2 $VANC1 --type Cluster --clusters $tenant/$NCCluster --volspec $project/$NC1DVol,$project/$NC2DVol,$project/$NC1LVol,$project/$NC2LVol
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    # If LSAN zoning detected, skip test2, as it cannot be tested.
    notInITLs LSAN || {
        echo "LSAN zones detected in test2. Test2 cannot be executed with LSAN zoning. Please reconfigure Network or disable routing in NetworkLite.java. test2 will be skipped." 
        run export_group delete $project/egtest2
        return 0;
    }
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --remVols $project/$NC2DVol,$project/$NC1LVol
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --addVolspec $project/$NC2DVol,$project/$NC1LVol
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --remHosts $NCHost1
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs nc1org || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs nc1org || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs nc1org || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --addHosts $NCHost1
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --remHosts $NCHost2
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    notInITLs nc2org || fail

    run export_group update $project/egtest2 --addHosts $NCHost2
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group update $project/egtest2 --remInits $NCHost2/$(pwwn EA)
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn EA) || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn EA) || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn EA) || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn EA) || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn EA) || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn EA) || fail

    run export_group update $project/egtest2 --remInits $NCHost1/$(pwwn E9)
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E9) || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E9) || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E9) || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E9) || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E9) || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E9) || fail

    run export_group update $project/egtest2 --addInits $NCHost2/$(pwwn EA)
    dumpexportsNC
    run export_group update $project/egtest2 --addInits $NCHost1/$(pwwn E9)
    dumpexportsNC
    getITLs $project/$NC1DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC1DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC2DVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2DVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail
    getITLs $project/$NC1LVol $VPLEXCL1_PORTID_GREP
    inITLs nc1org  || fail
    getITLs $project/$NC2LVol $VPLEXCL2_PORTID_GREP
    inITLs nc2org || fail

    run export_group delete $project/egtest2
    echoit "Test 2 passed."
}

test3() {
    echoit "VPlex Export Test3 - non cross connected / VMAX mix"
    create_nc_volumes
    create_noha_volumes
    run export_group create $project egtest3 $VANC2 --type Cluster --clusters $tenant/$NCCluster --volspec $project/$NC1DVol,$project/$NC2DVol,$project/$NC1LVol,$project/$NC2LVol,$project/$NC2NoHAVol
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --remInits $NCHost1/$(pwwn E8)
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --addInits $NCHost1/$(pwwn E8)
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --remInits $NCHost1/$(pwwn E9)
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --addInits $NCHost1/$(pwwn E9)
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --remVols $project/$NC1DVol,$project/$NC1LVol
    dumpexportsNC
    dumpexportsNoHA
    run export_group update $project/egtest3 --addVolspec $project/$NC1DVol,$project/$NC1LVol
    dumpexportsNC
    dumpexportsNoHA
    run export_group delete $project/egtest3
    echoit "Test 3 passed."
}

test4() {
    echoit "VPlex Export Test4 - duplication of volumes across Export Groups"
    create_cc_volumes
    run export_group create $project egtest4a $VACC1 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DVol,$project/$CC2DVol,$project/$CC1LVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group create $project egtest4b $VACC2 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DVol,$project/$CC2DVol,$project/$CC2LVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group update $project/egtest4a --remVols $project/$CC1DVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group update $project/egtest4b --remVols $project/$CC2DVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group update $project/egtest4a --remVols $project/$CC2DVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group update $project/egtest4b --remVols $project/$CC1DVol
    dumpexportsCC
    getITLs $project/$CC1DVol $VPLEXCL1_PORTID_GREP
    notInITLs cc1org cc2org  || fail
    getITLs $project/$CC1DVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2LVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail

    run export_group delete $project/egtest4a
    run export_group delete $project/egtest4b
    echoit "Test 4 passed."
}

test5() {
    echoit "VPlex Export Test5 - test export on HA side"
    create_cc_volumes

# Do export group create from Cluster1. Note that distributed volumes are exported
# from both clusters, except the NCCE volumes, which has the autoCrossConnectExport flag
# set to false in their Vpools, causing them to only be exported from Cluster1.
    run export_group create $project egtest5 $VACC1 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DNCCEVol,$project/$CC2DNCCEVol
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs cc1org cc2org || fail

# Now do an export from cluster 2 and see that even the NCCE volumes are exported from both clusters.
    run export_group create $project egtest5b $VACC2 --type Cluster --clusters $tenant/$CCCluster --volspec $project/$CC1DVol,$project/$CC2DVol,$project/$CC1DNCCEVol,$project/$CC2DNCCEVol
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    
    run export_group update $project/egtest5 --addVolspec $project/$CC2DVol,$project/$CC1LVol
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    
    run export_group update $project/egtest5 --remHosts $CCHost1
    run export_group update $project/egtest5b --remHosts $CCHost1
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org  || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc2org || fail
    notInITLs cc1org  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc2org || fail
    notInITLs cc1org  || fail
    
    run export_group update $project/egtest5 --addHosts $CCHost1
    run export_group update $project/egtest5b --addHosts $CCHost1
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs cc1org cc2org || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs cc1org cc2org || fail
    
    run export_group update $project/egtest5 --remInits $CCHost2/$(pwwn E2)
    run export_group update $project/egtest5b --remInits $CCHost2/$(pwwn E2)
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    notInITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    notInITLs $(pwwn E2)  || fail

    run export_group update $project/egtest5 --addInits $CCHost2/$(pwwn E2)
    run export_group update $project/egtest5b --addInits $CCHost2/$(pwwn E2)
    dumpexportsCC
    getITLs $project/$CC2DVol $VPLEXCL1_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DVol $VPLEXCL2_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC1LVol $VPLEXCL1_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC1DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL1_PORTID_GREP
    inITLs $(pwwn E2)  || fail
    getITLs $project/$CC2DNCCEVol $VPLEXCL2_PORTID_GREP
    inITLs $(pwwn E2)  || fail

    run export_group delete $project/egtest5
    echoit "Test 5 passed."
}

vplexexport_test() {
    didcleanup=0
    # Setup operations
    vplexexport_setup
    setup_virtual_pools
    # Create volumes 
    for cmd in ${ARGV}
    do
        [ "$cmd" == "test1" ] && test1;
        [ "$cmd" == "test2" ] && test2;
        [ "$cmd" == "test3" ] && test3;
        [ "$cmd" == "test4" ] && test4;
        [ "$cmd" == "test5" ] && test5;
        if [ "$cmd" == "cleanup" ]
        then
            cleanup
            didcleanup=1
        fi
    done
    if [ $didcleanup -eq 0 ]
    then
        echoit "No cleanup performed... remember to cleanup later or remove volumes/exports"
    fi
}
vplexexport_test

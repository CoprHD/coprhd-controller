#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

test_host_add_initiator() {
    echot "Add Initiator to Host Test"
    expname=${EXPORT_GROUP_NAME}t1
    item=${RANDOM}
    cfs="ExportGroup ExportMask Initiator"
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi

    verify_export ${expname}1 ${HOST1} gone

    test_pwwn=`randwwn`
    test_nwwn=`randwwn`

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

    # Verify the initiator does not exist in the ExportGroup
    runcmd export_group show $PROJECT/${expname}1 | grep ${test_pwwn} > /dev/null
    if [ $? -eq 0 ]; then
        echo "Add initiator to host test failed. Initiator "${test_pwwn}" already exists" 
        cleanup
        finish
    else
        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}

        # Add initiator to host cluster
        runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}

        # Verify the initiator does exists in the ExportGroup
        runcmd export_group show $PROJECT/${expname}1 | grep ${test_pwwn} > /dev/null
        if [ $? -ne 0 ]; then
            echo "Add initiator to host test passsed. Initiator "${test_pwwn}" has been added to export"
        else
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
            cleanup
            finish
        fi

        # Remove the shared export
        runcmd export_group delete $PROJECT/${expname}1

        verify_export ${expname}1 ${HOST1} gone
    fi
}

test_vcenter_event() {
    echot "vCenter Event Test: Export to cluster, move host into cluster, rediscover vCenter, approve event"
    expname=${EXPORT_GROUP_NAME}t2
    item=${RANDOM}
    cfs="ExportGroup ExportMask"
    mkdir -p results/${item}

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --clusters "emcworld/cluster-1"

    # move hosts21 into cluster-1
    remove_host_from_cluster "host21" "cluster-2"
    add_host_to_cluster "host21" "cluster-1"
    discover_vcenter "vcenter1"

    approve_pending_event

    # Remove the shared export
    runcmd export_group delete ${PROJECT}/${expname}1

    # Snap the DB again
    snap_db 2 ${cfs}

    # Validate nothing was left behind
    validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone

    # Set vCenter back to previous state
    remove_host_from_cluster "host21" "cluster-1"
    add_host_to_cluster "host21" "cluster-2"
    discover_vcenter "vcenter1"
}


# Helper methods for tests
add_host_to_cluster() {
    host=$1
    cluster=$2
    args="addHostTo $cluster $host"
    echo "Adding host $host to $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

remove_host_from_cluster() {
    host=$1
    cluster=$2
    args="removeHostFrom $cluster $host"
    echo "Removing host $host from $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

discover_vcenter() {
    vcenter=$1
    echo "Discovering vCenter $vcenter"
    vcenter discover $vcenter
    sleep 60
}

approve_pending_event() {
    EVENT_ID=$(events list emcworld | grep pending | awk '{print $1}')

    if [ -z "$EVENT_ID" ]
    then
      echo "No event found. Test failure."
      exit;
    fi

    echo "Approving event $EVENT_ID"
    events approve $EVENT_ID
    sleep 30
}

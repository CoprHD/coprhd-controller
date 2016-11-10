#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

test_1_host_add_initiator() {
    echot "test 1 - Add Initiator to Host"
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
    else
        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}

        # Add initiator to host cluster
        runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}

        # Verify the initiator does exists in the ExportGroup
        runcmd export_group show $PROJECT/${expname}1 | grep ${test_pwwn} > /dev/null
        if [ $? -ne 0 ]; then
            echo "Verified that initiator "${test_pwwn}" has been added to export"
        else
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
        fi

        runcmd initiator delete ${HOST1}/${test_pwwn}
        runcmd run transportzone remove ${FC_ZONE_A} ${test_pwwn}
    fi

    # Remove the shared export
    runcmd export_group delete $PROJECT/${expname}1

    verify_export ${expname}1 ${HOST1} gone
}

test_2_host_add_initiator() {
    echot "test 2 - Add Initiator to Host"
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


# Test Host Remove Initiator
#
# 1. Add manual host
# 2. Add 4 initiators to the host
# 3. Add these 4 initiators to the network assigned to your virtual array
# 4. Create and export a volume to this host
# 5. Verify that all 4 initiators are in the export group for this host
# 6. Remove 2 initiators from the host
# 7. Monitor the export group update task and verify the export group contains only 2 initiators when complete
# 8. Clean up
test_host_remove_initiator() {
    echot "Test host_remove_initiator Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_005_BlockDeviceController.createVolumes_before_device_create \
                               failure_006_BlockDeviceController.createVolumes_after_device_create \
                               failure_004_final_step_in_workflow_complete:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete \
                               failure_004_final_step_in_workflow_complete:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete"

    if [ "${SS}" = "vplex" ]; then
        storage_failure_injections="failure_007_NetworkDeviceController.zoneExportRemoveVolumes_before_unzone \
                                    failure_008_NetworkDeviceController.zoneExportRemoveVolumes_after_unzone \
                                    failure_009_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_before_operation \
                                    failure_010_VPlexVmaxMaskingOrchestrator.createOrAddVolumesToExportMask_after_operation"
    fi

    if [ "${SS}" = "vnx" -o "${SS}" = "vmax2" -o "${SS}" = "vmax3" ]
    then
    storage_failure_injections="failure_015_SmisCommandHelper.invokeMethod_createVolume \
                                    failure_011_VNXVMAX_Post_Placement_outside_trycatch \
                                    failure_012_VNXVMAX_Post_Placement_inside_trycatch"
    fi

    failure_injections="${common_failure_injections} ${storage_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_015"

    #######

    column_family="Volume ExportGroup ExportMask"
    random_number=${RANDOM}
    mkdir -p results/${random_number}
    volume=${VOLNAME}-${random_number}
    host=fakehost${random_number}
    exportgroup=exportgroup${random_number}
    
    # Snap DB
    snap_db 1 ${column_family}
        
    # Create new random WWNs for nodes and initiators
    node1=`randwwn 20 C1`
    node2=`randwwn 20 C2`
    node3=`randwwn 20 C3`
    node4=`randwwn 20 C4`
    init1=`randwwn 10 C1`
    init2=`randwwn 10 C2`
    init3=`randwwn 10 C3`
    init4=`randwwn 10 C4`
    
    # Add initator WWNs to the network
    run transportzone add $NH/${FC_ZONE_A} ${init1}
    run transportzone add $NH/${FC_ZONE_A} ${init2}
    run transportzone add $NH/${FC_ZONE_A} ${init3}
    run transportzone add $NH/${FC_ZONE_A} ${init4}
        
    # Create fake host
    runcmd hosts create $host $TENANT Other ${host}.lss.emc.com --port 1
    # runcmd hosts create $host $TENANT Other ${host}.lss.emc.com --port 8111 --username user --password 'password' --osversion 1.0 --cluster ${tenant}/${cluster}
    
    # Create new initators and add to fakehost
    runcmd initiator create $host FC ${init1} --node ${node1}
    runcmd initiator create $host FC ${init2} --node ${node2}
    runcmd initiator create $host FC ${init3} --node ${node3}
    runcmd initiator create $host FC ${init4} --node ${node4}

    # Create volume
    runcmd volume create ${volume} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
    
    # Zzzzzz
    sleep 2
    
    # Export the volume to the fake host    
    runcmd export_group create $PROJECT ${exportgroup} $NH --type Host --volspec ${PROJECT}/${volume} --hosts "${host}"
    
    # Double check the export group to ensure the initiators are present
    foundinit1=`export_group show $PROJECT/${exportgroup} | grep ${init1}`
    foundinit2=`export_group show $PROJECT/${exportgroup} | grep ${init2}`
    foundinit3=`export_group show $PROJECT/${exportgroup} | grep ${init3}`
    foundinit4=`export_group show $PROJECT/${exportgroup} | grep ${init4}`    
    
    if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
        # Fail, those initiators should have been added to the export group
        echo "+++ FAIL - Some initiators were not found on the export group...fail."
        exit 1
    else
        echo "+++ SUCCESS - All initiators from host present on export group"   
    fi
            
    # Zzzzzz
    sleep 2
    
    # Delete two initiators from the host...these should now be auto-removed from the export group 
    runcmd initiator delete ${host}/${init1}
    runcmd initiator delete ${host}/${init2}
    
    # Zzzzzz
    sleep 5
    
    # Ensure that initiator 1 and 2 have been removed
    foundinit1=`export_group show $PROJECT/${exportgroup} | grep ${init1}`
    foundinit2=`export_group show $PROJECT/${exportgroup} | grep ${init2}`
    foundinit3=`export_group show $PROJECT/${exportgroup} | grep ${init3}`
    foundinit4=`export_group show $PROJECT/${exportgroup} | grep ${init4}`

    if [[ "${foundinit1}" != "" || "${foundinit2}" != "" ]]; then
        # Fail, those initiators 1 and 2 should be removed and initiators 3 and 4 should still be present
        echo "+++ FAIL - Expected host initiators were not removed from the export group."
        exit 1
    else
        echo "+++ SUCCESS - All expected host initiators removed from export group" 
    fi
    
    # Cleanup    
    # 1. Unexport the volume
    # 2. Delete the volume
    # 3. Delete the export group
    # 4. Delete the host initiators
    # 5. Delete the host
    runcmd export_group update ${PROJECT}/${exportgroup} --remVols ${PROJECT}/${volume}
    runcmd volume delete ${PROJECT}/${volume} --wait
    runcmd export_group delete ${PROJECT}/${exportgroup}    
    runcmd initiator delete ${host}/${init3}
    runcmd initiator delete ${host}/${init4}
    runcmd hosts delete ${host}
    
    # Snap DB
    snap_db 2 ${column_family}
    
    # Validate DB
    validate_db 1 2 ${column_family}
}

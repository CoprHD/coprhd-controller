#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

# Test - Host Add Initiator
#
# Happy path test for add initiator to a host that is part of an exclusive and shared export group.
#
# 1. Export volume to an exclusive export group
# 2. Export volume to a shared export group
# 3. Add initiator port wwn to network
# 4. Add initiator to host
# 5. Delete exclusive export group
# 6. Delete shared export group
# 7. Delete initiator
# 8. Remove initiator port wwn from network
#
test_host_add_initiator() {
    echot "Add initiator to host"
    expname=${EXPORT_GROUP_NAME}t1
    item=${RANDOM}
    cfs="ExportGroup ExportMask Initiator Network"
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi

    snap_db 1 ${cfs}

    test_pwwn=`randwwn`
    test_nwwn=`randwwn`

    exclusive_export=${expname}1_exclusive
    cluster_export=${expname}1_cluster

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${CLUSTER} gone

    # Run the exclusive export group create command
    runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

    # Run the cluster export group create command
    runcmd export_group create $PROJECT ${cluster_export} $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-2 --clusters ${TENANT}/${CLUSTER}

    # Verify the initiator does not exist in the ExportGroup
    exclusive_init_test=`runcmd export_group show $PROJECT/${exclusive_export} | grep ${test_pwwn}`
    cluser_init_test=`runcmd export_group show $PROJECT/${cluster_export} | grep ${test_pwwn}`

    add_init="false"
    if [[ "${exclusive_init_test}" = "0" && "${cluster_init_test}" = "0" ]]; then
        echo "Add initiator to host test failed. Initiator "${test_pwwn}" already exists" 
    else
        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}

        # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups
        runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}

        # Verify the initiator does exists in the ExportGroup
        exclusive_init_test=`runcmd export_group show $PROJECT/${exclusive_export} | grep ${test_pwwn}`
        cluser_init_test=`runcmd export_group show $PROJECT/${cluster_export} | grep ${test_pwwn}`

        if [[ "${exclusive_init_test}" != "0" && "${cluster_init_test}" != "0" ]]; then
            add_init="true"
            echo "Verified that initiator "${test_pwwn}" has been added to export"
        else
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
        fi
    fi

    # Remove the exclusive export
    runcmd export_group delete $PROJECT/${exclusive_export}
    # Remove the shared export
    runcmd export_group delete $PROJECT/${cluster_export}

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${HOST1} gone

    if [ ${add_init} = "true"  ]; then
        runcmd initiator delete ${HOST1}/${test_pwwn}
        runcmd run transportzone remove ${FC_ZONE_A} ${test_pwwn}
    fi

    snap_db 2 ${cfs}  

    # Validate that nothing was left behind
    validate_db 1 2 ${cfs}          
}

# Test - Host Add Initiator Failure
#
# Happy path test for add initiator to a host that is part of an exclusive and shared export group.
#
# 1. Export volume to an exclusive export group
# 2. Export volume to a shared export group
# 3. Add initiator port wwn to network
# 4. Add initiator to host
# 5. Delete exclusive export group
# 6. Delete shared export group
# 7. Delete initiator
# 8. Remove initiator port wwn from network
#
test_host_add_initiator_failure() {
    echot "Add initiator to host with failure"
    expname=${EXPORT_GROUP_NAME}t1
    item=${RANDOM}
    expname=${EXPORT_GROUP_NAME}t1
    
    common_failure_injections="failure_004_final_step_in_workflow_complete"
    export_failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update \
                               failure_002_host_export_ComputeSystemControllerImpl.updateExportGroup_after_update"
    
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi
    
    snap_db 1 ${cfs}
    
    test_pwwn=`randwwn`
    test_nwwn=`randwwn`

    exclusive_export=${expname}1_exclusive
    cluster_export=${expname}1_cluster

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${CLUSTER} gone
    
    failure_injections="${common_failure_injections} ${export_failure_injections}"
    
    for failure in ${failure_injections}
    do
        secho "Running Add initiator to host with failure scenario: ${failure}..."
        
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
        
        # Check the state before exporting volumes
        snap_db 2 ${cfs}
        
        # Run the exclusive export group create command
        runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

        # Run the cluster export group create command
        runcmd export_group create $PROJECT ${cluster_export} $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-2 --clusters ${TENANT}/${CLUSTER}

        # Verify the initiator does not exist in the ExportGroup
        exclusive_init_test=`runcmd export_group show $PROJECT/${exclusive_export} | grep ${test_pwwn}`
        cluser_init_test=`runcmd export_group show $PROJECT/${cluster_export} | grep ${test_pwwn}`

        add_init="false"
        if [[ "${exclusive_init_test}" = "0" && "${cluster_init_test}" = "0" ]]; then
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" already exists" 
        else
            # Add initiator to network
            runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}
    
            # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups
            fail initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}
    
            # Let the async jobs calm down
            sleep 5
    
            # Perform any DB validation in here
            snap_db 3 ${cfs}

            # Validate nothing was left behind
            validate_db 2 3 ${cfs}

            # Rerun the command
            set_artificial_failure none
            runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}
    
            # Verify the initiator does exists in the ExportGroup
            exclusive_init_test=`runcmd export_group show $PROJECT/${exclusive_export} | grep ${test_pwwn}`
            cluser_init_test=`runcmd export_group show $PROJECT/${cluster_export} | grep ${test_pwwn}`

            if [[ "${exclusive_init_test}" != "0" && "${cluster_init_test}" != "0" ]]; then
                add_init="true"
                echo "Verified that initiator "${test_pwwn}" has been added to export"
            else
                echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
            fi
        fi

        # Remove the exclusive export
        runcmd export_group delete $PROJECT/${exclusive_export}
        # Remove the shared export
        runcmd export_group delete $PROJECT/${cluster_export}

        verify_export ${exclusive_export} ${HOST1} gone
        verify_export ${cluster_export} ${HOST1} gone
        
        if [ ${add_init} = "true"  ]; then
            runcmd initiator delete ${HOST1}/${test_pwwn}
            runcmd run transportzone remove ${FC_ZONE_A} ${test_pwwn}
        fi
        
        snap_db 4 ${cfs}  

        # Validate that nothing was left behind
        validate_db 1 4 ${cfs}
    done
}

test_vcenter_event() {
    echot "vCenter Event Test: Export to cluster, move host into cluster, rediscover vCenter, approve event"
    expname=${EXPORT_GROUP_NAME}t2
    item=${RANDOM}
    cfs="ExportGroup ExportMask Host Initiator Cluster"
    mkdir -p results/${item}
    set_controller_cs_discovery_refresh_interval 1

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --clusters "emcworld/cluster-1"

    # move hosts21 into cluster-1
    remove_host_from_cluster "host21" "cluster-2"
    add_host_to_cluster "host21" "cluster-1"
    discover_vcenter "vcenter1"

    EVENT_ID=$(get_pending_event)
    if [ -z "$EVENT_ID" ]
    then
      echo "FAILED. Expected an event"
    else
      approve_pending_event $EVENT_ID 
    fi

    # Remove the shared export
    runcmd export_group delete ${PROJECT}/${expname}1
    
    # Set vCenter back to previous state
    remove_host_from_cluster "host21" "cluster-1"
    add_host_to_cluster "host21" "cluster-2"
    discover_vcenter "vcenter1"

    # Snap the DB again
    snap_db 2 ${cfs}

    # Validate nothing was left behind
    validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone
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
}

get_pending_event() {
    echo $(events list emcworld | grep pending | awk '{print $1}')
} 

approve_pending_event() {
    echo "Approving event $1"
    events approve $1
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
                               failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update \
                               failure_002_host_export_ComputeSystemControllerImpl.updateExportGroup_after_update"


    #failure_injections="${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update"

    for failure in ${failure_injections}
    do
        secho "Running host_remove_initiator with failure scenario: ${failure}..."
        
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
            # Fail, initiators should have been added to the export group
            echo "+++ FAIL - Some initiators were not found on the export group...fail."
            exit 1
        else
            echo "+++ SUCCESS - All initiators from host present on export group"   
        fi
                
        # Zzzzzz
        sleep 2
        
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
        
        # Try and remove an initiator from the host, this should fail during updateExport()
        fail initiator delete ${host}/${init1}
        
        # Rerun the command
        set_artificial_failure none 
               
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
            # Fail, initiators 1 and 2 should be removed and initiators 3 and 4 should still be present
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
    done
}

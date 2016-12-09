#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

HAPPY_PATH_TEST_INJECTION="happy_path_test_injection"

HOST_TEST_CASES="test_host_add_initiator test_vcenter_event test_host_remove_initiator test_move_clustered_host_to_another_cluster test_move_non_clustered_host_to_cluster test_cluster_remove_host"

get_host_cluster() {
    tenant=$1
    hostname=$2
    clusterid=`hosts list ${tenant} | grep ${hostname} | awk '{print $5}'`
    echo `cluster list ${tenant} | grep ${clusterid} | awk '{print $1}'`
}

# Test - Host Add Initiator
#
# Happy path/failure test for add initiator to a host that is part of an exclusive and shared export group.
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
    echot "Test test_host_add_initiator_failure"
    
    test_name="test_host_add_initiator"
    cfs="ExportGroup ExportMask Initiator"
    host=fakehost-${RANDOM}
    fake_pwwn1=`randwwn`
    fake_nwwn1=`randwwn` 
    volume=${VOLNAME}-2        
            
    common_failure_injections="failure_004_final_step_in_workflow_complete"
    host_cluster_failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections} ${host_cluster_failure_injections}"
    
    for failure in ${failure_injections}
    do
        if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
            echot "Running happy path test for add initiator to host..."
        else    
            echot "Running Add initiator to host with failure scenario: ${failure}..."
        fi    
        
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        item=${RANDOM}
        mkdir -p results/${item}
        exclusive_export=fakeexport-${item}

        snap_db 1 ${cfs}

        # Run the exclusive export group create command
        runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${volume} --hosts ${HOST1}

        snap_db 2 ${cfs}        

        # Verify the initiator does not exist in the ExportGroup
        add_init="false"
        if [[ $(export_contains ${PROJECT}/$exclusive_export $fake_pwwn1) ]]; then
            echo "Add initiator to host test failed. Initiator "${fake_pwwn1}" already exists" 
            
            # Report results
            incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi  

        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${fake_pwwn1}
    
        # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups. This is because
        # The host is already part of the cluster that was used to create the cluster export group.
        if [ ${failure} = ${HAPPY_PATH_TEST_INJECTION} ]; then
            # If this is the happy path test, the command should succeed
            runcmd initiator create ${HOST1} FC ${fake_pwwn1} --node ${fake_nwwn1}
        else
            # Turn on failure at a specific point
            set_artificial_failure ${failure}            
            
            fail initiator create ${HOST1} FC ${fake_pwwn1} --node ${fake_nwwn1}
                
            # Let the async jobs calm down
            sleep 5
    
            # Perform any DB validation in here
            snap_db 3 ${cfs}

            # Validate nothing was left behind
            validate_db 2 3 ${cfs}
                
            # Rerun the command
            set_artificial_failure none
            runcmd initiator create ${HOST1} FC ${fake_pwwn1} --node ${fake_nwwn1}                
        fi
    
        # Verify the initiator has been added to the ExportGroup
        if [[ $(export_contains ${PROJECT}/$exclusive_export $fake_pwwn1) ]]; then
            add_init="true"
            echo "Verified that initiator "${fake_pwwn1}" has been added to export"
        else
            echo "Add initiator to host test failed. Initiator "${fake_pwwn1}" was not added to the export"  
		
		    # Report results
		    incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi

        if [ ${add_init} = "true" ]; then
            # If the initiator has been added, remove it here so the export groups are clean for the next failure injection
            runcmd initiator delete ${HOST1}/${fake_pwwn1}
            sleep 20
            runcmd transportzone remove ${FC_ZONE_A} ${fake_pwwn1}
            sleep 20
        fi
        
        # Cleanup export group  
        runcmd export_group update ${PROJECT}/${exclusive_export} --remVols ${PROJECT}/${volume}                         
        runcmd export_group delete ${PROJECT}/${exclusive_export}
        
        snap_db 4 ${cfs}  

        # Validate that nothing was left behind
        validate_db 1 4 ${cfs}

	   # Report results
	   report_results ${test_name} ${failure}
    done
}

test_vcenter_event() {
    test_name="test_vcenter_event"
    failure="only_one_test"
    echot "vCenter Event Test: Export to cluster, move host into cluster, rediscover vCenter, approve event"
    TEST_OUTPUT_FILE=test_output_${RANDOM}.log
    reset_counts
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
      finish -1
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

    # Report results
    report_results ${test_name} ${failure}
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
    test_name="test_host_remove_initiator"
    echot "Test host_remove_initiator Begins"

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                                failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"

    # Create volume
    random_number=${RANDOM}    
    volume1=${VOLNAME}-${random_number}
    runcmd volume create ${volume1} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB

    for failure in ${failure_injections}
    do
        echot "Running host_remove_initiator with failure scenario: ${failure}..."
        
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask"
        random_number=${RANDOM}
        mkdir -p results/${random_number}
        host1=fakehost1-${random_number}
        host2=fakehost2-${random_number}
        cluster1=fakecluster1-${random_number}
        exportgroup1=exportgroup1-${random_number}
        exportgroup2=exportgroup2-${random_number}
        
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
        
         # Create fake cluster
        runcmd cluster create ${cluster1} $TENANT
            
        # Create fake hosts and add them to cluster
        runcmd hosts create $host1 $TENANT Other ${host1}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}
        runcmd hosts create $host2 $TENANT Other ${host2}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}       
        
        # Create new initators and add to fakehost
        runcmd initiator create $host1 FC ${init1} --node ${node1}
        runcmd initiator create $host1 FC ${init2} --node ${node2}
        runcmd initiator create $host2 FC ${init3} --node ${node3}
        runcmd initiator create $host2 FC ${init4} --node ${node4}
    
        # Zzzzzz
        sleep 2
        
        # Export the volume to an exlusive (aka Host) export for host1
        runcmd export_group create ${PROJECT} ${exportgroup2} $NH --type Host --volspec ${PROJECT}/${volume1} --hosts "${host1}"
        # Export the volume to a shared (aka Cluster) export for cluster1   
        runcmd export_group create ${PROJECT} ${exportgroup1} $NH --type Cluster --volspec ${PROJECT}/${volume1} --clusters ${TENANT}/${cluster1}        
                                
        # List of all export groups being used
        exportgroups="${PROJECT}/${exportgroup1} ${PROJECT}/${exportgroup2}"
        
        for eg in ${exportgroups}
        do
            # Double check export group to ensure the hosts and initiators are present
            foundinit1=`export_group show ${eg} | grep ${init1}`
            foundinit2=`export_group show ${eg} | grep ${init2}`            
            foundhost1=`export_group show ${eg} | grep ${host1}`            
            
            if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundhost1}" = "" ]]; then
                # Fail, hosts and initiators should have been added to the export group
                echo "+++ FAIL - Some hosts and host initiators were not found on ${eg}...fail."
                # Report results
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                    exit 1
                fi
            else
                echo "+++ SUCCESS - All hosts and host initiators present on ${eg}"   
            fi
        done
                                    
        
        if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then
            # Turn on failure at a specific point
            set_artificial_failure ${failure}
        
            # Try and remove an initiator from the host, this should fail during updateExport()
            fail initiator delete ${host1}/${init1}
        fi
 
        # Zzzzzz
        secho "Sleeping for 5..."
        sleep 5
        
        # Rerun the command
        set_artificial_failure none 
               
        # Delete initiator from the host...it should now be auto-removed from the export groups 
        runcmd initiator delete ${host1}/${init1}
        
        # Zzzzzz
        secho "Sleeping for 5..."
        sleep 5

        for eg in ${exportgroups}
        do
            # Ensure that initiator 1 has been removed
            foundinit1=`export_group show ${eg} | grep ${init1}`            
            
            if [[ "${foundinit1}" != "" ]]; then
                # Fail, initiator 1 should be removed
                echo "+++ FAIL - Expected host initiators were not removed from the export group ${eg}."
                # Report results
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                    exit 1
                fi
            else
                echo "+++ SUCCESS - Expected host initiators removed from export group ${eg}." 
            fi                                     
        done
            
        # Cleanup export groups  
        runcmd export_group update ${PROJECT}/${exportgroup1} --remVols ${PROJECT}/${volume1}
        runcmd export_group delete ${PROJECT}/${exportgroup1}            
        runcmd export_group update ${PROJECT}/${exportgroup2} --remVols ${PROJECT}/${volume1}                                     
        runcmd export_group delete ${PROJECT}/${exportgroup2}
        
        # Cleanup everything else
        runcmd cluster delete ${TENANT}/${cluster1}
        runcmd initiator delete ${host1}/${init1}
        runcmd initiator delete ${host1}/${init2}
        runcmd initiator delete ${host2}/${init3}
        runcmd initiator delete ${host2}/${init4}
        runcmd hosts delete ${host1}
        runcmd hosts delete ${host2}
        
        # Snap DB
        snap_db 2 ${column_family}
        
        # Validate DB
        validate_db 1 2 ${column_family}

        # Report results
        report_results ${test_name} ${failure}
    done
    
    # Cleanup volumes
    runcmd volume delete ${PROJECT}/${volume1} --wait
}

test_move_clustered_host_to_another_cluster() {
    test_name="test_move_clustered_host_to_another_cluster"
    echot "Test test_move_clustered_host_to_another_cluster Begins"
        
    common_failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update"

    failure_injections="${HAPPY_PATH_TEST_INJECTION}" # ${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    # failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update"

    # Create volumes
    random_number=${RANDOM}        
    volume1=${VOLNAME}-1
    volume2=${VOLNAME}-2
        
    for failure in ${failure_injections}
    do
        secho "Running test_move_clustered_host_to_another_cluster with failure scenario: ${failure}..."
    
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask"
        random_number=${RANDOM}
        mkdir -p results/${random_number}
        volume1=${VOLNAME}-1
        volume2=${VOLNAME}-2
        
        host1=fakehost-1-${random_number}
        host2=fakehost-2-${random_number}
        cluster1=fakecluster-1-${random_number}
        cluster2=fakecluster-2-${random_number}
        
        exportgroup1=exportgroup-1-${random_number}
        exportgroup2=exportgroup-2-${random_number}
        
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
            
        # Create fake clusters
        runcmd cluster create ${cluster1} $TENANT
        runcmd cluster create ${cluster2} $TENANT
        
        # Create fake hosts
        runcmd hosts create ${host1} $TENANT Other ${host1}.lss.emc.com --port 1 --cluster $TENANT/${cluster1}
        runcmd hosts create ${host2} $TENANT Other ${host2}.lss.emc.com --port 1 --cluster $TENANT/${cluster2}
        
        # Create new initators and add to fakehosts
        runcmd initiator create ${host1} FC ${init1} --node ${node1}
        runcmd initiator create ${host1} FC ${init2} --node ${node2}
        runcmd initiator create ${host2} FC ${init3} --node ${node3}
        runcmd initiator create ${host2} FC ${init4} --node ${node4}
    
        # Export the volumes to the fake clusters    
        runcmd export_group create $PROJECT ${exportgroup1} $NH --type Cluster --volspec ${PROJECT}/${volume1} --clusters ${TENANT}/${cluster1}
        runcmd export_group create $PROJECT ${exportgroup2} $NH --type Cluster --volspec ${PROJECT}/${volume2} --clusters ${TENANT}/${cluster2}
        
        # Double check the export groups to ensure the initiators are present
        foundinit1=`export_group show $PROJECT/${exportgroup1} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup1} | grep ${init2}`
        foundinit3=`export_group show $PROJECT/${exportgroup2} | grep ${init3}`
        foundinit4=`export_group show $PROJECT/${exportgroup2} | grep ${init4}`
        
        if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
            # Fail, initiators should have been added to the export group
            echo "+++ FAIL - Some initiators were not found on the export groups...fail."
    	    # Report results
        	incr_fail_count
        	if [ "${NO_BAILING}" != "1" ]
        	then
        	    report_results ${test_name} ${failure}
                    finish -1
        	fi
        else
            echo "+++ SUCCESS - All initiators from clusters present on export group"   
        fi
        
        if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
            secho "Running happy path test for move clustered host to another cluster..."
            # Move host1 into cluster2
            runcmd hosts update $host1 --cluster ${TENANT}/${cluster2}
        else    
            secho "Running move clustered host to another cluster with failure scenario: ${failure}..."
            # Turn on failure at a specific point
            set_artificial_failure ${failure}
            fail hosts update $host1 --cluster ${TENANT}/${cluster2}
        fi
        
 
        # Failure checks go here.
        # 1. Host still belongs to old cluster
        # 2. Initiators not moved over
        if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then
        
           cluster=`get_host_cluster "emcworld" ${host1}`
           if [[ "${cluster}" != "${cluster1}" ]]; then
                echo "+++ FAIL - Host should belong to old cluster ${cluster1}...fail."
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                        finish -1
                fi
           fi
           
           # we expect an error here
           # Ensure that all initiators are now in same cluster
            foundinit1=`export_group show $PROJECT/${exportgroup1} | grep ${init1}`
            foundinit2=`export_group show $PROJECT/${exportgroup1} | grep ${init2}`
            foundinit3=`export_group show $PROJECT/${exportgroup2} | grep ${init3}`
            foundinit4=`export_group show $PROJECT/${exportgroup2} | grep ${init4}`
        
            if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
                # Fail, initiators should have been added to the export group
                echo "+++ FAIL - Some initiators were not found  in export group ${exportgroup2}...fail."
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                        finish -1
                fi
            else
                echo "+++ SUCCESS - All initiators from clusters present on export group ${exportgroup2}"   
            fi

           set_artificial_failure none
           
           runcmd hosts update $host1 --cluster ${TENANT}/${cluster2}
           sleep 60

        fi
        
        cluster=`get_host_cluster "emcworld" ${host1}`
        if [[ "${cluster}" != "${cluster2}" ]]; then
            echo "+++ FAIL - Host should belong to new cluster ${cluster2}...fail."
            incr_fail_count
            if [ "${NO_BAILING}" != "1" ]
            then
                report_results ${test_name} ${failure}
                    finish -1
            fi
        fi
        
        # Ensure that all initiators are now in same cluster
        foundinit1=`export_group show $PROJECT/${exportgroup2} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup2} | grep ${init2}`
        foundinit3=`export_group show $PROJECT/${exportgroup2} | grep ${init3}`
        foundinit4=`export_group show $PROJECT/${exportgroup2} | grep ${init4}`
    
        if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
            # Fail, initiators should have been added to the export group
            echo "+++ FAIL - Some initiators were not found  in export group ${exportgroup2}...fail."
        	incr_fail_count
        	if [ "${NO_BAILING}" != "1" ]
        	then
        	    report_results ${test_name} ${failure}
                    finish -1
        	fi
        else
            echo "+++ SUCCESS - All initiators from clusters present on export group ${exportgroup2}"   
        fi
    
        # The other export group should be deleted    
        fail export_group show $PROJECT/${exportgroup1}   
        
        runcmd export_group delete $PROJECT/${exportgroup2}
        
        # Snap DB
        snap_db 2 ${column_family}
    
        # Validate DB
        validate_db 1 2 ${column_family}

        # Report results
        report_results ${test_name} ${failure}
    done    
}


# Test - Move Non Clustered Host to Cluster
#
# Happy path test for manually moving (through API) a non-clustered host to a cluster.
#
# 1. Create host
# 2. Create initiator
# 3. Export volume to an exclusive export group for host
# 4. Export a second volume to a cluster export group
# 5. Host update to move the host to the cluster export group
# 6. Delete exclusive export group
# 7. Delete cluster export group
# 8. Delete host
# 9. Remove initiator port wwn from network
#
test_move_non_clustered_host_to_cluster() {
    test_name="test_move_non_clustered_host_to_cluster"
    failure="only_one_test"
    echot "Test test_move_non_clustered_host_to_cluster"
    random_number=${RANDOM}
    host1=fakehost1-${random_number}
    host2=fakehost2-${random_number}
    project1=${PROJECT}
    project2=fakeproject-${random_number}
    volume1=${VOLNAME}-1
    volume2=${VOLNAME}-2-${random_number}  
    cluster1=fakecluster-${random_number}
    
    cfs="ExportGroup ExportMask Network Host Initiator"

    host_cluster_failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update \
                                failure_032_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostAndInitiator&1 \
                                failure_033_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostVcenter&1"
    common_failure_injections="failure_004_final_step_in_workflow_complete"
    
    fake_pwwn1=`randwwn`
    fake_nwwn1=`randwwn`
    fake_pwwn2=`randwwn`
    fake_nwwn2=`randwwn`

    # Create a second project
    runcmd project create $project2 --tenant $TENANT 

    # Add initator WWNs to the network
    runcmd transportzone add $NH/${FC_ZONE_A} ${fake_pwwn1}
    runcmd transportzone add $NH/${FC_ZONE_A} ${fake_pwwn2}
            
    # Create fake hosts
    runcmd hosts create $host1 $TENANT Esx ${host1}.lss.emc.com --port 1
    runcmd hosts create $host2 $TENANT Esx ${host2}.lss.emc.com --port 1
            
    # Create new initators and add to fake hosts
    runcmd initiator create $host1 FC ${fake_pwwn1} --node ${fake_nwwn1}
    runcmd initiator create $host2 FC ${fake_pwwn2} --node ${fake_nwwn2}
    
    # Create the fake cluster
    runcmd cluster create ${cluster1} $TENANT    

    # Create a second volume for the new project
    runcmd volume create ${volume2} ${project2} ${NH} ${VPOOL_BASE} 1GB

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections} ${host_cluster_failure_injections}"

    for failure in ${failure_injections}
    do
        if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
            echot "Running happy path test for move non-clustered host to cluster..."
        else    
            echot "Running move non-clustered host to cluster with failure scenario: ${failure}..."
        fi    
        
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        item=${RANDOM}
        mkdir -p results/${item}
        cluster1_export1=clusterexport1-${item}
        cluster1_export2=clusterexport2-${item}

        snap_db 1 ${cfs}

        # Run the cluster export group create command
        runcmd export_group create $project1 ${cluster1_export1} $NH --type Cluster --volspec ${project1}/${volume1} --clusters ${TENANT}/${cluster1}
        runcmd export_group create $project2 ${cluster1_export2} $NH --type Cluster --volspec ${project2}/${volume2} --clusters ${TENANT}/${cluster1}

        snap_db 2 ${cfs}

        move_host="false"
        if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
            # Move the hosts to cluster - adds hosts to both ExportGroups           
            runcmd hosts update ${host1} --cluster ${TENANT}/${cluster1}
            runcmd hosts update ${host2} --cluster ${TENANT}/${cluster1}
        else    
            # Turn failure injection off
            set_artificial_failure ${failure}

            # Move the host to the cluster
            fail hosts update ${host1} --cluster ${TENANT}/${cluster1} 
    
            # Snap the DB after rollback
            snap_db 3 ${cfs}

            # Validate nothing was left behind
            validate_db 2 3 ${cfs}
                
            # Rerun the command
            set_artificial_failure none
            
            # Add the first host to the cluster 
            runcmd hosts update ${host1} --cluster ${TENANT}/${cluster1}

            snap_db 4 ${cfs}

            # Turn on failure again
            set_artificial_failure ${failure}

            # Move the second host to the cluster
            fail hosts update ${host2} --cluster ${TENANT}/${cluster1}

            snap_db 5 ${cfs}

            # Validate nothing was left behind
            validate_db 4 5 ${cfs}

            # Turn failure injection off
            set_artificial_failure none

            # Retry move the second host to cluster
            runcmd hosts update ${host2} --cluster ${TENANT}/${cluster1}          
        fi

        if [[ $(export_contains $project1/$cluster1_export1 $host1) && $(export_contains $project2/$cluster1_export2 $host1) && $(export_contains $project1/$cluster1_export1 $host2) && $(export_contains $project2/$cluster1_export2 $host2) ]]; then
            move_host="true"
            echo "Host" ${host1} "and" ${host2} "have been successfully moved to cluster" ${cluster1}
        else
            echo "Failed to move host" ${host1} "and" ${host2} "to cluster" ${cluster1}  
            
            # Report results
            incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi    

        if [ ${move_host} = "true"  ]; then
            # Also removes the export groups/mask
            runcmd hosts update ${host1} --cluster null
            runcmd hosts update ${host2} --cluster null
        fi
        
        snap_db 6 ${cfs}  

        # Validate that nothing was left behind
        validate_db 1 6 ${cfs}          

        # Report results
        report_results ${test_name} ${failure}
    done
}

# Searches an ExportGroup for a given value. Returns 0 if the value is found,
# 1 if the value is not found.
# usage: if [ $(export_contains $export_group_name $search_value) ]; then
# export_name should be composed of project/export_name    
export_contains() {
    export_name=$1
    search_value=$2
   
    export_group show ${export_name} | grep ${search_value}
}

# Test Cluster Remove Host
#
# 1. Create 2 hosts and initiators for those hosts.
# 2. Create a cluster and update both hosts to put them into the cluster.
# 3. Export a volume to this cluster (cluster export group is created)
# 4. Update a host to remove it from this cluster.
# 5. This host should no longer be in the cluster's export group.
# 6. Clean up
test_cluster_remove_host() {
    test_name="test_cluster_remove_host"
    echot "Test cluster_remove_host Begins"
    
    # Turn off validation, shouldn't need to do this but until we have
    # all the updates for export simplification it may be a necessary
    # evil.
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                                failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update \
                                failure_027_host_cluster_ComputeSystemControllerImpl.deleteExportGroup_before_delete \
                                failure_028_host_cluster_ComputeSystemControllerImpl.deleteExportGroup_after_delete \
                                failure_032_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostAndInitiator&1 \
                                failure_033_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostVcenter&1 \
                                failure_042_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences"

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="${HAPPY_PATH_TEST_INJECTION}"    

    # Create volumes
    random_number=${RANDOM}        
    
    # Create a second project
    PROJECT2=${PROJECT}-${RANDOM}
    runcmd project create ${PROJECT2} --tenant $TENANT
    
    # Create two volumes
    volume1=${VOLNAME}-1-${random_number}
    volume2=${VOLNAME}-2-${random_number}    
    runcmd volume create ${volume1} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
    runcmd volume create ${volume2} ${PROJECT2} ${NH} ${VPOOL_BASE} 1GB    
        
    for failure in ${failure_injections}
    do
        echot "Running cluster_remove_host with failure scenario: ${failure}..."
        
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask"
        random_number=${RANDOM}
        mkdir -p results/${random_number}
        host1=fakehost1-${random_number}
        host2=fakehost2-${random_number}
        cluster1=fakecluster1-${random_number}
        exportgroup1=exportgroup1-${random_number}
        exportgroup2=exportgroup2-${random_number}        
        
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
        
         # Create fake cluster
        runcmd cluster create ${cluster1} $TENANT
            
        # Create fake hosts and add them to cluster
        runcmd hosts create $host1 $TENANT Other ${host1}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}
        runcmd hosts create $host2 $TENANT Other ${host2}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}       
        
        # Create new initators and add to fakehost
        runcmd initiator create $host1 FC ${init1} --node ${node1}
        runcmd initiator create $host1 FC ${init2} --node ${node2}
        runcmd initiator create $host2 FC ${init3} --node ${node3}
        runcmd initiator create $host2 FC ${init4} --node ${node4}
    
        # Zzzzzz
        sleep 2
        
        # Export the volume to the fake cluster    
        runcmd export_group create ${PROJECT} ${exportgroup1} $NH --type Cluster --volspec ${PROJECT}/${volume1} --clusters ${TENANT}/${cluster1}
        runcmd export_group create ${PROJECT2} ${exportgroup2} $NH --type Cluster --volspec ${PROJECT2}/${volume2} --clusters ${TENANT}/${cluster1}
        
        # List of all export groups being used
        exportgroups="${PROJECT}/${exportgroup1} ${PROJECT2}/${exportgroup2}"
        
        for eg in ${exportgroups}
        do
            # Double check export group to ensure the hosts and initiators are present
            foundinit1=`export_group show ${eg} | grep ${init1}`
            foundinit2=`export_group show ${eg} | grep ${init2}`
            foundinit3=`export_group show ${eg} | grep ${init3}`
            foundinit4=`export_group show ${eg} | grep ${init4}`
            foundhost1=`export_group show ${eg} | grep ${host1}`
            foundhost2=`export_group show ${eg} | grep ${host2}`
            
            if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" || "${foundhost1}" = "" || "${foundhost2}" = "" ]]; then
                # Fail, hosts and initiators should have been added to the export group
                echo "+++ FAIL - Some hosts and host initiators were not found on ${eg}...fail."
                # Report results
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                    exit 1
                fi
            else
                echo "+++ SUCCESS - All hosts and host initiators present on ${eg}"   
            fi
        done
            
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
                        
        if [[ "${failure}" == *"deleteExportGroup"* ]]; then
            # Delete export group
            secho "Delete export group path..."
            
            # Try and remove both hosts from cluster, first should pass and second should fail
            runcmd hosts update $host1 --cluster null
            
            # Happy path would mean there is no fail, otherwise we should expect a failure
            if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then            
                fail hosts update $host2 --cluster null
            fi
        
            # Rerun the command with no failures
            set_artificial_failure none 
            runcmd hosts update $host2 --cluster null
            
            # Zzzzzz
            sleep 5
            
            for eg in ${exportgroups}
            do
                # Ensure that export group has been removed
                fail export_group show ${eg}
                
                echo "+++ Confirm export group ${eg} has been deleted, expect to see an exception below if it has..."
                foundeg=`export_group show ${eg} | grep ${eg}`
                
                if [ "${foundeg}" != "" ]; then
                    # Fail, export group should be removed
                    echo "+++ FAIL - Expected export group ${eg} was not deleted."
                    # Report results
                    incr_fail_count
                    if [ "${NO_BAILING}" != "1" ]
                    then
                        report_results ${test_name} ${failure}
                        exit 1
                    fi
                else
                    echo "+++ SUCCESS - Expected export group ${eg} was deleted." 
                fi
            done
                        
        else
            # Update export group
            secho "Update export group path..."
            
            # Happy path would mean there is no fail, otherwise we should expect a failure
            if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then
                # Try and remove one host from cluster, this should fail
                fail hosts update $host1 --cluster null
            fi
        
            # Rerun the command with no failures
            set_artificial_failure none 
            runcmd hosts update $host1 --cluster null
            
            # Zzzzzz
            sleep 5
            
            for eg in ${exportgroups}
            do
                # Ensure that initiator 1 and 2 and host1 have been removed
                foundinit1=`export_group show ${eg} | grep ${init1}`
                foundinit2=`export_group show ${eg} | grep ${init2}`
                foundhost1=`export_group show ${eg} | grep ${host1}`
                
                if [[ "${foundinit1}" != "" || "${foundinit2}" != "" || "${foundhost1}" != "" ]]; then
                    # Fail, initiators 1 and 2 and host1 should be removed and initiators 3 and 4 should still be present
                    echo "+++ FAIL - Expected host and host initiators were not removed from the export group ${eg}."
                    # Report results
                    incr_fail_count
                    if [ "${NO_BAILING}" != "1" ]
                    then
                        report_results ${test_name} ${failure}
                        exit 1
                    fi
                else
                    echo "+++ SUCCESS - Expected host and host initiators removed from export group ${eg}." 
                fi                                     
            done
            
            # Cleanup export groups  
            runcmd export_group update ${PROJECT}/${exportgroup1} --remVols ${PROJECT}/${volume1}
            runcmd export_group delete ${PROJECT}/${exportgroup1}            
            runcmd export_group update ${PROJECT2}/${exportgroup2} --remVols ${PROJECT2}/${volume2}                                     
            runcmd export_group delete ${PROJECT2}/${exportgroup2}
        fi    
        
        # Cleanup all
        runcmd cluster delete ${TENANT}/${cluster1}
        runcmd initiator delete ${host1}/${init1}
        runcmd initiator delete ${host1}/${init2}
        runcmd initiator delete ${host2}/${init3}
        runcmd initiator delete ${host2}/${init4}
        runcmd hosts delete ${host1}
        runcmd hosts delete ${host2}
        
        # Snap DB
        snap_db 2 ${column_family}
        
        # Validate DB
        validate_db 1 2 ${column_family}

        # Report results
    	report_results ${test_name} ${failure}
    done
    
     # Cleanup volumes
    runcmd volume delete ${PROJECT}/${volume1} --wait
    runcmd volume delete ${PROJECT2}/${volume2} --wait 
    runcmd project delete ${PROJECT2}
}

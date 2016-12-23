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

create_volume_and_datastore() {
    # tenant volname datastorename varray vpool project vcenter datacenter cluster
    tenant=$1
    volname=$2
    datastorename=$3

    virtualarray=`neighborhood list | grep ${4} | awk '{print $3}'`
    virtualpool=`cos list block | grep ${5} | awk '{print $3}'`
    project=`project list --tenant ${tenant} | grep "${6} " | awk '{print $4}'`
 
    vcenter=`vcenter list ${tenant} | grep ${7} | awk '{print $5}'`
    datacenter=`datacenter list ${7} | grep ${8} | awk '{print $4}'`
    cluster=`cluster list ${tenant} | grep ${9} | awk '{print $4}'`
    
    echo "=== catalog order CreateVolumeandDatastore ${tenant} project=${project},name=${volname},virtualPool=${virtualpool},virtualArray=${virtualarray},host=${cluster},datastoreName=${datastorename},size=1,vcenter=${vcenter},datacenter=${datacenter}"
    echo `catalog order CreateVolumeandDatastore ${tenant} project=${project},name=${volname},virtualPool=${virtualpool},virtualArray=${virtualarray},host=${cluster},datastoreName=${datastorename},size=1,vcenter=${vcenter},datacenter=${datacenter}`
}

delete_datastore_and_volume() {
    # tenant datastorename vcenter datacenter cluster
    tenant=$1   
    datastorename=$2

    vcenter=`vcenter list ${tenant} | grep ${3} | awk '{print $5}'`
    datacenter=`datacenter list ${3} | grep ${4} | awk '{print $4}'`
    cluster=`cluster list ${tenant} | grep ${5} | awk '{print $4}'`
    
    echo "=== catalog order DeleteDatastoreandVolume ${tenant} host=${cluster},datastoreName=${datastorename},vcenter=${vcenter},datacenter=${datacenter}"
    echo `catalog order DeleteDatastoreandVolume ${tenant} host=${cluster},datastoreName=${datastorename},vcenter=${vcenter},datacenter=${datacenter}`
}

create_datastore() {
    # tenant volname datastorename project vcenter datacenter cluster
    tenant=$1   
    volume=`volume list ${4} | grep ${2} | awk '{print $7}'`
    datastorename=$3
    project=`project list --tenant ${tenant} | grep "${4} " | awk '{print $4}'`
    vcenter=`vcenter list ${tenant} | grep ${5} | awk '{print $5}'`
    datacenter=`datacenter list ${5} | grep ${6} | awk '{print $4}'`
    cluster=`cluster list ${tenant} | grep ${7} | awk '{print $4}'`    
    
    echo "=== catalog order CreateVMwareDatastore ${tenant} host=${cluster},volumes=${volume},datastoreName=${datastorename},project=${project},vcenter=${vcenter},datacenter=${datacenter}"
    echo `catalog order CreateVMwareDatastore ${tenant} host=${cluster},volumes=${volume},datastoreName=${datastorename},project=${project},vcenter=${vcenter},datacenter=${datacenter}`
}

delete_datastore() {
    # tenant datastorename vcenter datacenter cluster
    tenant=$1   
    datastorename=$2
    vcenter=`vcenter list ${tenant} | grep ${3} | awk '{print $5}'`
    datacenter=`datacenter list ${3} | grep ${4} | awk '{print $4}'`
    cluster=`cluster list ${tenant} | grep ${5} | awk '{print $4}'`    
    
    echo "=== catalog order DeleteVMwareDatastore ${tenant} host=${cluster},datastoreName=${datastorename},vcenter=${vcenter},datacenter=${datacenter}"
    echo `catalog order DeleteVMwareDatastore ${tenant} host=${cluster},datastoreName=${datastorename},vcenter=${vcenter},datacenter=${datacenter}`
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
    random_number=${RANDOM}
    fake_pwwn1=`randwwn`
    fake_nwwn1=`randwwn`
    fake_pwwn2=`randwwn`
    fake_nwwn2=`randwwn`
    fake_pwwn3=`randwwn`
    fake_nwwn3=`randwwn`
    fake_pwwn4=`randwwn`
    fake_nwwn4=`randwwn`
    volume1=${VOLNAME}-2
    volume2=${VOLNAME}-${random_number}       
    project1=${PROJECT}
    project2=fakeproject-${random_number}
    host1=fakehost1-${random_number}
    host2=fakehost2-${random_number}          
    cluster1=fakecluster-${random_number}              
                            
    common_failure_injections="failure_004_final_step_in_workflow_complete"
    host_cluster_failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections} ${host_cluster_failure_injections}"
    
    # Create a second project
    runcmd project create $project2 --tenant $TENANT
    
    # Create the fake cluster
    runcmd cluster create ${cluster1} $TENANT
    
    # Create a second volume for the new project
    runcmd volume create ${volume2} ${project2} ${NH} ${VPOOL_BASE} 1GB
    
    # Add initiator to network
    runcmd run transportzone add ${FC_ZONE_A} ${fake_pwwn1}
    runcmd run transportzone add ${FC_ZONE_A} ${fake_pwwn2}
    
    # Create fake hosts
    runcmd hosts create $host1 $TENANT Esx ${host1}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}
    runcmd hosts create $host2 $TENANT Esx ${host2}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}
    
    # Create new initators and add to fake hosts
    runcmd initiator create $host1 FC ${fake_pwwn1} --node ${fake_nwwn1}
    runcmd initiator create $host2 FC ${fake_pwwn2} --node ${fake_nwwn2}
    
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
        cluster1_export1=fakeclusterexport1-${item}                    
        cluster1_export2=fakeclusterexport2-${item}

        # Snap the DB
        snap_db 1 ${cfs}

        # Create 2 ExportGroups for the same cluster but each using a different project.
        runcmd export_group create $project1 ${cluster1_export1} $NH --type Cluster --volspec ${project1}/${volume1} --clusters ${TENANT}/${cluster1}
        runcmd export_group create $project2 ${cluster1_export2} $NH --type Cluster --volspec ${project2}/${volume2} --clusters ${TENANT}/${cluster1}
        
        # Snap the DB
        snap_db 2 ${cfs}        

        # Verify the initiator does not exist in the ExportGroup
        add_init="false"
        if [[ $(export_contains ${project1}/$cluster1_export1 $fake_pwwn3) || $(export_contains ${project1}/$cluster1_export1 $fake_pwwn4) || $(export_contains ${project2}/$cluster1_export2 $fake_pwwn3) || $(export_contains ${project2}/$cluster1_export2 $fake_pwwn4) ]]; then
            echo "Add initiator to host test failed. Initiator "${fake_pwwn3}" and/or "${fake_pwwn4}" already exists" 
            
            # Report results
            incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi  

        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${fake_pwwn3}
        runcmd run transportzone add ${FC_ZONE_A} ${fake_pwwn4}
    
        # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups. This is because
        # The host is already part of the cluster that was used to create the cluster export group.
        if [ ${failure} = ${HAPPY_PATH_TEST_INJECTION} ]; then
            # If this is the happy path test, the command should succeed
            runcmd initiator create ${host1} FC ${fake_pwwn3} --node ${fake_nwwn3}
            runcmd initiator create ${host2} FC ${fake_pwwn4} --node ${fake_nwwn4}
        else
            # Turn on failure at a specific point
            set_artificial_failure ${failure}            
            
            fail initiator create ${host1} FC ${fake_pwwn3} --node ${fake_nwwn3}           

            # Snap the DB
            snap_db 3 ${cfs}

            # Validate nothing was left behind
            validate_db 2 3 ${cfs}
                
            # Rerun the command
            set_artificial_failure none
            # Add the initiator for host1
            runcmd initiator create ${host1} FC ${fake_pwwn3} --node ${fake_nwwn3}
            
            # Snap the DB
            snap_db 4 ${cfs}
            
            # Turn failure back on
            set_artificial_failure ${failure}
            
            # Fail while adding initiator for host2 
            fail initiator create ${host2} FC ${fake_pwwn4} --node ${fake_nwwn4} 
            
            # Snap the DB
            snap_db 5 ${cfs}
            
            # Validate nothing was left behind
            validate_db 4 5 ${cfs}
            
            # Rerun the command
            set_artificial_failure none
            # Add the host2 initiator
            runcmd initiator create ${host2} FC ${fake_pwwn4} --node ${fake_nwwn4}                                           
        fi
    
        # Verify the initiators have been added to both ExportGroups
        if [[ $(export_contains $project1/$cluster1_export1 $fake_pwwn3) && $(export_contains $project1/$cluster1_export1 $fake_pwwn4) && $(export_contains $project2/$cluster1_export2 $fake_pwwn3) && $(export_contains $project2/$cluster1_export2 $fake_pwwn4) ]]; then
            add_init="true"
            echo "Verified that initiators "${fake_pwwn3}" and/or "${fake_pwwn4}" have been added to export"
        else
            echo "Add initiator to host test failed. Initiators "${fake_pwwn3}" and/or "${fake_pwwn4}" were not added to the export"  
		
		    # Report results
		    incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi

        if [ ${add_init} = "true" ]; then
            # If the initiator has been added, remove it here so the export groups are clean for the next failure injection
            runcmd initiator delete ${host1}/${fake_pwwn3}
            runcmd initiator delete ${host2}/${fake_pwwn4}
            runcmd transportzone remove ${FC_ZONE_A} ${fake_pwwn3}
            runcmd transportzone remove ${FC_ZONE_A} ${fake_pwwn4}
            sleep 20
        fi
        
        # Cleanup export group  
        runcmd export_group update ${project1}/${cluster1_export1} --remVols ${project1}/${volume1}
        runcmd export_group update ${project2}/${cluster1_export2} --remVols ${project2}/${volume2}                         
        runcmd export_group delete ${project1}/${cluster1_export1}
        runcmd export_group delete ${project2}/${cluster1_export2}
        
        snap_db 6 ${cfs}  

        # Validate that nothing was left behind
        validate_db 1 6 ${cfs}

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

change_host_cluster() {
    host=$1
    from_cluster=$2
    to_cluster=$3
    vcenter=$4
    remove_host_from_cluster $host $from_cluster
    add_host_to_cluster $host $to_cluster
    discover_vcenter $vcenter
}

get_pending_event() {
    echo $(events list emcworld | grep pending | awk '{print $1}')
} 

get_failed_event() {
    echo $(events list emcworld | grep failed | awk '{print $1}')
}  

approve_pending_event() {
    echo "Approving event $1"
    events approve $1
}


# Test Host Remove Initiator
#
# 1. Create 2 volumes
# 2. Create 2 hosts with 2 initiators each
# 3. Create a cluster
# 4. Add both hosts to cluster
# 5. Export vol1 to host1 (exclusive export)
# 6. Export vol2 to cluster1 (shared export)
# 7. Remove 1 initiator from host1
# 8. Expect that both export groups should be updated and have that initiator removed
# 9. Clean up
test_host_remove_initiator() {
    test_name="test_host_remove_initiator"
    echot "Test host_remove_initiator Begins"
    
    # Turn off validation, shouldn't need to do this but until we have
    # all the updates for export simplification it may be a necessary
    # evil.
    secho "Turning ViPR validation temporarily OFF (needed for now)"
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                                failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update"    
    
    random_number=${RANDOM}
        
    # Create two volumes
    volume1=${VOLNAME}-1-${random_number}
    volume2=${VOLNAME}-2-${random_number}    
    runcmd volume create ${volume1} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
    runcmd volume create ${volume2} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB

    for failure in ${failure_injections}
    do
        echot "Running host_remove_initiator with failure scenario: ${failure}..."
        
        random_number=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask"
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
        runcmd export_group create ${PROJECT} ${exportgroup1} $NH --type Host --volspec ${PROJECT}/${volume1} --hosts "${host1}"
        # Export the volume to a shared (aka Cluster) export for cluster1   
        runcmd export_group create ${PROJECT} ${exportgroup2} $NH --type Cluster --volspec ${PROJECT}/${volume2} --clusters ${TENANT}/${cluster1}        
                                
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
        runcmd export_group update ${PROJECT}/${exportgroup2} --remVols ${PROJECT}/${volume2}                                     
        runcmd export_group delete ${PROJECT}/${exportgroup2}
        
        # Cleanup everything else
        runcmd cluster delete ${TENANT}/${cluster1}        
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
    runcmd volume delete ${PROJECT}/${volume2} --wait
    
    # Turn off validation back on
    secho "Turning ViPR validation ON"
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check true
}

test_move_clustered_host_to_another_cluster() {
    test_name="test_move_clustered_host_to_another_cluster"
    echot "Test test_move_clustered_host_to_another_cluster Begins"
        
    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_027_host_cluster_ComputeSystemControllerImpl.deleteExportGroup_before_delete \
                               failure_042_host_cluster_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences" 

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections}"

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

# Test - move clustered discovered host to another cluster
#
# Test for moving a clustered discovered host to another cluster.
#
# 1. Create volumes and datastores for cluster1 and cluster2
# 2. Move pre-existing cluster2 host to cluster1
# 3. Move host back to cluster2
# 4. Delete exports
# 5. Delete datastores and volumes
#
test_move_clustered_discovered_host_to_cluster() {
    test_name="test_move_non_clustered_host_to_cluster"
    failure="only_one_test"
    echot "Test test_move_non_clustered_host_to_cluster"
    cluster1="cluster-1"
    cluster2="cluster-2"
    host="host21"
    vcenter="vcenter1"
    random_num=${RANDOM}
    volume1=fakevolume1-${random_num}
    volume2=fakevolume2-${random_num}
    cluster1_export=cluster-1
    cluster2_export=cluster-2
    datastore1=fakedatastore1-${random_num}
    datastore2=fakedatastore2-${random_num}    
    set_controller_cs_discovery_refresh_interval 1
    cfs="ExportGroup ExportMask Network Host Initiator"

    run syssvc $SANITY_CONFIG_FILE localhost set_prop system_proxyuser_encpassword $SYSADMIN_PASSWORD

    host_cluster_failure_injections="failure_026_host_cluster_ComputeSystemControllerImpl.updateExportGroup_before_update \
                                     failure_029_host_cluster_ComputeSystemControllerImpl.verifyDatastore_after_verify \
                                     failure_030_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_unmount \
                                     failure_031_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_detach"
    common_failure_injections="failure_004_final_step_in_workflow_complete"

    item=${RANDOM}
    mkdir -p results/${item}

    snap_db 1 ${cfs}

    create_volume_and_datastore ${TENANT} ${volume1} ${datastore1} ${NH} ${VPOOL_BASE} ${PROJECT} ${vcenter} "DC-Simulator-1" ${cluster1}
    create_volume_and_datastore ${TENANT} ${volume2} ${datastore2} ${NH} ${VPOOL_BASE} ${PROJECT} ${vcenter} "DC-Simulator-1" ${cluster2}

    failure_injections="${HAPPY_PATH_TEST_INJECTION} ${host_cluster_failure_injections} ${common_failure_injections}" 

    for failure in ${failure_injections}
    do
        if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
            echot "Running happy path test for move non-clustered host to cluster..."
        else    
            echot "Running move non-clustered host to cluster with failure scenario: ${failure}..."
        fi    
        
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        move_host="false"

        # Move the host from cluster-2 into cluster-1
        change_host_cluster $host $cluster2 $cluster1 $vcenter           

        sleep 20

        EVENT_ID=$(get_pending_event)
        if [ -z "$EVENT_ID" ]; then
            echo "FAILED. Expected an event"
            # Move the host into cluster-1           
            change_host_cluster $host $cluster1 $cluster2 $vcenter
            EVENT_ID=$(get_pending_event)
            if [ "$EVENT_ID" ]; then
                approve_pending_event $EVENT_ID
            fi    
            finish -1
        else
            if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
                approve_pending_event $EVENT_ID
            else
                # Turn failure injection on
                set_artificial_failure ${failure}
                fail approve_pending_event $EVENT_ID

                # Verify that rollback moved the host back to cluster2
                cluster=`get_host_cluster "emcworld" ${host}`
                if [[ "${cluster}" != "${cluster2}" ]]; then
                    echo "+++ FAIL - Host should belong to old cluster ${cluster2}...fail."
                    incr_fail_count
                    if [ "${NO_BAILING}" != "1" ]; then
                        report_results ${test_name} ${failure}
                        finish -1
                    fi
                else
                    echo "Host has successfully been moved to cluster ${cluster2} on rollback."                    
                fi
           
                EVENT_ID=$(get_failed_event)    
                # turn failure injection off and retry the approval
                set_artificial_failure none
                approve_pending_event $EVENT_ID
                
                # Verify that the host has been moved to cluster1
                cluster=`get_host_cluster "emcworld" ${host}`
                if [[ "${cluster}" != "${cluster1}" ]]; then
                    echo "+++ FAIL - Host should belong to old cluster ${cluster1}...fail."
                    incr_fail_count
                    if [ "${NO_BAILING}" != "1" ]; then
                        report_results ${test_name} ${failure}
                        finish -1
                    fi
                else
                    echo "Host has successfully been moved to cluster ${cluster1}." 
                fi
            fi 
        fi        
        
        if [[ $(export_contains ${PROJECT}/$cluster1_export $host) && $(export_contains ${PROJECT}/$cluster2_export $host) == "" ]]; then
            move_host="true"
            echo "Host" ${host} "has been successfully moved to cluster" ${cluster2}
        else
            echo "Failed to move host" ${host} "to cluster" ${cluster2}  
            
            # Report results
            incr_fail_count
            if [ "${NO_BAILING}" != "1" ]; then
                report_results ${test_name} ${failure}
                finish -1
            fi
        fi    

        if [ ${move_host} = "true"  ]; then
            # Move the host into cluster-1           
            change_host_cluster $host $cluster1 $cluster2 $vcenter 
            
            sleep 20
            
            EVENT_ID=$(get_pending_event)
            if [ -z "$EVENT_ID" ]; then
                finish -1
            else
                approve_pending_event $EVENT_ID
            fi                  
        fi   

        # Report results
        report_results ${test_name} ${failure}
    done
    
    # Cleanup exports
    runcmd export_group update ${PROJECT}/${cluster1_export} --remVols ${PROJECT}/${volume1}
    runcmd export_group delete ${PROJECT}/${cluster1_export} 
    runcmd export_group update ${PROJECT}/${cluster2_export} --remVols ${PROJECT}/${volume2}
    runcmd export_group delete ${PROJECT}/${cluster2_export}     
    
    delete_datastore_and_volume ${TENANT} ${datastore1} ${vcenter} "DC-Simulator-1" ${cluster1}
    delete_datastore_and_volume ${TENANT} ${datastore2} ${vcenter} "DC-Simulator-1" ${cluster2}  
    
    snap_db 2 ${cfs}  

    # Validate that nothing was left behind
    validate_db 1 2 ${cfs}
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
    secho "Turning ViPR validation temporarily OFF (needed for now)"
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
        
        random_number=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask"        
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
    
    # Turn off validation back on
    secho "Turning ViPR validation ON"
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check true
}

# Test Cluster Remove Discovered Host

test_cluster_remove_discovered_host() {
    test_name="test_cluster_remove_discovered_host"
    echot "Test cluster_remove_discovered_host Begins"
    
    # Catalog needs this password set
    syssvc $SANITY_CONFIG_FILE localhost set_prop system_proxyuser_encpassword "ChangeMe1!"
     
    # Turn off validation, shouldn't need to do this but until we have
    # all the updates for export simplification it may be a necessary
    # evil.    
    secho "Turning ViPR validation temporarily OFF (needed for now)"
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check false
    
    # Allows discover to be run every 1 sec, needed for the scripts (default is every 60 sec)
    set_controller_cs_discovery_refresh_interval 1

    common_failure_injections="failure_004_final_step_in_workflow_complete \
                                     failure_029_host_cluster_ComputeSystemControllerImpl.verifyDatastore_after_verify \
                                     failure_030_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_unmount \
                                     failure_031_host_cluster_ComputeSystemControllerImpl.unmountAndDetach_after_detach"
        
    #failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    failure_injections="failure_029_host_cluster_ComputeSystemControllerImpl.verifyDatastore_after_verify"    
       
    # Real™ hosts/clusters/vcenters/datacenters provisioned during setup
    hostpostfix=".sim.emc.com"
    host1="host11"
    host2="host12"
    host3="host21"
    host4="host22"
    cluster1="cluster-1"
    cluster2="cluster-2"
    vcenter="vcenter1"
    datacenter="DC-Simulator-1"
    
#    port1=`initiator list ${host1}${hostpostfix} | awk '{print($1)}'`
#    port2=`initiator list ${host1}${hostpostfix} | awk '{print($2)}'`
#    port3=`initiator list ${host1}${hostpostfix} | awk '{print($3)}'`
#    port4=`initiator list ${host1}${hostpostfix} | awk '{print($4)}'`
#    port5=`initiator list ${host1}${hostpostfix} | awk '/^rbd:/ {print($5)}'`
#
#    echo ${port1}
#    echo "+++"
#    echo ${port2}
#    echo "+++"
#    echo ${port3}
#    echo "+++"
#    echo ${port4}
#    echo "+++"
#    echo ${port5}
#    echo "+++"
#    exit 1
    
    random_number=${RANDOM}        
    
    # Create a second project
    PROJECT2=${PROJECT}-${RANDOM}
    runcmd project create ${PROJECT2} --tenant $TENANT
    
    # Create two volumes and datastores
    volume1=${VOLNAME}-1-${random_number}
    volume2=${VOLNAME}-2-${random_number}
    datastore1="fakedatastore1"-${random_number}
    datastore2="fakedatastore2"-${random_number}
    create_volume_and_datastore $TENANT ${volume1} ${datastore1} $NH $VPOOL_BASE ${PROJECT} ${vcenter} ${datacenter} ${cluster1}
    #create_volume_and_datastore $TENANT ${volume2} ${datastore2} $NH $VPOOL_BASE ${PROJECT2} ${vcenter} ${datacenter} ${cluster1}
    
    # Export group name will be auto-generated as the cluster name
    exportgroup=${cluster1}
        
    for failure in ${failure_injections}
    do
        echot "Running cluster_remove_discovered_host with failure scenario: ${failure}..."
        
        random_number=${RANDOM}
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        column_family="Volume ExportGroup ExportMask Host"        
        mkdir -p results/${random_number}       
       
        # Snap DB
        snap_db 1 ${column_family}
                    
        # List of all export groups being used
        #exportgroups="${PROJECT}/${exportgroup} ${PROJECT2}/${exportgroup}"
        exportgroups="${PROJECT}/${exportgroup}"
        
        for eg in ${exportgroups}
        do
            # Double check export group to ensure the hosts are present            
            foundhost1=`export_group show ${eg} | grep ${host1}`
            foundhost2=`export_group show ${eg} | grep ${host2}`
            
            if [[ "${foundhost1}" = "" || "${foundhost2}" = "" ]]; then
                # Fail, hosts should have been added to the export group
                echo "+++ FAIL - Some hosts were not found on export group ${eg}...fail."
                # Report results
                incr_fail_count
                if [ "${NO_BAILING}" != "1" ]
                then
                    report_results ${test_name} ${failure}
                    exit 1
                fi
            else
                echo "+++ SUCCESS - All hosts present on export group ${eg}"   
            fi
        done
                                      
        if [[ "${failure}" == *"deleteExportGroup"* ]]; then
            # Delete export group
            secho "Delete export group path..."
            
#            # Try and remove both hosts from cluster, first should pass and second should fail
#            runcmd hosts update $host1 --cluster null
#            
#            # Happy path would mean there is no fail, otherwise we should expect a failure
#            if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then            
#                fail hosts update $host2 --cluster null
#            fi
#        
#            # Rerun the command with no failures
#            set_artificial_failure none 
#            runcmd hosts update $host2 --cluster null
#            
#            # Zzzzzz
#            sleep 5
#            
#            for eg in ${exportgroups}
#            do
#                # Ensure that export group has been removed
#                fail export_group show ${eg}
#                
#                echo "+++ Confirm export group ${eg} has been deleted, expect to see an exception below if it has..."
#                foundeg=`export_group show ${eg} | grep ${eg}`
#                
#                if [ "${foundeg}" != "" ]; then
#                    # Fail, export group should be removed
#                    echo "+++ FAIL - Expected export group ${eg} was not deleted."
#                    # Report results
#                    incr_fail_count
#                    if [ "${NO_BAILING}" != "1" ]
#                    then
#                        report_results ${test_name} ${failure}
#                        exit 1
#                    fi
#                else
#                    echo "+++ SUCCESS - Expected export group ${eg} was deleted." 
#                fi
#            done
                        
        else
            # Update export group
            secho "Update export group path..."
        
            # Vcenter call to remove host from cluster
            remove_host_from_cluster $host1 $cluster1                        
            
            # Run discover
            discover_vcenter ${vcenter}
            
            # Zzzzzz
            sleep 5
            
            # Find pending events which should be present after the discover
            EVENT_ID=$(get_pending_event)
            if [ -z "$EVENT_ID" ]; then
                echo "+++ FAILED. Expected an event! Re-add host to cluster..."
                add_host_to_cluster $host1 $cluster1
                EVENT_ID=$(get_pending_event)
                if [ -z "$EVENT_ID" ]; then
                    echo "+++ FAILED again! Expected an event for re-add host to cluster. Please check UI."
                else
                    approve_pending_event $EVENT_ID
                fi                
                exit 1
            else
                if [ ${failure} == ${HAPPY_PATH_TEST_INJECTION} ]; then
                    approve_pending_event $EVENT_ID
                else
                    # Turn failure injection on
                    set_artificial_failure ${failure}
                    # Expect to fail when approving the event
                    fail approve_pending_event $EVENT_ID
                    discover_vcenter ${vcenter}
                    sleep 5
                    EVENT_ID=$(get_failed_event)    
                    # Turn failure injection off and retry the approval
                    secho "Re-run with failure injection off..."
                    set_artificial_failure none
                    approve_pending_event $EVENT_ID
                fi 
            fi
            
            for eg in ${exportgroups}
            do
                # Ensure that host1 has been removed                
                foundhost1=`export_group show ${eg} | grep ${host1}`
                
                if [[ "${foundhost1}" != "" ]]; then
                    # Fail, initiators 1 and 2 and host1 should be removed and initiators 3 and 4 should still be present
                    echo "+++ FAIL - Expected host was not removed from export group ${eg}."
                    # Report results
                    incr_fail_count
                    if [ "${NO_BAILING}" != "1" ]
                    then
                        report_results ${test_name} ${failure}
                        exit 1
                    fi
                else
                    echo "+++ SUCCESS - Expected host removed from export group ${eg}." 
                fi                                     
            done
            
            # Add the host back to cluster
            secho "Test complete, add the host back to cluster..."
            add_host_to_cluster $host1 $cluster1                                  
            discover_vcenter ${vcenter}            
            sleep 5            
            EVENT_ID=$(get_pending_event)
            approve_pending_event $EVENT_ID
        fi    
        
        # Snap DB
        snap_db 2 ${column_family}
        
        # Validate DB
        validate_db 1 2 ${column_family}

        # Report results
        report_results ${test_name} ${failure}
    done
    
     # Cleanup volumes
    delete_volume_and_datastore $TENANT ${datastore1} ${vcenter} ${datacenter} ${cluster1}
    #delete_volume_and_datastore $TENANT ${datastore2} ${vcenter} ${datacenter} ${cluster1} 
    runcmd project delete ${PROJECT2}
    
    # Turn off validation back on
    secho "Turning ViPR validation ON"
    syssvc $SANITY_CONFIG_FILE localhost set_prop validation_check true
}

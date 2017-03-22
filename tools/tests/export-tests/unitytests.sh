#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
# run these by executing dutests.sh with one of the test function names below as the test arg
#

# Unity DU Prevention Validation Test 1
#
# Summary: Test remove initiator from one export group
#
# Basic Use Case for single host, single export group
# 1. Create 2 volume, 1 host export
# 2. Create two snapshots, and add snapsots to the export group
# 3. Add a new initiator to the host outside of ViPR
# 4. Remove volume from the export group
#    should fail
# 5. Remove the unknown initiator from the host
# 6. Remove snapshots from the host
#    should success
# 7. Remove first volume from the export group
#    should success
# 8. Remove second volume from the export group
#    should success, export mask get deleted in ViPR DB, and host is deleted from array
# 9. Add unknown initiator to the host again
# 10. Delete the export group, and create 2 volume, 1 host export
# 11. Delete export groups
#     should fail
# 12. Remove the unknown initiator
# 13. Delete export group
#     should success, the host should be deleted
# 14. Create 2 volume, 2 snapshot, 1 host export
# 15. Add a volume to the host outside of ViPR
# 16. Delete export group
#     should success, the host should not be deleted
# 17. Create 2 volume, 2 snapshot, 1 host export
# 18. Remove initiator from the export group
#     should fail due to the unknown volume
# 19. Remove the unknow volume from the host
# 20. Remove an initiator from the export group
#     should success, it will delete the initiator from host
# 21. Remove the last initiator from the export group
#     should success, the export mask is delted in ViPR DB, and the host is deleted from array
# 22. Clean up
#
unity_test_1() {
    echot "Unity Test 1: Export Group update/delete with one export group per host"
    expname=${EXPORT_GROUP_NAME}u1

    # Make sure we start clean; no masking view on the array
    verify_export "ignore" ${HOST1} gone

    # Create export with the 2 volume for one project
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export "ignore" ${HOST1} 2 2

    # Create a snapshot
    label=${RANDOM}
    snap_label1=${label}_1
    snap1=$PROJECT/${VOLNAME}-1/$snap_label1
    snap_label2=${label}_2
    snap2=$PROJECT/${VOLNAME}-3/$snap_label2

    runcmd blocksnapshot create $PROJECT/${VOLNAME}-1 ${snap_label1}
    runcmd blocksnapshot create $PROJECT/${VOLNAME}-3 ${snap_label2}

    runcmd export_group update $PROJECT/${expname}1 --addVolspec "$snap1","$snap2"
    verify_export "ignore" ${HOST1} 2 4

    # Add another initiator to the host
    PWWN=`getwwn`
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 3 4

    # Test remove volume
    fail export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 3 4

    # Now remove the initiator from the host
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export "ignore" ${HOST1} 2 4

    runcmd export_group update $PROJECT/${expname}1 --remVols "$snap1"
    verify_export "ignore" ${HOST1} 2 3

    runcmd export_group update $PROJECT/${expname}1 --remVols "$snap2"
    verify_export "ignore" ${HOST1} 2 2

    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"
    verify_export "ignore" ${HOST1} gone

    # Create the same export group again
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 2 2

    # Add unknown initiator to the host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 3 2

    # Run the export group command. Expect it to fail with validation
    fail export_group delete $PROJECT/${expname}1

    # Verify the mask wasn't touched
    verify_export "ignore" ${HOST1} 3 2

    # Now remove the initiator from the host
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export "ignore" ${HOST1} 2 2

    # Delete export group. Should success, and the host is deleted
    runcmd export_group delete $PROJECT/${expname}1
    verify_export "ignore" ${HOST1} gone

    # Test delete mask with unknown volume
    runcmd export_group create ${PROJECT} ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,$snap1,$snap2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 4

    # Create the volume and inventory-only delete it so we can use it later
    HIJACK=du-hijack-volume-${RANDOM}
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask has the new volume in it
    verify_export "ignore" ${HOST1} 2 5

    runcmd export_group delete $PROJECT/${expname}1
    verify_export "ignore" ${HOST1} 2 1

    # Create the same export group again
    runcmd export_group create ${PROJECT} ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2,$snap1,$snap2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 5

    fail export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 2 5

    # Now remove the volume from the host
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    verify_export "ignore" ${HOST1} 2 4

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 4

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} gone

    # Clean up
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}
    runcmd export_group delete $PROJECT/${expname}1
    runcmd blocksnapshot delete $snap1
    runcmd blocksnapshot delete $snap2
}

# Unity DU Prevention Validation Test 2
#
# Summary: Test export group update/deletion when host has multiple export groups
#
# Basic Use Case for single host, multiple export groups
# 1. Create 2 volume, 1 host export from the first project
# 2. Create 2 volume, 1 host export with the same host from the second project
# 3. Add a new initiator to the host outside of ViPR
# 4. Remove volume from export group 2
#    should fail
# 5. Remove the unknown initiator from the host
# 6. Remove first volume from the export group 2
#    should success
# 7. Remove second volume from the export group 2
#    should success, export mask get deleted in ViPR DB
# 8. Add unknown initiator to the host again
# 9. Delete export group 2, and create 2 volume, 1 host export with the same host from the second project
# 10. Delete export groups
#     should fail
# 11. Remove the unknown initiator
# 12. Delete export group of the second project
#     should success, the other export group should be untouched
# 13. Create 2 volume, 1 host export with the same host from the second project
# 14. Add a volume to the host outside of ViPR
# 15. Delete export group of the second project
#     should success, the other export group should be untouched
# 16. Create 2 volume, 1 host export with the same host from the second project again
# 17. Remove initiator from either export group
#     should fail due to the unknown volume
# 18. Remove the unknow volume from the host
# 19. Remove an initiator from export group 1
#     should success, but it will not delete the initiator from host
# 20. Remove the initiator from the second export group
#     should success, and the initiator is deleted from the host
# 21. Remove the last initiator from export group 1
#     should success, all volumes of the export are unmapped, and the export mask 1 is delted in ViPR DB, but it will not delete the initiator from the host
# 22. Remove the last initiator from the second export group
#     should success, the export mask is deleted from ViPR DB, and the host is deleted from array
# 23. Clean up
#
unity_test_2() {
    echot "Unity Test 2: Export Group update/deletion when host has multiple export groups"
    expname=${EXPORT_GROUP_NAME}u2

    # Make sure we start clean; no masking view on the array
    verify_export "ignore" ${HOST1} gone

    PROJECT2=${PROJECT}2

    isCreated=$(project list --tenant $TENANT | grep ${PROJECT2} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found project $PROJECT2"
    else
        run project create ${PROJECT2} --tenant $TENANT
    fi

    isCreated=$(volume list $PROJECT2 | grep P2${VOLNAME} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found volume in $PROJECT2"
    else
        run volume create P2${VOLNAME} ${PROJECT2} ${NH} ${VPOOL_BASE} 1GB --count 2
    fi

    # Create export with 2 volume for one project
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export "ignore" ${HOST1} 2 2

    # Create export with 2 volume for another project
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1,${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"

    verify_export "ignore" ${HOST1} 2 4

    PWWN=`getwwn`
    # Add another initiator to the host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 3 4

    # Test remove volume
    fail export_group update $PROJECT2/${expname}2 --remVols "${PROJECT2}/P2${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 3 4

    # Now remove the initiator from the host
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export "ignore" ${HOST1} 2 4

    runcmd export_group update $PROJECT2/${expname}2 --remVols "${PROJECT2}/P2${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 2 3

    runcmd export_group update $PROJECT2/${expname}2 --remVols "${PROJECT2}/P2${VOLNAME}-2"
    verify_export "ignore" ${HOST1} 2 2

    # Add unknown initiator to the host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 3 2

    runcmd export_group delete $PROJECT2/${expname}2
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1,${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 3 4

    # Run the export group command. Expect it to fail with validation
    fail export_group delete $PROJECT/${expname}1

    # Verify the mask wasn't touched
    verify_export "ignore" ${HOST1} 3 4

    fail export_group delete $PROJECT2/${expname}2

    # Verify the mask wasn't touched
    verify_export "ignore" ${HOST1} 3 4

    # Now remove the initiator from the host
    arrayhelper remove_initiator_from_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    # Verify the mask is back to normal
    verify_export "ignore" ${HOST1} 2 4

    # Only volumes of the mask will be removed, other volumes will be untouched
    runcmd export_group delete $PROJECT2/${expname}2
    verify_export "ignore" ${HOST1} 2 2

    # Test delete mask with unknown volume
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1,${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 4

    # Create the volume and inventory-only delete it so we can use it later.
    HIJACK=du-hijack-volume-${RANDOM}
    runcmd volume create ${HIJACK} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB --count 1

    # Get the device ID of the volume we created
    device_id=`get_device_id ${PROJECT}/${HIJACK}`

    runcmd volume delete ${PROJECT}/${HIJACK} --vipronly

    # Add the volume to the mask
    arrayhelper add_volume_to_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}

    # Verify the mask has the new volume in it
    verify_export "ignore" ${HOST1} 2 5

    runcmd export_group delete $PROJECT2/${expname}2
    verify_export "ignore" ${HOST1} 2 3

    # Create export group in project 2 again
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1,${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 5

    fail export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 2 5

    fail export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 2 5

    # Now remove the volume from the host
    arrayhelper remove_volume_from_mask ${SERIAL_NUMBER} ${device_id} ${HOST1}
    verify_export "ignore" ${HOST1} 2 4

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 4

    runcmd export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 4

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} gone

    runcmd export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} gone

    # Clean up
    arrayhelper delete_volume ${SERIAL_NUMBER} ${device_id}
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2
    runcmd volume delete --project $PROJECT2 --wait
}

# Unity DU Prevention Validation Test 3
#
# Summary: Test export group update/deletion when host has multiple export groups with cluster and host export types
#
# Basic Use Case for single host, multiple export groups
# 1. Create 1 volume, cluster export from the first project
# 2. Create 1 volume, 1 host export from the first project
# 3. Create 1 volume, cluster export from the second project
# 4. Create 1 volume, 1 host export with the same host from the second project
# 5. Delete one cluster export
# 6. Delete one host export
# 7. Delete anohter cluster export
# 8. Delete another host export
# 9. Create 1 volume, cluster export from the first project
# 10. Create 1 volume, 1 host export from the first project
# 11. Create 1 volume, cluster export from the second project
# 12. Create 1 volume, 1 host export with the same host from the second project
# 13. Remove host1 from one of the cluster exports
#     volumes on both cluster export get unmapped
# 14. Remove the host1 from another cluster export
#     no operation on array
# 15. Add host1 back to one cluster export
# 16. Add host1 back to another cluster export
# 17. Remove one initiator from one of the exclusive exports
#     initiator should be removed on array
# 18. Remove last initiator from the exclusive export
#     host should be deleted
# 19. Delete another exclusive export, and the two cluster exports
#     no operation on array
# 20. Clean up
#
unity_test_3() {
    echot "Unity Test 3: Export Group update/deletion when host has multiple export groups with cluster and host export types"
    expname=${EXPORT_GROUP_NAME}u3

    # Make sure we start clean; no masking view on the array
    verify_export "ignore" ${HOST1} gone

    PROJECT2=${PROJECT}2

    isCreated=$(project list --tenant $TENANT | grep ${PROJECT2} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found project $PROJECT2"
    else
        run project create ${PROJECT2} --tenant $TENANT
    fi

    isCreated=$(volume list $PROJECT2 | grep P2${VOLNAME} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found volume in $PROJECT2"
    else
        run volume create P2${VOLNAME} ${PROJECT2} ${NH} ${VPOOL_BASE} 1GB --count 2
    fi

    # Create cluster export with 1 volume for one project
    runcmd export_group create $PROJECT ${expname}cluster1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster ${TENANT}/${CLUSTER}
    verify_export "ignore" ${HOST1} 2 1
    verify_export "ignore" ${HOST2} 2 1

    # Create host export with 1 volume for the project
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Create cluster export with 1 volume for another project
    runcmd export_group create ${PROJECT2} ${expname}cluster2 $NH --type Cluster --volspec "${PROJECT2}/P2${VOLNAME}-1" --cluster ${TENANT}/${CLUSTER} 
    verify_export "ignore" ${HOST1} 2 3
    verify_export "ignore" ${HOST2} 2 2

    # Create host export with 1 volume for the project
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 4

    # Delete one cluster export
    runcmd export_group delete $PROJECT/${expname}cluster1
    verify_export "ignore" ${HOST1} 2 3
    verify_export "ignore" ${HOST2} 2 1

    # Delete one host export
    runcmd export_group delete $PROJECT/${expname}1
    verify_export "ignore" ${HOST1} 2 2

    # Delete another cluster export
    runcmd export_group delete ${PROJECT2}/${expname}cluster2
    verify_export "ignore" ${HOST1} 2 1
    verify_export "ignore" ${HOST2} gone

    # Delete another host export
    runcmd export_group delete ${PROJECT2}/${expname}2
    verify_export "ignore" ${HOST1} gone

    echo "Sleep 60 seconds"
    sleep 60

    # Create cluster export with 1 volume for one project
    runcmd export_group create $PROJECT ${expname}cluster1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1" --cluster ${TENANT}/${CLUSTER}
    verify_export "ignore" ${HOST1} 2 1
    verify_export "ignore" ${HOST2} 2 1

    # Create host export with 1 volume for the project
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Create cluster export with 1 volume for another project
    runcmd export_group create ${PROJECT2} ${expname}cluster2 $NH --type Cluster --volspec "${PROJECT2}/P2${VOLNAME}-1" --cluster ${TENANT}/${CLUSTER} 
    verify_export "ignore" ${HOST1} 2 3
    verify_export "ignore" ${HOST2} 2 2

    # Create host export with 1 volume for the project
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-2" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 4

    # Remove host1 from one of the cluster exports, volumes on both cluster export get unmapped 
    runcmd export_group update $PROJECT/${expname}cluster1 --remHosts ${HOST1}
    verify_export "ignore" ${HOST1} 2 2
    verify_export "ignore" ${HOST2} 2 2

    runcmd export_group update ${PROJECT2}/${expname}cluster2 --remHosts ${HOST1}
    verify_export "ignore" ${HOST1} 2 2
    verify_export "ignore" ${HOST2} 2 2

    # Add host1 back to the cluster export
    runcmd export_group update $PROJECT/${expname}cluster1 --addHosts ${HOST1}
    verify_export "ignore" ${HOST1} 2 3
    verify_export "ignore" ${HOST2} 2 2

    runcmd export_group update ${PROJECT2}/${expname}cluster2 --addHosts ${HOST1}
    verify_export "ignore" ${HOST1} 2 4
    verify_export "ignore" ${HOST2} 2 2

    # Remove one initiator from one of the exclusive exports, the initiator should be removed on array
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 4
    verify_export "ignore" ${HOST2} 2 2

    # Remove last initiator from the exclusive export, the host should be deleted
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} gone
    verify_export "ignore" ${HOST2} 2 2

    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2
    runcmd export_group delete $PROJECT/${expname}cluster1
    runcmd export_group delete $PROJECT2/${expname}cluster2

    # Clean up
    runcmd volume delete --project $PROJECT2 --wait
}

# Unity DU Prevention Validation Test 4
#
# Summary: Test export group update/deletion when host has multiple export groups with no or partial shared initiators
#
# Basic Use Case for single host, multiple export groups
# 1. Create 1 volume, 1 host export with the same host from the first project
# 2. Remove initiator 2 from the export group
# 3. Create 1 volume, 1 host export with the same host from the second project
# 4. Remove shared initiator 1 from export group 2
#    should fail since two export groups have different sets of initiators
# 5. Remove non shared initiator 2 from export group 2
#    should success
# 6. Remove last shared initiator (initiator 1) from export group 2
#    should success, both export groups have the same initiator 1
# 7. Remove initiator 1 from export group 1
#    should success, initiator 1 has already removed from array
# 8. Create two export groups with different sets of initiators
# 9. Remove last volume of export group 2
#    should success, non shared initiator should be removed
# 10. Create two export groups with different sets of initiators
# 11. Remove last volume of export group 1
#     should success, shared initiator should not be removed
# 12. Create two export groups again with different sets of initiators
# 13. Delete export group 2
#     should success, non shared initiator should be removed
# 14. Create two export groups again with different sets of initiators
#     should success, shared initiator should not be removed
# 15. Create two export groups with disjointed initiator sets
# 16. Remove initiator from export group 1
#     should success, LUN get removed, and initiator get deleted from array
# 17. Remove initiator from export group 2
#     should success, LUN get removed, initiator and host get deleted from array
# 18. Remove volume from export group 1
#     should success, LUN get removed, and initiator get deleted from array
# 19. Remove volume from export group 2
#     should success, LUN get removed, initiator and host get deleted from array
# 20. Delete export group 1
#     should success, LUN get removed, and initiator get deleted from array
# 21. Delete from export group 2
#     should success, LUN get removed, initiator and host get deleted from array
# 22. Clean up
#
unity_test_4() {
    echot "Unity Test 4: Export Group update/deletion when host has multiple export groups with no or partial shared initiators"
    expname=${EXPORT_GROUP_NAME}u4
    PWWN="${H1NI2}:${H1PI2}"

    # Make sure we start clean; no masking view on the array
    verify_export "ignore" ${HOST1} gone

    PROJECT2=${PROJECT}2

    isCreated=$(project list --tenant $TENANT | grep ${PROJECT2} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found project $PROJECT2"
    else
        run project create ${PROJECT2} --tenant $TENANT
    fi

    isCreated=$(volume list $PROJECT2 | grep P2${VOLNAME} | wc -l)
    if [ $isCreated -ne 0 ]; then
        echo "Found volume in $PROJECT2"
    else
        run volume create P2${VOLNAME} ${PROJECT2} ${NH} ${VPOOL_BASE} 1GB --count 2
    fi

    #######################################################
    # Export groups with shared and non shared initiators #
    #######################################################

    # Create two export groups again with different sets of initiators
    runcmd export_group create ${PROJECT} ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 1 1

    echo "Sleep 60 seconds"
    sleep 60

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Remove shared initiator will fail since two export groups have different sets of initiators
    fail export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI1}

    # Remove non shared initiator should success
    run export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 1 2

    # Now each export group have same initiator
    runcmd export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} gone

    run export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} gone

    # Create two export groups again with different sets of initiators
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2

    runcmd export_group create ${PROJECT} ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 1 1

    echo "Sleep 60 seconds"
    sleep 60

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Remove last volume of export group 2
    runcmd export_group update $PROJECT2/${expname}2 --remVols "${PROJECT2}/P2${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 1 1

    # Create export group 2 again with different sets of initiators
    runcmd export_group delete $PROJECT2/${expname}2
    echo "Sleep 60 seconds"
    sleep 60

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Remove last volume of export group 1
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 2 1

    # Create two export groups again with shared and non shared initiators
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2

    runcmd export_group create ${PROJECT} ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 1

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 1 1

    echo "Sleep 60 seconds"
    sleep 60

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Delete export group 2
    runcmd export_group delete $PROJECT2/${expname}2
    verify_export "ignore" ${HOST1} 1 1

    echo "Sleep 60 seconds"
    sleep 60

    # Create export group 2 again with different sets of initiators
    runcmd export_group create ${PROJECT2} ${expname}2 $NH --type Host --volspec "${PROJECT2}/P2${VOLNAME}-1" --hosts "${HOST1}"
    verify_export "ignore" ${HOST1} 2 2

    # Delete export group 1
    runcmd export_group delete $PROJECT/${expname}1
    verify_export "ignore" ${HOST1} 2 1

    runcmd export_group delete $PROJECT2/${expname}2
    verify_export "ignore" ${HOST1} gone

    ################################################
    # Export groups with disjointed initiator sets #
    ################################################

    # Create export groups with disjointed initiator sets
    runcmd export_group create ${PROJECT} ${expname}1 $NH --volspec "${PROJECT}/${VOLNAME}-1" --inits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 1

    # Add initiator 2 to host, so that export group 2 will use the same host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --volspec "${PROJECT2}/P2${VOLNAME}-1" --inits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 2 2

    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 1

    runcmd export_group update $PROJECT2/${expname}2 --remInits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} gone

    # Create export groups with disjointed initiators
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2
    runcmd export_group create ${PROJECT} ${expname}1 $NH --volspec "${PROJECT}/${VOLNAME}-1" --inits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 1

    # Add initiator 2 to host, so that export group 2 will use the same host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --volspec "${PROJECT2}/P2${VOLNAME}-1" --inits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 2 2

    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    verify_export "ignore" ${HOST1} 1 1

    runcmd export_group update $PROJECT2/${expname}2 --remVols "${PROJECT2}/P2${VOLNAME}-1"
    verify_export "ignore" ${HOST1} gone

    # Create export groups with disjointed initiators
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group delete $PROJECT2/${expname}2

    runcmd export_group create ${PROJECT} ${expname}1 $NH --volspec "${PROJECT}/${VOLNAME}-1" --inits ${HOST1}/${H1PI1}
    verify_export "ignore" ${HOST1} 1 1

    # Add initiator 2 to host, so that export group 2 will use the same host
    arrayhelper add_initiator_to_mask ${SERIAL_NUMBER} ${PWWN} ${HOST1}

    runcmd export_group create ${PROJECT2} ${expname}2 $NH --volspec "${PROJECT2}/P2${VOLNAME}-1" --inits ${HOST1}/${H1PI2}
    verify_export "ignore" ${HOST1} 2 2

    runcmd export_group delete $PROJECT/${expname}1
    verify_export "ignore" ${HOST1} 1 1

    runcmd export_group delete $PROJECT2/${expname}2
    verify_export "ignore" ${HOST1} gone

    # Clean up
    runcmd volume delete --project $PROJECT2 --wait
}

# Unity DU Prevention Validation Test 5
#
# Summary: Test remove volume, initiator from host that no longer exists on array
#
# Basic Use Case for single host, multiple export groups
# 1. Create 2 volume, 2 initiator export
# 2. Delete the host directly from array
# 3. Remove the first volume
#    should success
# 4. Remove the last volume
#    should success, export mask is deleted in ViPR
# 5. Create 2 volume, 2 initiator export again
# 6. Delete the host directly from array
# 7. Delete the first initiator from ViPR
#    should success
# 8. Delete the last initiator from ViPR
#    should success, export mask is deleted in ViPR
# 9. Create 2 volume, 2 initiator export again
# 10. Delete the host directly from array
# 11. Delete the export group
#     should success
#
unity_test_5() {
    echot "Unity Test 5: Export Group update/delete with no matched host"
    expname=${EXPORT_GROUP_NAME}u5

    # Make sure we start clean; no masking view on the array
    verify_export "ignore" ${HOST1} gone

    # Create export with 2 volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    verify_export "ignore" ${HOST1} 2 2

    # Now remove the host
    arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}

    # Verify the host is gone
    verify_export "ignore" ${HOST1} gone

    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-2"

    echo "Sleep 60 seconds"
    sleep 60

    # Create the same export group again
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 2 2

    # Now remove the host
    arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}

    # Verify the host is gone
    verify_export "ignore" ${HOST1} gone

    # Now remove initiators
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1}
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI2}

    echo "Sleep 60 seconds"
    sleep 60

    # Create the same export group again
    runcmd export_group delete $PROJECT/${expname}1
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --hosts "${HOST1}"

    # Verify the mask has the new initiator in it
    verify_export "ignore" ${HOST1} 2 2

    # Now remove the host
    arrayhelper delete_mask ${SERIAL_NUMBER} ${expname}1 ${HOST1}

    # Delete export group. Should success
    runcmd export_group delete $PROJECT/${expname}1
}

unity_test_all() {
    unity_test_1
    unity_test_2
    unity_test_3
    unity_test_4
    unity_test_5
}

Feature: CoprHD rollbacks for failed vmax3 volume creation

  @vmax3
  Scenario Outline: Database leaves nothing behind
    Given I have a CoprHD setup for vmax3 rollback testing
    And I am logged in as root
    When I create a volume that fails with <failure>
    Then the order should fail
    And the database should not have left anything behind
    But I can retry the order successfully

    Examples:
      | failure |
      | failure_004_final_step_in_workflow_complete |
      | failure_005_BlockDeviceController.createVolumes_before_device_create |
      | failure_006_BlockDeviceController.createVolumes_after_device_create  |
      | failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete |
      | failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete |
      | failure_004:failure_015_SmisCommandHelper.invokeMethod_ReturnElementsToStoragePool |
      | failure_015_SmisCommandHelper.invokeMethod_EMCCreateMultipleTypeElementsFromStoragePool |
      | failure_011_VNXVMAX_Post_Placement_outside_trycatch |                   
      | failure_012_VNXVMAX_Post_Placement_inside_trycatch |

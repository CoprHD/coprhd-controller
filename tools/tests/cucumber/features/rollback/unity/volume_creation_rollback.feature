Feature: CoprHD rollbacks for failed unity volume creation

  @unity
  Scenario Outline: Retrying volume creation after failure
    Given I have a CoprHD setup for unity rollback testing
    And I am logged in as root
    When I create a volume that fails with <failure>
    Then the order should fail
    And the database should not have left anything behind
    But I can retry the order successfully

    Examples:
      | failure |
      | failure_004_final_step_in_workflow_complete |
      | failure_005_BlockDeviceController.createVolumes_before_device_create |
      | failure_004:failure_014_BlockDeviceController.rollbackCreateVolumes_after_device_delete |

  @unity
  Scenario Outline: Failure leaves the device on the array
    Given I have a CoprHD setup for unity rollback testing
    And I am logged in as root
    When I create a volume that fails with <failure>
    Then the order should fail
    And the database should not have left anything behind
    And retrying the order will fail with 'LUN with this name already exists'

    Examples:
      | failure|
      | failure_004:failure_013_BlockDeviceController.rollbackCreateVolumes_before_device_delete |
      | failure_006_BlockDeviceController.createVolumes_after_device_create  |

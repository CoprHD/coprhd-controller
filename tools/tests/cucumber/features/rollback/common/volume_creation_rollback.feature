Feature: CoprHD rollback for failed volume creation

  Scenario Outline: Database leaves nothing behind
    Given I have a CoprHD setup for <system> rollback testing
    And I am logged in as root
    When I create a volume that fails with <failure>
    Then the order should fail
    And the database should not have left anything behind
    But I can retry the order successfully

    Examples:
      | system | failure |
      | vmax3  | failure_004_final_step_in_workflow_complete |

    @srdf
    Examples:
      | system | failure |
      | srdf_sync  | failure_004_final_step_in_workflow_complete |
      | srdf_async | failure_004_final_step_in_workflow_complete |

Feature: Allow Customer to select RDF Group as part of the Create block volume for VMware catalog service
  Project "project" does not map to an RDF Group.  
  Prerequisites to run this suite is to first:
  run "wftests.sh sanity.conf srdf async -setuphw none" to set up the tests without actually creating any volumes.  
  Mark the project that is created for the RDF group and replace the "S082215621" with that value.  
  Also create a project called "project" for the same tenant.
  Also add the license by running features/misc/setup_portalsvc.feature

  Background:
    Given the customer is logged in as root

  Scenario Outline: Test various combinations of volume creation via Create block volume for VMware.
    When they order a volume using the Create Volume for VMware catalog service
      | Project   | Virtual Pool   | RDF Group   |
      | <Project> | <Virtual Pool> | <RDF Group> |
    Then the order should succeed

    Examples:
      | Project    | Virtual Pool      | RDF Group |
      | project    | vpool_SRDF_TARGET | none      |
      | project    | vpool             | any       |
      | S08237360     | vpool             | none      |
      | S08237360     | vpool             | any       |

  Scenario: Ordering a volume with non-SRDF project and no RDF Group selection
    When they order a volume using the Create Volume for VMware catalog service
      | Project | Virtual Pool | RDF Group |
      | project | vpool        | none      |
    Then the order should fail

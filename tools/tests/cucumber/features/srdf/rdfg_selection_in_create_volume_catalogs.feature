Feature: Allow Customer to select RDF Group as part of the Create Block Volume catalog service
  Project "project" does not map to an RDF Group.

  Background:
    Given the customer is logged in as root

  Scenario Outline: Test various combinations of volume creation via Create Block Service
    When they order a volume using the Create Block Volume catalog service
      | Project   | Virtual Pool   | RDF Group   |
      | <Project> | <Virtual Pool> | <RDF Group> |
    Then the order should succeed

    Examples:
      | Project | Virtual Pool      | RDF Group |
      | project | vpool_SRDF_TARGET | none      |
      | project | vpool             | any       |
      | NPRDF19 | vpool             | none      |
      | NPRDF19 | vpool             | any       |

  Scenario: Ordering a volume with non-SRDF project and no RDF Group selection
    When they order a volume using the Create Block Volume catalog service
      | Project | Virtual Pool | RDF Group |
      | project | vpool        | none      |
    Then the order should not succeed

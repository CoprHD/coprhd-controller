Feature: Allow Customer to select RDF Group as part of the Change Virtual Pool to add SRDF catalog service.

  Background:
    Given the customer is logged in as root
      And they have ordered a volume using the Create Block Volume catalog service
        | Project | Virtual Pool |
        | NPRDF19 | vpool4change |

  Scenario: Use Change Volume Virtual Pool without selecting an RDF Group
    When they add SRDF using the Change Volume Virtual Pool catalog service
      | To         | RDF Group |
      | vpool_nocg | none      |
    Then the order should succeed

  Scenario: Use Change Volume Virtual Pool and select an RDF Group
    When they add SRDF using the Change Volume Virtual Pool catalog service
      | To         | RDF Group |
      | vpool_nocg | any       |
    Then the order should succeed

  Scenario: Use Change Virtual Pool without selecting an RDF Group
    When they add SRDF using the Change Virtual Pool catalog service
      | To         | RDF Group |
      | vpool_nocg | none      |
    Then the order should succeed

  Scenario: Use Change Virtual Pool and select an RDF Group
    When they add SRDF using the Change Virtual Pool catalog service
      | To         | RDF Group |
      | vpool_nocg | any       |
    Then the order should succeed

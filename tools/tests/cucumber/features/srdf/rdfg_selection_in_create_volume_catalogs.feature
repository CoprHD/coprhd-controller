Feature: Allow Customer to select RDF Group as part of the Create Volume Catalogs

  Scenario: Create volume with non-SRDF virtual pool
    Given the customer has a virtual pool without SRDF
    When they order a volume
    Then the order should succeed

  Scenario: Create SRDF volume without specifying an RDF Group
    Given the customer has a virtual pool with SRDF
    When they order a volume without specifying an RDF Group
    Then the order should succeed

  Scenario: Create SRDF volume with an RDF Group specified
    Given the customer has a virtual pool with SRDF
    When they order a volume with an RDF Group specified
    Then the order should succeed

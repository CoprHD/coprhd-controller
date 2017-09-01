Feature: Setup the portalsvc
  As a user
  When I setup the portalsvc
  Then I should be taken to the dashboard

  Scenario: Setup the portalsvc
    Given I am logged in as the root user
    When I complete the Initial Setup form
    Then I should be taken to the Dashboard page

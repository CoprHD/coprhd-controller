Feature: Testing PhantomJS

  Scenario: Smoke Testing
    Given I have not set the IN_BROWSER environment variable
    When I navigate to my app host
    Then I should see "Login"

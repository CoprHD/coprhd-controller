Feature: Testing Selenium

  Scenario: Testing
    Given I have set the IN_BROWSER environment variable
    When I navigate to my app host
    Then I should see "Login"

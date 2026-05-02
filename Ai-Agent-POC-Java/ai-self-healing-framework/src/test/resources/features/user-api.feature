# User API — GET /login/:id Self-Healing Validation
# Base URL: http://localhost:3000
# Endpoint: GET /login/:id
# Response shape: { success, message, data: { userId, name, email, status } }

Feature: User API Data Validation with Self-Healing

  As a QA Engineer
  I want to validate the GET API response using intelligent self-healing
  So that tests automatically adapt to API schema and response data changes

  Background:
    Given the User API endpoint is configured

  @smoke @self-healing
  Scenario Outline: Validate user data from GET API with auto-healing

    When I send a GET request for user with id "<UserId>"
    Then the response status code should be 200
    And the API response should match the expected JSON contract
    And the schema should be auto-healed if changes are detected
    And the feature file and step definitions should be updated if required

    Examples:
      | UserId |
      | 1      |
      | 2      |
      | 3      |

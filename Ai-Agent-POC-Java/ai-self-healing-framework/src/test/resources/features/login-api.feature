# Login API — Happy Path (Stateless, proper Given/When/Then)
# Base URL: http://localhost:3000

Feature: Login API CRUD Operations
  As a developer
  I want to validate the happy path for all Login API endpoints
  So that I can confirm the backend works correctly for valid requests

  Background:
    Given the API base URL is "http://localhost:3000"

  # ── GET /login ────────────────────────────────────────────────────────────────
  Scenario: GET /login returns all login records
    When I send a GET request to "/login"
    Then the response status code should be 200
    And the response body should not be empty
    And the response body should contain field "success" with value "true"
    And the response body should contain field "message" with value "All logins retrieved successfully"
    # And each login record should contain a name field
    # And each login record should contain non-empty field "email"
    # And each login record should contain non-empty field "status"

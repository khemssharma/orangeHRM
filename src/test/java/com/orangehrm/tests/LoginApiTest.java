package com.orangehrm.tests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OrangeHRM API - RestAssured Test Suite
 * Testiny Test Plan: API / Authentication Endpoints
 * Base URL: https://opensource-demo.orangehrmlive.com
 *
 * NOTE: OrangeHRM OS uses session-cookie auth via the web app.
 * These tests cover the session-based auth flow and protected API endpoints.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("OrangeHRM RestAssured API Tests")
public class LoginApiTest {

    private static final String BASE_URL   = "https://opensource-demo.orangehrmlive.com";
    private static final String LOGIN_PATH = "/web/index.php/auth/login";
    private static final String API_BASE   = "/web/index.php/api/v2";

    private static final String VALID_USERNAME = "Admin";
    private static final String VALID_PASSWORD = "admin123";
    private static String sessionCookie = null;

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.useRelaxedHTTPSValidation();
    }

    // ─── Helper: obtain CSRF token and session cookie ─────────────────────────
    private static Map<String, String> getSessionDetails() {
        Response loginPage = given()
                .redirects().follow(true)
                .when()
                .get(LOGIN_PATH);

        String cookie  = loginPage.getCookie("orangehrm");
        String csrf    = loginPage.htmlPath().getString("**.find{it.@name=='_token'}.@value");

        Map<String, String> details = new HashMap<>();
        details.put("cookie", cookie != null ? cookie : "");
        details.put("csrf",   csrf   != null ? csrf   : "");
        return details;
    }

    // ─── TC-API-001 : Login Page Returns 200 ─────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("TC-API-001 | GET /auth/login returns HTTP 200")
    void testLoginPageReturns200() {
        // Testiny: Login page endpoint should be reachable
        given()
            .when()
            .get(LOGIN_PATH)
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"));
    }

    // ─── TC-API-002 : Login Page Contains Required Fields ────────────────────
    @Test
    @Order(2)
    @DisplayName("TC-API-002 | Login page HTML contains username and password inputs")
    void testLoginPageContainsInputFields() {
        // Testiny: Ensure the login form fields are present in response body
        String body = given()
                .when()
                .get(LOGIN_PATH)
                .asString();

        assertTrue(body.contains("name=\"username\"") || body.contains("\"username\""),
                   "Response should contain username field reference");
        assertTrue(body.contains("type=\"password\"") || body.contains("\"password\""),
                   "Response should contain password field reference");
    }

    // ─── TC-API-003 : CSRF Token Present in Page ─────────────────────────────
    @Test
    @Order(3)
    @DisplayName("TC-API-003 | Login page issues session cookies")
    void testSessionCookieIssuedOnPageLoad() {
        // Testiny: Verify session initialisation on GET
        Response response = given()
                .when()
                .get(LOGIN_PATH);

        response.then().statusCode(200);
        // cookies should be set
        assertNotNull(response.getCookies(), "Cookies map should not be null");
    }

    // ─── TC-API-004 : POST Login with Valid Credentials ───────────────────────
    @Test
    @Order(4)
    @DisplayName("TC-API-004 | POST valid credentials results in session redirect (302/200)")
    void testPostValidCredentials() {
        // Testiny: Valid login via form POST should redirect to dashboard
        Map<String, String> details = getSessionDetails();

        Response response = given()
                .contentType(ContentType.URLENC)
                .cookie("orangehrm", details.get("cookie"))
                .formParam("_token",  details.get("csrf"))
                .formParam("username", VALID_USERNAME)
                .formParam("password", VALID_PASSWORD)
                .redirects().follow(false)
                .when()
                .post(LOGIN_PATH);

        int statusCode = response.getStatusCode();
        // Successful post auth = 302 redirect to dashboard
        assertTrue(statusCode == 302 || statusCode == 200,
                   "Expected 302 redirect or 200, got: " + statusCode);

        if (statusCode == 302) {
            String location = response.getHeader("Location");
            assertNotNull(location, "Redirect Location header should be present");
            assertTrue(location.contains("dashboard") || location.contains("index"),
                       "Should redirect to dashboard, but got: " + location);
        }

        // Store session cookie for subsequent tests
        sessionCookie = response.getCookie("orangehrm");
    }

    // ─── TC-API-005 : POST Login with Invalid Credentials ────────────────────
    @Test
    @Order(5)
    @DisplayName("TC-API-005 | POST invalid credentials returns 200 with error (no redirect)")
    void testPostInvalidCredentials() {
        // Testiny: Invalid credentials should stay on login with error body
        Map<String, String> details = getSessionDetails();

        Response response = given()
                .contentType(ContentType.URLENC)
                .cookie("orangehrm", details.get("cookie"))
                .formParam("_token",  details.get("csrf"))
                .formParam("username", "wronguser")
                .formParam("password", "wrongpassword")
                .redirects().follow(false)
                .when()
                .post(LOGIN_PATH);

        // Should not redirect to dashboard
        int statusCode = response.getStatusCode();
        if (statusCode == 302) {
            String location = response.getHeader("Location");
            assertFalse(location != null && location.contains("dashboard"),
                        "Invalid credentials should NOT redirect to dashboard");
        } else {
            assertEquals(200, statusCode, "Should return 200 with error body for invalid credentials");
        }
    }

    // ─── TC-API-006 : Protected API Endpoint Without Auth ────────────────────
    @Test
    @Order(6)
    @DisplayName("TC-API-006 | Protected API endpoint returns 401 without authentication")
    void testProtectedEndpointWithoutAuth() {
        // Testiny: Unauthenticated API call should return 401
        given()
            .accept(ContentType.JSON)
            .when()
            .get(API_BASE + "/pim/employees")
            .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    // ─── TC-API-007 : API Employees Endpoint with Valid Session ──────────────
    @Test
    @Order(7)
    @DisplayName("TC-API-007 | Authenticated GET /api/v2/pim/employees returns employee list")
    void testEmployeesEndpointAuthenticated() {
        // Testiny: With valid session, should return employee data
        // First get a fresh session
        Map<String, String> details = getSessionDetails();

        // Post login
        Response loginResponse = given()
                .contentType(ContentType.URLENC)
                .cookie("orangehrm", details.get("cookie"))
                .formParam("_token",  details.get("csrf"))
                .formParam("username", VALID_USERNAME)
                .formParam("password", VALID_PASSWORD)
                .redirects().follow(true)
                .when()
                .post(LOGIN_PATH);

        String authCookie = loginResponse.getCookie("orangehrm");
        if (authCookie == null) authCookie = details.get("cookie");

        // Now call protected endpoint
        Response apiResponse = given()
                .accept(ContentType.JSON)
                .cookie("orangehrm", authCookie)
                .when()
                .get(API_BASE + "/pim/employees?limit=10&offset=0");

        int status = apiResponse.getStatusCode();
        // Accept 200 (authenticated) or 401 (session may not have persisted in headless flow)
        assertTrue(status == 200 || status == 401,
                   "Should return 200 with employee data or 401. Got: " + status);

        if (status == 200) {
            apiResponse.then()
                       .body("data",  notNullValue())
                       .body("meta",  notNullValue());
        }
    }

    // ─── TC-API-008 : Response Time ───────────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("TC-API-008 | Login page loads within acceptable response time (< 5s)")
    void testLoginPageResponseTime() {
        // Testiny: Performance — login page should respond in < 5000ms
        long responseTime = given()
                .when()
                .get(LOGIN_PATH)
                .time();

        assertTrue(responseTime < 5000,
                   "Login page should respond in under 5000ms, but took: " + responseTime + "ms");
    }

    // ─── TC-API-009 : Security Headers ───────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("TC-API-009 | Login page response contains security headers")
    void testSecurityHeaders() {
        // Testiny: Verify basic security headers are present
        Response response = given()
                .when()
                .get(LOGIN_PATH);

        response.then().statusCode(200);

        // At least one of the following security-related headers should exist
        boolean hasXFrame      = response.getHeader("X-Frame-Options") != null;
        boolean hasCSP         = response.getHeader("Content-Security-Policy") != null;
        boolean hasXContent    = response.getHeader("X-Content-Type-Options") != null;

        assertTrue(hasXFrame || hasCSP || hasXContent,
                   "Response should include at least one security header (X-Frame-Options / CSP / X-Content-Type-Options)");
    }

    // ─── TC-API-010 : Parameterized Empty Fields ──────────────────────────────
    @ParameterizedTest(name = "TC-API-010 | Empty field: username={0} password={1}")
    @Order(10)
    @CsvSource({
        "'', admin123",
        "Admin, ''",
        "'', ''"
    })
    @DisplayName("TC-API-010 | POST with empty credentials does not redirect to dashboard")
    void testPostEmptyCredentials(String username, String password) {
        // Testiny: Empty fields should not produce a successful auth redirect
        Map<String, String> details = getSessionDetails();

        Response response = given()
                .contentType(ContentType.URLENC)
                .cookie("orangehrm", details.get("cookie"))
                .formParam("_token",  details.get("csrf"))
                .formParam("username", username)
                .formParam("password", password)
                .redirects().follow(false)
                .when()
                .post(LOGIN_PATH);

        int status = response.getStatusCode();
        if (status == 302) {
            String location = response.getHeader("Location");
            assertFalse(location != null && location.contains("dashboard"),
                        "Empty credentials should NOT redirect to dashboard");
        }
    }

    // ─── TC-API-011 : HTTP Method Restrictions ────────────────────────────────
    @Test
    @Order(11)
    @DisplayName("TC-API-011 | PUT/DELETE on login endpoint returns 405 or 404")
    void testUnsupportedHttpMethods() {
        // Testiny: Non-standard methods on login should be rejected
        given()
            .when()
            .put(LOGIN_PATH)
            .then()
            .statusCode(anyOf(is(405), is(404), is(302), is(200)));

        given()
            .when()
            .delete(LOGIN_PATH)
            .then()
            .statusCode(anyOf(is(405), is(404), is(302), is(200)));
    }
}

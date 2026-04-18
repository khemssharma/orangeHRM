package com.orangehrm.tests;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrangeHRM Login Page - JUnit 5 + Selenium Test Suite
 * Testiny Test Plan: Login Functionality
 * Base URL: https://opensource-demo.orangehrmlive.com/web/index.php/auth/login
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("OrangeHRM Login Page Tests")
public class LoginTest {

    private static WebDriver driver;
    private static WebDriverWait wait;

    private static final String BASE_URL = "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login";
    private static final String VALID_USERNAME = "Admin";
    private static final String VALID_PASSWORD = "admin123";
    private static final String DASHBOARD_URL = "/web/index.php/dashboard/index";

    // ─── Locators ────────────────────────────────────────────────────────────
    private static final By USERNAME_INPUT   = By.name("username");
    private static final By PASSWORD_INPUT   = By.name("password");
    private static final By LOGIN_BUTTON     = By.cssSelector("button[type='submit']");
    private static final By ERROR_MESSAGE    = By.cssSelector(".oxd-alert-content-text");
    private static final By DASHBOARD_HEADER = By.cssSelector(".oxd-topbar-header-breadcrumb h6");
    private static final By PAGE_LOGO        = By.cssSelector(".orangehrm-login-logo img");
    private static final By FORGOT_PASSWORD  = By.cssSelector(".orangehrm-login-forgot > p");

    @BeforeAll
    static void setUpDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                     "--window-size=1440,900", "--disable-gpu");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @BeforeEach
    void navigateToLogin() {
        driver.get(BASE_URL);
        wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_INPUT));
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) driver.quit();
    }

    // ─── TC-001 : Page Load ───────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("TC-001 | Login page loads successfully")
    void testLoginPageLoads() {
        // Testiny: Verify login page elements are visible
        assertTrue(driver.findElement(USERNAME_INPUT).isDisplayed(),  "Username field should be visible");
        assertTrue(driver.findElement(PASSWORD_INPUT).isDisplayed(),  "Password field should be visible");
        assertTrue(driver.findElement(LOGIN_BUTTON).isDisplayed(),    "Login button should be visible");
        assertTrue(driver.findElement(PAGE_LOGO).isDisplayed(),       "OrangeHRM logo should be visible");
        assertEquals("https://opensource-demo.orangehrmlive.com/web/index.php/auth/login",
                     driver.getCurrentUrl(), "URL should match login page");
    }

    // ─── TC-002 : Page Title ──────────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("TC-002 | Page title is correct")
    void testPageTitle() {
        // Testiny: Verify browser tab title
        assertEquals("OrangeHRM", driver.getTitle(), "Page title should be 'OrangeHRM'");
    }

    // ─── TC-003 : Successful Login ────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("TC-003 | Successful login with valid credentials")
    void testSuccessfulLogin() {
        // Testiny: Valid Admin login should redirect to dashboard
        driver.findElement(USERNAME_INPUT).sendKeys(VALID_USERNAME);
        driver.findElement(PASSWORD_INPUT).sendKeys(VALID_PASSWORD);
        driver.findElement(LOGIN_BUTTON).click();

        wait.until(ExpectedConditions.urlContains(DASHBOARD_URL));

        assertTrue(driver.getCurrentUrl().contains(DASHBOARD_URL),
                   "Should redirect to dashboard after successful login");
        assertTrue(driver.findElement(DASHBOARD_HEADER).isDisplayed(),
                   "Dashboard header should be visible after login");
    }

    // ─── TC-004 : Invalid Username ────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("TC-004 | Login fails with invalid username")
    void testInvalidUsername() {
        // Testiny: Wrong username should show error
        driver.findElement(USERNAME_INPUT).sendKeys("WrongUser");
        driver.findElement(PASSWORD_INPUT).sendKeys(VALID_PASSWORD);
        driver.findElement(LOGIN_BUTTON).click();

        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(ERROR_MESSAGE));
        assertTrue(error.getText().contains("Invalid credentials"),
                   "Error message should indicate invalid credentials");
        assertTrue(driver.getCurrentUrl().contains("/auth/login"),
                   "Should remain on login page after failed login");
    }

    // ─── TC-005 : Invalid Password ────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("TC-005 | Login fails with invalid password")
    void testInvalidPassword() {
        // Testiny: Wrong password should show error
        driver.findElement(USERNAME_INPUT).sendKeys(VALID_USERNAME);
        driver.findElement(PASSWORD_INPUT).sendKeys("WrongPass");
        driver.findElement(LOGIN_BUTTON).click();

        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(ERROR_MESSAGE));
        assertTrue(error.getText().contains("Invalid credentials"),
                   "Error message should indicate invalid credentials");
    }

    // ─── TC-006 : Empty Fields ────────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("TC-006 | Login fails with empty username and password")
    void testEmptyCredentials() {
        // Testiny: Empty submission should show validation errors
        driver.findElement(LOGIN_BUTTON).click();

        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.cssSelector(".oxd-input-field-error-message")));

        java.util.List<WebElement> errors = driver.findElements(
                By.cssSelector(".oxd-input-field-error-message"));
        assertTrue(errors.size() >= 2, "Should show at least 2 validation error messages");
        assertTrue(errors.stream().anyMatch(e -> e.getText().contains("Required")),
                   "Should show 'Required' validation message");
    }

    // ─── TC-007 : Empty Username Only ─────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("TC-007 | Login fails with empty username only")
    void testEmptyUsername() {
        // Testiny: Missing username validation
        driver.findElement(PASSWORD_INPUT).sendKeys(VALID_PASSWORD);
        driver.findElement(LOGIN_BUTTON).click();

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".oxd-input-field-error-message")));

        WebElement error = driver.findElement(By.cssSelector(".oxd-input-field-error-message"));
        assertEquals("Required", error.getText(), "Should show 'Required' for empty username");
    }

    // ─── TC-008 : Empty Password Only ────────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("TC-008 | Login fails with empty password only")
    void testEmptyPassword() {
        // Testiny: Missing password validation
        driver.findElement(USERNAME_INPUT).sendKeys(VALID_USERNAME);
        driver.findElement(LOGIN_BUTTON).click();

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".oxd-input-field-error-message")));

        WebElement error = driver.findElement(By.cssSelector(".oxd-input-field-error-message"));
        assertEquals("Required", error.getText(), "Should show 'Required' for empty password");
    }

    // ─── TC-009 : Password Masking ────────────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("TC-009 | Password field masks input")
    void testPasswordMasking() {
        // Testiny: Password input should be of type 'password'
        WebElement passwordField = driver.findElement(PASSWORD_INPUT);
        assertEquals("password", passwordField.getAttribute("type"),
                     "Password field should have type='password' to mask input");
    }

    // ─── TC-010 : Forgot Password Link ───────────────────────────────────────
    @Test
    @Order(10)
    @DisplayName("TC-010 | Forgot Password link is clickable and navigates")
    void testForgotPasswordLink() {
        // Testiny: Clicking Forgot Password should navigate to reset page
        WebElement forgotLink = driver.findElement(FORGOT_PASSWORD);
        assertTrue(forgotLink.isDisplayed(), "Forgot Password link should be visible");
        forgotLink.click();

        wait.until(ExpectedConditions.urlContains("/requestPasswordResetCode"));
        assertTrue(driver.getCurrentUrl().contains("/requestPasswordResetCode"),
                   "Should navigate to password reset page");
    }

    // ─── TC-011 : Parameterized Invalid Credentials ──────────────────────────
    @ParameterizedTest(name = "TC-011 | Invalid combo: [{0}] / [{1}]")
    @Order(11)
    @CsvSource({
        "admin,    admin123",
        "Admin,    Admin123",
        "ADMIN,    admin123",
        "Admin,    ''",
        "'',       admin123"
    })
    @DisplayName("TC-011 | Parameterized: Various invalid credential combos")
    void testVariousInvalidCredentials(String username, String password) {
        // Testiny: Multiple invalid combos should all fail login
        if (!username.isEmpty()) driver.findElement(USERNAME_INPUT).sendKeys(username);
        if (!password.isEmpty()) driver.findElement(PASSWORD_INPUT).sendKeys(password);
        driver.findElement(LOGIN_BUTTON).click();

        // Should stay on login page OR show error
        boolean staysOnLoginPage = driver.getCurrentUrl().contains("/auth/login");
        assertTrue(staysOnLoginPage, "Should not log in with invalid credentials: " + username + "/" + password);
        driver.get(BASE_URL); // reset for next iteration
    }

    // ─── TC-012 : SQL Injection ───────────────────────────────────────────────
    @Test
    @Order(12)
    @DisplayName("TC-012 | SQL Injection attempt is blocked")
    void testSqlInjection() {
        // Testiny: Input sanitisation — SQL injection should not bypass login
        driver.findElement(USERNAME_INPUT).sendKeys("' OR '1'='1");
        driver.findElement(PASSWORD_INPUT).sendKeys("' OR '1'='1");
        driver.findElement(LOGIN_BUTTON).click();

        // Should remain on login page
        boolean onLoginPage = driver.getCurrentUrl().contains("/auth/login");
        boolean hasError = !driver.findElements(ERROR_MESSAGE).isEmpty();
        assertTrue(onLoginPage || hasError, "SQL injection should not bypass authentication");
    }

    // ─── TC-013 : XSS Attempt ─────────────────────────────────────────────────
    @Test
    @Order(13)
    @DisplayName("TC-013 | XSS attempt is sanitised")
    void testXssAttempt() {
        // Testiny: XSS input should not execute as script
        String xssPayload = "<script>alert('XSS')</script>";
        driver.findElement(USERNAME_INPUT).sendKeys(xssPayload);
        driver.findElement(PASSWORD_INPUT).sendKeys(xssPayload);
        driver.findElement(LOGIN_BUTTON).click();

        // No alert should appear
        assertThrows(NoAlertPresentException.class,
                     () -> driver.switchTo().alert(),
                     "XSS payload should not trigger a JavaScript alert");
    }

    // ─── TC-014 : Username Case Sensitivity ──────────────────────────────────
    @Test
    @Order(14)
    @DisplayName("TC-014 | Username is case-sensitive")
    void testUsernameCaseSensitivity() {
        // Testiny: Lowercase 'admin' should not authenticate as 'Admin'
        driver.findElement(USERNAME_INPUT).sendKeys("admin");
        driver.findElement(PASSWORD_INPUT).sendKeys(VALID_PASSWORD);
        driver.findElement(LOGIN_BUTTON).click();

        boolean onLoginPage = driver.getCurrentUrl().contains("/auth/login");
        boolean hasError = !driver.findElements(ERROR_MESSAGE).isEmpty();
        assertTrue(onLoginPage || hasError, "Login should be case-sensitive for username");
    }

    // ─── TC-015 : Long Input Fields ──────────────────────────────────────────
    @Test
    @Order(15)
    @DisplayName("TC-015 | Login handles very long username input gracefully")
    void testLongUsernameInput() {
        // Testiny: Extremely long username should not crash the page
        String longUsername = "A".repeat(300);
        driver.findElement(USERNAME_INPUT).sendKeys(longUsername);
        driver.findElement(PASSWORD_INPUT).sendKeys(VALID_PASSWORD);
        driver.findElement(LOGIN_BUTTON).click();

        // Page should not crash — either stays on login or shows error
        assertDoesNotThrow(() -> driver.getCurrentUrl(),
                           "Page should handle long input without crashing");
        assertTrue(driver.getCurrentUrl().contains("/auth/login") ||
                   !driver.findElements(ERROR_MESSAGE).isEmpty(),
                   "Should not log in with excessively long username");
    }
}

package com.vmataj.expense;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "browser.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:expense-browser;DB_CLOSE_DELAY=-1")
class BrowserTest {
    @LocalServerPort int port;
    private WebDriver browser;
    private WebDriverWait wait;

    @BeforeEach
    void openBrowser() {
        var options = new ChromeOptions().addArguments("--headless=new", "--window-size=1280,900");
        String version = System.getProperty("browser.version");
        if (version != null && !version.isBlank()) options.setBrowserVersion(version);
        browser = new ChromeDriver(options);
        wait = new WebDriverWait(browser, Duration.ofSeconds(10));
    }

    @AfterEach
    void closeBrowser() {
        if (browser != null) browser.quit();
    }

    @Test
    void submitsAnExpenseAndFiltersTheLedger() {
        browser.get(home() + "approvals");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#claim")));
        browser.findElement(By.name("description")).sendKeys("Airport rail fare");
        browser.findElement(By.name("amount")).sendKeys("32.40");
        browser.findElement(By.cssSelector("#claim button")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("claims"), "Airport rail fare"));

        new Select(browser.findElement(By.id("filter"))).selectByValue("APPROVED");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("status"), "No claims match"));
        assertFalse(browser.findElement(By.id("claims")).getText().contains("Airport rail fare"));
    }

    private String home() {
        return "http://127.0.0.1:" + port + "/";
    }

    @Test
    void guestAddsAnExpenseAndFindsItAfterReload() {
        browser.get(home());
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#summary article")));
        click(By.id("add"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#editor[open] [name=description]")))
            .sendKeys("Selenium groceries");
        browser.findElement(By.cssSelector("#editor[open] [name=amount]")).sendKeys("23.40");
        click(By.id("editor-submit"));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("editor")));
        click(By.cssSelector("[data-page=expenses]"));
        browser.navigate().refresh();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("entry-search"))).sendKeys("Selenium groceries");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("expenses-body"), "Selenium groceries"));
        assertEquals(1, browser.findElements(By.cssSelector("#expenses-body tr")).size());
    }

    private void click(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }
}

package runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Cucumber TestNG runner.
 *
 * <p>
 * Picks up all {@code .feature} files under
 * {@code src/test/resources/features/}
 * and maps steps using the {@code steps} package.
 * </p>
 *
 * <h3>Run in isolation:</h3>
 * 
 * <pre>
 *   mvn test -Dtest=runner.CucumberRunner -Dapi.base.url=http://localhost:3000
 * </pre>
 *
 * <h3>Run with tag filter:</h3>
 * 
 * <pre>
 *   mvn test -Dtest=runner.CucumberRunner -Dcucumber.filter.tags="@smoke"
 * </pre>
 */
@CucumberOptions(features = "src/test/resources/features", glue = "steps", plugin = {
        "pretty",
        "html:target/cucumber-reports/login-api-report.html",
        "json:target/cucumber-reports/login-api-report.json"
}, monochrome = true)
public class CucumberRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}

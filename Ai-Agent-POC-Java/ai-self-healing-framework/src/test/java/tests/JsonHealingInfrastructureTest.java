package tests;

import api.healing.DynamicValidationEngine;
import api.healing.JsonDiffEngine;
import api.healing.JsonDiffResult;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JsonHealingInfrastructureTest {

    @Test
    public void jsonDiffShouldDetectRename() {
        JsonDiffEngine engine = new JsonDiffEngine();

        String oldJson = """
                {
                  "data": {
                    "username": "john",
                    "email": "user1@example.com"
                  }
                }
                """;

        String newJson = """
                {
                  "data": {
                    "name": "john",
                    "email": "user1@example.com"
                  }
                }
                """;

        JsonDiffResult diff = engine.compareJson(oldJson, newJson);

        Assert.assertEquals(diff.renamed().get("data.username"), "data.name");
        Assert.assertTrue(diff.added().isEmpty(), "Rename should not remain in added paths");
        Assert.assertTrue(diff.removed().isEmpty(), "Rename should not remain in removed paths");
    }

    @Test
    public void dynamicValidationShouldResolveLegacyFieldAliases() {
        DynamicValidationEngine engine = new DynamicValidationEngine();

        String actualJson = """
                {
                  "success": true,
                  "message": "Login retrieved successfully",
                  "data": {
                    "userId": 1,
                    "name": "john",
                    "email": "user1@example.com",
                    "status": "active"
                  }
                }
                """;

        DynamicValidationEngine.ValidationResult result = engine.validate(
                "http://localhost:3000/login/1",
                "GET",
                "user-api-expected-response.json",
                actualJson);

        Assert.assertTrue(result.matched(), "Schema-driven validation should match the stored user contract");
        Assert.assertEquals(
                engine.extractDynamicField("http://localhost:3000/login/1", "GET", actualJson, "username"),
                "john");
        Assert.assertEquals(
                engine.extractDynamicField("http://localhost:3000/login/1", "GET", actualJson, "id"),
                "1");
    }
}

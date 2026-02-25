import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PatientIntegrationTest {

  private static RequestSpecification baseSpec;

  @BeforeAll
  static void setUp() {
    RestAssured.reset();

    baseSpec = new RequestSpecBuilder()
        .setBaseUri("http://localhost")
        .setPort(4004)
        .setAccept(ContentType.JSON)
        .setContentType(ContentType.JSON)
        .build();

    // NOTE:
    // Avoid manipulating global RestAssured filters unless required.
    // RestAssured.filters(new ArrayList<>()) can lead to brittle behavior.
  }

  @Test
  public void shouldReturnPatientsWithValidToken() {
    String token = getToken();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Test failed: Authentication token is null/blank. Check /auth/login status and response body.");
    }

    Response response = given()
        .spec(baseSpec)
        .header("Authorization", "Bearer " + token)
        .log().all()
        .when()
        .get("/api/v1/patients");

    System.out.println("Response Status: " + response.getStatusCode());
    System.out.println("Response Body: " + response.getBody().asString());
    System.out.println("Response Headers: " + response.getHeaders());

    response.then().statusCode(200);
  }

  @Test
  public void shouldReturn429AfterLimitExceeded() throws InterruptedException {
    String token = getToken();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Test failed: Authentication token is null/blank. Check /auth/login status and response body.");
    }

    int total = 15;
    int tooManyRequests = 0;

    for (int i = 1; i <= total; i++) {
      Response response = given()
          .spec(baseSpec)
          .header("Authorization", "Bearer " + token)
          .when()
          .get("/api/v1/patients");

      System.out.printf("Request %d -> Status: %d%n", i, response.statusCode());

      if (response.statusCode() == 429) {
        tooManyRequests++;
      }
      Thread.sleep(100);
    }

    assertTrue(tooManyRequests >= 1, "Expected at least 1 request to be rate limited (429)");
  }

  private static String getToken() {
    String loginPayload = """
          {
            "email": "testuser@test.com",
            "password": "password123"
          }
        """;

    Response loginResponse = given()
        .spec(baseSpec)
        .body(loginPayload)
        .log().all()
        .when()
        .post("/auth/login");

    System.out.println("Login Status: " + loginResponse.getStatusCode());
    System.out.println("Login Body: " + loginResponse.getBody().asString());

    loginResponse.then().statusCode(200);

    return loginResponse
        .then()
        .extract()
        .jsonPath()
        .getString("token");
  }
}

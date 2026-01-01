import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.internal.filter.CsrfFilter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

public class PatientIntegrationTest {
//  @BeforeEach
//  void resetRA() {
//    RestAssured.reset();
//  }
  @BeforeAll
  static void setUp(){
    RestAssured.reset();

    RestAssured.filters(new ArrayList<>());
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = 4004;
    // disable csrf
    RestAssured.filters(new CsrfFilter().setCsrfConfig());

  }

  @Test
  public void shouldReturnPatientsWithValidToken () {
    String token = getToken();

    if (token == null) {
      throw new IllegalStateException("Test failed: Authentication token is null. Check /auth/login status.");
    } else {
      System.out.println("Generated Token: " + token);
    }

    RequestSpecification requestSpec = new RequestSpecBuilder()
            .addHeader("Authorization", "Bearer " + token)
            .build();

    Response response = given()
            .spec(requestSpec)
            .accept(ContentType.JSON)
            .queryParam("page", "1")
            .queryParam("size", "3")
            .queryParam("sort", "asc")
            .queryParam("sortField", "name")
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
    int total = 10;
    int tooManyRequests = 0;

    for (int i =1; i <= total; i++) {
      Response response = RestAssured
          .given()
          .header("Authorization", "Bearer " + token)
          .get("/api/v1/patients");

      System.out.printf("Request %d -> Status: %d%n", i,
          response.statusCode());

      if(response.statusCode()== 429){
        tooManyRequests++;
      }
      Thread.sleep(100);
    }

    assertTrue(tooManyRequests >= 1,
        "Expected at least 1 request to be rate limited (429)");
  }


  private static String getToken() {
    String loginPayload = """
          {
            "email": "testuser@test.com",
            "password": "password123"
          }
        """;

    String token = given()
        .contentType("application/json")
        .body(loginPayload)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .get("token");
    return token;
  }
}

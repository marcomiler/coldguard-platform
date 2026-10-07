package com.coldguard.gateway.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.infrastructure.DownstreamCallException;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserPage;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserView;
import io.grpc.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  private static final String ID = "3f1c2f7e-0000-4000-8000-000000000001";
  private static final UserView MARTA =
      new UserView(
          ID,
          "marta",
          "marta@example.com",
          "Marta",
          List.of("AUDITOR"),
          true,
          "2026-10-05T12:00:00Z");

  private static final String VALID_CREATE =
      """
      {"username":"marta","email":"marta@example.com","displayName":"Marta",
       "initialPassword":"long-enough-pw","roles":["AUDITOR"]}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IdentityGrpcClient identity;

  @Test
  void list_returnsThePageWithoutAnySecret() throws Exception {
    given(identity.listUsers(0, 0)).willReturn(new UserPage(List.of(MARTA), 0, 20, 1, 1));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].username").value("marta"))
        .andExpect(jsonPath("$.items[0].roles[0]").value("AUDITOR"))
        .andExpect(jsonPath("$.items[0].password").doesNotExist())
        .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void create_returns201WithLocationAndNeverEchoesThePassword() throws Exception {
    given(
            identity.createUser(
                "marta", "marta@example.com", "Marta", "long-enough-pw", List.of("AUDITOR")))
        .willReturn(MARTA);

    mockMvc
        .perform(post("/api/v1/users").contentType("application/json").content(VALID_CREATE))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/users/" + ID))
        .andExpect(jsonPath("$.userId").value(ID))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("long-enough-pw"))));
  }

  @Test
  void create_validatesTheShapeAndNeverEchoesTheValues() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content(
                    """
                    {"username":" ","email":"not-an-email","displayName":"M",
                     "initialPassword":"secret-pw-value","roles":[]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[?(@.field=='username')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='roles')]").exists())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-pw-value"))));
  }

  @Test
  void create_withAnUnknownRole_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content(VALID_CREATE.replace("AUDITOR", "ROOT")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
  }

  @Test
  void assignAndRevokeRole_returnTheUpdatedUser() throws Exception {
    given(identity.assignRole(ID, "OPERATOR", "cover")).willReturn(MARTA);
    given(identity.revokeRole(ID, "OPERATOR", "moved")).willReturn(MARTA);

    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/roles")
                .contentType("application/json")
                .content("{\"role\":\"OPERATOR\",\"reason\":\"cover\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/users/" + ID + "/roles")
                .contentType("application/json")
                .content("{\"role\":\"OPERATOR\",\"reason\":\"moved\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void roleChange_requiresARole_andAReason() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/roles")
                .contentType("application/json")
                .content("{\"reason\":\"cover\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/roles")
                .contentType("application/json")
                .content("{\"role\":\"OPERATOR\",\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void setEnabled_withoutTheFlag_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/enabled")
                .contentType("application/json")
                .content("{\"reason\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("enabled"));
  }

  @Test
  void setEnabled_passesTheFlagAndReason() throws Exception {
    given(identity.setUserEnabled(ID, false, "left")).willReturn(MARTA);

    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/enabled")
                .contentType("application/json")
                .content("{\"enabled\":false,\"reason\":\"left\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void identityRefusalsKeepTheirBusinessCodes() throws Exception {
    check(Status.Code.PERMISSION_DENIED, null, 403, "FORBIDDEN");
    check(Status.Code.NOT_FOUND, "USER_NOT_FOUND", 404, "USER_NOT_FOUND");
    check(Status.Code.ALREADY_EXISTS, "USER_ALREADY_EXISTS", 409, "USER_ALREADY_EXISTS");
    check(Status.Code.FAILED_PRECONDITION, "USER_STATE_CONFLICT", 409, "USER_STATE_CONFLICT");
    check(Status.Code.INVALID_ARGUMENT, null, 400, "INVALID_REQUEST");
  }

  private void check(Status.Code grpc, String published, int httpStatus, String code)
      throws Exception {
    willThrow(new DownstreamCallException("incident-service", grpc, published, "detail", null))
        .given(identity)
        .getUser(any());

    mockMvc
        .perform(get("/api/v1/users/" + ID))
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  void identityDown_isServiceUnavailable() throws Exception {
    given(identity.listUsers(anyInt(), anyInt()))
        .willThrow(
            new DownstreamCallException(
                "incident-service", Status.Code.UNAVAILABLE, null, "down", null));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
  }

  @Test
  void malformedBody_isBadRequestWithoutEchoingIt() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users").contentType("application/json").content("{not json: secret-pw"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-pw"))));
  }
}

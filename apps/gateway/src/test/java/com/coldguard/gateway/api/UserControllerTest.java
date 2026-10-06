package com.coldguard.gateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldguard.gateway.infrastructure.IdentityGrpcClient;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserPage;
import com.coldguard.gateway.infrastructure.IdentityGrpcClient.UserView;
import com.coldguard.gateway.infrastructure.IdentityServiceException;
import com.coldguard.gateway.infrastructure.UserAdministrationException;
import com.coldguard.gateway.infrastructure.UserAdministrationException.Kind;
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

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IdentityGrpcClient identity;

  @Test
  void list_returnsThePageWithoutAnySecret() throws Exception {
    given(identity.listUsers(0, 20)).willReturn(new UserPage(List.of(MARTA), 0, 20, 1, 1));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.users[0].username").value("marta"))
        .andExpect(jsonPath("$.users[0].roles[0]").value("AUDITOR"))
        .andExpect(jsonPath("$.users[0].password").doesNotExist())
        .andExpect(jsonPath("$.users[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void create_returns201AndNeverEchoesThePassword() throws Exception {
    given(
            identity.createUser(
                "marta", "marta@example.com", "Marta", "long-enough-pw", List.of("AUDITOR")))
        .willReturn(MARTA);

    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content(
                    """
                    {"username":"marta","email":"marta@example.com","displayName":"Marta",
                     "initialPassword":"long-enough-pw","roles":["AUDITOR"]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(ID))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("long-enough-pw"))));
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
  void setEnabled_withoutTheFlag_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/" + ID + "/enabled")
                .contentType("application/json")
                .content("{\"reason\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_USER_REQUEST"));
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
  void identityRefusalsMapToProblemDetails() throws Exception {
    check(Kind.FORBIDDEN, 403, "USER_ADMIN_FORBIDDEN");
    check(Kind.NOT_FOUND, 404, "USER_NOT_FOUND");
    check(Kind.ALREADY_EXISTS, 409, "USER_ALREADY_EXISTS");
    check(Kind.CONFLICT, 409, "USER_STATE_CONFLICT");
    check(Kind.INVALID, 400, "INVALID_USER_REQUEST");
  }

  private void check(Kind kind, int httpStatus, String code) throws Exception {
    willThrow(new UserAdministrationException(kind, "detail")).given(identity).getUser(any());

    mockMvc
        .perform(get("/api/v1/users/" + ID))
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  void identityDown_isBadGateway() throws Exception {
    given(identity.listUsers(anyInt(), anyInt()))
        .willThrow(new IdentityServiceException("down", null));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("IDENTITY_SERVICE_UNAVAILABLE"));
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

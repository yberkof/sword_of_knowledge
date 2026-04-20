package com.sok.backend.docs;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sok.backend.api.AuthController;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.config.PublicApiPaths;
import com.sok.backend.service.AuthLinkService;
import com.sok.backend.service.AuthService;
import com.sok.backend.service.AuthTokenService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP auth routes from {@code docs/new-client-path.md}; run via {@code mvn test}. */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class NewClientPathAuthMvcTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private AuthService authService;
  @MockBean private AuthLinkService authLinkService;
  @MockBean private AuthTokenService authTokenService;

  @Test
  void permitAllIncludesAuthPrefix() {
    boolean found = false;
    for (String p : PublicApiPaths.PERMIT_ALL) {
      if ("/api/auth/**".equals(p)) {
        found = true;
        break;
      }
    }
    Assertions.assertTrue(found);
  }

  @Test
  void exchangeRequiresIdToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("idToken is required"));
  }

  @Test
  void exchangeUnavailableWhenIdentityNotConfigured() throws Exception {
    when(authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)).thenReturn(false);
    mockMvc
        .perform(
            post("/api/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"x\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("identity_not_configured"));
  }

  @Test
  void tokenRequiresGrantType() throws Exception {
    mockMvc
        .perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("grantType is required"));
  }

  @Test
  void loginRequiresEmailAndPassword() throws Exception {
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("email and password are required"));
  }

  @Test
  void refreshRequiresRefreshToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("refreshToken is required"));
  }

  @Test
  void logoutRequiresRefreshToken() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.ok").value(false));
  }
}

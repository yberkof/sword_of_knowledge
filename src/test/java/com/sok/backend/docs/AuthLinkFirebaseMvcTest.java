package com.sok.backend.docs;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sok.backend.api.AuthController;
import com.sok.backend.auth.identity.AuthGrantTypes;
import com.sok.backend.security.AuthenticatedUser;
import com.sok.backend.service.AuthLinkService;
import com.sok.backend.service.AuthService;
import com.sok.backend.service.AuthTokenService;
import com.sok.backend.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthLinkFirebaseMvcTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private AuthService authService;
  @MockBean private AuthLinkService authLinkService;
  @MockBean private AuthTokenService authTokenService;
  @MockBean private RateLimitService rateLimitService;

  @BeforeEach
  void authenticate() {
    when(rateLimitService.allow(anyString(), anyInt(), anyLong())).thenReturn(true);
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new AuthenticatedUser("email-user-1"), null);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void linkFirebaseRequiresIdToken() throws Exception {
    when(authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)).thenReturn(true);
    mockMvc
        .perform(post("/api/auth/link/firebase").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("idToken is required"));
  }

  @Test
  void linkFirebaseDelegatesToService() throws Exception {
    when(authService.grantAvailable(AuthGrantTypes.GOOGLE_FIREBASE_ID_TOKEN)).thenReturn(true);
    mockMvc
        .perform(
            post("/api/auth/link/firebase")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"firebase-jwt-here\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
    verify(authLinkService).linkFirebaseForCurrentUser(eq("email-user-1"), eq("firebase-jwt-here"));
  }
}

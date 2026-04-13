package com.sok.backend.docs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sok.backend.api.ProfileController;
import com.sok.backend.api.dto.ProfileResponse;
import com.sok.backend.security.AuthenticatedUser;
import com.sok.backend.service.AuthTokenService;
import com.sok.backend.service.ProfileService;
import java.util.Collections;
import java.util.Optional;
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

/** Profile bootstrap from {@code docs/new-client-path.md}; run via {@code mvn test}. */
@WebMvcTest(controllers = ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class NewClientPathProfileMvcTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private AuthTokenService authTokenService;
  @MockBean private ProfileService profileService;

  @BeforeEach
  void authenticate() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(new AuthenticatedUser("firebase-uid-1"), null);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getProfile404WhenNewUser() throws Exception {
    when(profileService.getProfile("firebase-uid-1")).thenReturn(Optional.empty());
    mockMvc
        .perform(get("/api/profile"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not found"));
  }

  @Test
  void postProfileCreates201() throws Exception {
    ProfileResponse created =
        new ProfileResponse(
            "firebase-uid-1",
            "N",
            "u1",
            "",
            "",
            1,
            0,
            0,
            0,
            "",
            "",
            0,
            Collections.<String>emptyList());
    when(profileService.createIfMissing(anyString(), any())).thenReturn(created);
    mockMvc
        .perform(
            post("/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"N\",\"username\":\"u1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").value("firebase-uid-1"));
  }
}

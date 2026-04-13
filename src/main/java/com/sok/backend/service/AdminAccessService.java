package com.sok.backend.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
  private final Set<String> adminUids = new HashSet<String>();

  public AdminAccessService(@Value("${app.admin-uids:}") String adminUidsCsv) {
    if (adminUidsCsv != null && !adminUidsCsv.trim().isEmpty()) {
      adminUids.addAll(Arrays.asList(adminUidsCsv.split(",")));
    }
  }

  public boolean isAdmin(String uid) {
    if (uid == null) return false;
    return adminUids.contains(uid.trim());
  }
}

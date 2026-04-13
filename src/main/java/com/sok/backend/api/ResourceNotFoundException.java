package com.sok.backend.api;

/** Domain resource missing (e.g. catalog item). */
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}

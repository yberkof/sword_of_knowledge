package com.sok.backend.api;

/** Thrown when authentication failed or credentials are invalid. */
public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}

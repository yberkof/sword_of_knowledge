package com.sok.backend.api;

public class BadRequestException extends RuntimeException {
  private final Object body;

  public BadRequestException(Object body) {
    this.body = body;
  }

  public Object body() {
    return body;
  }
}

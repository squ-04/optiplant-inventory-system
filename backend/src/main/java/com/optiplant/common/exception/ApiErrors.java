package com.optiplant.common.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice
public class ApiErrors {
    public static ProblemDetail problem(int status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setProperty("code", code);
        return problem;
    }

    public static void write(HttpServletResponse response, JsonMapper json, int status,
                             String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getOutputStream().write(json.writeValueAsBytes(problem(status, code, detail)));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> application(ApiException error) {
        return ResponseEntity.status(error.status()).body(problem(error.status(), error.code(), error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalid(Exception error) {
        return ResponseEntity.badRequest().body(problem(400, "VALIDATION_FAILED", "La solicitud no es válida."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(405).body(problem(405, "METHOD_NOT_ALLOWED", "La operación no está disponible para este recurso."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error) {
        return ResponseEntity.internalServerError().body(problem(500, "INTERNAL_ERROR", "No se pudo completar la solicitud."));
    }
}

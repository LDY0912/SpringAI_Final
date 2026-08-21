package com.skala.helpdesk.web;

import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HelpDeskExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ProblemDetail badRequest(Exception error) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("잘못된 실습 요청");
        problem.setDetail(error.getMessage());
        return problem;
    }

    /** 거절한 초장문·개인정보 원문을 오류 응답이나 로그에 다시 싣지 않는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException error) {
        String detail = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("잘못된 실습 요청");
        problem.setDetail(detail.isBlank() ? "요청 본문을 확인해 주세요." : detail);
        return problem;
    }
}

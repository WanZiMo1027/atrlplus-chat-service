package com.atriplus.chat.controller;

import feign.FeignException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 管理 API 只返回可展示的错误，不透传 TencentDB 或内部服务响应正文。 */
@RestControllerAdvice(assignableTypes = {L1AdminApiController.class, GroupReplyConfigController.class})
public class AdminApiExceptionHandler {
    public record ApiError(String message) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError(exception.getReason()));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> upstream(FeignException exception) {
        HttpStatus status = switch (exception.status()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String message = status == HttpStatus.BAD_GATEWAY
                ? "Agent 或 TencentDB 暂时不可用" : "远端请求未完成，请刷新后重试";
        return ResponseEntity.status(status).body(new ApiError(message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> database(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("数据库暂时不可用，请稍后重试"));
    }
}

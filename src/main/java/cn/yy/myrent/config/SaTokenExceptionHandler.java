package cn.yy.myrent.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.yy.myrent.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, "please login first"));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(NotPermissionException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, "no permission"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(e.getMessage()));
    }
}

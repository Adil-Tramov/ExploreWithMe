package ru.practicum.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ErrorHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(final MethodArgumentNotValidException e) {
        log.info("400 {}", e.getMessage(), e);
        String errorMessage = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        ApiError apiError = buildApiError(HttpStatus.BAD_REQUEST, "Incorrectly made request.", errorMessage, e);
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameterException(final MissingServletRequestParameterException e) {
        log.info("400 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.BAD_REQUEST, "Required request parameter for method parameter is not present.", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(final IllegalArgumentException e) {
        log.info("400 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.BAD_REQUEST, "Illegal argument.", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConditionsNotMetException.class)
    public ResponseEntity<ApiError> handleOperationConditionsNotMetException(final ConditionsNotMetException e) {
        log.info("400 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.BAD_REQUEST, "For the requested operation the conditions are not met.", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(final NotFoundException e) {
        log.info("404 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.NOT_FOUND, "The required object was not found.", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({AlreadyExistsException.class, DataIntegrityViolationException.class, ConflictException.class})
    public ResponseEntity<ApiError> handleConflictException(final RuntimeException e) {
        log.info("409 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.CONFLICT, "Integrity constraint has been violated.", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeignException(final FeignException e) {
        log.info("Feign error: status={}, message={}", e.status(), e.getMessage());

        String errorMessage = extractErrorMessage(e);
        HttpStatus status = HttpStatus.valueOf(e.status());

        ApiError apiError = buildApiError(
                status,
                status.getReasonPhrase(),
                errorMessage,
                e
        );

        return new ResponseEntity<>(apiError, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(final Exception e) {
        log.error("500 {}", e.getMessage(), e);
        ApiError apiError = buildApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Error occurred", e.getMessage(), e);
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ApiError buildApiError(HttpStatus status, String reason, String message, Exception e) {
        return ApiError.builder()
                .status(status)
                .reason(reason)
                .message(message)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .stackTrace(getStackTrace(e))
                .build();
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private String extractErrorMessage(FeignException e) {
        try {
            if (e.responseBody().isPresent()) {
                byte[] body = e.responseBody().get().array();
                String bodyString = new String(body);
                log.debug("Feign error body: {}", bodyString);

                try {
                    var jsonNode = objectMapper.readTree(bodyString);
                    if (jsonNode.has("message")) {
                        return jsonNode.get("message").asText();
                    }
                    if (jsonNode.has("reason")) {
                        return jsonNode.get("reason").asText();
                    }
                } catch (Exception ex) {
                    log.debug("Failed to parse Feign error body as JSON", ex);
                }

                return bodyString;
            }
        } catch (Exception ex) {
            log.warn("Failed to extract error message from Feign exception", ex);
        }

        return e.getMessage();
    }
}
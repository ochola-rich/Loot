package com.loot.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void tournamentNotFoundMapsTo404() {
        ResponseEntity<ApiError> response = handler.handleTournamentNotFound(new TournamentNotFoundException(7));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorCode()).isEqualTo("TOURNAMENT_NOT_FOUND");
        assertThat(response.getBody().message()).contains("7");
    }

    @Test
    void paymentFailedMapsTo402WithGatewayMessage() {
        ResponseEntity<ApiError> response = handler.handlePaymentFailed(new PaymentFailedException("Insufficient funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody().errorCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.getBody().message()).isEqualTo("Insufficient funds");
    }

    @Test
    void validationFailureMapsTo400WithFieldDetails() {
        FieldError fieldError = new FieldError("request", "playerPhone", "must be an E.164 phone number");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().message()).contains("playerPhone").contains("E.164");
    }

    @Test
    void responseStatusExceptionPassesThroughItsOwnStatusAndReason() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Tournament 1 is not OPEN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Tournament 1 is not OPEN");
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingItsMessage() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("db connection leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("db connection leaked");
    }

    @Test
    void includesTraceIdFromMdcWhenPresent() {
        MDC.put("traceId", "trace-123");

        ResponseEntity<ApiError> response = handler.handleTournamentNotFound(new TournamentNotFoundException(1));

        assertThat(response.getBody().traceId()).isEqualTo("trace-123");
    }

    @Test
    void traceIdIsNullWhenMdcIsEmpty() {
        ResponseEntity<ApiError> response = handler.handleTournamentNotFound(new TournamentNotFoundException(1));

        assertThat(response.getBody().traceId()).isNull();
    }
}

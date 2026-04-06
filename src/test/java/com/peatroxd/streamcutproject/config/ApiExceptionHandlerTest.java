package com.peatroxd.streamcutproject.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler("1KB");

    @Test
    void rendersStablePayloadForOversizeUpload() {
        HttpServletRequest request = request("/api/jobs/upload");

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(1024),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().error()).isEqualTo("Payload Too Large");
        assertThat(response.getBody().message()).isEqualTo("Upload exceeds the 1 KB limit.");
        assertThat(response.getBody().path()).isEqualTo("/api/jobs/upload");
    }

    @Test
    void rendersStablePayloadForResponseStatusException() {
        HttpServletRequest request = request("/api/jobs/upload");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploaded file must not be empty"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("uploaded file must not be empty");
        assertThat(response.getBody().path()).isEqualTo("/api/jobs/upload");
    }

    private static HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}

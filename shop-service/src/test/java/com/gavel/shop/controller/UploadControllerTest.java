package com.gavel.shop.controller;

import com.gavel.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock private ShopService shopService;
    @Mock private Authentication auth;
    @Mock private MultipartFile file;

    private UploadController controller;

    @BeforeEach
    void setUp() {
        controller = new UploadController(shopService);
    }

    @Test
    void upload_sellerRole_returnsUrl() throws Exception {
        when(auth.getDetails()).thenReturn(Map.of("role", "seller"));
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
        when(file.getSize()).thenReturn(10L);
        when(shopService.uploadImage(eq("image/jpeg"), any(), eq(10L)))
                .thenReturn("http://localhost/uploads/img.jpg");

        ResponseEntity<?> response = controller.uploadImage(file, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("url", "http://localhost/uploads/img.jpg");
    }

    @Test
    void upload_nonSellerRole_returnsForbidden() {
        when(auth.getDetails()).thenReturn(Map.of("role", "buyer"));

        ResponseEntity<?> response = controller.uploadImage(file, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void upload_ioException_returns500() throws Exception {
        when(auth.getDetails()).thenReturn(Map.of("role", "seller"));
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream()).thenThrow(new IOException("disk error"));

        ResponseEntity<?> response = controller.uploadImage(file, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

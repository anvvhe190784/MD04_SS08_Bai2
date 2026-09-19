package com.example.appointmentservice;

import com.example.appointmentservice.dto.ApiResponseError;
import com.example.appointmentservice.dto.DoctorScheduleDto;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AppointmentFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private RestTemplate restTemplate;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("doctorServiceCB");
        circuitBreaker.reset();
    }

    @Test
    @DisplayName("[Bài tập 2] Khi Doctor-Service bị sập, POST /api/v1/appointments trả về 503 và ApiResponseError chuẩn")
    void testFallbackWhenDoctorServiceIsDown() throws Exception {
        // Mô phỏng Doctor-Service không phản hồi (Connection refused)
        when(restTemplate.getForObject(anyString(), eq(DoctorScheduleDto.class)))
                .thenThrow(new ResourceAccessException("Connection refused: connect"));

        String requestJson = """
                {
                    "patientName": "Nguyễn Văn A",
                    "doctorId": 1,
                    "reason": "Đau bụng"
                }
                """;

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Doctor Service Error"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("[Bài tập 2] Kiểm tra method checkDoctor trong service kích hoạt getDoctorFallback trả về ApiResponseError")
    void testDirectServiceFallbackMethod() {
        when(restTemplate.getForObject(anyString(), eq(DoctorScheduleDto.class)))
                .thenThrow(new ResourceAccessException("Service Unavailable"));

        Object result = appointmentService.checkDoctor(1L);

        assertNotNull(result);
        assertInstanceOf(ApiResponseError.class, result);
        ApiResponseError error = (ApiResponseError) result;
        assertEquals("Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây", error.getMessage());
        assertEquals(503, error.getStatus());
        assertEquals("Doctor Service Error", error.getError());
    }

    @Test
    @DisplayName("[Bài tập 2] Khi Doctor-Service hoạt động bình thường, POST /api/v1/appointments thành công 200 OK")
    void testSuccessWhenDoctorServiceIsUp() throws Exception {
        DoctorScheduleDto mockDto = new DoctorScheduleDto(1L, "Dr. Nguyen Van A", "Tim mach", "08:00 - 11:30", true);
        when(restTemplate.getForObject(anyString(), eq(DoctorScheduleDto.class))).thenReturn(mockDto);

        String requestJson = """
                {
                    "patientName": "Nguyễn Văn A",
                    "doctorId": 1,
                    "reason": "Đau bụng"
                }
                """;

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.doctorSchedule.doctorName").value("Dr. Nguyen Van A"));
    }
}

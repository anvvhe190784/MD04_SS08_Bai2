package com.example.appointmentservice.controller;

import com.example.appointmentservice.dto.ApiResponseError;
import com.example.appointmentservice.dto.AppointmentRequest;
import com.example.appointmentservice.dto.AppointmentResponse;
import com.example.appointmentservice.dto.DoctorScheduleDto;
import com.example.appointmentservice.service.AppointmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * [Bài tập 2] API đặt lịch hẹn: POST /api/v1/appointments (hoặc /appointments/book)
     * Gọi service kiểm tra bác sĩ (có Circuit Breaker & Fallback trả về ApiResponseError)
     * Khi doctor-service sập hoặc Circuit Breaker mở -> trả về ApiResponseError với HTTP 503
     */
    @PostMapping(value = {"/api/v1/appointments", "/appointments/book"})
    public ResponseEntity<?> createAppointment(@RequestBody AppointmentRequest request) {
        Object doctorResult = appointmentService.checkDoctor(request.getDoctorId());
        if (doctorResult instanceof ApiResponseError error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }

        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [Bài tập 1] API kiểm tra lịch trực bác sĩ
     */
    @GetMapping("/appointments/check-doctor/{doctorId}")
    public ResponseEntity<AppointmentResponse> checkDoctorSchedule(@PathVariable Long doctorId) {
        DoctorScheduleDto schedule = appointmentService.getDoctorSchedule(doctorId);
        String cbState = appointmentService.getCircuitBreakerState();

        boolean isSuccess = schedule != null && schedule.isAvailable() && !schedule.getDoctorName().startsWith("N/A");
        String message = isSuccess
                ? "Lay thong tin lich truc bac si thanh cong."
                : "Khong the lay lich truc bac si (Doctor-Service sap hoac Circuit Breaker dang OPEN).";

        return ResponseEntity.ok(new AppointmentResponse(
                isSuccess,
                message,
                null,
                schedule,
                cbState
        ));
    }

    /**
     * API kiểm tra trạng thái và metrics của Circuit Breaker doctorServiceCB
     */
    @GetMapping("/appointments/circuit-breaker-status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        return ResponseEntity.ok(appointmentService.getCircuitBreakerDetails());
    }
}

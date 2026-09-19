package com.example.appointmentservice.dto;

import java.time.LocalDateTime;

public class ApiResponseError {
    private String message;
    private int status;
    private String error;
    private String timestamp;

    public ApiResponseError() {
    }

    public ApiResponseError(String message, int status, String error, String timestamp) {
        this.message = message;
        this.status = status;
        this.error = error;
        this.timestamp = timestamp;
    }

    public static ApiResponseError ofDoctorServiceError() {
        return new ApiResponseError(
                "Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây",
                503,
                "Doctor Service Error",
                LocalDateTime.now().toString()
        );
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

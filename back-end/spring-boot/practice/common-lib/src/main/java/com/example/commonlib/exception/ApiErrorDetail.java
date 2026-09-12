package com.example.commonlib.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorDetail {
    private String field;
    private String message;
    private Object rejectedValue;
}

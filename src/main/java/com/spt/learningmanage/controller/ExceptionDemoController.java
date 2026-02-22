package com.spt.learningmanage.controller;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class ExceptionDemoController {

    @GetMapping("/demo/error/business")
    public void businessError() {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Demo business exception");
    }

    @GetMapping("/demo/error/system")
    public void systemError() {
        throw new RuntimeException("Demo system exception");
    }

    @GetMapping("/demo/error/validate")
    public void validateError(@RequestParam @Min(value = 1, message = "value must be greater than or equal to 1") int value) {
        // Empty implementation on purpose. Invalid input triggers global validation handling.
    }
}

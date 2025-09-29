package com.yujian.yupicturebackend.controller;

import com.yujian.yupicturebackend.service.verification.VerificationOrchestrator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationOrchestrator verificationOrchestrator;

    public VerificationController(VerificationOrchestrator verificationOrchestrator) {
        this.verificationOrchestrator = verificationOrchestrator;
    }

    @PostMapping("/run")
    public VerificationOrchestrator.VerificationSummary runVerification() throws ExecutionException, InterruptedException {
        return verificationOrchestrator.runVerificationProcess();
    }
}

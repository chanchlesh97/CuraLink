package com.curalink.patientservice.aspects;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PatientServiceMetrics {
    private final MeterRegistry meterRegistry;

    public PatientServiceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* com.curalink.patientservice.service.PatientService.getPatients(..))")
    public Object monitorGetPatient(ProceedingJoinPoint joinPoint) throws Throwable {
        meterRegistry.counter("custom.redis.cache.miss", "cache", "patients").increment();
        Object result = joinPoint.proceed();
        return result;
    }

}

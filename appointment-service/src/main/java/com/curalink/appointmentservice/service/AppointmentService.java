package com.curalink.appointmentservice.service;

import com.curalink.appointmentservice.dto.AppointmentResponseDto;
import com.curalink.appointmentservice.entity.Appointment;
import com.curalink.appointmentservice.mapper.AppointmentMapper;
import com.curalink.appointmentservice.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<AppointmentResponseDto> getAppointmentsByDateRange(
            LocalDateTime from, LocalDateTime to
    ) {
        return appointmentRepository.findByStartTimeBetween(from, to)
                .stream()
                .map( appointment -> {
                    AppointmentResponseDto appointmentResponseDto = new AppointmentResponseDto();
                    appointmentResponseDto.setId(appointment.getId());
                    appointmentResponseDto.setVersion(appointment.getVersion());
                    appointmentResponseDto.setStartTime(appointment.getStartTime());
                    appointmentResponseDto.setEndTime(appointment.getEndTime());
                    appointmentResponseDto.setReason(appointment.getReason());
                    appointmentResponseDto.setPatientId(appointment.getPatientId());
                    appointmentResponseDto.setReason(appointment.getReason());
                    return appointmentResponseDto;
                })
                .toList();
    }
}

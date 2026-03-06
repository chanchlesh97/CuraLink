package com.curalink.appointmentservice.mapper;

import com.curalink.appointmentservice.dto.AppointmentRequestDto;
import com.curalink.appointmentservice.dto.AppointmentResponseDto;
import com.curalink.appointmentservice.entity.Appointment;

public class AppointmentMapper {
    public static AppointmentResponseDto toDto(Appointment appointment) {
        return new AppointmentResponseDto(
                appointment.getId(),
                appointment.getPatientId(),
                "",
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getReason(),
                appointment.getVersion()
        );
    }

//    public static Appointment toEntity(AppointmentRequestDto appointmentRequestDto) {
//        return new Appointment(
//                appointmentRequestDto.get
//        )
//    }
}



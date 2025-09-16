package com.curalink.patientservice.controller;

import com.curalink.patientservice.DTO.PatientRequestDTO;
import com.curalink.patientservice.DTO.PatientResponseDTO;
import com.curalink.patientservice.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
    private final PatientService patientService;
    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public ResponseEntity<List<PatientResponseDTO>> getPatients(){
        List<PatientResponseDTO> patients = patientService.getPatients();
        return ResponseEntity.ok().body(patients);
    }

    @PostMapping
    public ResponseEntity<PatientResponseDTO> createPatient(@Valid @RequestBody PatientRequestDTO patientRequestDTO){
        PatientResponseDTO newPatientDto = this.patientService.addPatient(patientRequestDTO);
        return ResponseEntity.ok().body(newPatientDto);
    }
}

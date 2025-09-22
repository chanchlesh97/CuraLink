package com.curalink.patientservice.service;

import billing.BillingAccountResponse;
import com.curalink.patientservice.DTO.PatientRequestDTO;
import com.curalink.patientservice.DTO.PatientResponseDTO;
import com.curalink.patientservice.exception.EmailAlreadyExistsException;
import com.curalink.patientservice.exception.PatientNotFoundException;
import com.curalink.patientservice.grpc.BillingServiceGrpcClient;
import com.curalink.patientservice.mapper.PatientMapper;
import com.curalink.patientservice.model.Patient;
import com.curalink.patientservice.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    PatientRepository patientRepository;
    BillingServiceGrpcClient billingServiceGrpcClient;

    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
    }

    public List<PatientResponseDTO> getPatients(){
        List<Patient> patients = patientRepository.findAll();
        return patients.stream().map(PatientMapper::toDTO).toList();
    }

    public PatientResponseDTO addPatient(PatientRequestDTO patientRequestDTO){
            if(patientRepository.existsByEmail(patientRequestDTO.getEmail())){
                throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientRequestDTO.getEmail());
            }
        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));
          BillingAccountResponse response = billingServiceGrpcClient.createBillingAccount(newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());
          System.out.println("Billing Account created: " + response.toString());
        return PatientMapper.toDTO(newPatient);
    }

    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO){
        // Implementation for updating a patient
        Patient patient = patientRepository.findById(id).orElseThrow(
                () -> new PatientNotFoundException("Patient not found with ID: " + id)
        );
        if(patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(), id)){
            throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientRequestDTO.getEmail());
        }
        patient.setName(patientRequestDTO.getName());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setDateOfBirth(patientRequestDTO.getDateOfBirth() != null ? java.time.LocalDate.parse(patientRequestDTO.getDateOfBirth()) : patient.getDateOfBirth());
        Patient updatedPatient = patientRepository.save(patient);
        return PatientMapper.toDTO(updatedPatient);
    }

    public void deletePatient(UUID id){
        if(!patientRepository.existsById(id)){
            throw new PatientNotFoundException("Patient not found with ID: " + id);
        }
        patientRepository.deleteById(id);
    }

}

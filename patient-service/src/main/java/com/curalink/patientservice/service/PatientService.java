package com.curalink.patientservice.service;

import billing.BillingAccountResponse;
import com.curalink.patientservice.DTO.PagedPatientResponseDTO;
import com.curalink.patientservice.DTO.PatientRequestDTO;
import com.curalink.patientservice.DTO.PatientResponseDTO;
import com.curalink.patientservice.exception.EmailAlreadyExistsException;
import com.curalink.patientservice.exception.PatientNotFoundException;
import com.curalink.patientservice.grpc.BillingServiceGrpcClient;
import com.curalink.patientservice.kafka.KafkaProducer;
import com.curalink.patientservice.mapper.PatientMapper;
import com.curalink.patientservice.model.Patient;
import com.curalink.patientservice.repository.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(
            PatientRepository patientRepository,
            BillingServiceGrpcClient billingServiceGrpcClient,
            KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    @Cacheable(
            value = "patients",
            key = "#page + '-' + #size + '-' + #sort + '-' + #sortField",
            condition = "#searchValue == ''"
    )
    public PagedPatientResponseDTO getPatients(int page, int size, String sort, String sortField, String searchValue){
        log.info("[REDIS]: Cache miss - fetching patients from DB.");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("Error sleeping thread: " + e.getMessage());
        }
        Pageable pageable = PageRequest.of( page-1, size, sort.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending() );

        Page<Patient> patientPage;
        if (searchValue == null || searchValue.isBlank()) {
            patientPage = patientRepository.findAll(pageable);
        } else {
            patientPage = patientRepository.findByNameContainingIgnoreCase(searchValue, pageable);
        }
        List<PatientResponseDTO> patients = patientPage.getContent().stream().map(PatientMapper::toDTO).toList();
        return new PagedPatientResponseDTO(
                patients,
                patientPage.getNumber()+1,
                patientPage.getSize(),
                patientPage.getTotalPages(),
                (int)patientPage.getTotalElements()
        );
    }

    public PatientResponseDTO addPatient(PatientRequestDTO patientRequestDTO){
        if(patientRepository.existsByEmail(patientRequestDTO.getEmail())){
            throw new EmailAlreadyExistsException("A patient with this email already exists: " + patientRequestDTO.getEmail());
        }
        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));
        BillingAccountResponse response = billingServiceGrpcClient.createBillingAccount(newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());
        System.out.println("Billing Account created: " + response.toString());
        kafkaProducer.sendEvent(newPatient);
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

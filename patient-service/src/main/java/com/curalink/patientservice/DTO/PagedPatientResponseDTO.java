package com.curalink.patientservice.DTO;

import java.util.List;

public class PagedPatientResponseDTO {
    public PagedPatientResponseDTO() {
    }

    public PagedPatientResponseDTO(List<PatientResponseDTO> patients, int page, int size, int totalPages, long totalElements) {
        this.patients = patients;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }

    private List<PatientResponseDTO> patients;
    private int page;
    private int size;
    private int totalPages;
    private long totalElements;


    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public List<PatientResponseDTO> getPatients() {
        return patients;
    }

    public void setPatients(List<PatientResponseDTO> patients) {
        this.patients = patients;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}
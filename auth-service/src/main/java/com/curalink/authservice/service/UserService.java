package com.curalink.authservice.service;

import com.curalink.authservice.model.User;
import com.curalink.authservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    public Optional<User> findByEmail(String email) {
        // Implement your user retrieval logic here
        Optional<User> user = userRepository.findByEmail(email);
        return user;
    }
}

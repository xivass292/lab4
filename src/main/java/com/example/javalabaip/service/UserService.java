package com.example.javalabaip.service;

import com.example.javalabaip.cache.CacheManager;
import com.example.javalabaip.dto.UserDto;
import com.example.javalabaip.model.User;
import com.example.javalabaip.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    public UserService(UserRepository userRepository, CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        String cacheKey = "findAll";
        if (cacheManager.containsUserListKey(cacheKey)) {
            return cacheManager.getUserList(cacheKey);
        }

        List<UserDto> result = userRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        cacheManager.putUserList(cacheKey, result);
        return result;
    }

    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        if (cacheManager.containsUserKey(id)) {
            return cacheManager.getUser(id);
        }

        User user = userRepository.findByIdWithLocations(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        UserDto result = convertToDto(user);
        cacheManager.putUser(id, result);
        return result;
    }

    @Transactional(readOnly = true)
    public UserDto findByUsername(String username) {
        String cacheKey = "findByUsername:" + username;
        if (cacheManager.containsUserListKey(cacheKey)) {
            return cacheManager.getUserList(cacheKey).get(0); // Assuming single result
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
        UserDto result = convertToDto(user);
        cacheManager.putUserList(cacheKey, List.of(result));
        cacheManager.putUser(user.getId(), result); // Also cache by ID
        return result;
    }

    @Transactional
    public UserDto create(UserDto userDto) {
        User user = new User();
        user.setUsername(userDto.getUsername());
        User savedUser = userRepository.save(user);
        UserDto result = convertToDto(savedUser);
        cacheManager.clearAllCache(); // Clear all caches
        return result;
    }

    @Transactional
    public UserDto update(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        String oldUsername = user.getUsername();
        user.setUsername(userDto.getUsername());
        User updatedUser = userRepository.save(user);
        UserDto result = convertToDto(updatedUser);
        cacheManager.clearAllCache(); // Clear all caches
        return result;
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        cacheManager.clearAllCache(); // Clear all caches
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }
}
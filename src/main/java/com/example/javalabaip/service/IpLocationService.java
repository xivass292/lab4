package com.example.javalabaip.service;

import com.example.javalabaip.cache.CacheManager;
import com.example.javalabaip.dto.LocationResponseDto;
import com.example.javalabaip.dto.UserDto;
import com.example.javalabaip.model.Location;
import com.example.javalabaip.model.User;
import com.example.javalabaip.repository.LocationRepository;
import com.example.javalabaip.repository.UserRepository;
import com.example.javalabaip.util.IpAddressValidator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IpLocationService {

    private static final Logger logger = LoggerFactory.getLogger(IpLocationService.class);
    private final RestTemplate restTemplate;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    @Autowired
    public IpLocationService(RestTemplate restTemplate, LocationRepository locationRepository, UserRepository userRepository, CacheManager cacheManager) {
        this.restTemplate = restTemplate;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public List<LocationResponseDto> findAll() {
        String cacheKey = "findAll";
        if (cacheManager.containsLocationListKey(cacheKey)) {
            return cacheManager.getLocationList(cacheKey);
        }

        List<LocationResponseDto> result = locationRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        cacheManager.putLocationList(cacheKey, result);
        return result;
    }

    @Transactional(readOnly = true)
    public LocationResponseDto findById(Long id) {
        if (cacheManager.containsLocationKey(id)) {
            return cacheManager.getLocation(id);
        }

        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Location not found with id: " + id));
        LocationResponseDto result = convertToDto(location);
        cacheManager.putLocation(id, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<LocationResponseDto> findByUsername(String username) {
        String cacheKey = "findByUsername:" + username;
        if (cacheManager.containsLocationListKey(cacheKey)) {
            return cacheManager.getLocationList(cacheKey);
        }

        List<Location> locations = locationRepository.findByUsername(username);
        List<LocationResponseDto> result = locations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        cacheManager.putLocationList(cacheKey, result);
        return result;
    }

    @Transactional
    public LocationResponseDto create(String ipAddress, UserDto userDto) {
        if (!IpAddressValidator.getInstance().isValidIpAddress(ipAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный формат IP-адреса: " + ipAddress);
        }

        User user = userRepository.findByUsername(userDto.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + userDto.getUsername()));

        try {
            String apiUrl = "http://ip-api.com/json/" + ipAddress;
            Location location = restTemplate.getForObject(apiUrl, Location.class);

            if (location == null || location.getCity() == null || location.getCountry() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный IP-адрес или ошибка API");
            }

            location.setIpAddress(ipAddress);
            location.setUser(user);
            Location savedLocation = locationRepository.save(location);
            LocationResponseDto result = convertToDto(savedLocation);
            cacheManager.invalidateLocationCache(savedLocation.getId(), userDto.getUsername());
            return result;
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный IP-адрес: " + ipAddress, e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка получения данных о местоположении", e);
        }
    }

    @Transactional
    public LocationResponseDto update(Long id, LocationResponseDto locationDto) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Location not found with id: " + id));
        location.setIpAddress(locationDto.getIpAddress());
        location.setCity(locationDto.getCity());
        location.setCountry(locationDto.getCountry());
        location.setContinent(locationDto.getContinent());
        location.setLatitude(locationDto.getLatitude());
        location.setLongitude(locationDto.getLongitude());
        location.setTimezone(locationDto.getTimezone());
        Location updatedLocation = locationRepository.save(location);
        LocationResponseDto result = convertToDto(updatedLocation);
        cacheManager.invalidateLocationCache(id, location.getUser().getUsername());
        return result;
    }

    @Transactional
    public void delete(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Location not found with id: " + id));
        String username = location.getUser().getUsername();
        locationRepository.deleteById(id);
        cacheManager.invalidateLocationCache(id, username);
    }

    private LocationResponseDto convertToDto(Location location) {
        LocationResponseDto dto = new LocationResponseDto();
        dto.setIpAddress(location.getIpAddress());
        dto.setCity(location.getCity());
        dto.setCountry(location.getCountry());
        dto.setContinent(location.getContinent());
        dto.setLatitude(location.getLatitude());
        dto.setLongitude(location.getLongitude());
        dto.setTimezone(location.getTimezone());
        return dto;
    }
}
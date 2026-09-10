package com.example.airportManager.service.impl;

import com.example.airportManager.dto.profile.PassengerProfileResponseDTO;
import com.example.airportManager.dto.profile.PassengerProfileUpdateDTO;
import com.example.airportManager.model.PassengerProfile;
import com.example.airportManager.model.User;
import com.example.airportManager.repository.PassengerProfileRepository;
import com.example.airportManager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerProfileServiceImplTest {

    @Mock
    private PassengerProfileRepository passengerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PassengerProfileServiceImpl passengerProfileService;

    @Test
    void updateMyProfile_shouldUpdateAndReturnProfile_whenUserAndProfileExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("ion@test.com");
        user.setPhone("0722000000");

        PassengerProfile profile = new PassengerProfile();
        profile.setUserId(userId);

        PassengerProfileUpdateDTO dto =
                new PassengerProfileUpdateDTO("PASSPORT", "AB9999", "RO", "GOLD", "0733000000");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passengerProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        // save() intoarce exact obiectul primit, ca in majoritatea repository-urilor JPA reale.
        when(passengerProfileRepository.save(profile)).thenReturn(profile);

        // Act
        PassengerProfileResponseDTO result = passengerProfileService.updateMyProfile(userId, dto);

        // Assert: aici verificam continutul DTO-ului construit manual in service (nu exista mapper).
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.docType()).isEqualTo("PASSPORT");
        assertThat(result.docNumber()).isEqualTo("AB9999");
        assertThat(result.loyaltyTier()).isEqualTo("GOLD");
        // Verificam si ca profilul din memorie a fost efectiv modificat inainte de salvare.
        assertThat(profile.getDocNumber()).isEqualTo("AB9999");
    }

    @Test
    void getMyProfile_shouldThrowEntityNotFoundException_whenUserDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> passengerProfileService.getMyProfile(userId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}

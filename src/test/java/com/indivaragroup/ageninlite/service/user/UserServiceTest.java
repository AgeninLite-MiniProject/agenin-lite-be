package com.indivaragroup.ageninlite.service.user;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.dto.user.UserSearchItemDto;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void searchByPhonePrefix_happyPath_localFormat_returnsNormalizedMatches() {
        // User types "0812" (local), DB stores "+62812..."
        MstUser agent = MstUser.builder()
                .userId(UUID.randomUUID())
                .userName("Budi")
                .phoneNumber("+628123456789")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();
        Page<MstUser> page = new PageImpl<>(List.of(agent), PageRequest.of(0, 50), 1);

        when(userRepository.searchAgentsByPhonePrefix(eq("+62812"), any(Pageable.class)))
                .thenReturn(page);

        List<UserSearchItemDto> result = userService.searchByPhonePrefix("0812");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Budi");
        assertThat(result.get(0).getPhone()).isEqualTo("+628123456789");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).searchAgentsByPhonePrefix(eq("+62812"), any(Pageable.class));
    }

    @Test
    void searchByPhonePrefix_alreadyE164_passesThrough() {
        Page<MstUser> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(userRepository.searchAgentsByPhonePrefix(eq("+62812"), any(Pageable.class)))
                .thenReturn(emptyPage);

        List<UserSearchItemDto> result = userService.searchByPhonePrefix("+62812");

        assertThat(result).isEmpty();
        verify(userRepository).searchAgentsByPhonePrefix(eq("+62812"), any(Pageable.class));
    }

    @Test
    void searchByPhonePrefix_tooShortInput_throwsUSR_0003() {
        assertThatThrownBy(() -> userService.searchByPhonePrefix("08"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.USR_0003);

        verifyNoInteractions(userRepository);
    }

    @Test
    void searchByPhonePrefix_nonDigitInput_throwsUSR_0003() {
        assertThatThrownBy(() -> userService.searchByPhonePrefix("0812abc"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.USR_0003);

        verifyNoInteractions(userRepository);
    }

    @Test
    void searchByPhonePrefix_nullInput_throwsUSR_0003() {
        assertThatThrownBy(() -> userService.searchByPhonePrefix(null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.USR_0003);

        verifyNoInteractions(userRepository);
    }

    @Test
    void searchByPhonePrefix_noMatches_returnsEmptyList() {
        Page<MstUser> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(userRepository.searchAgentsByPhonePrefix(eq("+62899"), any(Pageable.class)))
                .thenReturn(emptyPage);

        List<UserSearchItemDto> result = userService.searchByPhonePrefix("0899");

        assertThat(result).isEmpty();
        verify(userRepository).searchAgentsByPhonePrefix(eq("+62899"), any(Pageable.class));
    }

    @Test
    void searchByPhonePrefix_alwaysUsesPageSize50() {
        Page<MstUser> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(userRepository.searchAgentsByPhonePrefix(any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        userService.searchByPhonePrefix("0812");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchAgentsByPhonePrefix(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void searchByPhonePrefix_mapsEntityToDto() {
        MstUser agent1 = MstUser.builder()
                .userId(UUID.randomUUID())
                .userName("Budi")
                .phoneNumber("+628123456789")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();
        MstUser agent2 = MstUser.builder()
                .userId(UUID.randomUUID())
                .userName("Ani")
                .phoneNumber("+628987654321")
                .role("AGENT")
                .userStatus("PASSIVE")
                .isDeleted(false)
                .build();
        Page<MstUser> page = new PageImpl<>(List.of(agent1, agent2), PageRequest.of(0, 50), 2);

        when(userRepository.searchAgentsByPhonePrefix(any(), any(Pageable.class)))
                .thenReturn(page);

        List<UserSearchItemDto> result = userService.searchByPhonePrefix("0812");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserSearchItemDto::getName).containsExactly("Budi", "Ani");
        assertThat(result).extracting(UserSearchItemDto::getStatus).containsExactly("ACTIVE", "PASSIVE");
    }
}

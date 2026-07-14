package com.indivaragroup.ageninlite.service.user;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.common.utils.PhoneUtils;
import com.indivaragroup.ageninlite.dto.user.UserSearchItemDto;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private static final int SEARCH_RESULT_CAP = 50;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserSearchItemDto> searchByPhonePrefix(String rawQuery) {
        log.info("Process search by phone prefix: {}", rawQuery);

        String normalizedPrefix;
        try {
            normalizedPrefix = PhoneUtils.normalizePrefix(rawQuery);
        } catch (IllegalArgumentException ex) {
            throw new AppException(UserErrorCode.USR_0003);
        }

        Pageable pageable = PageRequest.of(0, SEARCH_RESULT_CAP);
        Page<MstUser> result = userRepository.searchAgentsByPhonePrefix(normalizedPrefix, pageable);

        return result.getContent().stream()
                .map(user -> UserSearchItemDto.builder()
                        .user_id(user.getUserId())
                        .name(user.getUserName())
                        .phone(user.getPhoneNumber())
                        .status(user.getUserStatus())
                        .build())
                .toList();
    }
}

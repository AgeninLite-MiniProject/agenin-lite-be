package com.indivaragroup.ageninlite.controller.user;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.dto.user.UserSearchItemDto;
import com.indivaragroup.ageninlite.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchByPhone_happyPath_returns200WithArray() throws Exception {
        UserSearchItemDto item = UserSearchItemDto.builder()
                .user_id(UUID.randomUUID())
                .name("Budi")
                .phone("+628123456789")
                .status("ACTIVE")
                .build();
        when(userService.searchByPhonePrefix(eq("0812"))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/users/search").param("phone", "0812"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user_id").exists())
                .andExpect(jsonPath("$[0].name").value("Budi"))
                .andExpect(jsonPath("$[0].phone").value("+628123456789"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void searchByPhone_emptyResult_returns200WithEmptyArray() throws Exception {
        when(userService.searchByPhonePrefix(eq("0899"))).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search").param("phone", "0899"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchByPhone_tooShortInput_returns400() throws Exception {
        when(userService.searchByPhonePrefix(eq("08")))
                .thenThrow(new AppException(UserErrorCode.USR_0003));

        mockMvc.perform(get("/api/users/search").param("phone", "08"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchByPhone_passesRawParamToService() throws Exception {
        when(userService.searchByPhonePrefix(eq("+62812"))).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search").param("phone", "+62812"))
                .andExpect(status().isOk());

        verify(userService).searchByPhonePrefix("+62812");
    }
}

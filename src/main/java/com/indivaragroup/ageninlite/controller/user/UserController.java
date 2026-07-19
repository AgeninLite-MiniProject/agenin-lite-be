package com.indivaragroup.ageninlite.controller.user;

import com.indivaragroup.ageninlite.dto.user.UserSearchItemDto;
import com.indivaragroup.ageninlite.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchItemDto>> searchByPhone(
            @RequestParam(name = "phone") String phone
    ){
        return ResponseEntity.ok(userService.searchByPhonePrefix(phone));
    }
}

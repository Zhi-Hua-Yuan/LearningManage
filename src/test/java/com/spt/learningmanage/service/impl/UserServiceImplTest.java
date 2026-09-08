package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.SystemRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.user.UserUpdateRequest;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerShouldPersistCanonicalUserRole() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        userService.register("stage1-user", "Stage One", "password123", "password123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(SystemRoleEnum.USER.getValue(), captor.getValue().getUserRole());
    }

    @Test
    void userUpdateRequestShouldNotExposeRoleFields() {
        boolean exposesRoleField = Arrays.stream(UserUpdateRequest.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .anyMatch(name -> name.equals("role") || name.equals("userrole") || name.equals("systemrole"));

        assertFalse(exposesRoleField);
    }

    @Test
    void registerShouldMapPrecheckDuplicateToCanonicalConflict() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register("duplicate", "Duplicate", "password123", "password123"));

        assertEquals(ErrorCode.ACCOUNT_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void registerShouldMapUniqueConstraintRaceToCanonicalConflict() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_account"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register("duplicate", "Duplicate", "password123", "password123"));

        assertEquals(ErrorCode.ACCOUNT_ALREADY_EXISTS, exception.getErrorCode());
    }
}

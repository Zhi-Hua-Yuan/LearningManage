package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("USER", captor.getValue().getUserRole());
    }
}

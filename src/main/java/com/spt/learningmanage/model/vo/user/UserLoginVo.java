package com.spt.learningmanage.model.vo.user;

import lombok.Data;

@Data
public class UserLoginVo {
    private Long id;
    private String username;
    private String token;
}


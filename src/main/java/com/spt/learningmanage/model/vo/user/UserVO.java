package com.spt.learningmanage.model.vo.user;

import lombok.Data;
import java.util.Date;

@Data
public class UserVO {
    private Long id;
    private String account;
    private String username;
    private String userRole;
    private Date createTime;
}
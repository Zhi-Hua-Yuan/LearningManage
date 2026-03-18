package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String account; // 账号
    private String username; // 用户名
    private String password;
    private String userRole;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    private Integer isDelete;
}

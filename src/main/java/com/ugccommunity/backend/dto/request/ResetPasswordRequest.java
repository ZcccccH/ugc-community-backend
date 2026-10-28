package com.ugccommunity.backend.dto.request;

import javax.validation.constraints.NotBlank;

public class ResetPasswordRequest {
    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "绑定手机号不能为空")
    private String phone;

    @NotBlank(message = "绑定邮箱不能为空")
    private String email;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

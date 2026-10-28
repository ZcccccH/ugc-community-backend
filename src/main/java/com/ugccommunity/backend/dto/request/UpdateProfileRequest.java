package com.ugccommunity.backend.dto.request;

import javax.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    @Size(max = 255, message = "背景图地址过长")
    private String backgroundImage;

    @Size(max = 500, message = "简介不能超过500个字符")
    private String bio;

    private Integer gender;

    @Size(max = 20, message = "手机号格式不正确")
    private String phone;

    @Size(max = 100, message = "邮箱过长")
    private String email;

    @Size(max = 20, message = "生日格式不正确")
    private String birthday;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
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

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }
}

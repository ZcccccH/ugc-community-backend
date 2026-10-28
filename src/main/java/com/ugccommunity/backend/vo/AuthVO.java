package com.ugccommunity.backend.vo;

public class AuthVO {
    private String token;
    private UserVO user;

    public AuthVO() {
    }

    public AuthVO(String token, UserVO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }
}

package com.ugccommunity.backend.vo;

public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String backgroundImage;
    private Integer gender;
    private String genderText;
    private String role;
    private Integer status;
    private String phone;
    private String email;
    private String birthday;
    private String bio;
    private Integer followCount;
    private Integer fansCount;
    private Integer likeCount;
    private Integer privacyAgreed;
    private Integer workCount;
    private Integer favoriteCount;
    private Boolean pinned;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public String getGenderText() { return genderText; }
    public void setGenderText(String genderText) { this.genderText = genderText; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Integer getFollowCount() { return followCount; }
    public void setFollowCount(Integer followCount) { this.followCount = followCount; }
    public Integer getFansCount() { return fansCount; }
    public void setFansCount(Integer fansCount) { this.fansCount = fansCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getPrivacyAgreed() { return privacyAgreed; }
    public void setPrivacyAgreed(Integer privacyAgreed) { this.privacyAgreed = privacyAgreed; }
    public Integer getWorkCount() { return workCount; }
    public void setWorkCount(Integer workCount) { this.workCount = workCount; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
}

package com.example.emailprocessor.auth.dto;

import com.example.emailprocessor.auth.model.User;

public class UserDTO {
    private String username;
    private String email;
    private boolean firstLogin;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isFirstLogin() { return firstLogin; }
    public void setFirstLogin(boolean firstLogin) { this.firstLogin = firstLogin; }

    public static UserDTO fromEntity(User user) {
        UserDTO dto = new UserDTO();
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstLogin(user.isFirstLogin());
        return dto;
    }
}

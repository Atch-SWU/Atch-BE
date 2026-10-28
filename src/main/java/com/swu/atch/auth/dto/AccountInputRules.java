package com.swu.atch.auth.dto;

public final class AccountInputRules {
    private AccountInputRules() { }
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,16}$";
    public static final String NICKNAME = "^[가-힣a-zA-Z]{2,8}$";
}

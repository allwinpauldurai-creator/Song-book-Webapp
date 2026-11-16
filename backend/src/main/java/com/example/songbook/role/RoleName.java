package com.example.songbook.role;

/** Persisted roles. Guests are anonymous and have no role. */
public enum RoleName {
    MEMBER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}

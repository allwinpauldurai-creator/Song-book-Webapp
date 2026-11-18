package com.example.songbook.role;

/**
 * Persisted roles. Guests are anonymous and have no role.
 * PUBLISHER may create books and manage the books they uploaded; ADMIN manages everything.
 */
public enum RoleName {
    MEMBER,
    PUBLISHER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}

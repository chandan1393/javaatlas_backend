package com.javaatlas.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PasswordResetServiceTest {

    @Test
    void storesOnlyTheSha256OfTheToken() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", PasswordResetService.sha256("hello"));
    }
}

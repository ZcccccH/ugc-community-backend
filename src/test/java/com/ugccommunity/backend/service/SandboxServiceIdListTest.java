package com.ugccommunity.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SandboxServiceIdListTest {
    @Test
    void removesIdFromCsv() {
        assertEquals("1,3", SandboxService.withoutId("1,2,3", 2L));
        assertEquals("", SandboxService.withoutId("2", 2L));
        assertEquals("1", SandboxService.withoutId(" 1 , 2 ", 2L));
    }

    @Test
    void returnsNullWhenNothingToChange() {
        assertNull(SandboxService.withoutId("1,3", 2L));
        assertNull(SandboxService.withoutId(null, 2L));
        assertNull(SandboxService.withoutId("", 2L));
    }

    @Test
    void nullStringFromOldClientsIsNeverStored() {
        assertEquals("", SandboxService.notNullString("null"));
        assertEquals("", SandboxService.notNullString(" null "));
        assertNull(SandboxService.notNullString(null));
        assertEquals("师父", SandboxService.notNullString("师父"));
        assertEquals("nullable", SandboxService.notNullString("nullable"));
    }
}

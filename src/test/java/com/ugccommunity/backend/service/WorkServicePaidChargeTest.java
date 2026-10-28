package com.ugccommunity.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WorkServicePaidChargeTest {
    private static boolean[] plan(boolean was, int oldAmt, boolean now, int newAmt) {
        return WorkService.paidChargePlan(was, "solid", oldAmt, now, "solid", newAmt);
    }

    @Test
    void enablingChargesOnce() {
        assertArrayEquals(new boolean[]{false, true}, plan(false, 1, true, 1));
    }

    @Test
    void disablingRefunds() {
        // 原来关闭不退，关掉再打开会被重复扣
        assertArrayEquals(new boolean[]{true, false}, plan(true, 2, false, 2));
    }

    @Test
    void unchangedLockedWorkIsNotChargedAgain() {
        assertArrayEquals(new boolean[]{false, false}, plan(true, 2, true, 2));
    }

    @Test
    void priceChangeWhileLockedSwapsTheDeposit() {
        assertArrayEquals(new boolean[]{true, true}, plan(true, 1, true, 5));
        assertArrayEquals(new boolean[]{true, true}, WorkService.paidChargePlan(true, "solid", 1, true, "flame", 1));
    }

    @Test
    void staysFreeWithoutAnyCharge() {
        assertArrayEquals(new boolean[]{false, false}, plan(false, 1, false, 3));
    }
}

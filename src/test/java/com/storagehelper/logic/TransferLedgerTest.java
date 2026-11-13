package com.storagehelper.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferLedgerTest {
    @Test void requestedWorkIsNotCreditedAndCompletedDepositsCanBeReturnedAfterIdle() {
        var h = new TransferLedger<String>();
        h.begin("stone/components", true, 30, 64, 0);
        assertEquals(0,h.quantity()); // Six requested but no vanilla transfer has happened.
        h.completed(6,false);
        assertTrue(h.verify(58,6));
        h.completed(2,true); assertTrue(h.verify(60,4));
        h.completed(4,true); assertTrue(h.verify(64,0));
        assertEquals(0,h.quantity());
    }
    @Test void repeatedDirectionChangesAccumulateOnlyTheRemainingNetBalance() {
        var h = new TransferLedger<String>();
        h.begin("a",true,27,20,5); h.completed(6,false); h.completed(2,true);
        h.begin("a",true,29,16,9); h.completed(2,false);
        assertEquals(6,h.quantity()); assertEquals(27,h.preferredSource());
        h.completed(6,true); assertTrue(h.verify(20,5));
    }
    @Test void withdrawalsAndIdentityReplacementHaveIndependentBalances() {
        var h = new TransferLedger<String>();
        h.begin("a/plain",false,3,0,8); h.completed(6,false); assertTrue(h.verify(6,2));
        h.completed(2,true); assertTrue(h.verify(4,4));
        h.begin("a/named",true,30,3,0); assertEquals(0,h.quantity());
        h.completed(1,false); assertTrue(h.verify(2,1));
    }
    @Test void correctionsOrOverReversalInvalidateTheBalance() {
        var h = new TransferLedger<String>();
        h.begin("a",true,27,64,0); h.completed(6,false);
        assertFalse(h.verify(64,0)); assertEquals(0,h.quantity());
        h.begin("a",true,27,64,0); h.completed(2,false); h.completed(3,true);
        assertNull(h.identity()); assertEquals(0,h.quantity());
    }
}

package com.storagehelper.logic;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Chooses an insertable compatible partial stack before an empty destination. */
public final class SingleItemTransferPlanner {
    private SingleItemTransferPlanner() {}
    public static int destination(List<Stack> slots, Stack source, IntPredicate accepts, IntUnaryOperator limit) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < slots.size(); i++) {
                Stack target = slots.get(i);
                if (!accepts.test(i) || limit.applyAsInt(i) < 1) continue;
                if (pass == 0 && target != null && !source.type().fragile() && !target.type().fragile()
                        && source.type().equals(target.type()) && target.count() < limit.applyAsInt(i)) return i;
                if (pass == 1 && target == null) return i;
            }
        }
        return -1;
    }
}

package com.equitytrade.booking.pnl.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PnlDecimal {

    private PnlDecimal() {
    }

    static BigDecimal api(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal rounded = value.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (rounded.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }
}

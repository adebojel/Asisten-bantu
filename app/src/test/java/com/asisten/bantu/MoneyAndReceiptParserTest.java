package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MoneyAndReceiptParserTest {
    @Test public void parsesExactBanknoteDenominations() {
        assertEquals(100_000L, MoneyAndReceiptParser.parseAiNominal("Uang kertas Rp 100.000"));
        assertEquals(50_000L, MoneyAndReceiptParser.parseAiNominal("Terlihat Rp 50.000"));
        assertEquals(20_000L, MoneyAndReceiptParser.parseRupiahBill("Nominal Rp 20.000", null).amount);
    }

    @Test public void doesNotMatchDenominationInsideLargerAmounts() {
        assertEquals(0L, MoneyAndReceiptParser.parseAiNominal("Saldo Rp 1.000.000"));
        assertEquals(0L, MoneyAndReceiptParser.parseAiNominal("Transfer Rp 250.000"));
        assertEquals(0L, MoneyAndReceiptParser.parseRupiahBill("Saldo Rp 1.000.000", null).amount);
        assertEquals(0L, MoneyAndReceiptParser.parseRupiahBill("Nominal Rp 250.000", null).amount);
    }
}

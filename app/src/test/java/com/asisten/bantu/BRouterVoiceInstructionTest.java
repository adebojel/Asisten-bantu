package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class BRouterVoiceInstructionTest {
    @Test public void translatesCommonPedestrianTurnsToIndonesian() {
        assertEquals("lurus terus", BRouterVoiceInstruction.toIndonesian(1, 0));
        assertEquals("belok kiri", BRouterVoiceInstruction.toIndonesian(2, 0));
        assertEquals("belok kanan", BRouterVoiceInstruction.toIndonesian(5, 0));
        assertEquals("putar balik", BRouterVoiceInstruction.toIndonesian(15, 0));
    }

    @Test public void handlesRoundaboutExitAndUnknownCommands() {
        assertEquals("keluar bundaran pada jalan ke 2",
                BRouterVoiceInstruction.toIndonesian(13, 2));
        assertNull(BRouterVoiceInstruction.toIndonesian(99, 0));
    }
}

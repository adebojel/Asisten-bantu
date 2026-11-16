package com.asisten.bantu;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceSilenceCommandParserTest {

    @Test public void silenceAndWakePhrasesAreRecognized() {
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("Kobra, diam dulu"));
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("mode diam"));
        assertTrue(VoiceSilenceCommandParser.isWakeActivationCommand("Kobra aktifkan pendengaran"));
        assertTrue(VoiceSilenceCommandParser.isWakeActivationCommand("saya panggil Kobra"));
    }

    @Test public void cancellationRequiresAnExplicitStandalonePhrase() {
        assertTrue(VoiceSilenceCommandParser.isExplicitCancelCommand("Tidak jadi"));
        assertTrue(VoiceSilenceCommandParser.isExplicitCancelCommand("JANGAN KIRIM"));
        assertFalse(VoiceSilenceCommandParser.isExplicitCancelCommand("Tidak ada masalah, kirim informasinya"));
        assertFalse(VoiceSilenceCommandParser.isExplicitCancelCommand("Saya tidak yakin"));
    }

    @Test public void mediaStopDoesNotInterruptAssistantSpeech() {
        assertFalse(VoiceSilenceCommandParser.isSpeechInterruptCommand("stop video"));
        assertFalse(VoiceSilenceCommandParser.isSpeechInterruptCommand("hentikan musik"));
        assertTrue(VoiceSilenceCommandParser.isSpeechInterruptCommand("berhenti bicara"));
        assertTrue(VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("diam dulu"));
    }

    @Test public void matchingUsesWholePhraseBoundaries() {
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("Kobra, jangan bicara sekarang"));
        assertFalse(VoiceSilenceCommandParser.isSilenceCommand("saya sedang diamati"));
        assertFalse(VoiceSilenceCommandParser.isWakeActivationCommand("aktifitas berjalan"));
    }
}

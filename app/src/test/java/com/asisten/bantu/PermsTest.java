package com.asisten.bantu;

import android.Manifest;
import org.junit.Assert.assertArrayEquals;
import org.junit.Assert.assertEquals;
import org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class PermsTest {

    @Test
    public void legacyRequiredSetContainsOnlyMicrophonePermission() {
        assertArrayEquals(
                new String[] { Manifest.permission.RECORD_AUDIO },
                Perms.required()
        );
    }

    @Test
    public void smsReadAndSendAreSeparateCapabilities() {
        assertArrayEquals(
                new String[] { Manifest.permission.SEND_SMS },
                Perms.forGroup(Perms.GROUP_SMS)
        );
        assertArrayEquals(
                new String[] { Manifest.permission.READ_SMS },
                Perms.forGroup(Perms.GROUP_SMS_READ)
        );
    }

    @Test
    public void permissionPickerHasOneLabelPerGroup() {
        assertEquals(Perms.groupLabels().length, Perms.GROUP_SMS_READ + 1);
    }

    @Test
    public void invalidGroupDoesNotRequestAnything() {
        assertTrue(Perms.forGroup(-1).length == 0);
        assertTrue(Perms.forGroup(999).length == 0);
    }
}

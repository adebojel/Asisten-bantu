package com.asisten.bantu;

import android.Manifest;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void locationGroupContainsOnlyLocationPermissions() {
        assertArrayEquals(
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                Perms.forGroup(Perms.GROUP_LOCATION)
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

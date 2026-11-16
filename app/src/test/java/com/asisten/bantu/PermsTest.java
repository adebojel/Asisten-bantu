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
    public void placeCallGroupDoesNotRequestAnswerOrPhoneStatePermissions() {
        assertArrayEquals(
                new String[] { Manifest.permission.CALL_PHONE },
                Perms.forGroup(Perms.GROUP_CALLS)
        );
    }

    @Test
    public void activityRecognitionGroupContainsOnlyStepPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            assertArrayEquals(
                    new String[] { Manifest.permission.ACTIVITY_RECOGNITION },
                    Perms.forGroup(Perms.GROUP_ACTIVITY)
            );
        } else {
            assertEquals(0, Perms.forGroup(Perms.GROUP_ACTIVITY).length);
        }
    }

    @Test
    public void bluetoothGroupRequestsConnectButNeverAdvertise() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertArrayEquals(
                    new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    Perms.forGroup(Perms.GROUP_BLUETOOTH)
            );
        } else {
            assertEquals(0, Perms.forGroup(Perms.GROUP_BLUETOOTH).length);
        }
    }

    @Test
    public void phoneStateGroupIsIndependent() {
        assertArrayEquals(
                new String[] { Manifest.permission.READ_PHONE_STATE },
                Perms.forGroup(Perms.GROUP_PHONE_STATE)
        );
    }

    @Test
    public void answerCallGroupDoesNotRequestUnrelatedPhonePermissions() {
        assertArrayEquals(
                new String[] { Manifest.permission.ANSWER_PHONE_CALLS },
                Perms.forGroup(Perms.GROUP_ANSWER_CALLS)
        );
    }

    @Test
    public void permissionPickerHasOneLabelPerGroup() {
        assertEquals(Perms.groupLabels().length, Perms.GROUP_PHONE_STATE + 1);
    }

    @Test
    public void invalidGroupDoesNotRequestAnything() {
        assertTrue(Perms.forGroup(-1).length == 0);
        assertTrue(Perms.forGroup(999).length == 0);
    }
}

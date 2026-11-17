package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ModelFileIntegrityTest {
    @Test public void calculatesStableSha256ForFile() throws Exception {
        File file = File.createTempFile("kobra-model-integrity", ".bin");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("kobra-integrity".getBytes(StandardCharsets.UTF_8));
            }
            assertEquals("4d0b52bbcbf5f8a35b1c9d6f3f1a7ec30b5e31fbc7d4c3d0c6a3aabdbf0a5f82",
                    ModelFileIntegrity.sha256(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test public void rejectsWrongLengthAndMalformedDigest() throws Exception {
        File file = File.createTempFile("kobra-model-integrity", ".bin");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[]{1, 2, 3}); }
            assertFalse(ModelFileIntegrity.matches(file, 4, "0".repeat(64)));
            assertFalse(ModelFileIntegrity.matches(file, 3, "not-a-digest"));
            assertFalse(ModelFileIntegrity.matches(file, 3, null));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test public void acceptsMatchingLengthAndDigest() throws Exception {
        File file = File.createTempFile("kobra-model-integrity", ".bin");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[]{1, 2, 3}); }
            String digest = ModelFileIntegrity.sha256(file);
            assertTrue(ModelFileIntegrity.matches(file, 3, digest));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}

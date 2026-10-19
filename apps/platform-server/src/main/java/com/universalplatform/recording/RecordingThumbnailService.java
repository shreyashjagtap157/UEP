package com.universalplatform.recording;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class RecordingThumbnailService {
    private static final int PROCESS_TIMEOUT_SECONDS = 60;
    private final String ffmpeg;

    RecordingThumbnailService(@Value("${platform.recording.ffmpeg-command:ffmpeg}") String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    Path create(Path video, Path thumbnail) {
        try {
            Files.createDirectories(thumbnail.getParent());
            Process p = new ProcessBuilder(ffmpeg, "-y", "-ss", "00:00:05", "-i", video.toString(), "-frames:v", "1",
                    "-vf", "scale=640:-2", thumbnail.toString()).redirectErrorStream(true).start();
            if (!p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                try (InputStream drain = p.getInputStream()) {
                    drain.transferTo(OutputStream.nullOutputStream());
                }
                throw new IllegalStateException("Thumbnail generation timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
            }
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes());
            }
            if (p.exitValue() != 0 || !Files.exists(thumbnail))
                throw new IllegalStateException("Thumbnail generation failed: " + out);
            return thumbnail;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to generate recording thumbnail", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate recording thumbnail", e);
        }
    }

    /** SHA-256 of a file, hex encoded. */
    static String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1024 * 1024];
            for (int n; (n = in.read(buf)) > 0;)
                md.update(buf, 0, n);
            StringBuilder b = new StringBuilder(64);
            for (byte x : md.digest())
                b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

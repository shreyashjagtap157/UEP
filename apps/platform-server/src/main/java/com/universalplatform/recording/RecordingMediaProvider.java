package com.universalplatform.recording;

public interface RecordingMediaProvider {
    StartResult startRoomComposite(String roomName, RecordingQualityPreset quality, String outputPath);
    void stop(String egressId);
    EgressState status(String egressId);
    record StartResult(String egressId, String status) {}
    record EgressState(String egressId, String status, String filePath, long durationNanos, String error) {}
}

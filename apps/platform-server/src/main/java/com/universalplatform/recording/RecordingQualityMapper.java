package com.universalplatform.recording;

final class RecordingQualityMapper {
    private RecordingQualityMapper() {}
    static String liveKitPreset(RecordingQualityPreset quality) {
        return switch (quality == null ? RecordingQualityPreset.BALANCED : quality) {
            case ECONOMY, BALANCED -> "H264_720P_30";
            case HIGH_QUALITY, SOURCE_ARCHIVE -> "H264_1080P_30";
        };
    }
}

package com.universalplatform.recording;

public final class RecordingQualityMapperTest {
    public static void main(String[] args) {
        assert "H264_720P_30".equals(RecordingQualityMapper.liveKitPreset(RecordingQualityPreset.ECONOMY));
        assert "H264_720P_30".equals(RecordingQualityMapper.liveKitPreset(RecordingQualityPreset.BALANCED));
        assert "H264_1080P_30".equals(RecordingQualityMapper.liveKitPreset(RecordingQualityPreset.HIGH_QUALITY));
        assert "H264_1080P_30".equals(RecordingQualityMapper.liveKitPreset(RecordingQualityPreset.SOURCE_ARCHIVE));
        assert "H264_720P_30".equals(RecordingQualityMapper.liveKitPreset(null));
    }
}

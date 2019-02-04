// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.vr180.media.rtmp;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Message processing for an RTMP client connection. Refer to RTMP specification at
 * https://www.adobe.com/devnet/rtmp.html for more details.
 */
public final class RtmpMessage {

  /*
   * Chunk stream-related values
   */
  public static final byte RTMP_VERSION = 3;

  // Support for flash client version 9.0.124.2
  public static final int CLIENT_VERSION = (9 << 24) | (0 << 16) | (124 << 8) | 2;

  public static final int BYTE_SIZE = 1;
  public static final int INT_SIZE = 4;
  public static final int HANDSHAKE_LEN = 1536;

  public static final int DEFAULT_CHUNK_SIZE = 128;
  public static final int MIN_CHUNK_SIZE = 64;
  public static final int MIN_WINDOW_SIZE = 4 * MIN_CHUNK_SIZE;

  public static final int CHUNK_FORMAT_FULL = 0;
  public static final int CHUNK_FORMAT_NO_STREAM_ID = 1;
  public static final int CHUNK_FORMAT_TIME_DELTA = 2;
  public static final int CHUNK_FORMAT_NO_HEADER = 3;

  public static final int CHUNK_FORMAT_FULL_SIZE = 11;
  public static final int CHUNK_FORMAT_NO_STREAM_ID_SIZE = 7;
  public static final int CHUNK_FORMAT_TIME_DELTA_SIZE = 3;

  // 3 bytes for the chunk basic header + largest chunk message header + extended timestamp
  public static final int MAX_HEADER_SIZE =
      3 + RtmpMessage.CHUNK_FORMAT_FULL_SIZE + RtmpMessage.INT_SIZE;

  private static final int CHUNK_FORMAT_MASK = 0x3;
  private static final int CHUNK_FORMAT_SHIFT = 6;
  private static final int CHUNK_STREAM_ID_MASK = 0x3f;
  private static final int CHUNK_STREAM_ID_SHIFT = 0;
  private static final int TIMESTAMP_MASK = 0x00ffffff;
  private static final int LENGTH_MASK = 0x00ffffff;
  private static final int MESSAGE_STREAM_ID_MASK = 0x00ffffff;

  /*
   * Chunk stream IDs
   */

  /** Indicates an extended chunk stream ID in the range 64-319 */
  public static final int CHUNK_STREAM_ID_EXTENDED = 0;
  /** Indicates a full chunk stream ID in the range 64-65599 */
  public static final int CHUNK_STREAM_ID_FULL = 1;
  /** Indicates the chunk stream for protocol control messages. */
  public static final int CHUNK_STREAM_ID_CONTROL = 2;
  /** Indicates the chunk stream for AMF command and data messages. */
  public static final int CHUNK_STREAM_ID_AMF = 3;
  /** Indicates the chunk stream for video messages. */
  public static final int CHUNK_STREAM_ID_VIDEO = 6;
  /** Indicates the chunk stream for audio messages. */
  public static final int CHUNK_STREAM_ID_AUDIO = 4;

  private static final int MIN_FULL_CHUNK_STREAM_ID = 320;
  private static final int MIN_EXTENDED_CHUNK_STREAM_ID = 64;

  /*
   * Message stream IDs
   */
  public static final int MESSAGE_STREAM_CONTROL = 0;
  public static final int MESSAGE_STREAM_AUDIO_VIDEO = 1;

  /*
   * Protocol control message type IDs and corresponding lengths
   */
  public static final int MESSAGE_TYPE_SET_CHUNK_SIZE = 1;
  public static final int MESSAGE_LEN_SET_CHUNK_SIZE = 4;
  public static final int MESSAGE_TYPE_ABORT = 2;
  public static final int MESSAGE_LEN_ABORT = 4;
  public static final int MESSAGE_TYPE_ACKNOWLEDGEMENT = 3;
  public static final int MESSAGE_LEN_ACKNOWLEDGEMENT = 4;
  public static final int MESSAGE_TYPE_USER_CONTROL = 4;
  public static final int MESSAGE_TYPE_WINDOW_ACK_SIZE = 5;
  public static final int MESSAGE_LEN_WINDOW_ACK_SIZE = 4;
  public static final int MESSAGE_TYPE_SET_PEER_BANDWIDTH = 6;
  public static final int MESSAGE_LEN_SET_PEER_BANDWIDTH = 5;

  /*
   * Window size limit types
   */
  public static final int WINDOW_SIZE_LIMIT_TYPE_HARD = 0;
  public static final int WINDOW_SIZE_LIMIT_TYPE_SOFT = 1;
  public static final int WINDOW_SIZE_LIMIT_TYPE_DYNAMIC = 2;

  /*
   * RTMP commands (user control messages)
   */
  public static final int RTMP_MESSAGE_COMMAND_AMF0 = 20;
  public static final int RTMP_MESSAGE_COMMAND_AMF3 = 17;
  public static final int RTMP_MESSAGE_DATA_AMF0 = 18;
  public static final int RTMP_MESSAGE_DATA_AMF3 = 15;
  public static final int RTMP_MESSAGE_SHARED_OBJECT_AMF0 = 19;
  public static final int RTMP_MESSAGE_SHARED_OBJECT_AMF3 = 16;
  public static final int RTMP_MESSAGE_AUDIO = 8;
  public static final int RTMP_MESSAGE_VIDEO = 9;
  public static final int RTMP_MESSAGE_AGGREGATE = 22;
  public static final int RTMP_MESSAGE_BANDWIDTH_TEST = 42;

  /*
   * NetConnection Commands
   */
  public static final String NETCONNECTION_CONNECT_NAME = "connect";
  public static final String NETCONNECTION_RELEASE_STREAM_NAME = "releaseStream";
  public static final String NETCONNECTION_CREATE_STREAM_NAME = "createStream";
  public static final String NETCONNECTION_PUBLISH_STREAM_NAME = "publish";
  public static final String NETCONNECTION_STREAM_DATA_NAME = "@setDataFrame";
  public static final String NETCONNECTION_STREAM_DATA_METADATA = "onMetaData";
  public static final String NETCONNECTION_PUBLISH_TYPE = "live";
  public static final int NETCONNECTION_CONNECT_TRANSACTION_ID = 1;
  public static final int NETCONNECTION_ONSTATUS_TRANSACTION_ID = 2;
  public static final int NETCONNECTION_FIRST_UNUSED_TRANSACTION_ID = 10;
  public static final String NETCONNECTION_PROPERTY_APP = "app";
  public static final String NETCONNECTION_PROPERTY_FLASH_VERSION = "flashVer";
  public static final String NETCONNECTION_PROPERTY_FLASH_VERSION_ALT = "flashver";
  public static final String NETCONNECTION_PROPERTY_TC_URL = "tcUrl";
  public static final String NETCONNECTION_PROPERTY_TYPE = "type";
  public static final String NETCONNECTION_TYPE_NONPRIVATE = "nonprivate";

  /*
   * AMF0 commands
   */
  public static final String AMF_COMMAND_RESPONSE_RESULT = "_result";
  public static final String AMF_COMMAND_RESPONSE_ERROR = "_error";
  public static final String AMF_COMMAND_RESPONSE_ONSTATUS = "onStatus";
  public static final String AMF_RESPONSE_LEVEL_KEY = "level";
  public static final String AMF_RESPONSE_CODE_KEY = "code";
  public static final String AMF_RESPONSE_LEVEL_VALUE_STATUS = "status";
  public static final String AMF_NETCONNECTION_STATUS_SUCCESS = "NetConnection.Connect.Success";
  public static final String AMF_PUBLISH_STATUS_SUCCESS = "NetStream.Publish.Start";

  /*
   * Stream meta data property names
   */
  public static final String META_DATA_PROPERTY_DURATION = "duration";
  public static final String META_DATA_PROPERTY_WIDTH = "width";
  public static final String META_DATA_PROPERTY_HEIGHT = "height";
  public static final String META_DATA_PROPERTY_VIDEO_DATA_RATE = "videodatarate";
  public static final String META_DATA_PROPERTY_FRAME_RATE = "framerate";
  public static final String META_DATA_PROPERTY_VIDEO_CODEC_ID = "videocodecid";
  public static final String META_DATA_PROPERTY_AUDIO_DATA_RATE = "audiodatarate";
  public static final String META_DATA_PROPERTY_AUDIO_SAMPLE_RATE = "audiosamplerate";
  public static final String META_DATA_PROPERTY_AUDIO_SAMPLE_SIZE = "audiosamplesize";
  public static final String META_DATA_PROPERTY_IS_STEREO = "stereo";
  public static final String META_DATA_PROPERTY_AUDIO_CODEC_ID = "audiocodecid";
  public static final String META_DATA_PROPERTY_ENCODER = "encoder";
  public static final String META_DATA_PROPERTY_FILE_SIZE = "filesize";

  /*
   * Video codec IDs
   */
  public static final int RTMP_VIDEO_CODEC_SORENSON_H263 = 0x02;
  public static final int RTMP_VIDEO_CODEC_SCREEN_VIDEO = 0x03;
  public static final int RTMP_VIDEO_CODEC_ON2_VP6 = 0x04;
  public static final int RTMP_VIDEO_CODEC_ON2_VP6_ALPHA = 0x05;
  public static final int RTMP_VIDEO_CODEC_SCREEN2_VIDEO = 0x06;
  public static final int RTMP_VIDEO_CODEC_AVC = 0x07;
  public static final int RTMP_VIDEO_CODEC_VP8 = 0x08;

  /*
   * Audio codec IDs
   */
  public static final int RTMP_AUDIO_CODEC_UNCOMPRESSED = 0x00;
  public static final int RTMP_AUDIO_CODEC_ADPCM = 0x01;
  public static final int RTMP_AUDIO_CODEC_MP3 = 0x02;
  public static final int RTMP_AUDIO_CODEC_LINEAR_PCM = 0x03;
  public static final int RTMP_AUDIO_CODEC_NELLYMOSER16MONO = 0x04;
  public static final int RTMP_AUDIO_CODEC_NELLYMOSER8MONO = 0x05;
  public static final int RTMP_AUDIO_CODEC_NELLYMOSER = 0x06;
  public static final int RTMP_AUDIO_CODEC_G711_ALAW = 0x07;
  public static final int RTMP_AUDIO_CODEC_G711_ULAW = 0x08;
  public static final int RTMP_AUDIO_CODEC_AAC = 0x0a;
  public static final int RTMP_AUDIO_CODEC_SPEEX = 0x0b;
  public static final int RTMP_AUDIO_CODEC_MP3_8KHZ = 0x0e;

  /*
   * Audio/video FLV encoding values.
   */
  private static final int AUDIO_SAMPLE_SIZE_AAC = 16;
  @SuppressWarnings("unused") private static final int SAMPLE_RATE_5KHZ_CODE = 0x0;
  @SuppressWarnings("unused") private static final int SAMPLE_RATE_11KHZ_CODE = 0x1;
  @SuppressWarnings("unused") private static final int SAMPLE_RATE_22KHZ_CODE = 0x2;
  private static final int SAMPLE_RATE_44KHZ_CODE = 0x3;
  private static final int AAC_CONTROL_BYTE =
      ((((RTMP_AUDIO_CODEC_AAC & 0xf) << 4) | ((SAMPLE_RATE_44KHZ_CODE & 0x3) << 2) | 0x3) & 0xff);
  private static final byte[] FLV_AAC_AUDIO_TAG_DATA = { (byte) AAC_CONTROL_BYTE, 1 };
  private static final byte[] FLV_AAC_AUDIO_TAG_CONFIG = { (byte) AAC_CONTROL_BYTE, 0 };

  private static final int AVC_KEY_FRAME_TYPE = 1;
  private static final int AVC_INTER_FRAME_TYPE = 2;
  private static final int AVC_CONFIG_TYPE = 0;
  private static final int AVC_DATA_TYPE = 1;

  private static final int AVC_INTER_FRAME_CONTROL_BYTE =
      (((AVC_INTER_FRAME_TYPE & 0xf) << 4) | ((RTMP_VIDEO_CODEC_AVC & 0xf) & 0xff));
  private static final int AVC_KEY_FRAME_CONTROL_BYTE =
      (((AVC_KEY_FRAME_TYPE & 0xf) << 4) | ((RTMP_VIDEO_CODEC_AVC & 0xf) & 0xff));

  private static final byte[] FLV_AVC_VIDEO_INTER_FRAME_DATA_TAG =
      { AVC_INTER_FRAME_CONTROL_BYTE, AVC_DATA_TYPE, 0, 0, 0 };
  private static final byte[] FLV_AVC_VIDEO_KEY_FRAME_DATA_TAG =
      { AVC_KEY_FRAME_CONTROL_BYTE, AVC_DATA_TYPE, 0, 0, 0 };
  private static final byte[] FLV_AVC_VIDEO_INTER_FRAME_CONFIG_TAG =
      { AVC_INTER_FRAME_CONTROL_BYTE, AVC_CONFIG_TYPE, 0, 0, 0 };
  private static final byte[] FLV_AVC_VIDEO_KEY_FRAME_CONFIG_TAG =
      { AVC_KEY_FRAME_CONTROL_BYTE, AVC_CONFIG_TYPE, 0, 0, 0 };

  /*
   * AVCC Box config values.  Format is as follows in bits:
   *
   * 8   version ( always 0x01 )
   * 8   avc profile ( sps[0][1] )
   * 8   avc compatibility ( sps[0][2] )
   * 8   avc level ( sps[0][3] )
   * 6   reserved ( all bits on )
   * 2   NALULengthSizeMinusOne
   * 3   reserved ( all bits on )
   * 5   number of SPS NALUs (usually 1)
   * repeated once per SPS:
   *   16     SPS size
   *   variable   SPS NALU data
   * 8   number of PPS NALUs (usually 1)
   * repeated once per PPS
   *   16    PPS size
   *   variable PPS NALU data
   */
  private static final int AVCC_SPS_PREFIX_SIZE = 8;
  private static final int AVCC_PPS_PREFIX_SIZE = 3;
  private static final int AVC_VERSION = 0x01;
  // From ffmpeg. No documentation on how to interpret
  private static final int AVC_PROFILE = 0x64;
  // From ffmpeg. No documentation on how to interpret
  private static final int AVC_COMPATIBILITY = 0x00;
  // From ffmpeg. No documentation on how to interpret
  private static final int AVC_LEVEL = 0x0d;
  private static final int AVC_NALU_LENGTH_SPEC = 0xfc /* reserved bits */ | 0x3 /* 4-byte */;
  private static final int AVC_SPS_COUNT_RESERVED = 0xe0;
  private static final int AVC_SPS_COUNT_MASK = 0x1f;

  /**
   * Gets the chunk message header format type from the chunk basic header.
   *
   * @return One of {@link #CHUNK_FORMAT_FULL}, {@link #CHUNK_FORMAT_NO_HEADER},
   *   {@link #CHUNK_FORMAT_NO_STREAM_ID}, {@link #CHUNK_FORMAT_NO_HEADER}
   */
  public static int getChunkMessageHeaderFormat(byte basicHeader) {
    return ((basicHeader >> CHUNK_FORMAT_SHIFT) & CHUNK_FORMAT_MASK);
  }

  /**
   * Gets the chunk stream ID from the chunk basic header.
   * @return {@link #CHUNK_STREAM_ID_EXTENDED}, {@link #CHUNK_STREAM_ID_FULL}, or the chunk
   *   stream ID in the range 2-63.
   */
  public static int getChunkBasicHeaderStreamId(byte basicHeader) {
    return ((basicHeader >> CHUNK_STREAM_ID_SHIFT) & CHUNK_STREAM_ID_MASK);
  }

  /** Encode the chunk basic header based on the format and chunk stream ID */
  public static byte createChunkBasicHeader(int chunkFormat, int chunkStreamId) {
    return (byte) (((chunkFormat & CHUNK_FORMAT_MASK) << CHUNK_FORMAT_SHIFT)
        | ((chunkStreamId & CHUNK_STREAM_ID_MASK) << CHUNK_STREAM_ID_SHIFT));
  }

  /** Extract the extended chunk stream ID from the given chunk basic header CSID element. */
  public static int getExtendedChunkStreamId(byte csid) {
    return (int) csid + MIN_EXTENDED_CHUNK_STREAM_ID;
  }

  /** Extract the full chunk stream ID from the given chunk basic header CSID elements. */
  public static int getFullChunkStreamId(byte csid1, byte csid2) {
    return getExtendedChunkStreamId(csid1) + ((int) csid2 * 256);
  }

  /**
   * Checks whether the given 24-bit timestamp or timestamp delta indicates the need for
   * an extended 32-bit value
   */
  public static boolean isTimestampExtended(int timestamp) {
    return (timestamp & TIMESTAMP_MASK) == TIMESTAMP_MASK || ((timestamp & ~TIMESTAMP_MASK) != 0);
  }

  /** Checks whether the given chunk stream ID requires a full size header */
  public static boolean chunkStreamIdRequiresFullHeader(int chunkStreamId) {
    return (chunkStreamId >= MIN_FULL_CHUNK_STREAM_ID);
  }

  /** Checks whether the given chunk stream ID requires an extended size header */
  public static boolean chunkStreamIdRequiresExtendedHeader(int chunkStreamId) {
    return (chunkStreamId >= MIN_EXTENDED_CHUNK_STREAM_ID)
        && (chunkStreamId < MIN_FULL_CHUNK_STREAM_ID);
  }

  /**
   * Create an encoded extended chunk stream ID
   */
  public static int createExtendedChunkStreamId(int chunkStreamId) {
    return (chunkStreamId - MIN_EXTENDED_CHUNK_STREAM_ID);
  }

  /** Checks whether the given length is valid for an RTMP message header */
  public static boolean isValidLength(int length) {
    return (length & ~LENGTH_MASK) == 0;
  }

  /** Checks whether the given message type is valid for an RTMP message header */
  public static boolean isValidMessageType(int messageType) {
    return (messageType & ~0xff) == 0;
  }

  /** Checks whether the given message stream ID is valid for an RTMP message header */
  public static boolean isValidMessageStreamId(int messageStreamId) {
    return (messageStreamId & ~MESSAGE_STREAM_ID_MASK) == 0;
  }

  /** Generate an int from 3 bytes in MSB order from the given array and offset */
  public static int getThreeByteInt(byte[] buffer, int offset) {
    return ((buffer[offset] & 0xff) << 16)
        | ((buffer[offset + 1] & 0xff) << 8) | (buffer[offset + 2] & 0xff);
  }

  /** Generate an int from 3 bytes in MSB order from the given buffer and offset */
  public static int getThreeByteInt(ByteBuffer buffer, int offset) {
    return ((buffer.get(offset) & 0xff) << 16)
        | ((buffer.get(offset + 1) & 0xff) << 8) | (buffer.get(offset + 2) & 0xff);
  }

  /** Get the internal sample size used by the given audio codec. */
  public static int getAudioSampleSize(int audioCodecId) throws ProtocolException {
    if (audioCodecId == RTMP_AUDIO_CODEC_AAC) {
      return AUDIO_SAMPLE_SIZE_AAC;
    }
    throw new ProtocolException("Unsupported audio codec: " + audioCodecId);
  }

  /** Get the FLV control tag to prefix audio data. */
  public static byte[] getAudioControlTag(int audioCodecId, boolean isConfig)
      throws ProtocolException {
    if (audioCodecId == RTMP_AUDIO_CODEC_AAC) {
      return (isConfig ? FLV_AAC_AUDIO_TAG_CONFIG : FLV_AAC_AUDIO_TAG_DATA);
    }
    throw new ProtocolException("Unsupported audio codec: " + audioCodecId);
  }

  /** Get the stereo enabled mode for the given audio codec */
  public static boolean getAudioIsStereo(int audioCodecId) throws ProtocolException {
    if (audioCodecId == RTMP_AUDIO_CODEC_AAC) {
      return true;
    }
    throw new ProtocolException("Unsupported audio codec: " + audioCodecId);
  }

  /** Get the FLV control tag to prefix video data. */
  public static byte[] getVideoControlTag(int videoCodecId, boolean isConfig, boolean isKeyFrame)
      throws ProtocolException {
    if (videoCodecId == RTMP_VIDEO_CODEC_AVC) {
      if (isConfig) {
        return (isKeyFrame
            ? FLV_AVC_VIDEO_KEY_FRAME_CONFIG_TAG : FLV_AVC_VIDEO_INTER_FRAME_CONFIG_TAG);
      } else {
        return (isKeyFrame
            ? FLV_AVC_VIDEO_KEY_FRAME_DATA_TAG : FLV_AVC_VIDEO_INTER_FRAME_DATA_TAG);
      }
    }
    throw new ProtocolException("Unsupported video codec: " + videoCodecId);
  }

  /**
   * Create an AVCC box config buffer from the given SPS and PPS data.
   */
  public static ByteBuffer createAvccBox(ByteBuffer videoConfigSpsBuffer,
      ByteBuffer videoConfigPpsBuffer) {
    int spsLen = videoConfigSpsBuffer.remaining();
    int ppsLen = videoConfigPpsBuffer.remaining();
    int messageSize = AVCC_SPS_PREFIX_SIZE + spsLen + AVCC_PPS_PREFIX_SIZE + ppsLen;
    ByteBuffer avccBuffer = ByteBuffer.allocate(messageSize);
    avccBuffer.order(ByteOrder.BIG_ENDIAN);
    avccBuffer.limit(messageSize);
    avccBuffer.put((byte) AVC_VERSION);
    avccBuffer.put((byte) AVC_PROFILE);
    avccBuffer.put((byte) AVC_COMPATIBILITY);
    avccBuffer.put((byte) AVC_LEVEL);
    avccBuffer.put((byte) AVC_NALU_LENGTH_SPEC);
    avccBuffer.put((byte) (AVC_SPS_COUNT_RESERVED | (0x1 & AVC_SPS_COUNT_MASK)));
    avccBuffer.put((byte) ((spsLen >> 8) & 0xff));
    avccBuffer.put((byte) (spsLen & 0xff));
    avccBuffer.put(videoConfigSpsBuffer);
    avccBuffer.put((byte) 0x01);
    avccBuffer.put((byte) ((ppsLen >> 8) & 0xff));
    avccBuffer.put((byte) (ppsLen & 0xff));
    avccBuffer.put(videoConfigPpsBuffer);
    avccBuffer.position(0);
    return avccBuffer;
  }
}

#ifndef HACKRFTRANSFER_H
#define HACKRFTRANSFER_H

#include <hackrf.h>
#include "dataStream/ByteStream.h"
#include <jni.h>
#include <cstdio>
#include <ctime>

#define FREQ_MIN_HZ     (1000000ll)    /* 1MHz */
#define FREQ_MAX_HZ     (6000000000ll) /* 6000MHz */
#define FREQ_ABS_MAX_HZ (7250000000ll) /* 7250MHz */
#define DEFAULT_FREQ_HZ (900000000ll)  /* 900MHz */

#define SAMPLE_RATE_MIN_HZ     (2000000)  /* 2MHz min sample rate */
#define SAMPLE_RATE_MAX_HZ     (20000000) /* 20MHz max sample rate */
#define DEFAULT_SAMPLE_RATE_HZ (10000000) /* 10MHz default sample rate */

enum transceiver_mode{
    TRANSCEIVER_MODE_OFF = 0,
    TRANSCEIVER_MODE_RX = 1,
    TRANSCEIVER_MODE_TX = 2,
    TRANSCEIVER_MODE_SS = 3,
    TRANSCEIVER_MODE_RX_SWEEP = 5,
};

class NativeByteBuffer;
class HackRFTransfer;
class ByteStream;

class HackRFTransfer {
public:
    HackRFTransfer();
    virtual ~HackRFTransfer();
    static HackRFTransfer &getInstance();

    void writeBuffer(uint8_t *data, uint32_t size);
    void writeBuffer(NativeByteBuffer *buffer);

    void readBuffer(uint8_t *data, uint32_t size);
    void readBuffer(NativeByteBuffer *buffer);

    hackrf_error openConnection(int file_descriptor);
    hackrf_error dropConnection();
    bool isDisconnected() const;

    hackrf_error startRx();
    hackrf_error stopRx();
    hackrf_error startTx();
    hackrf_error stopTx();

    hackrf_error startRecording(const char *path);
    void stopRecording();
    bool isRecording() const;

    hackrf_error setFrequency(uint64_t freq_hz);
    uint64_t getFrequency() const;

    hackrf_error setSampleRate(uint32_t sampleRate_hz);
    uint32_t getSampleRate() const;

    hackrf_error setLNAGain(uint8_t gain);
    uint8_t getLNAGain() const;

    hackrf_error setVGAGain(uint8_t gain);
    uint8_t getVGAGain() const;

    hackrf_error setTxVGAGain(uint8_t gain);
    uint8_t getTxVGAGain() const;

    hackrf_error setAmpEnable(const uint8_t enable);
    uint8_t isAmpEnable() const;

    hackrf_error setAntennaEnable(uint8_t enable);
    uint8_t isAntennaEnable() const;

    ByteStream * getByteStream();
    transceiver_mode getTransceiverMode() const;

    static int rx_callback(hackrf_transfer *);
    static int tx_callback(hackrf_transfer *);

    static void runRecording(const char *path);

    static void flush_callback(void *flush_ctx, int success);
    static void tx_complete_callback(hackrf_transfer *transfer, int success);

private:
    hackrf_device *device = nullptr;
    ByteStream *outgoingByteStream = nullptr;

    uint64_t frequency= 0;
    uint32_t sampleRate = 0;

    uint8_t lnaGain = 8; /* RX LNA (IF) gain, 0-40dB, 8dB steps */
    uint8_t vgaGain = 20; /* RX VGA (baseband) gain, 0-62dB, 2dB steps */
    uint8_t txVgaGain = 0; /* TX VGA (IF) gain, 0-47dB, 1dB steps */

    bool isOpen = false;
    bool isAmplificationEnabled = false;
    bool isAntennaPowerEnable = false;
    bool isRecodingEnable = false;

    transceiver_mode transceiverMode = TRANSCEIVER_MODE_OFF;
};

#endif
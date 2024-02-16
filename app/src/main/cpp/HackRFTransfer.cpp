#include "HackRFTransfer.h"
#include "dataStream/ByteStream.h"
#include "dataStream/NativeByteBuffer.h"
#include "dataStream/BuffersStorage.h"
#include "SimpleLog.h"
#include <thread>
#include <filesystem>

HackRFTransfer &HackRFTransfer::getInstance() {
    static HackRFTransfer instance{};
    return instance;
}

HackRFTransfer::HackRFTransfer() {
    outgoingByteStream = new ByteStream();
}

HackRFTransfer::~HackRFTransfer() {
    if (outgoingByteStream != nullptr) {
        delete outgoingByteStream;
        outgoingByteStream = nullptr;
    }
    if (device != nullptr) {
        hackrf_close(device);
        device = nullptr;
    }
}

hackrf_error HackRFTransfer::openConnection(int file_descriptor) {

    if (device != nullptr) {
        LOGE("device already open..");
        return HACKRF_ERROR_BUSY;
    }

    int result = hackrf_init();
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_init() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);;
    }

    result = hackrf_open_by_file_descriptor(file_descriptor, &device);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_open() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    isOpen = true;
    return HACKRF_SUCCESS;
}

hackrf_error HackRFTransfer::dropConnection() {

    if (device == nullptr) {
        LOGE("no open hackrf devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_close(device);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_close() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    isOpen = false;
    return HACKRF_SUCCESS;
}

bool HackRFTransfer::isDisconnected() const {
    return !isOpen;
}

hackrf_error HackRFTransfer::startRx() {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    // Check if the sample rate is not set or set to an invalid value
    if (sampleRate < SAMPLE_RATE_MIN_HZ || sampleRate > SAMPLE_RATE_MAX_HZ) {
        LOGE("Sample rate not set or out of bounds. Setting to default: %u Hz",
             DEFAULT_SAMPLE_RATE_HZ);

        // Check and set the sample rate (call your method for setting the sample rate)
        hackrf_error sampleRateResult = setSampleRate(DEFAULT_SAMPLE_RATE_HZ);
        if (sampleRateResult != HACKRF_SUCCESS) {
            return sampleRateResult;
        }
        sampleRate = DEFAULT_SAMPLE_RATE_HZ;
    }

    // Check if the frequency is not set or set to an invalid value
    if (frequency < FREQ_MIN_HZ || frequency > FREQ_MAX_HZ) {
        LOGE("Frequency not set or out of bounds. Setting to default: %llu Hz",
             DEFAULT_FREQ_HZ);
        // Check and set the frequency (call your method for setting the frequency)
        hackrf_error frequencyResult = setFrequency(DEFAULT_FREQ_HZ);
        if (frequencyResult != HACKRF_SUCCESS) {
            return frequencyResult;
        }
        frequency = DEFAULT_FREQ_HZ;
    }

    int result = hackrf_start_rx(device, rx_callback, nullptr);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_start_rx() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    LOGE("hackrf_start_rx() done");
    transceiverMode = TRANSCEIVER_MODE_RX;
    return HACKRF_SUCCESS;
}

hackrf_error HackRFTransfer::stopRx() {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_stop_rx(device);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_stop_rx() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    LOGE("hackrf_stop_rx() done");
    transceiverMode = TRANSCEIVER_MODE_OFF;
    return HACKRF_SUCCESS;
}

hackrf_error HackRFTransfer::startTx() {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    // Check if the sample rate is not set or set to an invalid value
    if (sampleRate < SAMPLE_RATE_MIN_HZ || sampleRate > SAMPLE_RATE_MAX_HZ) {
        LOGE("Sample rate not set or out of bounds. Setting to default: %u Hz",
             DEFAULT_SAMPLE_RATE_HZ);

        // Check and set the sample rate (call your method for setting the sample rate)
        hackrf_error sampleRateResult = setSampleRate(DEFAULT_SAMPLE_RATE_HZ);
        if (sampleRateResult != HACKRF_SUCCESS) {
            return sampleRateResult;
        }
        sampleRate = DEFAULT_SAMPLE_RATE_HZ;
    }

    // Check if the frequency is not set or set to an invalid value
    if (frequency < FREQ_MIN_HZ || frequency > FREQ_MAX_HZ) {
        LOGE("Frequency not set or out of bounds. Setting to default: %llu Hz",
             DEFAULT_FREQ_HZ);
        // Check and set the frequency (call your method for setting the frequency)
        hackrf_error frequencyResult = setFrequency(DEFAULT_FREQ_HZ);
        if (frequencyResult != HACKRF_SUCCESS) {
            return frequencyResult;
        }
        frequency = DEFAULT_FREQ_HZ;
    }

    int result = hackrf_enable_tx_flush(
            device, flush_callback, nullptr);

    result |= hackrf_set_tx_block_complete_callback(
            device, tx_complete_callback);

    result |= hackrf_start_tx(device, tx_callback, nullptr);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_start_tx() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    LOGE("hackrf_start_tx() done");
    transceiverMode = TRANSCEIVER_MODE_TX;
    return HACKRF_SUCCESS;
}

hackrf_error HackRFTransfer::stopTx() {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_stop_tx(device);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_stop_tx() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
    }

    LOGE("hackrf_stop_tx() done");
    transceiverMode = TRANSCEIVER_MODE_TX;
    return HACKRF_SUCCESS;
}

hackrf_error HackRFTransfer::startRecording(const char *path) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    if (!std::__fs::filesystem::exists(path)) {
        LOGE("File does not exist in path : %s\n", path);
    }

    isRecodingEnable = true;

    std::thread recordingThread(&HackRFTransfer::runRecording, path);

    return HACKRF_SUCCESS;
}

void HackRFTransfer::stopRecording() {
    isRecodingEnable = false;
}

bool HackRFTransfer::isRecording() const {
    return isRecodingEnable;
}

hackrf_error HackRFTransfer::setFrequency(uint64_t freq_hz) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    // Check if the frequency is within the allowed range
    if (freq_hz < FREQ_MIN_HZ || freq_hz > FREQ_MAX_HZ) {
        LOGE("Frequency out of bounds: %llu Hz (Min: %llu Hz, Max: %llu Hz)",
             freq_hz, FREQ_MIN_HZ, FREQ_MAX_HZ);
        return HACKRF_ERROR_INVALID_PARAM;
    }

    int result = hackrf_set_freq(device, freq_hz);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_freq() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    frequency = freq_hz;
    return HACKRF_SUCCESS;
}

uint64_t HackRFTransfer::getFrequency() const {
    return frequency;
}

hackrf_error HackRFTransfer::setSampleRate(uint32_t sampleRate_hz) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    // Check if the sample rate is within the allowed range
    if (sampleRate_hz < SAMPLE_RATE_MIN_HZ || sampleRate_hz > SAMPLE_RATE_MAX_HZ) {
        LOGE("Sample rate out of bounds: %u Hz (Min: %u Hz, Max: %u Hz)",
             sampleRate_hz, SAMPLE_RATE_MIN_HZ, SAMPLE_RATE_MAX_HZ);
        return HACKRF_ERROR_INVALID_PARAM;
    }

    int result = hackrf_set_sample_rate_manual(device, sampleRate_hz, 1);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_sample_rate() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    sampleRate = sampleRate_hz;
    return HACKRF_SUCCESS;
}

uint32_t HackRFTransfer::getSampleRate() const {
    return sampleRate;
}

hackrf_error HackRFTransfer::setLNAGain(uint8_t gain) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    if (gain % 8)
        LOGW("warning: lna_gain must be a multiple of 8");

    int result = hackrf_set_lna_gain(device, gain);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_lna_gain() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    lnaGain = gain;
    return HACKRF_SUCCESS;
}

uint8_t HackRFTransfer::getLNAGain() const {
    return lnaGain;
}

hackrf_error HackRFTransfer::setVGAGain(uint8_t gain) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    if (gain % 2)
        LOGW("warning: vga_gain (-g) must be a multiple of 2");

    int result = hackrf_set_vga_gain(device, gain);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_vga_gain() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);

    }

    vgaGain = gain;
    return HACKRF_SUCCESS;
}

uint8_t HackRFTransfer::getVGAGain() const {
    return vgaGain;
}

hackrf_error HackRFTransfer::setTxVGAGain(uint8_t gain) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_set_txvga_gain(device, gain);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_txvga_gain() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    txVgaGain = gain;
    return HACKRF_SUCCESS;
}

uint8_t HackRFTransfer::getTxVGAGain() const {
    return txVgaGain;
}

hackrf_error HackRFTransfer::setAmpEnable(const uint8_t enable) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_set_amp_enable(device, enable);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_amp_enable() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    isAmplificationEnabled = enable;
    return HACKRF_SUCCESS;
}

uint8_t HackRFTransfer::isAmpEnable() const {
    return isAmplificationEnabled;
}

hackrf_error HackRFTransfer::setAntennaEnable(uint8_t enable) {

    if (device == nullptr) {
        LOGE("No open HackRF devices");
        return HACKRF_ERROR_NOT_FOUND;
    }

    int result = hackrf_set_antenna_enable(device, enable);
    if (result != HACKRF_SUCCESS) {
        LOGE("hackrf_set_antenna_enable() failed: %s (%d)",
             hackrf_error_name(static_cast<hackrf_error>(result)), result);
        return static_cast<hackrf_error>(result);
    }

    isAntennaPowerEnable = enable;
    return HACKRF_SUCCESS;
}

uint8_t HackRFTransfer::isAntennaEnable() const {
    return isAntennaPowerEnable;
}

transceiver_mode HackRFTransfer::getTransceiverMode() const {
    return transceiverMode;
}

void HackRFTransfer::writeBuffer(uint8_t *data, uint32_t size) {
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(size);
    buffer->writeBytes(data, size);
    outgoingByteStream->append(buffer);
}

void HackRFTransfer::writeBuffer(NativeByteBuffer *buffer) {
    outgoingByteStream->append(buffer);
}

void HackRFTransfer::readBuffer(uint8_t *data, uint32_t size) {
    auto *buffer = new NativeByteBuffer(data, size);
    outgoingByteStream->get(buffer);
    buffer->flip();
    outgoingByteStream->discard((uint32_t) buffer->remaining());
}

void HackRFTransfer::readBuffer(NativeByteBuffer *buffer) {
    outgoingByteStream->get(buffer);
    buffer->flip();
    outgoingByteStream->discard((uint32_t) buffer->remaining());
}

int callsPerSecond = 0; // Counter for function calls
auto startTime = std::chrono::steady_clock::now();

#include <chrono>
#include <unistd.h>

int HackRFTransfer::rx_callback(hackrf_transfer *transfer) {

    auto currentTime = std::chrono::steady_clock::now();
    auto elapsedTime = std::chrono::duration_cast<std::chrono::seconds>(
            currentTime - startTime).count();
    if (elapsedTime >= 1) {
        // Log the number of function calls per second
        LOGE("Calls per second: %d", callsPerSecond);

        // Reset the counter and timer
        callsPerSecond = 0;
        startTime = std::chrono::steady_clock::now();
    }
    HackRFTransfer::getInstance().writeBuffer(transfer->buffer, transfer->valid_length);
    return 0;
}

void HackRFTransfer::runRecording(const char *path) {

    FILE *recordingFile = fopen(path, "wb");
    ByteStream *stream = HackRFTransfer::getInstance().getByteStream();

    if (recordingFile == nullptr) {
        LOGE("Failed to open file: %s\n", path);
        HackRFTransfer::getInstance().stopRecording();
        return;
    }

    struct itimerval interval_timer = {
            .it_interval = {.tv_sec = 1, .tv_usec = 0},
            .it_value = {.tv_sec = 1, .tv_usec = 0}};
    setitimer(ITIMER_REAL, &interval_timer, nullptr);

    while (HackRFTransfer::getInstance().isRecording()) {

        if (!stream->hasData()) {
            usleep(5000);
        }
        while (stream->hasData()) {
            size_t bytes_written;

            NativeByteBuffer *buffer = stream->getFirst();
            buffer->clear();

            bytes_written = fwrite(buffer->bytes(), 1, buffer->remaining(), recordingFile);

            buffer->flip();

            if (buffer->remaining() != bytes_written) {
                LOGE("write failed");
                HackRFTransfer::getInstance().stopRecording();
            }

            stream->discard(bytes_written);
        }

        // Wait for SIGALRM from interval timer, or another signal.
        pause();
    }

    // Stop interval timer.
    interval_timer.it_value.tv_sec = 0;
    setitimer(ITIMER_REAL, &interval_timer, nullptr);

    // Close the recording file.
    fclose(recordingFile);
    HackRFTransfer::getInstance().stopRecording();
}

int HackRFTransfer::tx_callback(hackrf_transfer *transfer) {
    size_t bytes_to_read;
    size_t bytes_read;

    /* If the last data was already buffered, stop. */
    if (!HackRFTransfer::getInstance().outgoingByteStream->hasData()) {
        return -1;
    }

    /* Determine how many bytes we need to put in the buffer. */
    bytes_to_read = transfer->buffer_length;

    /* Fill the buffer. */
    auto *buffer = new NativeByteBuffer(
            transfer->buffer,
            transfer->buffer_length);

    buffer->clear();
    HackRFTransfer::getInstance().
            outgoingByteStream->get(buffer);
    buffer->flip();

    /* get filled buffer size */
    bytes_read = buffer->remaining();
    HackRFTransfer::getInstance().
            outgoingByteStream->discard((uint32_t) bytes_read);

    /* Now set the valid length to the bytes we put in the buffer. */
    transfer->valid_length = bytes_read;

    /* If we filled the number of bytes needed, return normally. */
    if (bytes_read == bytes_to_read) {
        return 0;
    }

    /* Otherwise, the data ran short. If not repeating, this is the last data. */
    /* Then return normally. */
    return 0;
}

void HackRFTransfer::tx_complete_callback(hackrf_transfer *transfer, int success) {}

void HackRFTransfer::flush_callback(void *flush_ctx, int success) {}

ByteStream *HackRFTransfer::getByteStream() {
    return outgoingByteStream;
}
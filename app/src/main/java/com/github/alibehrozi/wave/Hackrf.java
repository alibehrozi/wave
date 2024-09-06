package com.github.alibehrozi.wave;

import androidx.annotation.NonNull;

public class Hackrf {

    /**
     * Opens a connection to the HackRF device.
     *
     * @param fileDescriptor The file descriptor of the HackRF device.
     * @return The status of the operation.
     */
    public static native int open(int fileDescriptor);

    /**
     * Gets hackrf state Open(true) / Close (false)
     *
     * @return The status of the hackRF
     */
    public static native boolean isOpen();

    /**
     * Close a previously opened device
     *
     * @return @ref HACKRF_SUCCESS on success or variant of @ref hackrf_error
     */
    public static native int close();

    /**
     * Start receiving
     * <p>
     * Should be called after setting gains, frequency and sampling rate,
     * as these values won't get reset but instead keep their last value,
     * thus their state is unknown.
     * <p>
     * The callback is called with a @ref hackrf_transfer object whenever the buffer is full.
     * The callback is called in an async context so no libhackrf functions should be called from it.
     * The callback should treat its argument as read-only.
     *
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int startRx();

    /**
     * Stop receiving
     *
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int stopRx();

    /**
     * Start transmitting
     * <p>
     * Should be called after setting gains, frequency and sampling rate,
     * as these values won't get reset but instead keep their last value,
     * thus their state is unknown.
     * Setting flush function (using @ref hackrf_enable_tx_flush) and/or setting block complete callback (using @ref hackrf_set_tx_block_complete_callback) (if these features are used) should also be done before this.
     * <p>
     * The callback is called with a @ref hackrf_transfer object whenever a transfer buffer is needed to be filled with samples. The callback is called in an async context so no libhackrf functions should be called from it. The callback should treat its argument as read-only, except the @ref hackrf_transfer.buffer and @ref hackrf_transfer.valid_length.
     *
     * @param callback tx_callback
     * @return @{@link hackrf_error#HACKRF_SUCCESS} on success or @ref hackrf_error variant
     */
    public static native int startTx(TxCallback callback);

    /**
     * Stop transmission
     *
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int stopTx();

    /**
     * Data stream to/from HackRF
     *
     * @return {@link com.github.alibehrozi.wave.dataStream.ByteBufferStream} address in memory
     */
    public static native long getByteStream();

    /**
     * Gets current mode of the HackRF device.
     *
     * @return different modes of the HackRF device. see @{@link transceiver_mode}
     */
    public static native int getTransceiverMode();

    /**
     * Sets the frequency of the HackRF device.
     *
     * @param frequency The desired frequency in Hz.
     * @return The status of the operation.
     */
    public static native int setFrequency(long frequency);

    /**
     * Gets the frequency of the HackRF device.
     *
     * @return Frequency in Hz.
     */
    public static native long getFrequency();

    /**
     * Sets the sample rate of the HackRF device.
     *
     * @param sampleRate The desired sample rate in Hz.
     * @return The status of the operation.
     */
    public static native int setSampleRate(long sampleRate);

    /**
     * Gets the sample rate of the HackRF device.
     *
     * @return Sample rate in Hz.
     */
    public static native long getSampleRate();

    /**
     * Set LNA gain
     * <p>
     * Set the RF RX gain of the MAX2837 transceiver IC ("IF" gain setting) in decibels.
     * Must be in range 0-40dB, with 8dB steps.
     *
     * @param value RX IF gain value in dB
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int setLNAGain(int value);

    /**
     * Gets the RF RX gain of the MAX2837 transceiver IC.
     *
     * @return RX IF gain value in dB
     */
    public static native int getLNAGain();

    /**
     * Set baseband RX gain of the MAX2837 transceier IC ("BB" or "VGA" gain setting) in decibels.
     * Must be in range 0-62dB with 2dB steps.
     *
     * @param value RX BB gain value in dB
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int setVGAGain(int value);

    /**
     * Gets baseband RX gain of the MAX2837 transceier IC.
     *
     * @return RX BB gain value in dB
     */
    public static native int getVGAGain();

    /**
     * Set RF TX gain of the MAX2837 transceiver IC ("IF" or "VGA" gain setting) in decibels.
     * Must be in range 0-47dB in 1dB steps.
     *
     * @param value TX IF gain value in dB
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int setTxVGAGain(int value);

    /**
     * Gets RF TX gain of the MAX2837 transceiver IC.
     *
     * @return TX IF gain value in dB
     */
    public static native int getTxVGAGain();

    /**
     * Enable/disable 14dB RF amplifier
     * <p>
     * Enable / disable the ~11dB RF RX/TX amplifiers U13/U25 via controlling switches U9 and U14.
     *
     * @param value enable (1) or disable (0) amplifier
     * @return @{@link hackrf_error#HACKRF_SUCCESS}  on success or @{@link hackrf_error} variant
     */
    public static native int setAmpEnable(boolean value);

    /**
     * Gets RF amplifier state.
     *
     * @return amplifier is enable (true) or disable (false)
     */
    public static native boolean isAmpEnable();

    /**
     * Enable / disable bias-tee (antenna port power)
     * <p>
     * Enable or disable the **3.3V (max 50mA)** bias-tee (antenna port power).
     * Defaults to disabled.
     * <p>
     * **NOTE:** the firmware auto-disables this after returning to IDLE mode,
     * so a perma-set is not possible, which means all software supporting HackRF
     * devices must support enabling bias-tee, as setting it externally is not possible like it is with RTL-SDR for example.
     *
     * @param enable enable (1) or disable (0) bias-tee
     * @return @{@link hackrf_error#HACKRF_SUCCESS} on success or @ref hackrf_error variant
     */
    public static native int setAntennaEnable(boolean enable);

    /**
     * Gets bias-tee (antenna port power) state.
     *
     * @return bias-tee enable (true) or disable (false)
     */
    public static native boolean isAntennaEnable();

    public enum transceiver_mode {
        /**
         * Transceiver is in Off mode (0)
         */
        HACKRF_TRANSCEIVER_MODE_OFF(0),
        /**
         * Transceiver is in Receive mode (1)
         */
        HACKRF_TRANSCEIVER_MODE_RECEIVE(1),
        /**
         * Transceiver is in Transmit mode (2)
         */
        HACKRF_TRANSCEIVER_MODE_TRANSMIT(2),
        /**
         * Transceiver is in Single Sideband (SS) mode (3)
         */
        HACKRF_TRANSCEIVER_MODE_SS(3),
        /**
         * Transceiver is in CPLD (Complex Programmable Logic Device) update mode (4)
         */
        TRANSCEIVER_MODE_CPLD_UPDATE(4),
        /**
         * Transceiver is in Receive Sweep mode (5)
         */
        TRANSCEIVER_MODE_RX_SWEEP(5);

        // The integer value associated with each transceiver mode.
        final int transceiverMode;

        /**
         * Constructor for transceiver_mode enum, sets the associated integer value.
         *
         * @param transceiverMode The integer value representing the transceiver mode.
         */
        transceiver_mode(int transceiverMode) {
            this.transceiverMode = transceiverMode;
        }
    }

    /**
     * Error codes returned by libhackrf functions.
     * <p>
     * functions that are specified to return int are actually returning these enum values.
     */
    public enum hackrf_error {

        /**
         * No error occurred.
         */
        HACKRF_SUCCESS(0, "HACKRF_SUCCESS"),

        /**
         * TRUE value, returned by some functions that return a boolean value.
         */
        HACKRF_TRUE(1, "HACKRF_TRUE"),

        /**
         * The function was called with invalid parameters.
         */
        HACKRF_ERROR_INVALID_PARAM(-2, "Invalid parameter(s)"),

        /**
         * USB device not found.
         */
        HACKRF_ERROR_NOT_FOUND(-5, "HackRF not found"),

        /**
         * Resource is busy, possibly the device is already opened.
         */
        HACKRF_ERROR_BUSY(-6, "HackRF busy"),

        /**
         * Memory allocation (on host side) failed.
         */
        HACKRF_ERROR_NO_MEM(-11, "Insufficient memory"),

        /**
         * LibUSB error.
         */
        HACKRF_ERROR_LIBUSB(-1000, "USB error"),

        /**
         * Error setting up transfer thread (pthread-related error).
         */
        HACKRF_ERROR_THREAD(-1001, "Transfer thread error"),

        /**
         * Streaming thread could not start due to an error.
         */
        HACKRF_ERROR_STREAMING_THREAD_ERR(-1002, "Streaming thread encountered an error"),

        /**
         * Streaming thread stopped due to an error.
         */
        HACKRF_ERROR_STREAMING_STOPPED(-1003, "Streaming stopped"),

        /**
         * Streaming thread exited normally.
         */
        HACKRF_ERROR_STREAMING_EXIT_CALLED(-1004, "Streaming terminated"),

        /**
         * The installed firmware does not support this function.
         */
        HACKRF_ERROR_USB_API_VERSION(-1005, "Feature not supported by installed firmware"),

        /**
         * Cannot exit library as one or more HackRFs still in use.
         */
        HACKRF_ERROR_NOT_LAST_DEVICE(-2000, "One or more HackRFs still in use"),

        /**
         * Unspecified error.
         */
        HACKRF_ERROR_OTHER(-9999, "Unspecified error");

        private final int code;
        private final String description;

        hackrf_error(int code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * Get the integer error code.
         *
         * @return the error code
         */
        public int getCode() {
            return code;
        }

        /**
         * Get a human-readable description of the error.
         *
         * @return the description of the error
         */
        public String getDescription() {
            return description;
        }

        @NonNull
        @Override
        public String toString() {
            return description;
        }

        /**
         * Get the HackrfError enum from an integer code.
         *
         * @param code the error code
         * @return the corresponding HackrfError enum value
         */
        public static hackrf_error fromCode(int code) {
            for (hackrf_error error : values()) {
                if (error.code == code) {
                    return error;
                }
            }
            return HACKRF_ERROR_OTHER; // Default to OTHER if code is not found
        }
    }
}
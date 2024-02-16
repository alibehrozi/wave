package com.github.alibehrozi.wave;

/**
 * Callback for receiving data.
 */
public interface RxCallback {

    /**
     * This method is called when data is received.
     *
     * @param data The received data as a byte array.
     */
    void onData(byte[] data);
}
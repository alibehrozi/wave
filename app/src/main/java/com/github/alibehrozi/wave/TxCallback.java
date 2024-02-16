package com.github.alibehrozi.wave;

/**
 * Callback for signaling completion of a transmit operation.
 */
public interface TxCallback {

    /**
     * This method is called when the transmit operation is completed.
     */
    void onComplete();
}
#ifndef SIGNAL_SOURCE_H
#define SIGNAL_SOURCE_H

#include "../../core/Block.h"
#include <cmath>

/**
 * Supported signal types
 */
enum class SignalType {
    SINE = 0,
    SQUARE = 1,
    TRIANGLE = 2,
    SAWTOOTH = 3
};

/**
 * DSP block that generates periodic signals
 */
class SignalSource : public Block {
public:
    /**
     * Create a new SignalSource
     * @param type Data type to output (FLOAT or COMPLEX_FLOAT)
     * @param sample_rate Sample rate in Hz
     * @param frequency Signal frequency in Hz
     * @param amplitude Signal amplitude
     * @param signal_type Type of signal to generate
     * @param name Block name
     */
    SignalSource(DataType type, double sample_rate, double frequency, double amplitude = 1.0, SignalType signal_type = SignalType::SINE, const std::string& name = "signal_source");

    virtual ~SignalSource();

    /**
     * Perform signal generation
     */
    void work() override;

    /**
     * Reset signal phase
     */
    void reset() override;

    /**
     * Check if block is ready to work
     */
    bool is_ready() override;

    /**
     * Set signal frequency
     */
    void set_frequency(double frequency);

    /**
     * Set signal amplitude
     */
    void set_amplitude(double amplitude);

    /**
     * Set signal type
     */
    void set_signal_type(SignalType signal_type);

private:
    DataType type_;
    double sample_rate_;
    double frequency_;
    double amplitude_;
    SignalType signal_type_;
    double phase_;
    double phase_inc_;

    /**
     * Internal generation helper
     */
    template<typename T>
    void generate(T* output, size_t nitems);

    /**
     * Update phase increment based on frequency and sample rate
     */
    void update_phase_inc();
};

#endif // SIGNAL_SOURCE_H

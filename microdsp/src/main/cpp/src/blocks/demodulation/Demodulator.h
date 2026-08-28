#ifndef DEMODULATOR_H
#define DEMODULATOR_H

#include "../../core/Block.h"

/**
 * Base class for all demodulators.
 * Typically takes COMPLEX_FLOAT input and produces FLOAT output.
 */
class Demodulator : public Block {
public:
    Demodulator(const std::string& name) : Block(name) {
        add_input_port("in", DataType::COMPLEX_FLOAT);
        add_output_port("out", DataType::FLOAT);
    }

    virtual ~Demodulator() {}
};

#endif // DEMODULATOR_H

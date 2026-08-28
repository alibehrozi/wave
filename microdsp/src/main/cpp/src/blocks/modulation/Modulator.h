#ifndef MODULATOR_H
#define MODULATOR_H

#include "../../core/Block.h"

/**
 * Base class for all modulators.
 * Typically takes FLOAT input (e.g. audio) and produces COMPLEX_FLOAT output (IQ).
 */
class Modulator : public Block {
public:
    Modulator(const std::string& name) : Block(name) {
        add_input_port("in", DataType::FLOAT);
        add_output_port("out", DataType::COMPLEX_FLOAT);
    }

    virtual ~Modulator() {}
};

#endif // MODULATOR_H

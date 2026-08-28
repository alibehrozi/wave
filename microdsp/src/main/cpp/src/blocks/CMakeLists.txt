add_library(blocks OBJECT
        sources/SdrSource.cpp
        sources/SdrSource_jni.cpp
        sources/AudioSource.cpp
        sources/AudioSource_jni.cpp
        sources/FileSource.cpp
        sources/FileSource_jni.cpp
        sources/NoiseSource.cpp
        sources/NoiseSource_jni.cpp
        sources/SignalSource.cpp
        sources/SignalSource_jni.cpp

        filters/FirFilter.cpp
        filters/FirFilter_jni.cpp
        filters/FilterDesign.cpp
        filters/HilbertFilter.cpp
        filters/IirFilter.cpp

        demodulation/AmDemodulator.cpp
        demodulation/FmDemodulator.cpp
        demodulation/SsbDemodulator.cpp
        demodulation/WfmDemodulator.cpp
        demodulation/FskDemodulator.cpp
        demodulation/BpskDemodulator.cpp
        demodulation/Demodulation_jni.cpp

        modulation/AmModulator.cpp
        modulation/FmModulator.cpp
        modulation/SsbModulator.cpp
        modulation/FskModulator.cpp
        modulation/BpskModulator.cpp
        modulation/Modulation_jni.cpp

        utils/RationalResampler.cpp
        utils/AgcBlock.cpp
        utils/SquelchBlock.cpp
        utils/Utils_jni.cpp

        sinks/FileSink.cpp
        sinks/FileSink_jni.cpp
        sinks/AudioSink.cpp
        sinks/AudioSink_jni.cpp
        sinks/SdrSink.cpp
        sinks/SdrSink_jni.cpp

        sources/JavaSource.cpp
        sinks/JavaSink.cpp
        JavaInterop_jni.cpp

        math/Add.cpp
        math/AddConst.cpp
        math/Multiply.cpp
        math/MultiplyConst.cpp
        math/Subtract.cpp
        math/Divide.cpp
        math/Abs.cpp
        math/ComplexToMag.cpp
        math/ComplexToMagSquared.cpp
        math/ComplexToRealImag.cpp
        math/RealImagToComplex.cpp
        math/Conjugate.cpp
        math/Log10.cpp
        math/Math_jni.cpp
)

target_include_directories(blocks
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/..
)

target_link_libraries(blocks
        PUBLIC
        core
        external_dependencies
        android
        log
)

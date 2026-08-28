#ifndef HACKRF_INFO_H
#define HACKRF_INFO_H

#include <string>
#include <vector>
#include <cstdint>
#include "HackRFDevice.h"

namespace sdr {

    class HackRFInfo {
    public:
        explicit HackRFInfo(HackRfDevice* device);

        // Board Information
        uint8_t getBoardId();
        std::string getBoardIdName();
        uint8_t getBoardRevision();
        std::string getBoardRevisionName();
        std::string getManufacturerInfo();

        // Version Information
        std::string getVersionString();
        uint16_t getUsbApiVersion();
        std::string getFirmwareVersion();

        // Part ID and Serial
        std::string getPartIdString();
        uint32_t getPartId(int index);
        std::string getSerialNumber();

        // Device capabilities and platform
        uint32_t getSupportedPlatforms();
        std::vector<std::string> getSupportedPlatformNames();
        std::string getPlatformCompatibilityInfo();

        // Opera Cake & CPLD
        std::vector<uint8_t> getOperaCakeBoards();
        std::string getOperaCakeInfo();
        uint32_t getCpldChecksum();

        // Full info dump (matches hackrf_info tool output)
        std::string getFullInfo();

        // Validation
        bool isValid() const;

    private:
        HackRfDevice* device_;
    };

} // namespace sdr

#endif // HACKRF_INFO_H

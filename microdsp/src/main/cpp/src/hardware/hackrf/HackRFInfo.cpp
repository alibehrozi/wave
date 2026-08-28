#include "HackRFInfo.h"
#include <sstream>
#include <iomanip>
#include <cstdio>

namespace sdr {

    HackRFInfo::HackRFInfo(HackRfDevice* device)
            : device_(device) {
    }

    bool HackRFInfo::isValid() const {
        return device_ && device_->isConnected();
    }

    uint8_t HackRFInfo::getBoardId() {
        if (!isValid()) return BOARD_ID_UNDETECTED;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint8_t board_id = BOARD_ID_UNDETECTED;
        if (hackrf_board_id_read(dev, &board_id) == HACKRF_SUCCESS) {
            return board_id;
        }
        return BOARD_ID_UNDETECTED;
    }

    std::string HackRFInfo::getBoardIdName() {
        if (!isValid()) return "Unknown";
        uint8_t board_id = getBoardId();
        const char* name = hackrf_board_id_name((hackrf_board_id)board_id);
        return name ? name : "Unknown";
    }

    uint8_t HackRFInfo::getBoardRevision() {
        if (!isValid()) return BOARD_REV_UNDETECTED;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint16_t usb_version = getUsbApiVersion();
        uint8_t board_id = getBoardId();
        uint8_t board_rev = BOARD_REV_UNDETECTED;

        if ((usb_version >= 0x0106) &&
            ((board_id == BOARD_ID_HACKRF1_OG) ||
             (board_id == BOARD_ID_HACKRF1_R9) ||
             (board_id == BOARD_ID_PRALINE))) {
            if (hackrf_board_rev_read(dev, &board_rev) == HACKRF_SUCCESS) {
                return board_rev;
            }
        }
        return BOARD_REV_UNDETECTED;
    }

    std::string HackRFInfo::getBoardRevisionName() {
        uint8_t board_rev = getBoardRevision();
        switch (board_rev) {
            case BOARD_REV_UNDETECTED:
                return "Undetected";
            case BOARD_REV_UNRECOGNIZED:
                return "Unrecognized";
            default:
                const char* name = hackrf_board_rev_name((hackrf_board_rev)board_rev);
                return name ? name : "Unknown";
        }
    }

    std::string HackRFInfo::getManufacturerInfo() {
        uint8_t board_rev = getBoardRevision();
        if (board_rev > BOARD_REV_HACKRF1_OLD) {
            if (board_rev & HACKRF_BOARD_REV_GSG) {
                return "Great Scott Gadgets";
            } else {
                return "Non-GSG";
            }
        }
        return "Great Scott Gadgets";
    }

    std::string HackRFInfo::getVersionString() {
        if (!isValid()) return "";
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        char version[256] = {0};
        if (hackrf_version_string_read(dev, version, sizeof(version) - 1) == HACKRF_SUCCESS) {
            return std::string(version);
        }
        return "";
    }

    uint16_t HackRFInfo::getUsbApiVersion() {
        if (!isValid()) return 0;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint16_t usb_version = 0;
        if (hackrf_usb_api_version_read(dev, &usb_version) == HACKRF_SUCCESS) {
            return usb_version;
        }
        return 0;
    }

    std::string HackRFInfo::getFirmwareVersion() {
        if (!isValid()) return "";
        std::string version = getVersionString();
        uint16_t usb_version = getUsbApiVersion();

        char buffer[300];
        snprintf(buffer, sizeof(buffer), "%s (API:%x.%02x)",
                 version.c_str(),
                 (usb_version >> 8) & 0xFF,
                 usb_version & 0xFF);
        return std::string(buffer);
    }

    uint32_t HackRFInfo::getPartId(int index) {
        if (!isValid() || index < 0 || index > 1) return 0;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        read_partid_serialno_t read_partid_serialno;
        if (hackrf_board_partid_serialno_read(dev, &read_partid_serialno) == HACKRF_SUCCESS) {
            return read_partid_serialno.part_id[index];
        }
        return 0;
    }

    std::string HackRFInfo::getPartIdString() {
        if (!isValid()) return "";
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        read_partid_serialno_t read_partid_serialno;
        if (hackrf_board_partid_serialno_read(dev, &read_partid_serialno) == HACKRF_SUCCESS) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "0x%08x 0x%08x",
                     read_partid_serialno.part_id[0],
                     read_partid_serialno.part_id[1]);
            return std::string(buffer);
        }
        return "";
    }

    std::string HackRFInfo::getSerialNumber() {
        if (!isValid()) return "";
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        read_partid_serialno_t read_partid_serialno;
        if (hackrf_board_partid_serialno_read(dev, &read_partid_serialno) == HACKRF_SUCCESS) {
            char buffer[64];
            snprintf(buffer, sizeof(buffer), "%08x%08x%08x%08x",
                     read_partid_serialno.serial_no[0],
                     read_partid_serialno.serial_no[1],
                     read_partid_serialno.serial_no[2],
                     read_partid_serialno.serial_no[3]);
            return std::string(buffer);
        }
        return "";
    }

    uint32_t HackRFInfo::getSupportedPlatforms() {
        if (!isValid()) return 0;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint16_t usb_version = getUsbApiVersion();
        uint32_t supported_platform = 0;
        if (usb_version >= 0x0106) {
            if (hackrf_supported_platform_read(dev, &supported_platform) == HACKRF_SUCCESS) {
                return supported_platform;
            }
        }
        return 0;
    }

    std::vector<std::string> HackRFInfo::getSupportedPlatformNames() {
        std::vector<std::string> names;
        uint32_t platform = getSupportedPlatforms();
        uint8_t board_rev = getBoardRevision();

        if (platform & HACKRF_PLATFORM_JAWBREAKER) {
            names.push_back("Jawbreaker");
        }
        if (platform & HACKRF_PLATFORM_RAD1O) {
            names.push_back("rad1o");
        }
        if ((platform & HACKRF_PLATFORM_HACKRF1_OG) ||
            (platform & HACKRF_PLATFORM_HACKRF1_R9)) {
            names.push_back("HackRF One");
        }
        if (platform & HACKRF_PLATFORM_PRALINE) {
            if (board_rev & HACKRF_BOARD_REV_GSG) {
                names.push_back("HackRF Pro");
            } else {
                names.push_back("Praline");
            }
        }
        return names;
    }

    std::string HackRFInfo::getPlatformCompatibilityInfo() {
        uint32_t platform = getSupportedPlatforms();
        uint8_t board_id = getBoardId();

        switch (board_id) {
            case BOARD_ID_HACKRF1_OG:
                if (!(platform & HACKRF_PLATFORM_HACKRF1_OG)) {
                    return "Error: Firmware does not support HackRF One revisions older than r9.";
                }
                break;
            case BOARD_ID_HACKRF1_R9:
                if (!(platform & HACKRF_PLATFORM_HACKRF1_R9)) {
                    return "Error: Firmware does not support HackRF One r9.";
                }
                break;
            case BOARD_ID_JAWBREAKER:
                if (!(platform & HACKRF_PLATFORM_JAWBREAKER)) {
                    return "Error: Firmware does not support hardware platform.";
                }
                break;
            case BOARD_ID_RAD1O:
                if (!(platform & HACKRF_PLATFORM_RAD1O)) {
                    return "Error: Firmware does not support hardware platform.";
                }
                break;
            case BOARD_ID_PRALINE:
                if (!(platform & HACKRF_PLATFORM_PRALINE)) {
                    return "Error: Firmware does not support hardware platform.";
                }
                break;
        }
        return "Compatible";
    }

    std::vector<uint8_t> HackRFInfo::getOperaCakeBoards() {
        std::vector<uint8_t> boards;
        if (!isValid()) return boards;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint8_t operacakes[8] = {0};
        int result = hackrf_get_operacake_boards(dev, &operacakes[0]);
        if (result == HACKRF_SUCCESS) {
            for (int j = 0; j < 8; j++) {
                if (operacakes[j] == HACKRF_OPERACAKE_ADDRESS_INVALID) {
                    break;
                }
                boards.push_back(operacakes[j]);
            }
        }
        return boards;
    }

    std::string HackRFInfo::getOperaCakeInfo() {
        auto boards = getOperaCakeBoards();
        if (boards.empty()) return "None";

        std::stringstream ss;
        for (size_t j = 0; j < boards.size(); j++) {
            if (j > 0) ss << ", ";
            ss << "Address: " << static_cast<int>(boards[j]);
        }
        return ss.str();
    }

    uint32_t HackRFInfo::getCpldChecksum() {
#ifdef HACKRF_ISSUE_609_IS_FIXED
        if (!isValid()) return 0;
        hackrf_device* dev = reinterpret_cast<hackrf_device*>(device_->getNativeHandle());
        uint32_t cpld_crc = 0;
        if (hackrf_cpld_checksum(dev, &cpld_crc) == HACKRF_SUCCESS) {
            return cpld_crc;
        }
#endif
        return 0;
    }

    std::string HackRFInfo::getFullInfo() {
        std::stringstream ss;
        ss << "Board ID Number: " << static_cast<int>(getBoardId()) << " (" << getBoardIdName() << ")\n";
        ss << "Firmware Version: " << getFirmwareVersion() << "\n";
        ss << "Part ID Number: " << getPartIdString() << "\n";
        ss << "Serial Number: " << getSerialNumber() << "\n";
        ss << "Hardware Revision: " << getBoardRevisionName() << "\n";

        auto platforms = getSupportedPlatformNames();
        if (!platforms.empty()) {
            ss << "Hardware supported by installed firmware:\n";
            for (const auto& name : platforms) {
                ss << "    " << name << "\n";
            }
        }

        auto operacakes = getOperaCakeBoards();
        for (uint8_t addr : operacakes) {
            ss << "Opera Cake found, address: " << static_cast<int>(addr) << "\n";
        }

        uint32_t cpld = getCpldChecksum();
        if (cpld != 0) {
            char cpld_buf[32];
            snprintf(cpld_buf, sizeof(cpld_buf), "0x%08x", cpld);
            ss << "CPLD checksum: " << cpld_buf << "\n";
        }

        return ss.str();
    }

} // namespace sdr

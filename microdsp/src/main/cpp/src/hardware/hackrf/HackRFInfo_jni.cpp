#include <jni.h>
#include <string>
#include <vector>
#include "HackRFInfo.h"

#define JNI_FUNC(name) Java_com_github_alibehrozi_wave_microdsp_hardware_sdr_drivers_hackrf_HackRFInfo_##name

using namespace sdr;

extern "C" {

JNIEXPORT jlong JNICALL JNI_FUNC(nativeCreateHackRFInfo)(JNIEnv* env, jclass clazz, jlong deviceHandle) {
    auto* sdrDevice = reinterpret_cast<HackRfDevice*>(deviceHandle);
    if (!sdrDevice) return 0;
    return reinterpret_cast<jlong>(new HackRFInfo(sdrDevice));
}

JNIEXPORT void JNICALL JNI_FUNC(nativeDestroyHackRFInfo)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    if (info) delete info;
}

JNIEXPORT jbyte JNICALL JNI_FUNC(getBoardID)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jbyte>(info->getBoardId()) : 0;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getBoardIDName)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getBoardIdName().c_str() : "Unknown");
}

JNIEXPORT jbyte JNICALL JNI_FUNC(getBoardRevision)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jbyte>(info->getBoardRevision()) : 0;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getBoardRevisionName)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getBoardRevisionName().c_str() : "Unknown");
}

JNIEXPORT jstring JNICALL JNI_FUNC(getBoardManufacturerInfo)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getManufacturerInfo().c_str() : "");
}

JNIEXPORT jstring JNICALL JNI_FUNC(getVersionString)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getVersionString().c_str() : "");
}

JNIEXPORT jshort JNICALL JNI_FUNC(getUSBAPIVersion)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jshort>(info->getUsbApiVersion()) : 0;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getFirmwareVersion)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getFirmwareVersion().c_str() : "");
}

JNIEXPORT jint JNICALL JNI_FUNC(getPartID)(JNIEnv* env, jclass clazz, jlong nativePtr, jint index) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jint>(info->getPartId(index)) : 0;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getPartIDString)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getPartIdString().c_str() : "");
}

JNIEXPORT jint JNICALL JNI_FUNC(getSupportedPlatform)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jint>(info->getSupportedPlatforms()) : 0;
}

JNIEXPORT jobjectArray JNICALL JNI_FUNC(getSupportedPlatformNames)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    std::vector<std::string> names;
    if (info) names = info->getSupportedPlatformNames();

    jobjectArray array = env->NewObjectArray(names.size(), env->FindClass("java/lang/String"), env->NewStringUTF(""));
    for (size_t i = 0; i < names.size(); i++) {
        env->SetObjectArrayElement(array, i, env->NewStringUTF(names[i].c_str()));
    }
    return array;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getPlatformCompatibilityInfo)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getPlatformCompatibilityInfo().c_str() : "");
}

JNIEXPORT jbyteArray JNICALL JNI_FUNC(getOperaCakeBoards)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    std::vector<uint8_t> boards;
    if (info) boards = info->getOperaCakeBoards();

    jbyteArray array = env->NewByteArray(boards.size());
    env->SetByteArrayRegion(array, 0, boards.size(), reinterpret_cast<const jbyte*>(boards.data()));
    return array;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getOperaCakeInfo)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getOperaCakeInfo().c_str() : "");
}

JNIEXPORT jint JNICALL JNI_FUNC(getCPLDChecksum)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? static_cast<jint>(info->getCpldChecksum()) : 0;
}

JNIEXPORT jstring JNICALL JNI_FUNC(getDeviceInfo)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return env->NewStringUTF(info ? info->getFullInfo().c_str() : "");
}

JNIEXPORT jboolean JNICALL JNI_FUNC(isDeviceValid)(JNIEnv* env, jclass clazz, jlong nativePtr) {
    auto* info = reinterpret_cast<HackRFInfo*>(nativePtr);
    return info ? (info->isValid() ? JNI_TRUE : JNI_FALSE) : JNI_FALSE;
}

} // extern "C"

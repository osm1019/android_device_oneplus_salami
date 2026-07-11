#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'hardware/oplus',
    'hardware/pixelworks/interfaces',
    'hardware/qcom-caf/sm8550',
    'vendor/oneplus/sm8550-common',
    'vendor/qcom/opensource/display',
    'vendor/qcom/opensource/commonsys-intf/display',
]


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    (
        'libhwconfigurationutil',
        'vendor.oplus.hardware.cammidasservice-V1-ndk',
    ): lib_fixup_vendor_suffix,
}

blob_fixups: blob_fixups_user_type = {
    'odm/etc/camera/CameraHWConfiguration.config': blob_fixup()
        .regex_replace('SystemCamera =  0;  0;  0;  1;  0;  1;', 'SystemCamera =  0;  0;  0;  0;  0;  0;'),
    (
        'odm/etc/libnfc-mtp-SN100.conf_22811',
        'odm/etc/libnfc-mtp-SN100.conf_22861'
    ): blob_fixup()
        .regex_replace('(NXPLOG_.*_LOGLEVEL)=0x03', '\\1=0x02')
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0'),
    'odm/lib64/libAlgoProcess.so': blob_fixup()
        .replace_needed('android.hardware.graphics.common-V3-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
    (
        'odm/lib64/libCOppLceTonemapAPI.so',
        'odm/lib64/libCS.so',
        'odm/lib64/libSuperRaw.so',
        'odm/lib64/libYTCommon.so',
        'odm/lib64/libyuv2.so'
    ): blob_fixup()
        .replace_needed('libstdc++.so', 'libstdc++_vendor.so'),
    'odm/lib64/libOGLManager.so': blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),
    # libui ABI-shadow fix (aston 166b8fe9). The odm camera algo libs were built against
    # ColorOS's smaller GraphicBuffer; on A16 the platform libui GraphicBuffer is larger, so
    # ~GraphicBuffer() walks past the chunk -> SIGSEGV in the camera provider on teardown.
    # Ship stock odm libui as libui_oplus.so (see proprietary-files rename) with its SONAME
    # fixed + allocator-V1->V2 (only V2 exists on this build), and repoint the 5 odm consumers'
    # DT_NEEDED. com.qti.node.dewarp (QTI camx) intentionally stays on platform libui.
    'odm/lib64/libHIS.so': blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock')
        .replace_needed('libui.so', 'libui_oplus.so'),
    (
        'odm/lib64/libsharebuffer_impl.so',
        'odm/lib64/libEIS.so',
        'odm/lib64/hw/camera.oemlayer.so',
        'odm/lib64/camera/components/com.oplus.node.sstabphoto.so',
    ): blob_fixup()
        .replace_needed('libui.so', 'libui_oplus.so'),
    'odm/lib64/libui_oplus.so': blob_fixup()
        .fix_soname()
        .replace_needed('android.hardware.graphics.allocator-V1-ndk.so', 'android.hardware.graphics.allocator-V2-ndk.so'),
    'odm/lib64/libarcsoft_high_dynamic_range_v4.so': blob_fixup()
        .clear_symbol_version('remote_handle_close')
        .clear_symbol_version('remote_handle_invoke')
        .clear_symbol_version('remote_handle_open')
        .clear_symbol_version('remote_register_buf_attr')
        .clear_symbol_version('remote_register_buf'),
    'odm/lib64/libextensionlayer.so': blob_fixup()
        .replace_needed('libziparchive.so', 'libziparchive_odm.so'),
    (
        'vendor/bin/hw/vendor.qti.camera.provider-service_64',
        'vendor/lib64/camx.provider-impl.so',
    ): blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v34.so'),
    'vendor/etc/libnfc-nci.conf': blob_fixup()
        .regex_replace('NFC_DEBUG_ENABLED=1', 'NFC_DEBUG_ENABLED=0')
}  # fmt: skip

module = ExtractUtilsModule(
    'salami',
    'oneplus',
    namespace_imports=namespace_imports,
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    add_firmware_proprietary_file=True,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm8550-common', module.vendor
    )
    utils.run()

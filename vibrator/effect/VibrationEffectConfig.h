/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * salami (OnePlus 11) haptic profile tables for liboplusvibratoreffect.
 * Profile switch is handled in hardware/oplus/.../effect/effect.cpp when
 * HAPTIC_PROFILE_SUPPORT is defined.
 *
 * Stock waveforms from OOS libqtivibratoreffect.so (sla0815 + soft).
 * YAAP crisp/gentle retained as alternate profiles.
 */

#pragma once

#define HAPTIC_PROFILE_SUPPORT 1

#include "standard_effect.h"
#include "yaap_haptic_profiles.h"
#include "salami_stock_effects.h"
#include "primitive_effect.h"
#include "generated_primitive_profiles.h"

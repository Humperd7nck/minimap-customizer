#!/usr/bin/env sh
# Independent core/Swing-control checks only; NOT a substitute for gradle clean build.
set -eu
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT HUP INT TERM
main=src/main/java/com/minimapfilter
test=src/test/java/com/minimapfilter
# Window checks require a graphical display or isolated Xvfb server.
headless=true
if [ "${1:-}" = "--placement-only" ] || [ "${1:-}" = "--quick-controls-only" ] || [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--volume-only" ] || [ "${1:-}" = "--popup-only" ] || [ "${1:-}" = "--dropdown-only" ] || [ "${1:-}" = "--reminders-only" ] || [ "${1:-}" = "--banner-only" ] || [ "${1:-}" = "--drawer-only" ] || [ "${1:-}" = "--direct-tools-only" ]; then headless=false; fi
javac --release 11 -d "$out" \
 "$main/InfoPanelQuickControls.java" "$test/InfoPanelQuickControlsChecks.java" "$test/InfoPanelQuickControlsWindowChecks.java" "$test/InfoPanelPopupPlacementWindowChecks.java" "$main/InfoPanelHammerOrb.java" "$main/InfoPanelEyeOrb.java" "$main/InfoPanelToolDrawer.java" "$main/InfoPanelDrawerPainter.java" "$test/InfoPanelToolDrawerChecks.java" "$test/InfoPanelToolDrawerWindowChecks.java" "$test/InfoPanelDirectToolsWindowChecks.java" \
 "$main/InfoPanelVolumeBar.java" "$main/SquareHudFooterMode.java" "$main/SquareHudFooterVisibility.java" \
 "$main/MasterVolumeScripts.java" "$main/InfoPanelNativeVolume.java" "$main/InfoPanelVolume.java" \
 "$main/InfoPanelReminderInput.java" "$main/InfoPanelAlarm.java" "$main/InfoPanelReminders.java" "$main/InfoPanelReminderBanner.java" \
 "$main/InfoPanelNotebook.java" "$main/InfoPanelNotebookStore.java" \
 "$main/InfoPanelText.java" "$main/InfoPanelMetrics.java" \
 "$main/InfoPanelCalculator.java" "$main/InfoPanelTools.java" \
 "$main/InfoPanelPingState.java" "$main/InfoPanelFormatting.java" \
 "$main/SquareHudInfoText.java" "$main/InfoPanelPopup.java" "$main/InfoPanelPopupPosition.java" \
 "$main/InfoPanelCounts.java" "$main/InfoPanelLatency.java" \
 "$main/InfoPanelDropdown.java" "$main/InfoPanelSelectorPopup.java" "$main/TrackingPopup.java" "$main/InfoPanelSettingLayout.java" "$main/SquareHudGeometry.java" "$main/SquareHudText.java" \
 "$test/InfoPanelReminderBannerWindowChecks.java" "$test/InfoPanelReminderBannerChecks.java" "$test/InfoPanelReminderWindowChecks.java" "$test/InfoPanelReminderChecks.java" "$test/InfoPanelDropdownChecks.java" "$test/InfoPanelDropdownWindowChecks.java" "$test/InfoPanelVolumeBarChecks.java" "$test/InfoPanelVolumeBarWindowChecks.java" "$test/InfoPanelVolumeChecks.java" "$test/InfoPanelV3Checks.java" "$test/TrackingPopupChecks.java" "$test/InfoPanelPopupWindowChecks.java" "$test/InfoPanelLayoutChecks.java"
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelV3Checks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelLayoutChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelVolumeChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelVolumeBarChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelDropdownChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelReminderChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelReminderBannerChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelToolDrawerChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.InfoPanelQuickControlsChecks
java -Djava.awt.headless="$headless" -cp "$out" com.minimapfilter.TrackingPopupChecks
# To exercise real windows as well, pass --windows on a machine with a display.
if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--popup-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelPopupWindowChecks "build/popup-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--volume-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelVolumeBarWindowChecks "build/popup-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--dropdown-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelDropdownWindowChecks "build/dropdown-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--reminders-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelReminderWindowChecks "build/reminder-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--banner-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelReminderBannerWindowChecks "build/banner-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--drawer-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelToolDrawerWindowChecks "build/drawer-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--direct-tools-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelDirectToolsWindowChecks "build/direct-tools-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--quick-controls-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelQuickControlsWindowChecks "build/quick-controls-smoke"
fi

if [ "${1:-}" = "--windows" ] || [ "${1:-}" = "--placement-only" ] || [ "${1:-}" = "--popup-only" ]; then
 java -cp "$out" com.minimapfilter.InfoPanelPopupPlacementWindowChecks
fi

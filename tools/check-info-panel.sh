#!/usr/bin/env sh
# Independent headless core/Swing-control checks only; NOT a substitute for gradle clean build.
set -eu
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT HUP INT TERM
main=src/main/java/com/minimapfilter
test=src/test/java/com/minimapfilter
javac --release 11 -d "$out" \
 "$main/InfoPanelQuickControls.java" "$test/InfoPanelQuickControlsChecks.java" \
 "$main/InfoPanelHammerOrb.java" "$main/InfoPanelEyeOrb.java" "$main/InfoPanelToolDrawer.java" "$main/InfoPanelDrawerPainter.java" "$test/InfoPanelToolDrawerChecks.java" \
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
 "$test/InfoPanelReminderBannerChecks.java" "$test/InfoPanelReminderChecks.java" "$test/InfoPanelDropdownChecks.java" \
 "$test/InfoPanelVolumeBarChecks.java" "$test/InfoPanelVolumeChecks.java" "$test/InfoPanelV3Checks.java" "$test/TrackingPopupChecks.java" "$test/InfoPanelLayoutChecks.java"
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelV3Checks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelLayoutChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelVolumeChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelVolumeBarChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelDropdownChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelReminderChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelReminderBannerChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelToolDrawerChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.InfoPanelQuickControlsChecks
java -Djava.awt.headless=true -cp "$out" com.minimapfilter.TrackingPopupChecks

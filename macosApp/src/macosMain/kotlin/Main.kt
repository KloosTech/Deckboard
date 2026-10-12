package de.kloostech.deckboard.macos

import androidx.compose.ui.window.Window
import de.kloostech.deckboard.App
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy

fun main() {
    NSApplication.sharedApplication()
    // A bare executable is not a regular app by default: without this its window never becomes key and gets no keyboard input.
    NSApp?.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    Window("Deckboard (macOS native)") {
        App()
    }
    NSApp?.activateIgnoringOtherApps(true)
    NSApp?.run()
}

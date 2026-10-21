# Minecraft 1.21.10 / 4.1.2 backport

Upstream 4.1.2 adjusts primary-button handling for Minecraft 26.3's SDL input
numbering. Minecraft 1.21.10 still uses GLFW: its primary button is zero.
The mapped backport uses GLFW_MOUSE_BUTTON_LEFT for the corresponding controls
rather than copying SDL's button-one literal into a GLFW client.

The matching Printer production-client harness checks all seven upstream
Ctrl/normal-click AUTO transitions through Minecraft's real mouse handler.
This is a local client integration check; this repository's CI runs unit and
standalone/direct/forced-legacy server GameTests.

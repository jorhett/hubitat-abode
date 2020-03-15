# Abode Alarm driver for Hubitat

Allows retrieving mode for each defined area, and changing the mode of each area of an Abode Alarm system.

## Inspired by

* Hubitat example driver code from https://github.com/hubitat/HubitatPublic/tree/master/examples/drivers
* AbodePy by https://github.com/MisterWil/abodepy

## Warranty

I certify that this code is not suitable for any purpose.
If you are foolish enough to install it, **gremlins will eat everything you love**.
*Don't say I didn't tell you.*

I'm associated with neither Hubitat nor Abode in any way other than being a customer of theirs.
They've likely never seen this driver and certainly don't provide support for it.

# Installation

Follow Hubitat's instructions for adding new driver code:
  [https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers)

Import the driver from following URL:
  [https://raw.githubusercontent.com/jorhett/hubitat-abode/master/AbodeAlarm.groovy](https://github.com/jorhett/hubitat-abode/blob/master/AbodeAlarm.groovy)

## Requirements

* In Abode System Settings, `Arm Fault Type` must be set to **Direct Arm**. The driver cannot yet respond to a prompt about faulty devices.

* Multi-Factor Authentication is supported. *You are using MFA for your home security, aren't you?* **Aren't you?**
  You'll have to supply the MFA code when saving preferences for a successful connection if MFA is enabled.

## Known limitations

* This is best used if you want to control state from Hubitat to Abode. We cannot yet determine mode changes from the Abode side without performing a Refresh. If an area mode is changed by something different (e.g. Abode CUE Automations, Mobile App, IFTTT, etc) then you will need to hit *Refresh* on this panel before it is visible. You can setup periodic refresh on the interface if necessary, but I advice against doing this. If you want to use the Abode to control status of Hubitat modes, wait until the websocket implementation works in a later release.

* The Abode Event socket uses a proprietary Socket.IO implementation, so we can't utilize this until Hubitat provides a socket.io library.

* I can't find any data returned by the API to identify which areas are in use. My Iota only seems to use `area_1` but there are two areas returned by the API, both of which can be armed and disarmed. Right now two areas are hardcoded in the interface. If any of you using it have more than two areas, please send me some trace output from your panel.

* The tokens supplied by Abode expire in roughly a week. You can see the expiration date in the driver data at the bottom of the page. I haven't yet had the chance to run a token past its lifetime to determine if we can renewed it or not. It is possible we'll have to manually re-auth every week. This will work fine for username/password but may require human intervention for MFA auth. *I'll figure this out when I stop resetting my session lifetime for testing purposes* ;-)

* It is totally possible to identify devices on the Abode and make them visible to Hubitat. I haven't coded this yet because I personally didn't need it, and it's not going to work well until the event socket works.

# Development Roadmap

You should not install any version of this driver that starts with `0.` unless you are willing to
~~die in horrible pain~~ supply extensive debug logs, and add debugging snippets on request.

Which is to say--I'm going to ignore any *I want this feature to be provided for me* issues until at least version 1.0.

If you put forth the effort to provide useful debugging details or attempt a Pull Request for the feature you want,
I'll treat it with respect and dignity *whenever life allows me time to work on this*.

## Things you should read

* Standard [CHANGELOG](CHANGELOG.md) contains change history
* Licensed under the [Apache License, Version 2.0](LICENSE)

A failure to properly blame me when using my code will lead to me publicly mocking you in my books,
articles, and speaking engagements for decades. If your work benefits an entity, then my lawyer and theirs
will learn to Tango on your dime.

The [code is free to use according to the terms in LICENSE](LICENSE). Play nice.

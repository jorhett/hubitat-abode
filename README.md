# Abode Alarm driver for Hubitat

Allows retrieving status and changing the mode of an Abode Alarm system.

## Inspired by

* Hubitat example driver code from https://github.com/hubitat/HubitatPublic/tree/master/examples/drivers
* AbodePy by https://github.com/MisterWil/abodepy

## Warranty

I certify that this code is not suitable for any purpose.
If you are foolish enough to install it, **gremlins will eat everything you love**.
*Don't say I didn't tell you.*

I'm associated with neither Hubitat nor Abode in any way other than being a customer of theirs.
They've likely never seen this driver, and should warn you not to install it if they did.

# Installation

Follow Hubitat's instructions for adding new driver code:
  [https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers)

Import the driver from following URL:
  [https://raw.githubusercontent.com/jorhett/hubitat-abode/master/AbodeAlarm.groovy](https://github.com/jorhett/hubitat-abode/blob/master/AbodeAlarm.groovy)

Weep, because **you were warned and didn't listen**.

## Requirements

* In Abode System Settings, `Arm Fault Type` must be set to **Direct Arm**. The driver cannot yet respond to a prompt about faulty devices.

* Multi-Factor Authentication is supported. *You are using MFA for your home security, aren't you?* **Aren't you?**
  You'll have to supply the MFA code when saving preferences for a successful connection if MFA is enabled.

## Known limitations

* If an area mode is changed by something different (e.g. Mobile App, IFTTT, etc) then you will need to hit *Refresh*
  on this panel before it is visible. While I could setup automated refresh, I am instead focusing on getting the WebSocket
  working so we'll receive notification of the change.

* I can't find any data returned by the API to identify which areas are in use. My Iota only seems to use `area_1`
  but there are two areas returned by the API, both of which can be armed and disarmed.

* The tokens supplied by Abode expire in roughly a week. You can see the expiration date in the driver data at the bottom of the page. I haven't yet had the chance to run a token past its lifetime to determine if we can renewed it or not. It is possible we'll have to manually re-auth every week. This will work fine for username/password but may require human intervention for MFA auth. *I'll figure this out when I stop resetting my session lifetime for testing purposes* ;-)

# Development Roadmap

You should not install any version of this driver that starts with `0.` unless you are willing to
~~die in horrible pain~~ supply extensive debug logs, run random `curl` commands I send you,
and create your own Pull Requests with fixes.

I'm going to ignore any *I want this feature to be provided for me* issues until at least version 1.0.
**You're more likely to win the lottery than see a v1.0**. *You've been warned.*

If you put forth the effort to provide useful debugging details or attempt a Pull Request for the feature you want,
I'll treat it with respect and dignity *whenever life allows me time to work on this*.

## Things you should read

* Standard [CHANGELOG](CHANGELOG.md) contains change history
* Licensed under the [Apache License, Version 2.0](LICENSE)

A failure to properly blame me when using my code will lead to me publicly mocking you in my books,
articles, and speaking engagements for decades. If your work benefits an entity, then my lawyer and theirs
will learn to Tango on your dime.

The code is free to use [according to the terms in LICENSE](LICENSE). Play nice.

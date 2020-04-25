# Abode Alarm driver for Hubitat

Realtime management and tracking of an Abode Alarm system. This works for both controlling Abode from Hubitat,
and also responding realtime in Hubitat to changes initiated from Abode (mobile, CUE automations, etc).

- Set Abode gateway mode via Hubitat Rule Manager or device page
- Update Hubitat Mode based on Abode gateway mode changes
- Copy Abode timeline events to Hubitat device events
- Abode gateway mode changes and events can be used as Triggers in Hubitat Rule Manager
- Renew access tokens automatically upon expiration

## Inspired by

* Hubitat example driver code from https://github.com/hubitat/HubitatPublic/tree/master/examples/drivers
* AbodePy by https://github.com/MisterWil/abodepy

# Installation

Follow Hubitat's instructions for adding new driver code:
  [https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers)

Import the driver from following URL:
  [https://raw.githubusercontent.com/jorhett/hubitat-abode/v1/drivers/AbodeAlarm.groovy](https://github.com/jorhett/hubitat-abode/blob/v1/drivers/AbodeAlarm.groovy)

## Requirements

* In Abode System Settings, `Arm Fault Type` must be set to **Direct Arm**. The driver cannot yet respond to a prompt about faulty devices.

* Multi-Factor Authentication is supported. *You are using MFA for your home security, aren't you?* **Aren't you?**
  You'll have to supply the MFA code when saving preferences for a successful connection if MFA is enabled.

## Known limitations

* The Abode API has been reverse engineered, and has not been officially published by Abode. They can make a breaking change without warning.

* The login expiration returned by the API does not appear to be used. My sessions have last beyond this time. The code successfully acquires new access tokens when the previous one expires. This appears to indicate that periodic re-authentication may not be necessary. See previous caveot that Abode could change this behavior without warning.

* The Abode API does not support triggering the alarm remotely, and Abode has stated that they have no plans to add this feature. This means that Hubitat Safety Monitor can be triggered when the Abode alarm goes off, but not vice versa.

* I can't find any data returned by the API to identify which areas are in use. My Iota only seems to use `area_1` but there are two areas returned by the API, both of which can be armed and disarmed. Right now the interface only uses area 1, as the events from Abode only reflect changes to area 1. If you are using more than one area, please send me some trace output from your panel.

* It is possible to identify devices on the Abode and make them visible to Hubitat. I haven't coded this yet because I personally didn't need it. Ping me if you feel otherwise.

# Development Roadmap

* Standard [CHANGELOG](CHANGELOG.md) contains past change history
* New features can be requested by supplying trace logs of the interesting traffic

I'm open to adding new features, but lacking a lab full of Abode gear, I can't develop the features without seeing the interactions in detail. So features will only be added if you are willing to supply extensive trace logs from your setup so that I can see what your equipment says.

If you put forth the effort to provide useful debugging details or attempt a Pull Request for the feature you want, I'll treat it with respect and dignity *whenever life allows me time to work on this*.

# License and Warranty

* Licensed under the [Apache License, Version 2.0](LICENSE)
* Warranty spelled out in the license clearly says:
    [Licensor provides the Work on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND](LICENSE)
* Neither Hubitat nor Abode provide support for this integration.
  I'm associated with neither Hubitat nor Abode in any way other than being a customer of theirs.

A failure to properly blame me when using my code will lead to me publicly mocking you in my books,
articles, and speaking engagements for decades. If your work benefits an entity, then my lawyer and theirs
will learn to Tango on your dime.

The [code is free to use according to the terms in LICENSE](LICENSE). Play nice.

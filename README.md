# OWLCMS - Olympic Weightlifting Competition Management System 
This software is a complete rewrite of `owlcms` which has been used to manage Olympic Weightlifting competitions since 2009. This 4<sup>th</sup> generation of the software uses up-to-date technologies to run both on local machines (Windows, Linux, Mac) or in the cloud (Heroku, Kubernetes)

The application can be used for anything from a club meet using a single laptop all the way up to a national championship with several platform, full jury, integration with streaming, and public internet scoreboards.

### Features and Documentation

See the application [Web Site](https://owlcms.github.io/owlcms4/#) for a full overview.  The following short videos demonstrate using the software in minimalist mode for a [very simple club meet](https://owlcms.github.io/owlcms4/#/Demo1) and using the typical features for a [full competition](https://owlcms.github.io/owlcms4/#/Demo2) (on-site or virtual).

### Installation Options
Several installation options are possible, depending on what is available at the competition site

#### Easiest: Cloud-Based Installation

- If there is good internet communication at the competition site, the process is extremely simple. 

  - There is a one-click install procedure to a *free* (0$) cloud service called Heroku (a division of Salesforce.com). 
  - The install will create your *own private copy* of the application, with your *own database*.
  - The owlcms software runs as a web site. All the various display screens and devices connect to the cloud using the competition site's network.

![Slide9](docs/img/PublicResults/CloudExplained/Slide9.SVG)

* [Heroku Cloud Installation Instructions](https://owlcms.github.io/owlcms4/#/Heroku)
* Configuration Options for [Large Cloud Competitions](https://owlcms.github.io/owlcms4/#/HerokuLarge)

#### Stand-alone: Laptop installation

If there is no good Internet connectivity at your competition site you can use a stand-alone setup and run the software on a laptop.  In that setup: 

- The OWLCMS software runs on a laptop (labeled owlcms in the diagram) which acts as a web server to the other screens and displays.
- The primary laptop and all the other screens and official stations are connected to a wi-fi network.  If there is none in the building, you will need to configure a local router and connect all machines to that router (exactly like a home network).
- All machines need a web browser to drive their display or screen.
- You can run owlcms on the same machine as one of the officials.  It is often the case that owlcms runs on the same machine as the announcer or the competition secretary.
- In the following drawing phones are shown as the referee device.  But you can actually referee using hand signals, flags, phones, or dedicated keypads (USB or Bluetooth). See [this page](https://owlcms.github.io/owlcms4/#/Refereeing)

![Slide1](docs/img/PublicResults/CloudExplained/Slide7.SVG)

See the following instructions

  * [Windows Stand-alone Installation](https://owlcms.github.io/owlcms4/#/LocalWindowsSetup)
* [Linux or Mac Stand-alone Installation](https://owlcms.github.io/owlcms4/#/LocalLinuxMacSetup)

#### Cloud-Based Virtual Competitions

In a virtual competition, the officials are in multiple locations.  In order to allow access by all officials, `owlcms` is run in the cloud with [remote refereeing](Refereeing#Mobile-Device-Refereeing) and video conferencing.

![Slide3](docs/img/PublicResults/CloudExplained/Slide5.SVG)

The following link describes these options and others.

*	[Cloud-based Virtual Competition Options](https://owlcms.github.io/owlcms4/#/VirtualOverview)

### Repositories for Releases and Pre-releases

Refer to the following [page](Releases.md) for the various modules and releases, including preliminary releases for early adopters.

### Support

- [Discussion list](https://groups.google.com/forum/#!forum/owlcms)  If you wish to discuss the program or ask questions, please add yourself to this discussion [group](https://groups.google.com/forum/#!forum/owlcms).  You can withdraw at any time.
- [Project board](https://github.com/jflamy/owlcms4/projects/1) This shows what we are working on, and our work priorities.  Check here first, we may actually already be working on it...
- [Issues and enhancement requests](https://github.com/jflamy/owlcms4/issues) This is the complete log of requests and planned enhancements. Use this page to report problems or suggest enhancements.

### Licensing and Notes

This is free, as-is, no warranty *whatsoever* software. If you just want to run it as is for your own club or federation, just download from the [Releases](https://github.com/owlcms/owlcms4/releases) repository and go ahead. You should perform your own tests to see if the software is fit for your own purposes and circumstances.

If however you wish to provide the software as a service to others (including by hosting it), or if you create a modified version, the license *requires* you to make full sources and building instructions available for free &ndash; just like this software is (see the [License](https://github.com/owlcms/owlcms4/blob/master/LICENSE.txt) for details.)

### Translation to Other Languages

- You are welcome to translate the screens and reports to your own language, or fix a translation.  Refer to the [translation documentation](https://owlcms.github.io/owlcms4/#/Translation) if you wish to contribute.

### Credits

The software is written and maintained by Jean-François Lamy, IWF International Technical Official Category 1 (Canada)

Thanks to Anders Bendix Nielsen (Denmark) and Alexey Ruchev (Russia) for their support, feedback and help testing this version of the software.

See the file [pom.xml](pom.xml) for the list of Open Source software used in the project.  In particular, this project relies heavily on the [Vaadin](https://vaadin.com) application framework, and their long-standing support for open-source software.
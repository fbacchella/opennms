[OpenNMS][]
===========

Welcome to a fork of OpenNMS that adds a new RRD strategy : org.opennms.netmgt.rrd.rrd4j.Rrd4JRrdStrategy. It uses [Rrd4J][] as a rrd store, that is both faster and closer to rrdtool than jrobin.

The source code to this strategy is at `opennms-rrd/opennms-rrd-rrd4j` in the source tree.

To use it, edit the rrd-configuration.xml, in the properties list change the value of `org.opennms.rrd.strategyClass` to `org.opennms.netmgt.rrd.rrd4j.Rrd4JRrdStrategy` and 
`org.opennms.rrd.usetcp` to `false`.

The RRD version can also be set with `org.rrd4j.core.RrdBackendFactory`. 1 is the old version, compatible with jrobin. 2 is a new and faster one. The differences are explained at [Rrdv2][]

```...
<property name="properties">
    <props>
		    <!-- General configuration -->
				<prop key="org.opennms.rrd.strategyClass">org.opennms.netmgt.rrd.rrd4j.Rrd4JRrdStrategy</prop>
				<prop key="org.opennms.rrd.usequeue">true</prop>
				<prop key="org.opennms.rrd.usetcp">false</prop>
				<prop key="org.rrd4j.core.RrdBackendFactory">2</prop>
...
```
[OpenNMS][] is the world's first enterprise grade network management application platform developed under the open source model.

Well, what does that mean?

*	World's First

	The OpenNMS Project was started in July of 1999 and registered on SourceForge in March of 2000. It has years of experience on the alternatives.

*	Enterprise Grade

	It was designed from "day one" to monitor tens of thousands to ultimately unlimited devices with a single instance. It brings the power, scalability and flexibility that enterprises and carriers demand.

*	Application Platform

	While OpenNMS is useful "out of the box," it is designed to be highly customizable to create an unique and integrated management solution.

* Open Source

	OpenNMS is 100% Free and Open Source software, with no license fees, software subscriptions or special "enterprise" versions.

Building OpenNMS
================

For details on building OpenNMS, please see the wiki page: [Building_OpenNMS][]

Contributing to OpenNMS
=======================

Before making a pull request, please submit an [OCA][] for copyright assignment.  Note that this does **not** mean that you are giving up your copyright of your changes to OpenNMS, it instead allows for _dual_ copyright over contributed code.

Also, it is recommended that you make your changes in master and make a pull request there, not in one of the release (eg. 1.12 or 1.10) branches.  Fixes can be cherry-picked back to a stable branch, but we try to keep churn out of the stable releases if possible.

If you are using Eclipse, please read the [Eclipse][] page for details on setting up your workspace for code conventions and the plugins we use.

[OpenNMS]:          http://www.opennms.org/
[OCA]:              http://www.opennms.org/wiki/OCA
[Eclipse]:          http://www.opennms.org/wiki/Eclipse
[Building_OpenNMS]: http://www.opennms.org/wiki/Building_OpenNMS
[Rrd4J]:						https://code.google.com/p/rrd4j/
[Rrdv2]:						https://code.google.com/p/rrd4j/wiki/FilePerformance
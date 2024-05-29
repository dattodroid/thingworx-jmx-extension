> [!CAUTION]
> This extension was created for educational purposes and has undergone no testing. It is neither supported nor endorsed by PTC

## Features

1. Provides a user interface (similar to jconsole) for browsing through the platform's JVM JMX Beans within ThingWorx
2. Provides the capability to _bind_ JMX attributes to modeled properties on Things

## Installation

1. Import the thingworx-jmx-extension-x.y.z.zip extension into ThingWorx
2. (Optional) Import Demo/JMXDemo_Entities.xml to get sample MBeans Things (OS, c3p0, JVM) and a Mashup with metrics

## Usage

- Open the `JMX.MainMashup` to browse the JMX attributes and create MBean container Things

![Slide1](https://github.com/dattodroid/thingworx-jmx-extension/assets/159778604/522b35d8-30b7-4bc7-a7c6-078483e61def)

- Tips
  - Press [Enter] in the MBean Filter field to start searching
  - Use the [View] links on the Container Thing to directly open its property page in Composer

- The attributes are exposed as normal properties on the container Things
  - Property values are automatically pulled from the JVM when accessed (driven by `aspect.cacheTime`)
  - Use the `RefreshMBeanAttributes` service to read values in batch (other services such as `GetPropertyValues` are also working, but the refresh service is more efficient) - you can call this service at regular internal from a timer to log the property values.

![Slide2](https://github.com/dattodroid/thingworx-jmx-extension/assets/159778604/524b6b42-2a2c-4958-8cef-8854036fe618)

## (Optional) JMXDemo_Entities.xml

- Sample MBeans container Things:
  - `jmx.OS` - OperationSystem metrics: free memory and CPU usages...
  - `jmx.JVM` - JVM metrics: Heap Memory usage and number of Threads ...
  - `jmx.C3P0.PP1` - Persistence Provider Connection Pool metrics: active connections, treads ...
- Enable the `jmx.RefreshTimer` timer to start logging historical data
- Use the mashup `jmx.DemoMetrics` to display those metrics

![Slide3](https://github.com/dattodroid/thingworx-jmx-extension/assets/159778604/e65dbd5f-1436-4bcf-b16b-c30fe4b23105)


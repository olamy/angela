<img src="angela.png" height="100" alt="Angela Logo" />

A distributed control framework to handle a Terracotta cluster and test clients

## What is the purpose of Angela?

One major obstacle to testing a client/server system is setting up the environment. Not only the server must be installed, but also the clients and both of them might have to be installed on different remote locations. 

Angela is meant to tackle this problem and ease the setup of a distributed environment.

It also helps with the control of that distributed environment (e.g. starting/stopping some of the components, fetching some remote files, monitoring, injecting network failures). 

The current implementation is targeted for Terracotta, which is a distributed data management platform.

Angela also supports Ehcache 2 and 3, which are implementations of a distributed cache.

Angela can be extensible to handle other distributed softwares.

## Initial setup

For running tests on a node, Angela expects a directory at /data/angela to store all its metadata. So make sure that this directory exists or can be created before running any tests. For more details on what what that directory is used for, refer to Angela Directory Structure

## Tsa Cluster example

Given the following cluster configuration:

```
  <servers>
    <server host="localhost" name="Server1">
      <logs>logs1</logs>
      <tsa-port>9510</tsa-port>
      <tsa-group-port>9530</tsa-group-port>
    </server>
  </servers>
```

We expect the TSA to contain one Terracotta server running on localhost, and this will be automatically resolved by Angela. We can ask now Angela to setup such a cluster:

```
    ConfigurationContext configContext = customConfigurationContext() (1)
        .tsa(tsa -> tsa (2)
            .topology(new Topology( (3)
                distribution(version(EHCACHE_VERSION), PackageType.KIT, LicenseType.EHCACHE_OS), (4)
                tcConfig(version(EHCACHE_VERSION), getClass().getResource("/tc-config-a.xml")))) (5)
        );

    ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster", configContext); (6)
    Tsa tsa = factory.tsa() (7)
        .startAll() (8)

    factory.close(); (9)
```

  1) Create a custom configuration context that is going to hold all the configurable bits

  2) Define the TSA config

  3) Specify the Terracotta cluster topology

  4) Specify the Terracotta distribution : version, package type (KIT) and License

  5) Specify the Terracotta cluster config

  6) Create a Tsa logical instance that serves as an endpoint to call functionalities regarding the Tsa lifecycle

  7) Install the Tsa from the distribution on the appropriate server(s) (localhost in this case)

  8) Start all servers from the Tsa

  9) Stop all Terracotta servers and cleans up the installation

## Tsa API

```
      Tsa tsa = factory.tsa() (1)
          .startAll() (2)

      TerracottaServer active = tsa.getActive(); (3)
      Collection<TerracottaServer> actives = tsa.getActives(); (4)
      TerracottaServer passive = tsa.getPassive(); (5)
      Collection<TerracottaServer> passives = tsa.getPassives(); (6)

      tsa.stopAll(); (7)

      tsa.start(active); (8)
      tsa.start(passive);

      tsa.stop(active); (9)
      Callable<TerracottaServerState> serverState = () -> tsa.getState(passive); (10)
      Awaitility.await()
          .pollInterval(1, SECONDS)
          .atMost(15, SECONDS)
          .until(serverState, is(TerracottaServerState.STARTED_AS_ACTIVE));
```

  1) Install all Terracotta servers for the given topology

  2) Start all Terracotta servers

  3) Get the reference of the active server. Null is returned if there is none. An exception is throw if there are more than one

  4) Get the references of all active servers. Get an empty collection if there are none.

  5) Get the reference of the passive server. Null is returned if there is none. An exception is throw if there are more than one

  6) Get the references of all passive servers. Get an empty collection if there are none.

  7) Stop all Terracotta servers

  8) Start one Terracotta server

  9) Stop one Terracotta server

  10) Get the current state of the Terracotta server

## Client array example

```
    ConfigurationContext configContext = customConfigurationContext()
        .clientArray(clientArray -> clientArray (1)
            .clientArrayTopology(new ClientArrayTopology( (2)
                distribution(version(EHCACHE_VERSION), PackageType.KIT, LicenseType.EHCACHE_OS), (3)
                newClientArrayConfig().host("localhost-1", "localhost").host("localhost-2", "localhost")) (4)
            )
        );
    ClusterFactory factory = new ClusterFactory("GettingStarted::runClient", configContext);
    ClientArray clientArray = factory.clientArray(); (5)
    ClientArrayFuture f = clientArray.executeOnAll((context) -> System.out.println("Hello")); (6)
    f.get(); (7)

    factory.close();
```

  1) Define the client array config

  2) Define the client array topology

  3) Specify the distribution from which to install the client jars

  4) Specify the list of hosts that are going to be used by this client array (two clients, both on localhost in this case)

  5) Create a client array on the remote servers

  6) Execute the lambda on all the remote clients

  7) Wait until all the clients finish their execution

Full example : See class [EhcacheTest](integration-test/src/test/java/org/terracotta/angela/EhcacheTest.java)

## How to build

    mvn clean install

## Run specific tests

    mvn test -f integration-test/pom.xml -Dtest=<test-name>

Be careful not to cd directly into the module, you would not use the right kit version !

## Use specific location for Angela kits

    -Dangela.rootDir=/Users/adah/kits/

## Do not delete after run

    -Dangela.skipUninstall=true

## Specify JVM vendor

    -Djava.build.vendor=zulu

## Things to know

 * Angela is looking for JDK's in `$HOME/.m2/toolchains.xml`, the standard Maven toolchains file.
 See https://maven.apache.org/guides/mini/guide-using-toolchains.html to get its format and learn more about it.
 * Angela uses SSH to connect to remote hosts, so every non-localhost machine name is expected to be accessible via ssh,
 with everything already configured for passwordless authentication.
 * Angela spawns a small controlling app on every remote hosts that is very network-latency sensitive and uses lots of
 random ports. In a nutshell, this means that testing across WANs or firewalls just doesn't work. 
 * Angela expects a writeable `/data` folder (or at least a pre-created, writeable `/data/angela` folder) on every
 machine she runs on, i.e.: the one running the test as well as all the remote hosts.

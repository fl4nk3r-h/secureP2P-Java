## Quick Guide

Build the project first:

```bash
mvn clean package
```

### Terminal 1 (Listener)
Start the listener peer (Alice) on port 12346:

```bash
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Alice 12346 listen
```

### Terminal 2 (Connector)
Start the connector peer (Bob) and connect to Alice:

```bash
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Bob 12347 connect localhost 12346
```

You can now type messages in either terminal. Use `/help` to see available chat commands.

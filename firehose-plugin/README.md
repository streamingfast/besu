# Besu Firehose Plugin

## Purpose of the Firehose Plugin
A simplified Besu plugin that outputs initialization messages and block production events in Firehose protocol format to stdout.

### Services Used
- **BesuEvents**
  * To listen to propagated blocks and log block production events

### Plugin Lifecycle
- **Register**
  * Basic plugin registration and logging
- **Start**
  * Output initialization message and connect to Besu events
- **Stop**
  * Disconnect from Besu events

## To Execute the Plugin

Build the plugin jar
```
./gradlew build
```

Install the plugin into `$BESU_HOME`

```
mkdir $BESU_HOME/plugins
cp build/libs/*.jar $BESU_HOME/plugins
```

Run the Besu node with your desired configuration
```
$BESU_HOME/bin/besu [your-config-options-here]
```

The plugin will output Firehose protocol messages to stdout:
- `FIRE INIT Firehose plugin initialized` - Plugin initialization
- `FIRE BLOCK {blockNum} {blockHash} {prevNum} {prevHash} {libNum} {timestamp} {base64-data}` - Each block produced` 
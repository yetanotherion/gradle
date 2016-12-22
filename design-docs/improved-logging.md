# Improved Logging

As a user, I want to be able to control the amount of information I see from my Gradle build and where I get that information from.

## Use Cases

* As a Gradle User, I want to configure the gradle different log levels for core and my plugin code
* As a Gradle User, I want to redirect specific log messages (e.g. to a file)
* My Gradle Build should be integrated with the logging infrastructure I want to configure
* As a Gradle User, I want to set different logging configurations for different environments (e.g. on CI Server, on Dev box)  

## Story: Allow configuration of custom logback appender
Could read appender JARs from `$GRADLE_HOME/logging/libs`

## Story: Allow user to specify logging framework and provide configuration for that framework
Add `logging {}` DSL allowing users to specify a logging framework. 

### User-facing changes
```gradle
logging {
    simple {
        configFile = '/path/to/simplelogger.properties'
        properties = ['-Dorg.slf4j.simpleLogger.log.foo.GreetingTask=trace']
    }
    logback {
        configFile = '/path/to/logback.xml'
    }
    jcl {/* TODO */}
    jul {
        configFile = '/path/to/logging.properties'
        // TODO: wasn't there something else here?
    }
    custom {
        factoryClass = 'foo.bar.MyLoggerFactory'
        // TODO: more here
    }
    nop {}
}

logging.simple.properties = ["-Dorg.slf4j.simpleLogger.log.foo.GreetingTask=trace", ...]
```

### Implementation
Add convenience methods to adding JUL adapter, Log4J adapter, or Logback adapter etc. to classpath
 
LogEvents at `LogLevel.LIFECYCLE` and finer are appended to `gradle-build.log`, if enabled.
LogEvents at `LogLevel.WARN` and more severe are appended to Console (if attached) and `gradle-build.log`, if enabled.

In other words:
 - WARN, QUIET, ERROR => attached Console
 - LIFECYCLE => ProgressBar
 - WARN, QUIET, ERROR, LIFECYCLE, INFO, DEBUG => Gradle log file

*Default config*
```gradle
logging {
    custom {
        factoryClass = 'org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext'
        // TODO: log file should be gradle-build.log?
        // TODO: default log level should be org.gradle.api.logging.LogLevel.LIFECYCLE
    }
}
```

### Open issues
 - Does this adversely affect consumers of the Tooling API?
 - Should logging be configured through gradle properties instead or additionally?

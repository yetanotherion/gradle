# Improved CLI

## Primary Goals
* Improve perceived performance by making user aware of background work
* Modern feel
* User extensibility

### Users' focus on CLI
In priority order, the user wants to answer the following questions:

* When will my build finish?
    * Soon (should I keep watching it) or later (I'll check back)
* Is there anything I should know about my build right now? (e.g. tests have failed, warnings)
* What is Gradle doing right now?
* Is my build successful?
* What command did I execute?

## Story: Display parallel work in-progress independently by operation
Show incomplete ProgressOperations on separate lines, up to a specified maximum number. 

### Implementation
`Console` Label is replaced by a multi-line area that displays multiple 
in-progress operations. The Status TextArea has a fixed height to prevent 
the Appendable TextArea from growing and shrinking when build output exceeds 
the size of the `Terminal`.

A `StatusBarFormatter` can be used to customize the intra-line display of each 
`ProgressOperation` chain (each `ProgressOperation` has a reference to its parent)

Display of a maximum number of tasks in parallel is handled by an Array-backed 
Map of size N, (hashing the OperationId perhaps) with collision strategy that 
looks for incrementing index for next available space until giving up if no space
is left. The intent is to allow long-running Operations to keep the same place 
in the order while running.

Implementation is similar to `ConsoleBackedProgressRenderer` with throttling,
a data structure for storing recent updates, a `StatusBarFormatter`, and access
to the `Console`.

Compiler daemons and test workers would submit BuildOperations with information about "what" is running "where".
    
### Test Coverage
* A terminal size with fewer than max parallel operations shows (rows - 2) operations in parallel
* Operation status lines are trimmed at (cols - 1)
* `System.in` and SystemConsole I/O is unaffected by the Status TextArea

### Open Issues
* Default # of parallel operations to display
* Whether to fill empty space from few operations in-progress (e.g. ` > IDLE`)

## Story: Display build progress as a progress bar through colorized output
Render ProgressEvents with build completeness information as a progress bar. 

### User-visible Changes
We can visually represent complete, in-progress, and un-started tasks of build using colorized output:

`#####green#####>##yellow##>#####black#####> 40% Building`

We can use colored background with empty space to achieve a professional look.
A red background can be used for failed tasks.  

### Implementation
`DefaultGradleLauncherFactory` registers a `BuildProgressListener` listening
to events from `BuildProgressLogger` and forwards them to a 
`ConsoleBackedBuildProgressRenderer`. 

### Test Coverage
* Gracefully degrades on terminals with lack of color/cursor support
* 

### Open Issues
* Characters outside ASCII will give a more polished look, but with risk of poor display if console font doesn't support them.
* Un-styled alternative:
```
[                          ] 0% Building
[##########                ] 40% Building
[################          ] 60% Building
[##########################] 100% BUILD SUCCESS
```

## Story: Redirect lifecycle/info/debug logging to log file
Logs directed at `LogLevel.LIFECYCLE` and finer are appended to `gradle-build.log`, if enabled.
Logs directed at `LogLevel.WARN` and more severe are appended to Console (if attached) and `gradle-build.log`, if enabled.

### Implementation
 TODO

### Open Issues
* Consider an option to prefix all log messages with their log level.

## Story: (Optional) Illustrate build phases independently
Similar to "Display build progress as a progress bar through colorized output" above, but having clearly separated output for each build phase.
For example, it could be 3 separate progress bars with independent lines.
 
### Implementation
 TODO

### User-facing changes
**Option 1**

All 3 lines displayed at the same time
```
##################green###################> Initialization Complete
##################green###################> Configuration Complete
#####green#####>##yellow##>#####black#####> 40% Building
```

**Option 2**

_Only 1_ of the following is displayed, depending on the build phase
```
#####green#####>##yellow##>#####black#####> 40% Initializing                                    <#Initialization#
#####green#####>##yellow##>#####black#####> 40% Configuring                     <#Configuration#<#Initialization#
#####green#####>##yellow##>#####black#####> 40% Building            <#Execution#<#Configuration#<#Initialization#
```

## Story: Display intra-operation progress
When we know in advance the number of things to be processed, we display progress in [Complete / All] format. 
Provide APIs to optionally add # complete and # of all items.  

For example:
```
[23 / 65] Configuring tasks
[999 / 1234] Running tests
[384 / 218932] Compiling classes
[56 / 245] Resolving dependencies
```

### Implementation
 TODO

## Story: Detect terminals that support UTF-8 and provide richer UI elements
Support detection and use of UCS-2 (default supported by Windows) or UTF-8 characters could further polish this if we choose.

This would allow us to use characters like ᐅ supported by a majority of monospace fonts.
More granular width for progress bars: ' ', '▏', '▎', '▎', '▍', '▍', '▌', '▌', '▋', '▋', '▊', '▊', '▉', '▉', '█'

## Story: "Fade out" task result
Tasks completed continue to be displayed for 1 more "tick" of the clock with their resolution status, 
but are displayed in a muted color (ANSI bright black, perhaps).

For example:
```
:api:jar UP-TO-DATE
```

## Story: Use 256 color output when supported
ANSI colors are limited and dependent on color mapping of the underlying terminal. 
We can achieve much better look and feel using a 256-color palette when supported.

We may also need to capture the default background color of the terminal so we 
can adjust colors to at least light and dark backgrounds. This can be done with 
control sequences on at least *nix terminal.
 
### Implementation
Capture output of `tput colors` when available. If >=256, use a Gradle-chosen
color palette.

### Open issues
* Color choices we would use if we could

## Story: Publisher API to send progress events
Allow users to register an event publisher so plugins can send updates

## Story: Indicate Gradle's continued progress in absence of intra-operation updates
Provide motion in output to indicate Gradle's working if build operations are long-running.

Options:
* Incrementing time
* Blinking a small area of progress bar
* Blinking indicator on progress operations
* Adjusting color of progress operations (may require 256 colors)
* Text-based spinner (e.g. "┘└┌┐" or "|/-\\")

### Implementation
ProgressOperations data store knows the time since each operation was started. The 
`StatusBarFormatter` can choose to render an operation slightly differently depending
on its lifespan.

### Open issues
Best choice for displaying this by default

## Story: Allow Console rendering to be plug-able
Add a public hook for users to register a Console and/or OutputEventRenderer to customize CLI 

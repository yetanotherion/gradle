# Improved CLI

## Goals
* Perceived Performance
* Modern feel

### Users' Goals of CLI
* What command did I execute
* When will my build finish
* What is Gradle doing right now
* Is it successful

## Story: Display status through Progress Bar
Better visual rendering of existing ProgressEvents.

Status Line at top
Overall Progress (Phase and %) # failures (only displayed if > 0)

### User-visible Changes
Status Bar at bottom is replaced by ProgressBar at the top of output, displaying a visual representation of build completeness.
 
 TODO: screenshot

### Implementation

### Test Coverage

### Open Issues

## Story: Redirect lifecycle/info/debug logging to log file

## Story: Illustrate build phases independently
Visually represent build lifecycle and where running build is. For example, it could be 3 separate progress bars with independent lines (like Buck) or horizontally

Initialization, Configuration, Execution

## Story: Display parallel build work-in-progress independently by operation
Separate line for each build operation in started but not completed

## Story: Display appendable text area for warnings/errors/user input

## Story: Display intra-operation progress
[X / Y] complete (like Bazel)
[23/65] Configuring tasks
[999/1234] Running tests
[384/218932] Compiling classes
[56/245] Resolving dependencies

### Implementation
Compiler Daemons would generate BuildOperations with “what” and “where”.
BuildOperations for Compiler Daemons and Test Worker

## Story: Detect terminals that support UTF-8 and provide richer UI elements
Background/spaces for ASCII charset. ' ', '▏', '▎', '▎', '▍', '▍', '▌', '▌', '▋', '▋', '▊', '▊', '▉', '▉', '█', for UTF-8
16-color (currently have ANSI 16-color support) vs. 256 color

## Story: Publisher API to send progress events
Allow users to register an event publisher so plugins can send updates

## Story: Indicate Gradle's continued progress in absence of intra-operation updates
Provide motion in output to indicate Gradle's working if build operations are long-running.

Options:
* Incrementing time (like Buck)
* Blinking Progress Bar
* Text-based spinner

## Story: Allow Console rendering to be pluggable
Add a public hook for users to register a Console and/or OutputEventRenderer to customize CLI 

## Story: Add polish to enhance users' delight
“Fade out” recently completed tasks

## Story: Show project progresses independently

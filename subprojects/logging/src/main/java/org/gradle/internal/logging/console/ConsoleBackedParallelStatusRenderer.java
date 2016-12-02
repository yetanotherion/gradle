/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.time.TimeProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ConsoleBackedParallelStatusRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final Console console;
    private final ProgressOperations operations = new ProgressOperations();
    private final DefaultStatusBarFormatter operationFormatter;
    private final ScheduledExecutorService executorService;
    private final TimeProvider timeProvider;
    private final int throttleMs;
    private final Object lock = new Object();
    private final List<OutputEvent> updateQueue = new ArrayList<OutputEvent>();
    private final List<BuildOperationStatus> statuses = new ArrayList<BuildOperationStatus>(4);

    public ConsoleBackedParallelStatusRenderer(OutputEventListener listener, Console console, DefaultStatusBarFormatter operationFormatter, TimeProvider timeProvider) {
        this.listener = listener;
        this.console = console;
        this.operationFormatter = operationFormatter;
        this.timeProvider = timeProvider;
        this.throttleMs = Integer.getInteger("org.gradle.console.throttle", 85);
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onOutput(OutputEvent event) {

    }

    private class BuildStatus {
        String status;
        Integer percentComplete;

        BuildStatus(String status, Integer percentComplete) {
            this.status = status;
            this.percentComplete = percentComplete;
        }
    }

    private class BuildOperationStatus {
        OperationIdentifier id;
        String status;
        String shortDescription;
        BuildOperationStatus parent;

        BuildOperationStatus(OperationIdentifier id, String status, String shortDescription, BuildOperationStatus parent) {
            this.id = id;
            this.status = status;
            this.shortDescription = shortDescription;
            this.parent = parent;
        }

        public String toString() {
            String output = " > " + (shortDescription == null ? status : shortDescription);

            BuildOperationStatus current = parent;
            while (current != null) {
                output = current.toString() + output;
                current = current.parent;
            }

            return output;
        }
    }
}

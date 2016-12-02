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

import org.fusesource.jansi.Ansi;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput;

import java.io.Flushable;
import java.io.IOException;

public class ProgressBarParallelStatusAnsiConsole implements Console {
    private final Flushable flushable;
    private final ColorMap colorMap;
    private final ProgressBar progressBar;
    private final ParallelStatusTextArea textArea;
    private final Ansi ansi;

    public ProgressBarParallelStatusAnsiConsole(Flushable flushable, ColorMap colorMap) {
        this.flushable = flushable;
        this.colorMap = colorMap;
        this.progressBar = new ProgressBar();
        this.textArea = new ParallelStatusTextArea();
        this.ansi = new Ansi();
    }

    @Override
    public TextArea getMainArea() {
        return textArea;
    }

    @Override
    public Label getStatusBar() {
        return progressBar;
    }

    @Override
    public void flush() {
        // TODO: need to redraw probably both progressbar and textarea
        try {
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class ProgressBar implements Label {
        @Override
        public void setText(String text) {

        }
    }

    private class ParallelStatusTextArea extends AbstractLineChoppingStyledTextOutput implements TextArea {

        @Override
        protected void doLineText(CharSequence text) {

        }

        @Override
        protected void doEndLine(CharSequence endOfLine) {

        }
    }
}

/*
 * The MIT License
 *
 * Copyright 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli;

import hudson.model.ExecutorTest;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.tasks.Shell;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author pjanouse
 */

// Scenario - running build during restart, next build scheduled and sits in the queue during restart call
public class RestartCommandTestWithRestart3 extends RestartCommandTestWithRestartBase {

    @Override
    public void testingPartBeforeRestart() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(project.getBuilds(), hasSize(1));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it is not running", project.isBuilding(), equalTo(true));
        project.scheduleBuild2(0);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.ADMINISTER, Jenkins.READ)
                .invoke();
        assertThat(result, succeededSilently());
    }

    @Override
    public void testingPartAfterRestart() throws Exception {
        FreeStyleProject project = (FreeStyleProject) j.getInstance().getItem("aProject");
        assertThat(project.isBuilding(), equalTo(true));
        assertThat(project.getBuilds(), hasSize(2));
        assertThat(project.getBuildByNumber(1), notNullValue());
        assertThat(project.getBuildByNumber(2), notNullValue());
        OnlineNodeCommandTest.getFinishingEvent(project).signal();
        Thread.sleep(1000);
        assertThat(project.isBuilding(), equalTo(false));
        assertThat(project.getBuildByNumber(1).getBuildStatusSummary().message, equalTo("aborted"));
        assertThat(project.getBuildByNumber(2).getBuildStatusSummary().message, equalTo("stable"));
    }
}

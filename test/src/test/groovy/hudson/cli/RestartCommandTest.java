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

import hudson.FilePath;
import hudson.model.Hudson;
import hudson.model.Slave;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author pjanouse
 */
public class RestartCommandTest {

    private CLICommandInvoker command;

//    @Rule public final RestartableJenkinsRule j = new RestartableJenkinsRule();
    @ClassRule public final static PersistentJenkinsRule j = new PersistentJenkinsRule();
//    @Rule public final JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setupClass() {
        System.out.println("setupClass(): Starting...");
    }

    @Before
    public void setUp() {
        System.out.println("setUp(): Starting...");

        command = new CLICommandInvoker(j, "restart");
    }

    @After
    public void nextTest() throws Exception {
        System.out.println("nextTest(): Starting...");
//        writeStatus(readStatus() + 1);
    }

    @AfterClass
    public static void cleanupClass() throws Exception {
        System.out.println("cleanup(): Starting...");

        j.remove();
    }


    @Test
    public void restartTests() throws Exception {
        System.out.println("restartTests(): Starting...");
        switch(j.readStatus()) {
            case 0:
                System.out.println("restartTests(): Case 0 starting...");
                j.writeStatus(1);
                restartShouldFailWithoutAdministerPermission();
                restartShouldSuccess1();
                break;
            case 1:
                System.out.println("restartTests(): Case 1 starting...");
                j.writeStatus(2);
                restartShouldSuccess2();
                break;
            default:
                System.out.println("restartTests(): Unrecognized case found!");
                break;
        }
    }

    public void restartShouldFailWithoutAdministerPermission() throws Exception {
        System.out.println("restartShouldFailWithoutAdministerPermission(): Starting...");
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invoke();
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Overall/Administer permission"));
    }

    public void restartShouldSuccess1() throws Exception {
        System.out.println("restartShouldSuccess1(): Staring...");
//        final CLICommandInvoker.Result result = command
//                .authorizedTo(Jenkins.ADMINISTER, Jenkins.READ)
//                .invoke();
//        assertThat(result, succeededSilently());
        j.createSlave("TestSlave", null, null);
        j.jenkins.getActiveInstance().restart();
        // reload-configuration is performed in a separate thread
        // we have to wait until it finishes
        while (!(j.jenkins.servletContext.getAttribute("app") instanceof Jenkins)) {
            System.out.println("Jenkins reload operation is performing, sleeping 1s...");
            Thread.sleep(1000);
        }
    }

    public void restartShouldSuccess2() throws Exception {
        System.out.println("restartShouldSuccess2(): Staring...");
        assertThat(j.jenkins.getNode("TestSlave"), notNullValue());
    }

    public static class PersistentJenkinsRule extends JenkinsRule {

        final static String JENKINS_HOME_NAME = "persistent-jenkins-temp";
        final static String TEMP_FILE = "restart-test.tmp";
        final static int RETENTION_TIME = 10*60*1000; // 10m

        @Override
        protected Hudson newHudson() throws Exception {
            System.out.println("newHudson(): Starting...");

            File home;
            ServletContext webServer = createWebServer();
            try {
                home = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
                System.out.println("newHudson(): Looking for a Jenkins instance at '"+home+'\'');

                //home.deleteOnExit();
                int status = readStatus();
                System.out.println("newHudson(): Status: "+status);
                if (status == 0 || !home.exists()) {
                    System.out.println("newHudson(): Old Jenkins instance NOT found or too old!");
                    if(home.exists()) {
                        new FilePath(home).deleteRecursive();
                        home = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
                    }
                    for (JenkinsRecipe.Runner r : recipes)
                        r.decorateHome(this, home);
                }
                else {
                    System.out.println("newHudson(): Old Jenkins instance found! Using...");
                }
            } catch (Exception e) {
                throw new AssumptionViolatedException("Jenkins allocation failed", e);
            }
            try {
                return new Hudson(home, webServer, getPluginManager());
            } catch (InterruptedException e) {
                throw new AssumptionViolatedException("Jenkins startup interrupted", e);
            }
        }

        public int readStatus() throws Exception {
            int status = 0;
            try {
                File f = new File(System.getProperty("java.io.tmpdir"), TEMP_FILE);
                if (f.exists()) {
                    System.out.println("readStatus(): Status file found!");

                    BufferedReader br = new BufferedReader(new FileReader(f));

                    String sCurrentLine;
                    System.out.println("=== begin ===");
                    while ((sCurrentLine = br.readLine()) != null) {
                        System.out.println(sCurrentLine);
                    }
                    System.out.println("=== end ===");
                    br.close();
                    br = new BufferedReader(new FileReader(f));
                    sCurrentLine = br.readLine();
                    br.close();
                    System.out.println("Line: '"+sCurrentLine+"'");
                    String[] tokens = sCurrentLine.split("\\t");
                    if (tokens.length == 3) {
                        if (tokens[0].equals("Test:")) {
                            if (System.currentTimeMillis() < (Long.valueOf(tokens[1]) + RETENTION_TIME)) {
                                System.out.println("readStatus(): tokens[2]: '"+tokens[2]+"'");
                                status = Integer.valueOf(tokens[2]);
                                System.out.println("readStatus(): status: '"+status+"'");
                            } else {
                                System.out.println("readStatus(): Too old status!");
                                // need restart
                            }
                        } else {
                            System.out.println("readStatus(): Unrecognized format found!");
                            // write start
                        }
                    } else {
                        System.out.println("readStatus(): Unrecognized format found!");
                        // write start
                    }
                }
            } catch (FileNotFoundException e) {
                // Just ignore
            } catch (IOException e) {
                // Just ignore
            }
            System.out.println("readStatus(): status: '"+status+"'");

            if (status == 0) {
                writeStatus(0);
            }
            System.out.println("readStatus(): status: '"+status+"'");

            return status;
        }

        public void writeStatus(final int status) throws Exception {
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(new File(System.getProperty("java.io.tmpdir"), TEMP_FILE)));
                bw.write("Test:\t"+System.currentTimeMillis()+"\t"+status   +"\n");
                bw.close();
            } catch (FileNotFoundException e) {
                // Just ignore
            } catch (IOException e){
                // Just ignore
            }
        }

        public void remove() throws Exception {
            System.out.println("remove(): Starting...");

            File f = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
            if (f.exists())
                new FilePath(f).deleteRecursive();

            f = new File(System.getProperty("java.io.tmpdir"), TEMP_FILE);
            if (f.exists())
                f.delete();
        }
    }
}

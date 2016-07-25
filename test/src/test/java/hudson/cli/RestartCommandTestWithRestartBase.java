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
import jenkins.model.Jenkins;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * @author pjanouse
 */
public abstract class RestartCommandTestWithRestartBase {

    abstract public void testingPartBeforeRestart() throws Exception;
    abstract public void testingPartAfterRestart() throws Exception;

    protected CLICommandInvoker command;

    @ClassRule public final static PersistentJenkinsRule j = new PersistentJenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "restart");
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        j.remove();
    }

    @Test
    public void testingRobot () throws Exception {
        switch(j.readStatus()) {
            case 0:
                j.writeStatus(1);
                testingPartBeforeRestart();
                j.WaitForJenkinsRestart();
                break;
            case 1:
                j.writeStatus(2);
                testingPartAfterRestart();
                break;
            default:
                fail("testingRobot(): Unrecognized case found!");
                break;
        }
    }

    public static class PersistentJenkinsRule extends JenkinsRule {
        final static String JENKINS_HOME_NAME = "persistent-jenkins-temp";
        final static String STATUS_FILE = "restart-status.tmp";
        final static String GUARD_FILE = "restart-guard.tmp";
        final static int MAX_RETENTION_TIME = 10*60*1000; // 10m
        final static int MAX_RESTARTS = 3;

        static int maxRestarts = MAX_RESTARTS;

        @Override
        protected Hudson newHudson() throws Exception {
            File home;
            ServletContext webServer = createWebServer();
            try {
                home = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
                System.out.println("newHudson(): Looking for a Jenkins instance at '"+home+'\'');

                //home.deleteOnExit();
                int status = readStatus();
                if (status == 0 || !home.exists()) {
                    System.out.println("newHudson(): Old Jenkins instance NOT found or too old, creating new one...");
                    if(home.exists()) {
                        new FilePath(home).deleteRecursive();
                        home = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
                    }
                    for (JenkinsRecipe.Runner r : recipes)
                        r.decorateHome(this, home);
                }
                else {
                    System.out.println("newHudson(): Old Jenkins instance found, using...");
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
            return readStatus(new File(System.getProperty("java.io.tmpdir"), STATUS_FILE));
        }

        int readStatus(final File file) {
            int status = 0;
            try {
                if (file.exists()) {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String sCurrentLine = br.readLine();
                    br.close();
                    String[] tokens = sCurrentLine.split("\\t");
                    if (tokens.length == 3) {
                        if (tokens[0].equals("Test:")) {
                            if (System.currentTimeMillis() < (Long.valueOf(tokens[1]) + MAX_RETENTION_TIME)
                                    || file.getName().compareTo(STATUS_FILE) != 0) {
                                status = Integer.valueOf(tokens[2]);
                            } else {
                                System.out.println("readStatus(): Status too old!");
                            }
                        } else {
                            fail("readStatus(): Unrecognized format found!");
                        }
                    } else {
                        fail("readStatus(): Unrecognized format found!");
                    }
                }
            } catch (FileNotFoundException e) {
                // Just ignore
            } catch (IOException e) {
                throw new AssumptionViolatedException("Status read failed", e);
            }

            if (status == 0 && file.getName().compareTo(STATUS_FILE) == 0) {
                writeStatus(0);
            }

            System.out.println(String.format("readStatus(): %s -> %d", file.getName(), status));
            return status;

        }

        void writeStatus(final File file, final int status) {
            System.out.println(String.format("writeStatus(): %s <- %d", file.getName(), status));
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write("Test:\t"+System.currentTimeMillis()+"\t"+status   +"\n");
                bw.close();
            } catch (FileNotFoundException e) {
                // Just ignore
            } catch (IOException e){
                throw new AssumptionViolatedException("Status write failed", e);
            }
        }

        public void writeStatus(final int status) {
            writeStatus(new File(System.getProperty("java.io.tmpdir"), STATUS_FILE), status);
            if (status == 0) {
                int guard_status = readStatus(new File(System.getProperty("java.io.tmpdir"), GUARD_FILE));
                if(guard_status >= getMaxRestarts()) {
                    fail("Set number of maximum restarts reached, possible never-ending loop detected!"
                            +"\nIf you need more restarts detection mechanism needs to be adjusted via calling method setMaxRestart(...).");
                }
                writeStatus(new File(System.getProperty("java.io.tmpdir"), GUARD_FILE), guard_status+1);
            }
        }

        public void remove() throws Exception {
            try {
                File f = new File(System.getProperty("java.io.tmpdir"), JENKINS_HOME_NAME);
                if (f.exists())
                    new FilePath(f).deleteRecursive();
            } catch (Exception e) {
                // Just ignore all issues
            }

            try {
                File f = new File(System.getProperty("java.io.tmpdir"), STATUS_FILE);
                if (f.exists())
                    f.delete();
            } catch (Exception e) {
                // Just ignore all issues
            }

            try {
                File f = new File(System.getProperty("java.io.tmpdir"), GUARD_FILE);
                if (f.exists())
                    f.delete();
            } catch (Exception e) {
                // Just ignore all issues
            }
        }

        public void WaitForJenkinsRestart() throws Exception {
            // reload-configuration is performed in a separate thread
            // we have to wait until it finishes
            while (!(jenkins.servletContext.getAttribute("app") instanceof Jenkins)) {
                System.out.println("Jenkins reload operation is performing, sleeping 1s...");
                Thread.sleep(1000);
            }
        }

        public static int getMaxRestarts() {
            return maxRestarts;
        }

        public static void setMaxRestarts(final int max) {
            maxRestarts = max;
        }
    }
}

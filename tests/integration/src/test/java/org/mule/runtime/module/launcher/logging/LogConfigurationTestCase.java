/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.launcher.logging;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_SIMPLE_LOG;

import org.mule.runtime.deployment.model.api.application.Application;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.DomainFileBuilder;
import org.mule.runtime.module.launcher.logging.rule.UseMuleLog4jContextFactory;
import org.mule.test.infrastructure.deployment.AbstractFakeMuleServerTestCase;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks log4j configuration for application and domains
 */
public class LogConfigurationTestCase extends AbstractFakeMuleServerTestCase {

  public static final String APP_NAME = "app1";
  public static final String DOMAIN_NAME = "domain";

  @Rule
  public UseMuleLog4jContextFactory muleLogging = new UseMuleLog4jContextFactory();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // here we're trying to test log separation so we need to
    // disable this default property of the fake mule server
    // in order to test that
    System.clearProperty(MULE_SIMPLE_LOG);
  }

  @Test
  public void defaultAppLoggingConfigurationOnlyLogsOnApplicationLogFile() throws Exception {
    muleServer.start();
    ApplicationFileBuilder applicationFileBuilder = new ApplicationFileBuilder(APP_NAME).definedBy("log/empty-config.xml");
    muleServer.deploy(applicationFileBuilder.getArtifactFile().toURI().toURL(), APP_NAME);
    ensureOnlyDefaultAppender();
  }

  @Test
  public void defaultAppInDomainLoggingConfigurationOnlyLogsOnApplicationLogFile() throws Exception {
    muleServer.start();
    File domainFile =
        new DomainFileBuilder(DOMAIN_NAME).definedBy("log/empty-domain/empty-domain-config.xml").getArtifactFile();
    muleServer.deployDomainFile(domainFile);

    ApplicationFileBuilder applicationFileBuilder =
        new ApplicationFileBuilder(APP_NAME).definedBy("log/empty-config.xml").deployedWith("domain", "domain");
    muleServer.deploy(applicationFileBuilder.getArtifactFile().toURI().toURL(), APP_NAME);
    ensureOnlyDefaultAppender();
  }

  @Test
  public void honorLog4jConfigFileForApp() throws Exception {
    muleServer.start();
    ApplicationFileBuilder applicationFileBuilder = new ApplicationFileBuilder(APP_NAME).definedBy("log/empty-config.xml")
        .usingResource("log/log4j-config.xml", "log4j2-test.xml");
    muleServer.deploy(applicationFileBuilder.getArtifactFile().toURI().toURL(), APP_NAME);
    ensureArtifactAppender("consoleForApp", ConsoleAppender.class);
  }

  @Test
  public void honorLog4jConfigFileForAppInDomain() throws Exception {
    muleServer.start();

    File domainFile = new DomainFileBuilder(DOMAIN_NAME)
        .definedBy("log/empty-domain-with-log4j/empty-domain-config.xml")
        .containingResource("log/empty-domain-with-log4j/log4j2-test.xml", "classes/log4j2-test.xml")
        .getArtifactFile();
    muleServer.deployDomainFile(domainFile);

    ApplicationFileBuilder applicationFileBuilder = new ApplicationFileBuilder(APP_NAME)
        .definedBy("log/empty-config.xml").deployedWith("domain", "domain");
    muleServer.deploy(applicationFileBuilder.getArtifactFile().toURI().toURL(), APP_NAME);
    ensureArtifactAppender("ConsoleForDomain", ConsoleAppender.class);
  }

  private void ensureOnlyDefaultAppender() throws Exception {
    assertThat(1, equalTo(appendersCount(APP_NAME)));
    assertThat(1, equalTo(selectByClass(APP_NAME, RollingFileAppender.class).size()));

    RollingFileAppender fileAppender = (RollingFileAppender) selectByClass(APP_NAME, RollingFileAppender.class).get(0);
    assertThat("defaultFileAppender", equalTo(fileAppender.getName()));
    assertThat(fileAppender.getFileName(), containsString(String.format("mule-app-%s.log", APP_NAME)));
  }

  private void ensureArtifactAppender(final String appenderName, final Class<? extends Appender> appenderClass) throws Exception {
    withAppClassLoader(APP_NAME, () -> {
      Logger logger = getRootLoggerForApp(APP_NAME);
      assertThat(Level.WARN, equalTo(logger.getLevel()));
      assertThat(true, equalTo(loggerHasAppender(APP_NAME, logger, appenderName)));


      assertThat(1, equalTo(appendersCount(APP_NAME)));
      assertThat(1, equalTo(selectByClass(APP_NAME, appenderClass).size()));
      assertThat(appenderName, equalTo(selectByClass(APP_NAME, appenderClass).get(0).getName()));

      return null;
    });
  }

  private boolean loggerHasAppender(String appName, Logger logger, String appenderName) throws Exception {
    return getContext(appName).getConfiguration().getLoggerConfig(logger.getName()).getAppenders().containsKey(appenderName);
  }

  private Logger getRootLoggerForApp(String appName) throws Exception {
    return getContext(appName).getLogger("");
  }

  private LoggerContext getContext(final String appName) throws Exception {
    return withAppClassLoader(appName, () -> {
      Application app = muleServer.findApplication(appName);
      ClassLoader classLoader = app.getMuleContext().getExecutionClassLoader();
      return (LoggerContext) LogManager.getContext(classLoader, false);
    });
  }

  private <T> T withAppClassLoader(String appName, Callable<T> closure) throws Exception {
    Application app = muleServer.findApplication(appName);
    ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader classLoader = app.getMuleContext().getExecutionClassLoader();
    Thread.currentThread().setContextClassLoader(classLoader);
    try {
      return closure.call();
    } finally {
      Thread.currentThread().setContextClassLoader(currentClassLoader);
    }
  }

  private List<Appender> selectByClass(String appName, Class<?> appenderClass) throws Exception {
    LoggerContext context = getContext(appName);
    List<Appender> filteredAppenders = new LinkedList<>();
    for (Appender appender : context.getConfiguration().getAppenders().values()) {
      if (appenderClass.isAssignableFrom(appender.getClass())) {
        filteredAppenders.add(appender);
      }
    }

    return filteredAppenders;
  }

  private int appendersCount(String appName) throws Exception {
    return selectByClass(appName, Appender.class).size();
  }
}

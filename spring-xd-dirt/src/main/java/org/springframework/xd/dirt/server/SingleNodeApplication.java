/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.xd.dirt.server;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.Assert;
import org.springframework.xd.batch.hsqldb.server.HsqlServerApplication;
import org.springframework.xd.dirt.server.options.SingleNodeOptions;
import org.springframework.xd.dirt.util.BannerUtils;
import org.springframework.xd.dirt.util.XdProfiles;

/**
 * Single Node XD Runtime.
 * 
 * @author Dave Syer
 * @author David Turanski
 * @author Ilayaperumal Gopinathan
 */
public class SingleNodeApplication {

	private ConfigurableApplicationContext adminContext;

	private ConfigurableApplicationContext pluginContext;

	private ConfigurableApplicationContext containerContext;

	public static void main(String[] args) {
		new SingleNodeApplication().run(args);
	}

	public SingleNodeApplication run(String... args) {

		System.out.println(BannerUtils.displayBanner(getClass().getSimpleName(), null));

		ContainerBootstrapContext bootstrapContext = new ContainerBootstrapContext(new SingleNodeOptions());

		SpringApplicationBuilder admin =
				new SpringApplicationBuilder(SingleNodeOptions.class, ParentConfiguration.class)
						.listeners(bootstrapContext.commandLineListener())
						.profiles(XdProfiles.ADMIN_PROFILE, XdProfiles.SINGLENODE_PROFILE)
						.initializers(new HsqldbServerProfileActivator())
						.child(SharedServerContextConfiguration.class, SingleNodeOptions.class)
						.listeners(bootstrapContext.commandLineListener())
						.child(SingleNodeOptions.class, AdminServerApplication.class)
						.listeners(bootstrapContext.commandLineListener());
		admin.run(args);

		SpringApplicationBuilder container = admin
				.sibling(SingleNodeOptions.class, ContainerServerApplication.class)
				.profiles(XdProfiles.CONTAINER_PROFILE, XdProfiles.SINGLENODE_PROFILE)
				.listeners(ApplicationUtils.mergeApplicationListeners(bootstrapContext.commandLineListener(),
						bootstrapContext.orderedContextInitializers()))
				.child(ContainerConfiguration.class)
				.listeners(bootstrapContext.commandLineListener())
				.web(false);
		container.run(args);

		adminContext = admin.context();

		containerContext = container.context();
		pluginContext = (ConfigurableApplicationContext) containerContext.getParent();

		return this;
	}

	public void close() {
		if (containerContext != null) {
			containerContext.close();
		}
		if (pluginContext != null) {
			pluginContext.close();
		}
		if (adminContext != null) {
			adminContext.close();
			ApplicationContext parent = adminContext.getParent();
			if (parent instanceof ConfigurableApplicationContext) {
				((ConfigurableApplicationContext) parent).close();
			}
		}
	}

	public ConfigurableApplicationContext adminContext() {
		return adminContext;
	}

	public ConfigurableApplicationContext pluginContext() {
		return pluginContext;
	}

	public ConfigurableApplicationContext containerContext() {
		return containerContext;
	}

	/**
	 * Initializer class that activates {@link HsqlServerApplication.HSQLDBSERVER_PROFILE} if the underlying datasource
	 * is hsql and embedded hsqldb is opted.
	 */
	class HsqldbServerProfileActivator implements
			ApplicationContextInitializer<ConfigurableApplicationContext> {

		private static final String SPRING_DATASOURCE_URL_OPTION = "${spring.datasource.url}";

		private static final String SINGLENODE_EMBEDDED_HSQL = "${singlenode.embeddedHsql}";

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			String dataSourceUrl = applicationContext.getEnvironment().resolvePlaceholders(SPRING_DATASOURCE_URL_OPTION);
			String embeddedHsql = applicationContext.getEnvironment().resolvePlaceholders(SINGLENODE_EMBEDDED_HSQL);
			Assert.notNull(dataSourceUrl, "At least one datasource (for batch) must be set.");
			Assert.notNull(embeddedHsql,
					"The property singlenode.embeddedHsql must be set for singlenode. Default is true.");
			if (dataSourceUrl.contains("hsql") && embeddedHsql.equals("true")) {
				applicationContext.getEnvironment().addActiveProfile(HsqlServerApplication.HSQLDBSERVER_PROFILE);
			}
		}
	}

}

/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.dirt.server.options;

import org.kohsuke.args4j.Option;

/**
 * Base class for command line options that are common to absolutely every setup (single and distributed).
 * 
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
public class CommonOptions {

	@Option(name = "--help", usage = "Show this help screen", aliases = { "-?", "-h" })
	private boolean showHelp = false;

	@Option(name = "--mgmtPort", usage = "The port for the management server", metaVar = "<mgmtPort>")
	private Integer mgmtPort;

	// Using wrapped here so that "showHelp" is not returned as a property by BeanPropertiesPropertySource
	public Boolean isShowHelp() {
		return showHelp ? true : null;
	}

	public Integer getXD_MGMT_PORT() {
		return mgmtPort;
	}

	public void setXD_MGMT_PORT(int mgmtPort) {
		this.mgmtPort = mgmtPort;
	}

}

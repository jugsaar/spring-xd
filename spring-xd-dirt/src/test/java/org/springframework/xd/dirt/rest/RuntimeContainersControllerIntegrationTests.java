/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.xd.dirt.rest;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.xd.dirt.container.ContainerAttributes;

/**
 * Tests REST compliance of containers endpoint.
 *
 * @author Ilayaperumal Gopinathan
 * @author Mark Fisher
 * @author David Turanski
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = { RestConfiguration.class, Dependencies.class })
public class RuntimeContainersControllerIntegrationTests extends AbstractControllerIntegrationTest {

	@Before
	public void before() {
		PageRequest pageable = new PageRequest(0, 20);
		ContainerAttributes container1 = new ContainerAttributes("1").setPid(1234).setHost("host1").setIp("127.0.0.1");
		ContainerAttributes container2 = new ContainerAttributes("2").setPid(2345).setHost("host2").setIp("192.168.2.1");
		List<ContainerAttributes> containerEntities = new ArrayList<ContainerAttributes>();
		containerEntities.add(container1);
		containerEntities.add(container2);
		Page<ContainerAttributes> pagedEntity = new PageImpl<ContainerAttributes>(containerEntities);
		when(containerAttributesRepository.findAll(pageable)).thenReturn(pagedEntity);
	}

	@Test
	public void testListContainers() throws Exception {
		mockMvc.perform(get("/runtime/containers").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andExpect(
				jsonPath("$.content", Matchers.hasSize(2))).andExpect(
				jsonPath("$.content[*].attributes.id", contains("1", "2"))).andExpect(
				jsonPath("$.content[*].attributes.pid", contains("1234", "2345"))).andExpect(
				jsonPath("$.content[*].attributes.host", contains("host1", "host2"))).andExpect(
				jsonPath("$.content[*].attributes.ip", contains("127.0.0.1", "192.168.2.1")));
	}
}

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

package org.springframework.integration.x.bus;

import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.Lifecycle;
import org.springframework.http.MediaType;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.x.bus.serializer.MultiTypeCodec;
import org.springframework.integration.x.bus.serializer.SerializationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.util.MimeType;


/**
 * @author David Turanski
 * @author Gary Russell
 */
public abstract class MessageBusSupport implements MessageBus, BeanFactoryAware {

	protected final Log logger = LogFactory.getLog(getClass());

	private volatile MultiTypeCodec<Object> codec;

	private final ContentTypeResolver contentTypeResolver = new StringConvertingContentTypeResolver();

	protected static final String ORIGINAL_CONTENT_TYPE_HEADER = "originalContentType";

	protected static final List<MediaType> MEDIATYPES_MEDIATYPE_ALL = Collections.singletonList(MediaType.ALL);

	private final List<Binding> bindings = Collections.synchronizedList(new ArrayList<Binding>());

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	private volatile BeanFactory beanFactory;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected BeanFactory getBeanFactory() {
		return beanFactory;
	}

	public void setCodec(MultiTypeCodec<Object> codec) {
		this.codec = codec;
	}

	protected IdGenerator getIdGenerator() {
		return idGenerator;
	}

	@Override
	public void unbindConsumers(String name) {
		deleteBindings("inbound." + name);
	}

	@Override
	public void unbindProducers(String name) {
		deleteBindings("outbound." + name);
	}

	@Override
	public void unbindConsumer(String name, MessageChannel channel) {
		deleteBinding("inbound." + name, channel);
	}

	@Override
	public void unbindProducer(String name, MessageChannel channel) {
		deleteBinding("outbound." + name, channel);
	}

	protected void addBinding(Binding binding) {
		this.bindings.add(binding);
	}

	protected void deleteBindings(String name) {
		Assert.hasText(name, "a valid name is required to remove bindings");
		synchronized (this.bindings) {
			Iterator<Binding> iterator = this.bindings.iterator();
			while (iterator.hasNext()) {
				Binding binding = iterator.next();
				if (binding.getEndpoint().getComponentName().equals(name)) {
					binding.stop();
					iterator.remove();
				}
			}
		}
	}

	protected void deleteBinding(String name, MessageChannel channel) {
		Assert.hasText(name, "a valid name is required to remove a binding");
		Assert.notNull(channel, "a valid channel is required to remove a binding");
		synchronized (this.bindings) {
			Iterator<Binding> iterator = this.bindings.iterator();
			while (iterator.hasNext()) {
				Binding binding = iterator.next();
				if (binding.getChannel().equals(channel) &&
						binding.getEndpoint().getComponentName().equals(name)) {
					binding.stop();
					iterator.remove();
					return;
				}
			}
		}
	}

	protected void stopBindings() {
		for (Lifecycle bean : this.bindings) {
			try {
				bean.stop();
			}
			catch (Exception e) {
				if (logger.isWarnEnabled()) {
					logger.warn("failed to stop adapter", e);
				}
			}
		}
	}

	// TODO: Performs serialization currently no transformation
	protected final Message<?> serializePayloadIfNecessary(Message<?> message, MediaType to) {
		Object originalPayload = message.getPayload();
		Object originalContentType = message.getHeaders().get(MessageHeaders.CONTENT_TYPE);
		Object contentType = originalContentType;
		if (to.equals(ALL)) {
			return message;
		}
		else if (to.equals(APPLICATION_OCTET_STREAM)) {
			contentType = resolveContentType(originalPayload);
			Object payload = serializePayloadIfNecessary(originalPayload);
			MessageBuilder<Object> messageBuilder = MessageBuilder.withPayload(payload)
					.copyHeaders(message.getHeaders())
					.setHeader(MessageHeaders.CONTENT_TYPE, contentType);
			if (originalContentType != null) {
				messageBuilder.setHeader(ORIGINAL_CONTENT_TYPE_HEADER, originalContentType);
			}
			return messageBuilder.build();
		}
		else {
			throw new IllegalArgumentException("'to' can only be 'ALL' or 'APPLICATION_OCTET_STREAM'");
		}
	}

	private byte[] serializePayloadIfNecessary(Object originalPayload) {
		if (originalPayload instanceof byte[]) {
			return (byte[]) originalPayload;
		}
		else {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				if (originalPayload instanceof String) {
					return ((String) originalPayload).getBytes("UTF-8");
				}
				this.codec.serialize(originalPayload, bos);
				return bos.toByteArray();
			}
			catch (IOException e) {
				throw new SerializationException("unable to serialize payload ["
						+ originalPayload.getClass().getName() + "]", e);
			}
		}
	}

	protected final Message<?> deserializePayloadIfNecessary(Message<?> message) {
		Message<?> messageToSend = message;
		Object originalPayload = message.getPayload();
		MimeType contentType = contentTypeResolver.resolve(message.getHeaders());
		Object payload = deserializePayload(originalPayload, contentType);
		if (payload != null) {
			MessageBuilder<Object> transformed = MessageBuilder.withPayload(payload).copyHeaders(message.getHeaders());
			Object originalContentType = message.getHeaders().get(ORIGINAL_CONTENT_TYPE_HEADER);
			transformed.setHeader(MessageHeaders.CONTENT_TYPE, originalContentType);
			transformed.setHeader(ORIGINAL_CONTENT_TYPE_HEADER, null);
			messageToSend = transformed.build();
		}
		return messageToSend;
	}

	private Object deserializePayload(Object payload, MimeType contentType) {
		if (payload instanceof byte[]) {
			if (APPLICATION_OCTET_STREAM.equals(contentType)) {
				return payload;
			}
			else {
				return deserializePayload((byte[]) payload, contentType);
			}
		}
		return payload;
	}

	private Object deserializePayload(byte[] bytes, MimeType contentType) {
		Class<?> targetType = null;
		try {
			if (contentType.equals(TEXT_PLAIN)) {
				return new String(bytes, "UTF-8");
			}
			targetType = Class.forName(contentType.getParameter("type"));


			return codec.deserialize(bytes, targetType);
		}
		catch (ClassNotFoundException e) {
			throw new SerializationException("unable to deserialize [" + targetType + "]. Class not found.", e);
		}
		catch (IOException e) {
			throw new SerializationException("unable to deserialize [" + targetType + "]", e);
		}

	}

	private String resolveContentType(Object originalPayload) {
		if (originalPayload instanceof byte[]) {
			return APPLICATION_OCTET_STREAM_VALUE;
		}
		if (originalPayload instanceof String) {
			return TEXT_PLAIN_VALUE;
		}
		return "application/x-java-object;type=" + originalPayload.getClass().getName();
	}

}

/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.io;

import org.springframework.core.NestedIOException;
import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Resource 接口的默认抽象实现。
 *
 * 它实现了 Resource 接口的大部分的公共实现，作为 Resource 接口中的重中之重
 *
 * Convenience base class for {@link Resource} implementations,
 * pre-implementing typical behavior.
 *
 * <p>The "exists" method will check whether a File or InputStream can
 * be opened; "isOpen" will always return false; "getURL" and "getFile"
 * throw an exception; and "toString" will return the description.
 *
 * @author Juergen Hoeller
 * @since 28.12.2003
 */
public abstract class AbstractResource implements Resource {

	/**
     * 判断是否存在
     *
	 * This implementation checks whether a File can be opened,
	 * falling back to whether an InputStream can be opened.
	 * This will cover both directories and content resources.
	 */
	@Override
	public boolean exists() {
		// Try file existence: can we find the file in the file system? 基于 File 进行判断
		try {
			return getFile().exists();
		} catch (IOException ex) {
			// Fall back to stream existence: can we open the stream? 基于 InputStream 进行判断
			try {
				getInputStream().close();
				return true;
			} catch (Throwable isEx) {
				return false;
			}
		}
	}

	/**
     * 通过 {@link #exists()} 判断，是否可读
     *
	 * This implementation always returns {@code true} for a resource
	 * that {@link #exists() exists} (revised as of 5.1).
	 */
	@Override
	public boolean isReadable() {
		return exists();
	}

	/**
     * 直接返回 false，表示未被打开
     *
	 * This implementation always returns {@code false}.
	 */
	@Override
	public boolean isOpen() {
		return false;
	}

	/**
     * 直接返回false，表示不为 File
     *
	 * This implementation always returns {@code false}.
	 */
	@Override
	public boolean isFile() {
		return false;
	}

	/**
     * 抛出 FileNotFoundException 异常，交给子类实现
     *
	 * This implementation throws a FileNotFoundException, assuming
	 * that the resource cannot be resolved to a URL.
	 */
	@Override
	public URL getURL() throws IOException {
		throw new FileNotFoundException(getDescription() + " cannot be resolved to URL");
	}

	/**
     * 基于 getURL() 返回的 URL 构建 URI
     *
	 * This implementation builds a URI based on the URL returned
	 * by {@link #getURL()}.
	 */
	@Override
	public URI getURI() throws IOException {
		URL url = getURL();
		try {
			return ResourceUtils.toURI(url);
		} catch (URISyntaxException ex) {
			throw new NestedIOException("Invalid URI [" + url + "]", ex);
		}
	}

	/**
     * 抛出 FileNotFoundException 异常，交给子类实现
     *
	 * This implementation throws a FileNotFoundException, assuming
	 * that the resource cannot be resolved to an absolute file path.
	 */
	@Override
	public File getFile() throws IOException {
		throw new FileNotFoundException(getDescription() + " cannot be resolved to absolute file path");
	}

	/**
     * 根据 getInputStream() 的返回结果构建 ReadableByteChannel
     *
	 * This implementation returns {@link Channels#newChannel(InputStream)}
	 * with the result of {@link #getInputStream()}.
	 * <p>This is the same as in {@link Resource}'s corresponding default method
	 * but mirrored here for efficient JVM-level dispatching in a class hierarchy.
	 */
	@Override
	public ReadableByteChannel readableChannel() throws IOException {
		return Channels.newChannel(getInputStream());
	}

	/**
     * 获取资源的长度
     *
     * 这个资源内容长度实际就是资源的字节长度，通过全部读取一遍来判断
     *
	 * This implementation reads the entire InputStream to calculate the
	 * content length. Subclasses will almost always be able to provide
	 * a more optimal version of this, e.g. checking a File length.
	 * @see #getInputStream()
	 */
	@Override
	public long contentLength() throws IOException {
		InputStream is = getInputStream();
		try {
			long size = 0;
			byte[] buf = new byte[255]; // 每次最多读取 255 字节
			int read;
			while ((read = is.read(buf)) != -1) {
				size += read;
			}
			return size;
		} finally {
			try {
				is.close();
			} catch (IOException ex) {
			}
		}
	}

	/**
     * 返回资源最后的修改时间
     *
	 * This implementation checks the timestamp of the underlying File,
	 * if available.
	 * @see #getFileForLastModifiedCheck()
	 */
	@Override
	public long lastModified() throws IOException {
		long lastModified = getFileForLastModifiedCheck().lastModified();
		if (lastModified == 0L) {
			throw new FileNotFoundException(getDescription() +
					" cannot be resolved in the file system for resolving its last-modified timestamp");
		}
		return lastModified;
	}

	/**
     * 抛出 FileNotFoundException 异常，交给子类实现
     *
	 * Determine the File to use for timestamp checking.
	 * <p>The default implementation delegates to {@link #getFile()}.
	 * @return the File to use for timestamp checking (never {@code null})
	 * @throws FileNotFoundException if the resource cannot be resolved as
	 * an absolute file path, i.e. is not available in a file system
	 * @throws IOException in case of general resolution/reading failures
	 */
	protected File getFileForLastModifiedCheck() throws IOException {
		return getFile();
	}

	/**
	 * 抛出 FileNotFoundException 异常，交给子类实现
	 * This implementation throws a FileNotFoundException, assuming
	 * that relative resources cannot be created for this resource.
	 */
	@Override
	public Resource createRelative(String relativePath) throws IOException {
		throw new FileNotFoundException("Cannot create a relative resource for " + getDescription());
	}

	/**
     * 获取资源名称，默认返回 null ，交给子类实现
     *
	 * This implementation always returns {@code null},
	 * assuming that this resource type does not have a filename.
	 */
	@Override
	@Nullable
	public String getFilename() {
		return null;
	}


	/**
	 * This implementation compares description strings.
	 * @see #getDescription()
	 */
	@Override
	public boolean equals(Object other) {
		return (this == other || (other instanceof Resource &&
				((Resource) other).getDescription().equals(getDescription())));
	}

	/**
	 * This implementation returns the description's hash code.
	 * @see #getDescription()
	 */
	@Override
	public int hashCode() {
		return getDescription().hashCode();
	}

	/**
     * 返回资源的描述
     *
	 * This implementation returns the description of this resource.
	 * @see #getDescription()
	 */
	@Override
	public String toString() {
		return getDescription();
	}

}

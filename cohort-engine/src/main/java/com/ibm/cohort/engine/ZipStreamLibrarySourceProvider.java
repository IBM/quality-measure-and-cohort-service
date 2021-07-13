/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.hl7.elm.r1.VersionedIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZIP archive-based implementation of MultiFormatLibrarySourceProvider.
 * Filenames can contain library ID and version based on the logic in the
 * provided FilenameToVersionedIdentifierStrategy. When no strategy is provided
 * {@link DefaultFilenameToVersionedIdentifierStrategy} is used.
 */
public class ZipStreamLibrarySourceProvider extends MultiFormatLibrarySourceProvider {

	private static final Logger logger = LoggerFactory.getLogger(ZipStreamLibrarySourceProvider.class);
	
	public ZipStreamLibrarySourceProvider(ZipInputStream zipInputStream, String... searchPaths) throws IOException {
		this(zipInputStream, new DefaultFilenameToVersionedIdentifierStrategy(), searchPaths);
	}

	public ZipStreamLibrarySourceProvider(ZipInputStream zipInputStream,
			FilenameToVersionedIdentifierStrategy idStrategy, String... searchPaths) throws IOException  {

		ZipEntry ze;
		while ((ze = zipInputStream.getNextEntry()) != null) {
			if (!ze.isDirectory()) {
				boolean filter = false;
				if( ! ArrayUtils.isEmpty(searchPaths) ) {
					String prefix = "";
					
					int ch;
					if( (ch=ze.getName().lastIndexOf('/')) != -1 ) {
						prefix = ze.getName().substring(0, ch);
					}
					filter = ! ArrayUtils.contains(searchPaths, prefix);
				} else { 
					filter = false;
				}
				
				if( ! filter ) {
					LibraryFormat format = LibraryFormat.forString(ze.getName());
					if( format != null ) {
						VersionedIdentifier id = idStrategy.filenameToVersionedIdentifier(ze.getName());
		
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						IOUtils.copy(zipInputStream, baos);
						Map<LibraryFormat, InputStream> formats = sources.computeIfAbsent(id, key -> new HashMap<>());
						formats.put(format, new ByteArrayInputStream(baos.toByteArray()));
						logger.debug("Found source Library '{}'", ze.getName() );
					} else { 
						logger.warn("Path '{}' contains an unrecognized/unsupported file extension", ze.getName() );
					}
				}
			}
		}
	}
}

/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.hl7.elm.r1.VersionedIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directory-based implementation of MultiFormatLibrarySourceProvider. Filenames can contain
 * library ID and version based on the logic in the provided FilenameToVersionedIdentifierStrategy.
 * When no strategy is provided {@link DefaultFilenameToVersionedIdentifierStrategy} is used.
 */
public class DirectoryLibrarySourceProvider extends MultiFormatLibrarySourceProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(DirectoryLibrarySourceProvider.class);
	
	public DirectoryLibrarySourceProvider(Path folderPath) throws IOException {
		this( folderPath, new DefaultFilenameToVersionedIdentifierStrategy() );
	}
	
	public DirectoryLibrarySourceProvider(Path folderPath, FilenameToVersionedIdentifierStrategy idStrategy) throws IOException {
		if( ! folderPath.toFile().isDirectory() ) {
			throw new IllegalArgumentException("Path is not a directory");
		}

		Files.walkFileTree(folderPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path entry, BasicFileAttributes attrs) throws IOException {
				if( ! attrs.isDirectory() && LibraryFormat.isSupportedPath( entry ) ) {
					try (InputStream is = Files.newInputStream(entry)) {
						LibraryFormat sourceFormat = LibraryFormat.forPath( entry );
						if( sourceFormat != null ) {
							String filename = entry.getFileName().toString();
							VersionedIdentifier id = idStrategy.filenameToVersionedIdentifier(filename);
							
							Map<LibraryFormat, InputStream> formats = sources.computeIfAbsent( id, key -> new HashMap<>() );
							
							String text = FileUtils.readFileToString(entry.toFile(), StandardCharsets.UTF_8);
							formats.put(sourceFormat, new ByteArrayInputStream( text.getBytes() ) );
							logger.debug("Found source Library '{}'", entry.toString() );
						} else { 
							logger.warn("Path '{}' contains an unrecognized/unsupported file extension", entry.toString() );
						}
					} 
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
}

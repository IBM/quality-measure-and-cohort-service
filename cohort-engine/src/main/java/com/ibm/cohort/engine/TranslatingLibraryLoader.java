/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.engine.execution.CqlLibraryReader;
import org.opencds.cqf.cql.engine.execution.LibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.cohort.engine.translation.CqlTranslationProvider;

/**
 * Implementation of the CQL Engine LibraryLoader interface that supports
 * on-demand translation as necessary.
 */
public class TranslatingLibraryLoader implements LibraryLoader {

	private static final Logger logger = LoggerFactory.getLogger(TranslatingLibraryLoader.class);
	
	public static final boolean DEFAULT_FORCE_TRANSLATION = false;

	private MultiFormatLibrarySourceProvider provider;
	private Map<VersionedIdentifier, Library> libraryCache = new HashMap<>();
	private CqlTranslationProvider translator;
	private boolean isForceTranslation = DEFAULT_FORCE_TRANSLATION;

	public TranslatingLibraryLoader() {
	}

	public TranslatingLibraryLoader(MultiFormatLibrarySourceProvider provider,
			CqlTranslationProvider translationProvider) {
		this(provider, translationProvider, DEFAULT_FORCE_TRANSLATION);
	}

	public TranslatingLibraryLoader(MultiFormatLibrarySourceProvider provider,
			CqlTranslationProvider translationProvider, boolean forceTranslation) {
		setSourceProvider(provider);
		setTranslationProvider(translationProvider);
		setForceTranslation(forceTranslation);
	}

	public void setSourceProvider(MultiFormatLibrarySourceProvider provider) {
		this.provider = provider;
	}

	public void setTranslationProvider(CqlTranslationProvider translator) {
		this.translator = translator;
	}

	public void setForceTranslation(boolean forceTranslation) {
		this.isForceTranslation = forceTranslation;
	}

	@Override
	public Library load(VersionedIdentifier libraryIdentifier) {
		Library library = libraryCache.get(libraryIdentifier);
		if (library == null) {
			org.hl7.elm.r1.VersionedIdentifier translatorVersionedId = new org.hl7.elm.r1.VersionedIdentifier()
					.withId(libraryIdentifier.getId()).withVersion(libraryIdentifier.getVersion());

			try {
				if (!isForceTranslation) {
					InputStream is = provider.getLibrarySource(translatorVersionedId, LibraryFormat.XML);
					if (is != null) {
						library = CqlLibraryReader.read(is);
					}
				}

				if (library == null) {
					InputStream is = provider.getLibrarySource(translatorVersionedId, LibraryFormat.CQL);
					if (is != null) {
						logger.debug("Translating \"{}\" version '{}'", translatorVersionedId.getId(), translatorVersionedId.getVersion());
						library = translator.translate(is);
						assert library.getIdentifier().getId() != null;
					} else {
						throw new IllegalArgumentException(String.format("No library source found for \"%s\" version '%s'",
								translatorVersionedId.getId(), translatorVersionedId.getVersion()));
					}
				}

				libraryCache.put(libraryIdentifier, library);
			} catch( IllegalArgumentException agex ) { 
				throw agex;
			} catch (Exception ex) {
				throw new RuntimeException("Failed to load library", ex);
			}
		}

		return library;
	}
}

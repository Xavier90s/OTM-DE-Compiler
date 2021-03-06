/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opentravel.schemacompiler.index;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.opentravel.ns.ota2.repositoryinfoext_v01_00.SubscriptionTarget;
import org.opentravel.schemacompiler.index.builder.IndexBuilder;
import org.opentravel.schemacompiler.index.builder.IndexBuilderFactory;
import org.opentravel.schemacompiler.repository.RepositoryItem;
import org.opentravel.schemacompiler.repository.RepositoryManager;

/**
 * Implementation of the <code>FreeTextSearchService</code> that performs indexing tasks in
 * real-time.  This implementation is indended for testing purposes since production indexing
 * should be performed in a background task.
 * 
 * @author S. Livezey
 */
public class RealTimeFreeTextSearchService extends FreeTextSearchService {

    private static Log log = LogFactory.getLog(RealTimeFreeTextSearchService.class);

    private IndexWriter indexWriter;
    
	/**
     * Constructor that specifies the folder location of the index and the repository
     * manager used to access the content to be indexed and searched for.
     * 
     * @param indexLocation  the folder location of the index directory
     * @param repositoryManager  the repository that owns all content to be indexed
     * @throws IOException  thrown if a low-level error occurs while initializing the search index
     */
	public RealTimeFreeTextSearchService(File indexLocation, RepositoryManager repositoryManager) throws IOException {
		super(indexLocation, repositoryManager);
	}

    /**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#onStartup(org.apache.lucene.store.Directory)
	 */
	@Override
	protected void onStartup(Directory indexDirectory) throws IOException {
		IndexWriterConfig writerConfig = new IndexWriterConfig( new StandardAnalyzer() );
		
		writerConfig.setOpenMode( OpenMode.CREATE_OR_APPEND );
		this.indexWriter = new IndexWriter( indexDirectory, writerConfig );
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#onShutdown()
	 */
	@Override
	protected void onShutdown() throws IOException {
		try {
			indexWriter.close();
			
		} finally {
			indexWriter = null;
		}
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#isIndexingServiceAvailable()
	 */
	@Override
	public boolean isIndexingServiceAvailable() {
		return isRunning();
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#newIndexReader(org.apache.lucene.store.Directory)
	 */
	@Override
	protected DirectoryReader newIndexReader(Directory indexDirectory) throws IOException {
		return DirectoryReader.open( indexWriter, false );
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#submitIndexingJob(java.util.List, boolean)
	 */
	@Override
	protected void submitIndexingJob(List<RepositoryItem> itemsToIndex, boolean deleteIndex) {
		try {
	    	IndexBuilderFactory factory = new IndexBuilderFactory( getRepositoryManager(), indexWriter );
			
			for (RepositoryItem item : itemsToIndex) {
		    	IndexBuilder<?> indexBuilder = deleteIndex ?
		    			factory.newDeleteIndexBuilder( item ) : factory.newCreateIndexBuilder( item );
		    			
	   	    	if (deleteIndex) {
	   	        	log.info("Indexing library: " + item.getFilename());
	   	        	
	   	    	} else {
	   	        	log.info("Deleting index for library: " + item.getFilename());
	   	    	}
				indexBuilder.performIndexingAction();
			}
			
			if (!deleteIndex) {
				factory.getFacetService().getIndexBuilder().performIndexingAction();
				factory.getValidationService().getIndexBuilder().performIndexingAction();
			}
			indexWriter.commit();
			refreshIndexReader();
			
		} catch (IOException e) {
			log.error("Error committing search index document(s).", e);
		}
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#deleteSearchIndex()
	 */
	@Override
	protected void deleteSearchIndex() {
		try {
	    	log.info("Deleting search index.");
			indexWriter.deleteAll();
			refreshIndexReader();
			
		} catch (IOException e) {
			log.error("Error purging search index.", e);
		}
	}

	/**
	 * @see org.opentravel.schemacompiler.index.FreeTextSearchService#submitIndexingJob(org.opentravel.ns.ota2.repositoryinfoext_v01_00.SubscriptionTarget)
	 */
	@Override
	protected void submitIndexingJob(SubscriptionTarget subscriptionTarget) {
		// TODO: Implement the 'submitIndexingJob(SubscriptionTarget)' method
	}
	
}

/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;


import mocha.foundation.Copying;
import mocha.foundation.OptionalInterface;
import mocha.foundation.SortDescriptor;

import java.util.List;

public class FetchRequest<E extends Model> implements Copying<FetchRequest<E>> {
	private Query query;
	private SortDescriptor[] sortDescriptors;
	private long fetchLimit;
	private boolean includesPropertyValues;
	private boolean returnsObjectsAsFaults;
	private String[] relationshipKeyPathsForPrefetching;
	private boolean returnsDistinctResults;
	private String[] propertiesToFetch;
	private long fetchOffset;
	private long fetchBatchSize;
	private String[] propertiesToGroupBy;
	private Query havingQuery;
	private Class<E> modelClass;
	private FetchRequestQuery<E> fetchRequestQuery;

	private FetchRequest() {
		this.setFetchLimit(Long.MAX_VALUE);
		this.setIncludesPropertyValues(true);
		this.setReturnsObjectsAsFaults(true);
		this.setReturnsDistinctResults(false);
		this.setFetchOffset(0);
		this.setFetchBatchSize(0);
	}

	public FetchRequest(Class<E> modelClass) {
		this();
		this.modelClass = modelClass;
	}


	FetchRequestQuery<E> getQuery(Store store) {
		if(this.fetchRequestQuery != null && this.fetchRequestQuery.getStore() == store) {
			return this.fetchRequestQuery.copy();
		} else {
			return new FetchRequestQuery<E>(this, store);
		}
	}

	Class<E> getModelClass() {
		return modelClass;
	}

	public FetchRequest<E> copy() {
		FetchRequest<E> fetchRequest = new FetchRequest<E>(this.modelClass);
		fetchRequest.setQuery(this.query);
		fetchRequest.setSortDescriptors(this.sortDescriptors);
		fetchRequest.setFetchLimit(this.fetchLimit);
		fetchRequest.setIncludesPropertyValues(this.includesPropertyValues);
		fetchRequest.setReturnsObjectsAsFaults(this.returnsDistinctResults);
		fetchRequest.setRelationshipKeyPathsForPrefetching(this.relationshipKeyPathsForPrefetching);
		fetchRequest.setReturnsDistinctResults(this.returnsDistinctResults);
		fetchRequest.setPropertiesToFetch(this.propertiesToFetch);
		fetchRequest.setFetchOffset(this.fetchOffset);
		fetchRequest.setFetchBatchSize(this.fetchBatchSize);
		fetchRequest.setPropertiesToGroupBy(this.propertiesToGroupBy);
		fetchRequest.setHavingQuery(this.havingQuery);
		return null;
	}

	// Properties

	public Query getQuery() {
		return this.query;
	}

	public void setQuery(Query query) {
		this.query = query;
	}

	public SortDescriptor[] getSortDescriptors() {
		return this.sortDescriptors;
	}

	public void setSortDescriptors(SortDescriptor... sortDescriptors) {
		this.sortDescriptors = sortDescriptors;
	}

	public void setSortDescriptors(List<SortDescriptor> sortDescriptors) {
		this.sortDescriptors = new SortDescriptor[sortDescriptors.size()];
		this.sortDescriptors = sortDescriptors.toArray(this.sortDescriptors);
	}

	public long getFetchLimit() {
		return this.fetchLimit;
	}

	public void setFetchLimit(long fetchLimit) {
		this.fetchLimit = fetchLimit;
	}

	public boolean isIncludesPropertyValues() {
		return this.includesPropertyValues;
	}

	public void setIncludesPropertyValues(boolean includesPropertyValues) {
		this.includesPropertyValues = includesPropertyValues;
	}

	public boolean isReturnsObjectsAsFaults() {
		return this.returnsObjectsAsFaults;
	}

	public void setReturnsObjectsAsFaults(boolean returnsObjectsAsFaults) {
		this.returnsObjectsAsFaults = returnsObjectsAsFaults;
	}

	public String[] getRelationshipKeyPathsForPrefetching() {
		return this.relationshipKeyPathsForPrefetching;
	}

	public void setRelationshipKeyPathsForPrefetching(String... relationshipKeyPathsForPrefetching) {
		this.relationshipKeyPathsForPrefetching = relationshipKeyPathsForPrefetching;
	}

	public void setRelationshipKeyPathsForPrefetching(List<String> relationshipKeyPathsForPrefetching) {
		this.relationshipKeyPathsForPrefetching = new String[relationshipKeyPathsForPrefetching.size()];
		this.relationshipKeyPathsForPrefetching = relationshipKeyPathsForPrefetching.toArray(this.relationshipKeyPathsForPrefetching);
	}

	public boolean isReturnsDistinctResults() {
		return this.returnsDistinctResults;
	}

	public void setReturnsDistinctResults(boolean returnsDistinctResults) {
		this.returnsDistinctResults = returnsDistinctResults;
	}

	public String[] getPropertiesToFetch() {
		return this.propertiesToFetch;
	}

	public void setPropertiesToFetch(String... propertiesToFetch) {
		this.propertiesToFetch = propertiesToFetch;
	}

	public void setPropertiesToFetch(List<String> propertiesToFetch) {
		this.propertiesToFetch = new String[propertiesToFetch.size()];
		this.propertiesToFetch = propertiesToFetch.toArray(this.propertiesToFetch);
	}

	public long getFetchOffset() {
		return this.fetchOffset;
	}

	public void setFetchOffset(long fetchOffset) {
		this.fetchOffset = fetchOffset;
	}

	public long getFetchBatchSize() {
		return this.fetchBatchSize;
	}

	public void setFetchBatchSize(long fetchBatchSize) {
		this.fetchBatchSize = fetchBatchSize;
	}

	public String[] getPropertiesToGroupBy() {
		return propertiesToGroupBy;
	}

	public void setPropertiesToGroupBy(String... propertiesToGroupBy) {
		this.propertiesToGroupBy = propertiesToGroupBy;
	}

	public void setPropertiesToGroupBy(List<String> propertiesToGroupBy) {
		this.propertiesToGroupBy = new String[propertiesToGroupBy.size()];
		this.propertiesToGroupBy = propertiesToGroupBy.toArray(this.propertiesToGroupBy);
	}
	public Query getHavingQuery() {
		return this.havingQuery;
	}

	public void setHavingQuery(Query havingQuery) {
		this.havingQuery = havingQuery;
	}
}

/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import android.util.Pair;
import mocha.foundation.Copying;
import mocha.foundation.MObject;
import mocha.foundation.SortDescriptor;

import java.util.*;

class FetchRequestQuery <E extends Model> extends MObject implements Copying<FetchRequestQuery<E>> {
	final Store store;
	final FetchRequest<E> fetchRequest;
	final ModelEntity<E> modelEntity;
	final Query query;
	final Query havingQuery;

	boolean distinct;
	String table;
	String[] columns;
	String selection;
	String[] selectionArgs;
	String groupBy;
	String having;
	String orderBy;

	List<String> selectedColumns;

	public FetchRequestQuery(FetchRequest<E> fetchRequest, Store store) {
		this(fetchRequest, store, true);
	}

	private FetchRequestQuery(FetchRequest<E> fetchRequest, Store store, boolean compile) {
		this.store = store;
		this.fetchRequest = fetchRequest;

		if(fetchRequest.getQuery() != null) {
			this.query = fetchRequest.getQuery().copy();
		} else {
			this.query = null;
		}

		if(fetchRequest.getHavingQuery() != null) {
			this.havingQuery = fetchRequest.getHavingQuery().copy();
		} else {
			this.havingQuery = null;
		}

		//noinspection unchecked
		this.modelEntity = this.store.getModelEntity(fetchRequest.getModelClass());

		if(compile) {
			this.compile();
		}
	}

	private void compile() {
		if(this.modelEntity == null) {
			throw new RuntimeException("No model entity found for " + this.fetchRequest.getModelClass());
		}

		this.distinct = this.fetchRequest.isReturnsDistinctResults();
		this.table = this.modelEntity.getTable();

		// Which columns to select
		String[] propertiesToFetch = this.fetchRequest.getPropertiesToFetch();
		String table = this.modelEntity.getTable();

		List<String> columns = new ArrayList<>();
		columns.add(table + "." + ModelEntity.PRIMARY_KEY_COLUMN);

		this.selectedColumns = new ArrayList<>();
		this.selectedColumns.add(ModelEntity.PRIMARY_KEY_COLUMN);

		if(propertiesToFetch != null && propertiesToFetch.length > 0) {
			for(String property : propertiesToFetch) {
				String column = this.modelEntity.getColumnForFieldName(property);

				if(column != null) {
					columns.add(table + "." + column);
					this.selectedColumns.add(column);
				}
			}
		} else {
			for(String column : this.modelEntity.getColumns()) {
				columns.add(table + "." + column);
				this.selectedColumns.add(column);
			}
		}

		String[] array = new String[columns.size()];
		this.columns = columns.toArray(array);

		// Where clause
		if(this.query != null) {
			Pair<String, List<String>> where = this.query.compile(this.modelEntity, this.table);
			this.selection = where.first;
			this.selectionArgs = where.second.toArray(new String[where.second.size()]);
		}

		// Group by
		String[] propertiesToGroupBy = this.fetchRequest.getPropertiesToGroupBy();
		if(propertiesToGroupBy != null && propertiesToGroupBy.length > 0) {
			if(propertiesToGroupBy.length == 1) {
				this.groupBy = propertiesToGroupBy[0];
			} else {
				StringBuilder builder = new StringBuilder();

				for (int i = 0; i < propertiesToGroupBy.length - 1; i++) {
					builder.append(propertiesToGroupBy[i]).append(", ");
				}

				this.groupBy = builder.toString() + propertiesToGroupBy[propertiesToGroupBy.length - 1];
			}
		}

		// Having
		if(this.havingQuery != null) {
			// TODO
			throw new RuntimeException("HAVING is not yet supported.");
		}

		// Order by
		StringBuilder orderBy = new StringBuilder();
		SortDescriptor[] sortDescriptors = this.fetchRequest.getSortDescriptors();

		if(sortDescriptors != null && sortDescriptors.length > 0) {
			for(SortDescriptor sortDescriptor : sortDescriptors) {
				String column = this.modelEntity.getColumnForFieldName(sortDescriptor.key);

				if(column != null) {
					orderBy.append(table).append(".").append(column).append(sortDescriptor.ascending ? " ASC, " : " DESC, ");
				}
			}
		}

		orderBy.append(table).append(".").append(ModelEntity.PRIMARY_KEY_COLUMN).append(" ASC");
		this.orderBy = orderBy.toString();
	}

	public void deleteAll() {
		this.store.getDatabase().delete(this.table, this.selection, this.selectionArgs);
	}

	public List<E> execute(FetchContext context) {
		return this.execute(context, null, null);
	}

	public List<E> execute(FetchContext context, String sectionProperty, List<List<Integer>> sectionOffsets) {
		long fetchBatchSize = this.fetchRequest.getFetchBatchSize();

		if(fetchBatchSize > 0 && fetchBatchSize < Long.MAX_VALUE) {
			long fetchLimit = this.fetchRequest.getFetchLimit();

			if(fetchLimit == 0 || fetchLimit < Long.MAX_VALUE || fetchLimit > fetchBatchSize) {
				return new BatchedQueryResultList<>(this, sectionProperty, sectionOffsets);
			}
		}

		return this.execute(context, this.fetchRequest.getFetchLimit(), this.fetchRequest.getFetchOffset(), sectionProperty, sectionOffsets);
	}

	private List<E> execute(FetchContext context, long limit, long offset, String sectionProperty, List<List<Integer>> sectionOffsets) {
		return this.parseAndCloseCursor(this.execute(this.columns, limit, offset, true), context, sectionProperty, sectionOffsets);
	}

	Cursor execute(String[] columns, long limit, long offset, boolean orderedCursor) {
		String limitString = null;

		if((limit > 0 && limit < Long.MAX_VALUE) || offset > 0) {
			limit = limit == 0 ? Long.MAX_VALUE : limit;
			limitString = String.format("%d, %d", offset, limit);
		}

		String orderBy = null;

		if(orderedCursor) {
			orderBy = this.orderBy;
		}

		return this.store.getDatabase().query(this.distinct, this.table, columns, this.selection, this.selectionArgs, this.groupBy, this.having, orderBy, limitString);
	}

	List<E> parseAndCloseCursor(Cursor cursor, FetchContext context) {
		return this.parseAndCloseCursor(cursor, context, null, null);
	}

	private List<E> parseAndCloseCursor(Cursor cursor, FetchContext context, String sectionProperty, List<List<Integer>> sectionOffsets) {
		List<E> list = new ArrayList<>(cursor.getCount());

		if(cursor.moveToFirst()) {
			final boolean determineSections = sectionProperty != null && sectionOffsets != null;
			final int sectionColumnIndex;

			String currentValue = null;
			List<Integer> currentOffsets = null;

			if(determineSections) {
				sectionColumnIndex = cursor.getColumnIndex(this.modelEntity.getColumnForFieldName(sectionProperty));
			} else {
				sectionColumnIndex = -1;
			}

			do {
				E model = this.modelEntity.parseCursor(cursor, context, this.selectedColumns, true);
				list.add(model);

				if(determineSections) {
					String value = cursor.getString(sectionColumnIndex);

					if(value == null) {
						value = "";
					}

					if(currentValue == null || !value.equals(currentValue)) {
						currentOffsets = new ArrayList<>();
						sectionOffsets.add(currentOffsets);

						currentValue = value;
					}

					currentOffsets.add(cursor.getPosition());
				}
			} while (cursor.moveToNext());
		}

		cursor.close();

		if(this.fetchRequest.hasRelationsNeedingPrefetching()) {
			//noinspection unchecked
			RelationshipPrefetcher prefetcher = new RelationshipPrefetcher(context, this.modelEntity.modelClass, this.fetchRequest, this.store);

			//noinspection unchecked
			prefetcher.prefetchRelationships(list);
		}

		return list;
	}

	public long count(long limit, long offset) {
		Cursor cursor = this.execute(new String[] { "COUNT(*)" }, limit, offset, false);
		cursor.moveToFirst();
		long count = cursor.getLong(0);
		cursor.close();

		return count;
	}

	public Store getStore() {
		return store;
	}

	public FetchRequestQuery<E> copy() {
		FetchRequestQuery<E> query = new FetchRequestQuery<>(this.fetchRequest, this.store, false);

		query.distinct = this.distinct;
		query.table = this.table;
		query.columns = this.columns;
		query.selection = this.selection;
		query.selectionArgs = this.selectionArgs;
		query.groupBy = this.groupBy;
		query.having = this.having;
		query.orderBy = this.orderBy;
		query.selectedColumns = this.selectedColumns;

		return query;
	}

}

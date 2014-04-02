/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import mocha.foundation.Copying;
import mocha.foundation.SortDescriptor;

import java.util.ArrayList;
import java.util.List;

class FetchRequestQuery <E extends Model> implements Copying<FetchRequestQuery<E>> {
	private final Store store;
	private final FetchRequest<E> fetchRequest;
	private final ModelEntity<E> modelEntity;

	private boolean distinct;
	private String table;
	private String[] columns;
	private String selection;
	private String[] selectionArgs;
	private String groupBy;
	private String having;
	private String orderBy;

	private List<String> selectedColumns;

	public FetchRequestQuery(FetchRequest<E> fetchRequest, Store store) {
		this(fetchRequest, store, true);
	}

	private FetchRequestQuery(FetchRequest<E> fetchRequest, Store store, boolean compile) {

			this.store = store;
		this.fetchRequest = fetchRequest;

		//noinspection unchecked
		this.modelEntity = this.store.getModelEntity(fetchRequest.getModelClass());

		if(compile) {
			this.compile();
		}
	}

	private void compile() {
		this.distinct = this.fetchRequest.isReturnsDistinctResults();
		this.table = this.modelEntity.getTable();

		// Which columns to select
		String[] propertiesToFetch = this.fetchRequest.getPropertiesToFetch();
		String table = this.modelEntity.getTable();

		if(propertiesToFetch != null && propertiesToFetch.length > 0) {
			List<String> columns = new ArrayList<String>();
			this.selectedColumns = new ArrayList<String>();

			columns.add(table + "." + ModelEntity.PRIMARY_KEY_COLUMN);
			this.selectedColumns.add(ModelEntity.PRIMARY_KEY_COLUMN);

			for(String property : propertiesToFetch) {
				String column = this.modelEntity.getColumnForFieldName(property);

				if(column != null) {
					columns.add(table + "." + column);
					this.selectedColumns.add(column);
				}
			}

			String[] array = new String[columns.size()];
			this.columns = columns.toArray(array);
		} else {
			this.columns = new String[] { table + ".*" };
			this.selectedColumns = null;
		}

		// Where clause
		// TODO

		// Group by
		// TODO

		// Having
		// TODO

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

	public List<E> execute() {
		// TODO: Handle fetchBatchSize
		return this.execute(this.fetchRequest.getFetchLimit(), this.fetchRequest.getFetchOffset());
	}

	private List<E> execute(long limit, long offset) {
		String limitString = null;

		if((limit > 0 && limit < Long.MAX_VALUE) || offset > 0) {
			limitString = String.format("LIMIT %d, %d", limit, offset);
		}

		return this.parseCursor(this.store.getDatabase().query(this.distinct, this.table, this.columns, this.selection, this.selectionArgs, this.groupBy, this.having, this.orderBy, limitString));
	}

	private List<E> parseCursor(Cursor cursor) {
		List<E> list = new ArrayList<E>(cursor.getCount());

		if(cursor.moveToFirst()) {
			do {
				E model = this.modelEntity.parseCursor(cursor, this.selectedColumns);
				list.add(model);
			} while (cursor.moveToNext());
		}

		cursor.close();

		return list;
	}

	public long count() {
		return 0;
	}

	public Store getStore() {
		return store;
	}

	public FetchRequestQuery<E> copy() {
		FetchRequestQuery<E> query = new FetchRequestQuery<E>(this.fetchRequest, this.store, false);

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

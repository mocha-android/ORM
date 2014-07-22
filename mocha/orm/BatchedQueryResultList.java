/**
 *  @author Shaun
 *  @date 4/2/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import mocha.foundation.MObject;

import java.util.*;

class BatchedQueryResultList<E extends Model> extends MObject implements List<E> {
	private long batchSize;
	private Model[] models;
	private List<Long> primaryKeys;
	private final Store store;
	private FetchRequestQuery<E> fetchRequestQuery;

	public BatchedQueryResultList(FetchRequestQuery<E> fetchRequestQuery) {
		this(fetchRequestQuery, null);
	}

	public BatchedQueryResultList(FetchRequestQuery<E> fetchRequestQuery, List<List<Integer>> groupOffsets) {
		this.batchSize = fetchRequestQuery.fetchRequest.getFetchBatchSize();
		this.primaryKeys = new ArrayList<Long>();
		this.store = fetchRequestQuery.store;
		this.fetchRequestQuery = fetchRequestQuery;

		MLog(LogLevel.WTF, "Created batched result list with size: " + this.batchSize);

		boolean useGrouping = false;

//			if(useGrouping) {
//				fetchProperties.add(groupPropertyName);
//			}

		Map<Object,Integer> groupIndexes = !useGrouping ? null : new HashMap<Object, Integer>();
		Object currentValue = null;
		List<Integer> currentOffsets = null;

		String[] columns = new String[] { fetchRequestQuery.table + "." + ModelEntity.PRIMARY_KEY_COLUMN };
		Cursor cursor = fetchRequestQuery.execute(columns, fetchRequestQuery.fetchRequest.getFetchLimit(), fetchRequestQuery.fetchRequest.getFetchOffset(), true);

		if(cursor.moveToFirst()) {
			do {
				this.primaryKeys.add(cursor.getLong(0));

//					if(useGrouping) {
//						Object value = cursor.getString(1);
//
//						if(value == null) continue;
//
//						if(currentValue == null || !value.equals(currentValue)) {
//							Integer index = groupIndexes.get(value);
//
//							if(index != null) {
//								currentOffsets = groupOffsets.get(index);
//							} else {
//								currentOffsets = null;
//							}
//
//							currentValue = value;
//						}
//
//						if(currentOffsets == null) {
//							currentOffsets = new ArrayList<Integer>();
//							groupIndexes.put(value, groupOffsets.size());
//							groupOffsets.add(currentOffsets);
//						}
//
//						currentOffsets.add(cursor.getPosition());
//					}
			} while(cursor.moveToNext());
		}

		cursor.close();

		MLog(LogLevel.WTF, "Fetched primary keys for batch: " + this.primaryKeys.size());

		this.models = new Model[this.primaryKeys.size()];
	}

	public boolean contains(Object o) {
		return o instanceof Model && this.primaryKeys.contains(((Model) o).primaryKey);
	}

	public boolean containsAll(Collection<?> objects) {
		for(Object o : objects) {
			if(!this.contains(o)) {
				return false;
			}
		}

		return true;
	}

	public int indexOf(Object o) {
		if(o instanceof Model) {
			return this.primaryKeys.indexOf(((Model)o).primaryKey);
		} else {
			return -1;
		}
	}

	public boolean isEmpty() {
		return primaryKeys.size() == 0;
	}

	public Iterator<E> iterator() {
		return new Iterator<E>() {
			private int position = 0;
			private int size = size();

			public boolean hasNext() {
				return this.position < this.size;
			}

			public E next() {
				if (this.position == this.size) {
					throw new NoSuchElementException();
				}

				return get(this.position++);
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public int lastIndexOf(Object o) {
		if(o instanceof Model) {
			return this.primaryKeys.lastIndexOf(((Model) o).primaryKey);
		} else {
			return -1;
		}
	}

	public ListIterator<E> listIterator() {
		return null;
	}

	public ListIterator<E> listIterator(int i) {
		return null;
	}

	public int size() {
		return this.primaryKeys.size();
	}

	@SuppressWarnings("unchecked")
	public E get(int i) {
		Model model = this.models[i];

		if(model == null) {
			this.fetchAroundIndex(i);

			if(this.models[i] == null) {
				MWarn("Did not fulfull request for %d (models size: %d, batch size: %d)", i, this.models.length, this.batchSize);
			}

			return (E)this.models[i];
		} else {
			return (E)model;
		}
	}

	private void fetchAroundIndex(int index) {
		int end = index;
		int start = index;
		int length = this.models.length;

		boolean goForward = true;
		boolean goBackward = true;
		for(int i = 1; i <= this.batchSize; i++) {
			if(goForward) {
				// Note: We increment end past the last index
				// so the range is setup properly for subList

				if(index + i >= length) {
					end = length;
					goForward = false;
				} else if(this.models[index + i] != null) {
					end = index + i;
					goForward = false;
				} else {
					end = index + i;
				}
			}

			if(goBackward) {
				if(index - i < 0) {
					goBackward = false;
				} else if(this.models[index - i] != null) {
					goBackward = false;
				} else {
					start = index - i;
				}
			}

			if(goForward && goBackward) {
				i++;
			}
		}

		if(end - start > this.batchSize) {
			MWarn("Trying to fetch more than batch size, batchSize: %d | fetching from %d to %d | built around index: %d", this.batchSize, start, end, index);
		} else {
			MLog("Tripped fetching at %d, fetching from %d to %d", index, start, end);
		}

		List<Long> ids;

		if(start == end) {
			ids = new ArrayList<Long>();
			ids.add(this.primaryKeys.get(start));
		} else {
			ids = this.primaryKeys.subList(start, end);
		}

		String[] selectionArgs = new String[ids.size()];
		StringBuilder selection = new StringBuilder();
		selection.append(this.fetchRequestQuery.table).append(".").append(ModelEntity.PRIMARY_KEY_COLUMN).append(" IN (?");
		selectionArgs[0] = String.valueOf(ids.get(0));

		int size = ids.size();
		for(int i = 1; i < size; i++) {
			selection.append(",?");
			selectionArgs[i] = String.valueOf(ids.get(i));
		}

		selection.append(")");

		List<E> models = this.fetchRequestQuery.parseCursor(this.store.getDatabase().query(this.fetchRequestQuery.table, this.fetchRequestQuery.columns, selection.toString(), selectionArgs, null, null, null));

//			if(this.toManyRelationshipsForPrefetching != null) {
//				prefetchRelationships(activeAndroid, models, this.toManyRelationshipsForPrefetching);
//			}

		for(E model : models) {
			// We're not sorting the query, so the cursor may not
			// necessarily be in the same order as it needs to be
			int modelIndex = start + ids.indexOf(model.primaryKey);

			this.models[modelIndex] = model;
		}
	}

	// Unsupported List methods
	public void add(int i, E e) { throw new UnsupportedOperationException(); }
	public boolean add(E e) { throw new UnsupportedOperationException(); }
	public boolean addAll(int i, Collection<? extends E> es) {throw new UnsupportedOperationException(); }
	public boolean addAll(Collection<? extends E> es) { throw new UnsupportedOperationException(); }
	public void clear() { throw new UnsupportedOperationException(); }
	public E remove(int i) { throw new UnsupportedOperationException(); }
	public boolean remove(Object o) { throw new UnsupportedOperationException(); }
	public boolean removeAll(Collection<?> objects) { throw new UnsupportedOperationException(); }
	public boolean retainAll(Collection<?> objects) { throw new UnsupportedOperationException(); }
	public E set(int i, E e) { throw new UnsupportedOperationException(); }
	public List<E> subList(int i, int i2) { throw new UnsupportedOperationException(); }
	public Object[] toArray() { throw new UnsupportedOperationException(); }
	public <T> T[] toArray(T[] ts) { throw new UnsupportedOperationException(); }
}

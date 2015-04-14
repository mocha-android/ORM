/*
 *  @author Shaun
 *  @date 7/23/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import mocha.foundation.IndexPath;
import mocha.foundation.MObject;

import java.util.*;

public class FetchedResultsController <M extends Model> extends MObject {

	private FetchRequest<M> fetchRequest;
	private List<M> fetchedObjects;
	private String sectionPropertyName;
	private Listener<M> listener;
	private List<SectionInfo> sections;
	private Store store;

	public interface Listener<M extends Model> {
		void controllerWillChangeContent(FetchedResultsController<M> controller);
		void controllerDidChangeContent(FetchedResultsController<M> controller);
	}

	public class SectionInfo {
		private final int numberOfObjects;
		private final int[] offsets;

		private SectionInfo(int[] offsets) {
			this.numberOfObjects = offsets.length;
			this.offsets = offsets;
		}

		public M getFirstObject() {
			if(this.numberOfObjects > 0) {
				return this.get(0);
			} else {
				return null;
			}
		}

		public M get(int index) {
			if(this.offsets.length > index) {
				return getFetchedObject(this.offsets[index]);
			} else {
				return null;
			}
		}

		public int getNumberOfObjects() {
			return this.numberOfObjects;
		}

	}

	public FetchedResultsController(Store store, FetchRequest<M> fetchRequest) {
		this(store, fetchRequest, null);
	}

	public FetchedResultsController(Store store, FetchRequest<M> fetchRequest, String sectionPropertyName) {
		this.store = store;
		this.fetchRequest = fetchRequest;
		this.sectionPropertyName = sectionPropertyName;
	}

	public void refresh() {
		if(this.listener != null) {
			this.listener.controllerWillChangeContent(this);
		}

		this.execute();

		if(this.listener != null) {
			this.listener.controllerDidChangeContent(this);
		}
	}

	public void execute() {
		this.sections = new ArrayList<>();
		List<List<Integer>> sectionOffsets = this.sectionPropertyName == null ? null : new ArrayList<List<Integer>>();
		this.fetchedObjects = this.store.execute(this.fetchRequest, new FetchContext(), this.sectionPropertyName, sectionOffsets);

		if(this.sectionPropertyName != null && sectionOffsets != null) {
			for(List<Integer> offsets : sectionOffsets) {
				int[] offsetsArray = new int[offsets.size()];

				for(int i = 0; i < offsetsArray.length; i++) {
					Integer offset = offsets.get(i);
					offsetsArray[i] = offset == null ? 0 : offset;
				}

				this.sections.add(new SectionInfo(offsetsArray));
			}
		} else {
			int size = this.fetchedObjects.size();
			int[] offsets = new int[size];

			for(int idx = 0; idx < size; idx++) {
				offsets[idx] = idx;
			}

			this.sections.add(new SectionInfo(offsets));
		}
	}

	public void destroy() {
		this.sections = null;
		this.fetchedObjects = null;
	}

	private M getFetchedObject(int index) {
		M object = this.fetchedObjects.get(index);

		if(object == null) {
			this.didFailToLoadFetchedObject();
			return null;
		} else {
			return object;
		}
	}

	/**
	 * This scenario occurs when the data behind a batched result set
	 * changes before the data is loaded.
	 *
	 * Default no-op. Subclasses should consider triggering a refresh of the data
	 * or throwing an exception.
	 */
	protected void didFailToLoadFetchedObject() {

	}

	public M get(IndexPath indexPath) {
		return this.sections.get(indexPath.section).get(indexPath.row);
	}

	public int indexOf(M object) {
		if(this.fetchedObjects != null) {
			return this.fetchedObjects.indexOf(object);
		} else {
			return -1;
		}
	}

	public IndexPath getIndexPathOf(M object) {
		if(this.fetchedObjects != null) {
			int index = this.fetchedObjects.indexOf(object);

			if(index != -1) {
				int section = 0;
				for(SectionInfo sectionInfo : this.sections) {
					int row = Arrays.binarySearch(sectionInfo.offsets, index);

					if(row != -1) {
						return IndexPath.withRowInSection(row, section);
					} else {
						section++;
					}
				}
			}
		}

		return null;
	}

	public int getNumberOfSections() {
		if(this.sections == null) {
			return 0;
		} else {
			return this.sections.size();
		}
	}

	public List<SectionInfo> getSections() {
		if(this.sections == null) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(this.sections);
		}
	}

	public SectionInfo getSection(int section) {
		if(this.sections == null) {
			throw new IndexOutOfBoundsException();
		} else {
			return this.sections.get(section);
		}
	}

	public List<M> getFetchedObjects() {
		if(this.fetchedObjects != null) {
			return this.fetchedObjects;
		} else {
			return Collections.emptyList();
		}
	}

	public boolean isEmpty() {
		return this.fetchedObjects == null || this.fetchedObjects.size() == 0;
	}

	public Listener<M> getListener() {
		return this.listener;
	}

	public void setListener(Listener<M> listener) {
		this.listener = listener;
	}

	public FetchRequest<M> getFetchRequest() {
		return this.fetchRequest;
	}

	public Store getStore() {
		return this.store;
	}
}

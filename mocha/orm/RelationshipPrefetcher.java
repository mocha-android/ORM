/**
 *  @author Shaun
 *  @date 7/22/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import android.database.Cursor;
import android.util.Pair;
import mocha.foundation.Maps;

import java.lang.reflect.Field;
import java.util.*;

final class RelationshipPrefetcher <E extends Model> {

	private final Class<E> modelClass;
	private final ModelEntity modelEntity;
	private final FetchRequest<E> fetchRequest;
	private final Store store;

	private Map<Long, E> keyedModels = new HashMap<>();
	private Long[] ids;

	RelationshipPrefetcher(Class<E> modelClass, FetchRequest<E> fetchRequest, Store store) {
		this.modelClass = modelClass;
		this.fetchRequest = fetchRequest;
		this.store = store;
		this.modelEntity = this.store.getModelEntity(this.modelClass);
	}

	void prefetchRelationships(List<E> list) {
		FetchRequest.Relation[] relationshipsForPrefetching = this.fetchRequest.getRelationsForPrefetching();
		if (relationshipsForPrefetching != null && relationshipsForPrefetching.length > 0) {
			this.keyedModels.clear();
			this.ids = new Long[list.size()];

			int i = 0;
			for (E model : list) {
				this.keyedModels.put(model.primaryKey, model);
				this.ids[i++] = model.primaryKey;
			}

			// Loop through each relationship
			for (FetchRequest.Relation relation : relationshipsForPrefetching) {
				if (relation.relationName == null || relation.relationClass == null || relation.type == null) continue;

				switch (relation.type) {
					case HAS_ONE:
						this.prefetchHasOne(relation);
						break;
					case HAS_MANY:
						this.prefetchHasMany(relation, list);
						break;
				}
			}
		}
	}

	private void prefetchHasOne(FetchRequest.Relation relation) {
		ModelEntity relationEntity = this.store.getModelEntity(relation.relationClass);
		Query query = (new Query()).in(relation.relationName, this.ids);
		Pair<String, List<String>> conditions = query.compile(relationEntity, relationEntity.table);
		String[] whereArgs = conditions.second.toArray(new String[conditions.second.size()]);

		//noinspection unchecked
		List<String> columns = relationEntity.getColumns();
		String[] columnsArray = columns.toArray(new String[columns.size()]);

		String relationColumn = relationEntity.getColumnForFieldName(relation.relationName);
		Cursor relationCursor = this.store.getDatabase().query(relationEntity.table, columnsArray, conditions.first, whereArgs, null, null, relationColumn + " ASC");

		// Iterate through
		Map<Long, E> remainingModels = Maps.copy(this.keyedModels);
		Field field = this.store.getModelEntity(relation.relationClass).getField(relation.relationName);

		if (relationCursor.moveToFirst()) {
			int relationIndex = relationCursor.getColumnIndex(relationColumn);

			do {
				Model model = relationEntity.parseCursor(relationCursor, columns, false);
				Model parent = remainingModels.remove(relationCursor.getLong(relationIndex));

				if (parent != null) {
					try {
						field.set(model, parent);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}

					parent.setHasOne(relation.relationName, model);
				}
			} while (relationCursor.moveToNext());
		}

		// Set any models without relationship values to null
		for(Model model : remainingModels.values()) {
			model.setHasOne(relation.relationName, null);
		}

		relationCursor.close();
	}

	private void prefetchHasMany(FetchRequest.Relation relation, List<E> list) {
		ModelEntity relationEntity = this.store.getModelEntity(relation.relationClass);
		Query query = (new Query()).in(relation.relationName, this.ids);
		Pair<String, List<String>> conditions = query.compile(relationEntity, relationEntity.table);
		String[] whereArgs = conditions.second.toArray(new String[conditions.second.size()]);

		//noinspection unchecked
		List<String> columns = relationEntity.getColumns();
		String[] columnsArray = columns.toArray(new String[columns.size()]);

		String relationColumn = relationEntity.getColumnForFieldName(relation.relationName);
		Cursor relationCursor = this.store.getDatabase().query(relationEntity.table, columnsArray, conditions.first, whereArgs, null, null, relationColumn + " ASC");


		// Iterate through
		Map<Long, E> remainingModels = Maps.copy(this.keyedModels);
		Field field = this.store.getModelEntity(relation.relationClass).getField(relation.relationName);

		if(relationCursor.moveToFirst()) {
			int relationIndex = relationCursor.getColumnIndex(relationColumn);
			List<Model> many = null;
			long activeForeignId = -1;
			Model activeModel = null;

			do {
				Model model = relationEntity.parseCursor(relationCursor, columns, false);
				final long foreignId = relationCursor.getLong(relationIndex);

				// Check if we match or if we've hit a new group
				if (foreignId != activeForeignId) {
					if (activeModel != null) {
						// New hit, we'll set cache now
						activeModel.setHasMany(relation.relationName, Collections.unmodifiableList(many));
					}

					activeModel = null;
					many = null;
				}

				// Reset everything if we don't have a last model
				if (activeModel == null) {
					activeModel = remainingModels.remove(foreignId);
					many = new ArrayList<>();
				}

				// Establish the bidirectional relationships
				try {
					field.set(model, activeModel);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}

				// Push model
				many.add(model);

				// Set active foreign id
				activeForeignId = foreignId;
			} while (relationCursor.moveToNext());

			// Save the last model group
			if (activeModel != null) {
				activeModel.setHasMany(relation.relationName, Collections.unmodifiableList(many));
			}

			// Set any models without relationship values to empty
			for(Model model : remainingModels.values()) {
				model.setHasMany(relation.relationName, (List)Collections.emptyList());
			}
		}

		relationCursor.close();
	}

}

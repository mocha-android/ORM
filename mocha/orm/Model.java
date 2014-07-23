/**
 *  @author Shaun
 *  @date 4/1/14
 *  @copyright 2014 Mocha. All rights reserved.
 */
package mocha.orm;

import mocha.foundation.MObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Model extends MObject {

	long primaryKey = 0;
	final Store store;
	final ModelEntity modelEntity;

	private Map<String,Object> hasOne;
	private Map<String,List> hasMany;

	public Model(Store store) {
		this.store = store;
		this.modelEntity = this.store.getModelEntity(this);
	}

	@SuppressWarnings("unchecked")
	protected <E extends Model> E hasOne(Class<E> modelClass, String foreignKey) {
		if(this.hasOne == null) {
			this.hasOne = new HashMap<>();
		}

		if(!this.hasOne.containsKey(foreignKey)) {
			FetchRequest<E> fetchRequest = new FetchRequest<>(modelClass);
			fetchRequest.setQuery((new Query()).eq("this", this.getValue(foreignKey)));
			fetchRequest.setFetchLimit(1);
			this.hasOne.put(foreignKey, this.store.first(fetchRequest));
		}

		return (E)this.hasOne.get(foreignKey);
	}

	<E extends Model> void setHasOne(String foreignKey, E model) {
		if(this.hasOne == null) {
			this.hasOne = new HashMap<>();
		}

		this.hasOne.put(foreignKey, model);
	}

	protected void clearHasOneCache(String foreignKey) {
		if(this.hasOne != null) {
			this.hasOne.remove(foreignKey);
		}
	}

	protected <E extends Model> List<E> hasMany(Class<E> modelClass, String foreignKey) {
		if(this.hasMany == null) {
			this.hasMany = new HashMap<>();
		}

		if(!this.hasMany.containsKey(foreignKey)) {
			FetchRequest<E> fetchRequest = new FetchRequest<>(modelClass);
			fetchRequest.setQuery((new Query()).eq(foreignKey, this.primaryKey));
			this.hasMany.put(foreignKey, this.store.execute(fetchRequest));
		}

		//noinspection unchecked
		return this.hasMany.get(foreignKey);
	}

	<E extends Model> void setHasMany(String foreignKey, List<E> models) {
		if(this.hasMany == null) {
			this.hasMany = new HashMap<>();
		}

		this.hasMany.put(foreignKey, models);
	}

	protected void clearHasManyCache(String foreignKey) {
		if(this.hasMany != null) {
			this.hasMany.remove(foreignKey);
		}
	}

	protected <E extends Model> void deleteHasMany(Class<E> modelClass, String foreignKey) {
		if(this.primaryKey == 0) return;

		for(Model m : this.hasMany(modelClass, foreignKey)) {
			m.delete();
		}

		this.clearHasManyCache(foreignKey);
	}

	Object getValue(String fieldName) {
		Field field = this.modelEntity.getField(fieldName);

		try {
			return field.get(this);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	public void save() {
		this.store.save(this);
	}

	public void delete() {
		this.store.delete(this);
	}

	protected String toStringExtra() {
		return "primaryKey=" + this.primaryKey;
	}

	public long getPrimaryKey() {
		return this.primaryKey;
	}

	public Store getStore() {
		return this.store;
	}

	public ModelEntity getModelEntity() {
		return this.modelEntity;
	}

	static Field getPrimaryKeyField() {
		try {
			return Model.class.getDeclaredField("primaryKey");
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
